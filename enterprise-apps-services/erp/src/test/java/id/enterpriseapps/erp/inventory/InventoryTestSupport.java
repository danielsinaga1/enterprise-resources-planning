package id.enterpriseapps.erp.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.support.ErpIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Helper bersama uji integrasi paket inventori (data unik per test, DB dipakai bersama per JVM). */
abstract class InventoryTestSupport extends ErpIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** Kode unik pendek (huruf besar, aman untuk kode gudang/item). */
    protected static String uniq(String prefix) {
        return prefix + "-" + Long.toString(System.nanoTime(), 36).toUpperCase() + SEQ.incrementAndGet();
    }

    protected static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    protected JsonNode createItem(String name, String unit) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", uniq("ITM"));
        body.put("name", name);
        body.put("unit", unit);
        Result r = call(post("/api/erp/inventory/items", body));
        assertEquals(201, r.status(), r.body().toString());
        return r.data();
    }

    protected String createWarehouse(String name) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", uniq("wh").replace("-", "_"));
        body.put("name", name);
        Result r = call(post("/api/erp/warehouses", body));
        assertEquals(201, r.status(), r.body().toString());
        return r.data().get("id").asText();
    }

    protected String defaultWarehouseId() {
        return jdbc.queryForObject("SELECT id FROM warehouses WHERE is_default = true AND is_active = true LIMIT 1",
                String.class);
    }

    protected JsonNode movement(String itemId, String warehouseId, String type, String qty, String unitCost,
                                String notes) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemId", itemId);
        if (warehouseId != null) {
            body.put("warehouseId", warehouseId);
        }
        body.put("movementType", type);
        body.put("quantity", qty);
        if (unitCost != null) {
            body.put("unitCost", unitCost);
        }
        body.put("date", today());
        if (notes != null) {
            body.put("notes", notes);
        }
        Result r = call(post("/api/erp/inventory/movements", body));
        assertEquals(201, r.status(), r.body().toString());
        return r.data();
    }

    protected BigDecimal itemStock(String itemId) {
        return jdbc.queryForObject("SELECT current_stock FROM inventory_items WHERE id = ?", BigDecimal.class, itemId);
    }

    protected BigDecimal itemAvg(String itemId) {
        return jdbc.queryForObject("SELECT average_cost FROM inventory_items WHERE id = ?", BigDecimal.class, itemId);
    }

    protected BigDecimal whStock(String itemId, String warehouseId) {
        List<BigDecimal> rows = jdbc.queryForList(
                "SELECT current_stock FROM inventory_stocks WHERE item_id = ? AND warehouse_id = ?",
                BigDecimal.class, itemId, warehouseId);
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    protected String accountId(String number) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", String.class, number);
    }

    protected static void assertNum(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    protected static void assertNum(String expected, JsonNode actual) {
        assertNotNull(actual);
        assertNum(expected, actual.decimalValue());
    }

    /** Jurnal seimbang (Σdebit = Σkredit) baik di baris jurnal maupun ledger. */
    protected void assertJournalBalanced(String journalEntryId) {
        BigDecimal items = jdbc.queryForObject("SELECT SUM(debit) - SUM(credit) FROM journal_entry_accounts "
                + "WHERE journal_entry_id = ?", BigDecimal.class, journalEntryId);
        BigDecimal ledger = jdbc.queryForObject("SELECT SUM(debit) - SUM(credit) FROM accounting_ledger_entries "
                + "WHERE reference_id = ?", BigDecimal.class, journalEntryId);
        assertEquals(0, items.signum());
        assertEquals(0, ledger.signum());
    }

    /** Saldo cache akun = jumlah ledger aktif (tidak ada drift setelah posting/pembatalan). */
    protected void assertBalanceMatchesLedger(String accountNumber) {
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

    /** Baris jurnal (akun, debit, kredit) untuk satu jurnal. */
    protected List<Map<String, Object>> journalLines(String journalEntryId) {
        return jdbc.queryForList("SELECT a.account_number AS acc, ji.debit AS debit, ji.credit AS credit, "
                + "ji.description AS description FROM journal_entry_accounts ji JOIN accounts a ON a.id = ji.account_id "
                + "WHERE ji.journal_entry_id = ? ORDER BY ji.debit DESC", journalEntryId);
    }

    protected String journalStatus(String journalEntryId) {
        return jdbc.queryForObject("SELECT status FROM journal_entries WHERE id = ?", String.class, journalEntryId);
    }
}
