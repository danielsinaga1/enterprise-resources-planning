package id.enterpriseapps.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.support.ErpIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Helper data uji modul produksi (data unik per test; DB dipakai bersama satu JVM). */
abstract class ProductionTestSupport extends ErpIntegrationTest {

    static final String RUNS = "/api/erp/inventory/production-runs";
    static final String DRAFTS = "/api/erp/inventory/production-runs/drafts";
    static final String ADMIN_EMAIL = "erp.admin@test.local";

    static String uid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Item persediaan baru lewat API inventori (sebagai admin). */
    String item(String name) throws Exception {
        Result r = call(post("/api/erp/inventory/items", map("code", "PRD-" + uid(), "name", name + " " + uid(), "unit", "kg")));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.data().get("id").asText();
    }

    String itemName(String itemId) {
        return jdbc.queryForObject("SELECT name FROM inventory_items WHERE id = ?", String.class, itemId);
    }

    String wh() {
        return jdbc.queryForObject("SELECT id FROM warehouses WHERE code = 'GDG-UTAMA'", String.class);
    }

    /** Stok masuk (gerakan In + jurnal saldo awal) lewat API gerakan stok. */
    void stockIn(String itemId, String qty, String unitCost) throws Exception {
        Result r = call(post("/api/erp/inventory/movements", map("itemId", itemId, "warehouseId", wh(), "movementType", "In",
                "quantity", qty, "unitCost", unitCost, "date", today())));
        assertEquals(201, r.status(), String.valueOf(r.body()));
    }

    /** Stok keluar manual (mis. output terpakai penjualan) lewat API gerakan stok. */
    void stockOut(String itemId, String qty) throws Exception {
        Result r = call(post("/api/erp/inventory/movements", map("itemId", itemId, "warehouseId", wh(), "movementType", "Out",
                "quantity", qty, "date", today())));
        assertEquals(201, r.status(), String.valueOf(r.body()));
    }

    String userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?)", String.class, email);
    }

    BigDecimal stock(String itemId) {
        return jdbc.queryForObject("SELECT current_stock FROM inventory_items WHERE id = ?", BigDecimal.class, itemId);
    }

    BigDecimal whStock(String itemId) {
        List<BigDecimal> r = jdbc.queryForList("SELECT current_stock FROM inventory_stocks WHERE item_id = ? AND warehouse_id = ?",
                BigDecimal.class, itemId, wh());
        return r.isEmpty() ? BigDecimal.ZERO : r.get(0);
    }

    BigDecimal avg(String itemId) {
        return jdbc.queryForObject("SELECT average_cost FROM inventory_items WHERE id = ?", BigDecimal.class, itemId);
    }

    /** Σ debit / Σ kredit baris jurnal per nomor akun untuk satu nomor jurnal. */
    BigDecimal jeSum(String entryNumber, String accountNumber, String side) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(ji." + side + "), 0) FROM journal_entry_accounts ji "
                        + "JOIN journal_entries j ON j.id = ji.journal_entry_id JOIN accounts a ON a.id = ji.account_id "
                        + "WHERE j.entry_number = ? AND a.account_number = ?",
                BigDecimal.class, entryNumber, accountNumber);
    }

    /** {Σ debit, Σ kredit} baris jurnal + {Σ debit, Σ kredit} ledger aktif-nya. */
    BigDecimal[] jeTotals(String entryNumber) {
        Map<String, Object> r = jdbc.queryForMap("SELECT COALESCE(SUM(ji.debit),0) d, COALESCE(SUM(ji.credit),0) c "
                + "FROM journal_entry_accounts ji JOIN journal_entries j ON j.id = ji.journal_entry_id WHERE j.entry_number = ?",
                entryNumber);
        Map<String, Object> l = jdbc.queryForMap("SELECT COALESCE(SUM(le.debit),0) d, COALESCE(SUM(le.credit),0) c "
                + "FROM accounting_ledger_entries le JOIN journal_entries j ON j.id = le.reference_id "
                + "WHERE j.entry_number = ? AND le.is_cancelled = false", entryNumber);
        return new BigDecimal[]{(BigDecimal) r.get("d"), (BigDecimal) r.get("c"), (BigDecimal) l.get("d"), (BigDecimal) l.get("c")};
    }

    String jeStatus(String entryNumber) {
        List<String> r = jdbc.queryForList("SELECT status FROM journal_entries WHERE entry_number = ?", String.class, entryNumber);
        return r.isEmpty() ? null : r.get(0);
    }

    int activeMovements(String runId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE reference_type = 'ProductionRun' "
                + "AND reference_id = ? AND is_cancelled = false", Integer.class, runId);
    }

    int cancelledMovements(String runId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE reference_type = 'ProductionRun' "
                + "AND reference_id = ? AND is_cancelled = true", Integer.class, runId);
    }

    /** Saldo cache akun = jumlah ledger aktif (tanpa drift setelah posting/edit/pembatalan). */
    void assertBalanceMatchesLedger(String accountNumber) {
        Map<String, Object> row = jdbc.queryForMap("SELECT a.balance AS bal, a.root_type AS rt, "
                + "COALESCE(SUM(le.debit),0) AS d, COALESCE(SUM(le.credit),0) AS c FROM accounts a "
                + "LEFT JOIN accounting_ledger_entries le ON le.account_id = a.id AND le.is_cancelled = false "
                + "WHERE a.account_number = ? GROUP BY a.id, a.balance, a.root_type", accountNumber);
        BigDecimal d = (BigDecimal) row.get("d");
        BigDecimal c = (BigDecimal) row.get("c");
        String rt = (String) row.get("rt");
        BigDecimal expected = "ASSET".equals(rt) || "EXPENSE".equals(rt) ? d.subtract(c) : c.subtract(d);
        assertNum(expected.toPlainString(), (BigDecimal) row.get("bal"));
    }

    static void assertNum(String expected, BigDecimal actual) {
        assertNotNull(actual, "expected " + expected + " but was null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    static void assertNum(String expected, JsonNode actual) {
        assertNotNull(actual, "expected " + expected + " but node missing");
        assertNum(expected, actual.decimalValue());
    }

    /** POST /production-runs (draft) → data {draft, warnings}. */
    JsonNode createDraft(Map<String, Object> body) throws Exception {
        Result r = call(post(RUNS, body));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.data();
    }

    /** Draft lalu posting langsung (role bypass) → data run. */
    JsonNode submitRun(Map<String, Object> body) throws Exception {
        String draftId = createDraft(body).get("draft").get("id").asText();
        Result r = call(post(DRAFTS + "/" + draftId + "/post", map()));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.data();
    }

    /** Item uji: GKP (bahan, stok 1000 kg @ 5.000), Beras (produk utama), Sekam (produk samping) + mesin & proses. */
    record Scenario(String gkp, String beras, String sekam, String machine, String process) {
    }

    Scenario scenario() throws Exception {
        String gkp = item("GKP");
        String beras = item("Beras");
        String sekam = item("Sekam");
        stockIn(gkp, "1000", "5000");
        Result m = call(post("/api/erp/inventory/machines", map("code", "HL-" + uid(), "name", "Huller", "idealRatePerHour", 100)));
        assertEquals(201, m.status(), String.valueOf(m.body()));
        Result p = call(post("/api/erp/inventory/production-processes", map("name", "Giling " + uid())));
        assertEquals(201, p.status(), String.valueOf(p.body()));
        return new Scenario(gkp, beras, sekam, m.data().get("id").asText(), p.data().get("id").asText());
    }

    /**
     * Run baku: 1000 kg GKP → 600 kg Beras (harga dari HPP loaded) + 200 kg Sekam (NRV 1.000), biaya giling
     * 100/kg input + kemas 50/kg output, shift 08–16 istirahat 12–13, satu mesin 100 kg/jam dengan downtime 30 menit.
     */
    Map<String, Object> standardBody(Scenario s) {
        String day = today();
        return map("date", day, "warehouseId", wh(), "notes", "Run uji",
                "inputs", List.of(input(s.gkp(), 1000)),
                "outputs", List.of(output(s.beras(), 600, null, false), output(s.sekam(), 200, 1000, true)),
                "costs", List.of(
                        map("description", "Biaya giling", "costSide", "InputSide", "basis", "InputQty", "rate", 100),
                        map("description", "Biaya kemas", "costSide", "OutputSide", "basis", "OutputQty", "rate", 50)),
                "processIds", List.of(s.process(), s.process()),
                "plannedStart", day + "T08:00:00.000Z", "plannedEnd", day + "T16:00:00.000Z",
                "breakStart", day + "T12:00:00.000Z", "breakEnd", day + "T13:00:00.000Z",
                "runMachines", List.of(map("machineId", s.machine(), "startAt", day + "T08:00:00.000Z", "endAt", day + "T16:00:00.000Z")),
                "downtimes", List.of(map("machineId", s.machine(), "category", "Breakdown", "durationMin", 30)),
                "shift", "1");
    }

    /** Body edit: 1000 kg GKP → 650 kg Beras + 150 kg Sekam (NRV 1.000), tanpa biaya/OEE. */
    Map<String, Object> editedBody(Scenario s) {
        return map("date", today(), "warehouseId", wh(), "notes", "Run uji revisi",
                "inputs", List.of(input(s.gkp(), 1000)),
                "outputs", List.of(output(s.beras(), 650, null, false), output(s.sekam(), 150, 1000, true)));
    }

    static Map<String, Object> input(String itemId, Object qty) {
        return map("itemId", itemId, "quantity", qty);
    }

    static Map<String, Object> output(String itemId, Object qty, Object unitPrice, boolean byProduct) {
        Map<String, Object> m = map("itemId", itemId, "quantity", qty, "isByProduct", byProduct);
        if (unitPrice != null) {
            m.put("unitPrice", unitPrice);
        }
        return m;
    }
}
