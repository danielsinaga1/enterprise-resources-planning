package id.enterpriseapps.erp.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stok opname: finalisasi memposting gerakan penyesuaian + jurnal selisih; pembatalan membalik semuanya. */
class StockOpnameFlowTest extends InventoryTestSupport {

    private static Map<String, Object> line(String itemId, Object actual, Object unitCost) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemId", itemId);
        m.put("actualStock", actual);
        if (unitCost != null) {
            m.put("unitCost", unitCost);
        }
        return m;
    }

    @Test
    void opname_posts_variance_and_cancel_reverses() throws Exception {
        asAdmin();
        String wh = createWarehouse("Gudang Opname Uji");
        JsonNode a = createItem("Opname Surplus", "Kg");
        JsonNode b = createItem("Opname Defisit", "Kg");
        JsonNode c = createItem("Opname Revaluasi", "Kg");
        JsonNode d = createItem("Opname Sama", "Kg");
        String ia = a.get("id").asText();
        String ib = b.get("id").asText();
        String ic = c.get("id").asText();
        String id = d.get("id").asText();
        movement(ia, wh, "In", "10", "1000", null);
        movement(ib, wh, "In", "10", "500", null);
        movement(ic, wh, "In", "4", "100", null);
        movement(id, wh, "In", "2", "100", null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", today());
        body.put("warehouseId", wh);
        body.put("notes", "opname uji");
        body.put("items", List.of(line(ia, 12, null), line(ib, 7, null), line(ic, 4, 150), line(id, 2, null)));
        Result created = call(post("/api/erp/stock-opname", body));
        assertEquals(201, created.status(), created.body().toString());
        JsonNode so = created.data();
        String soId = so.get("id").asText();
        assertEquals("Submitted", so.get("status").asText());
        assertEquals(wh, so.get("warehouseId").asText());
        assertEquals(4, so.get("items").size());
        assertTrue(so.get("createdBy").has("fullName"));
        assertFalse(so.has("warehouse"), "POST tidak meng-include warehouse");

        // Stok: surplus +2, defisit −3, revaluasi tanpa selisih qty, item sama tidak berubah.
        assertNum("12", itemStock(ia));
        assertNum("12", whStock(ia, wh));
        assertNum("7", itemStock(ib));
        assertNum("7", whStock(ib, wh));
        assertNum("4", itemStock(ic));
        assertNum("150", itemAvg(ic));
        assertNum("2", itemStock(id));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE reference_type = 'StockOpname' "
                + "AND reference_id = ?", Integer.class, soId));

        // Satu jurnal JV-<nomor>: surplus 2000, defisit 1500, revaluasi 4×(150−100)=200.
        String number = so.get("opnameNumber").asText();
        String jeId = jdbc.queryForObject("SELECT id FROM journal_entries WHERE entry_number = ?", String.class,
                "JV-" + number);
        assertJournalBalanced(jeId);
        BigDecimal inv = jdbc.queryForObject("SELECT SUM(ji.debit) - SUM(ji.credit) FROM journal_entry_accounts ji "
                + "JOIN accounts a ON a.id = ji.account_id WHERE ji.journal_entry_id = ? AND a.account_number = '1.4.0'",
                BigDecimal.class, jeId);
        assertNum("700", inv); // +2000 −1500 +200
        assertBalanceMatchesLedger("1.4.0");
        assertBalanceMatchesLedger("6.38");

        // Daftar (list kit: page → warehouses) dan detail.
        JsonNode list = ok(get("/api/erp/stock-opname?page=1&warehouseId=" + wh));
        JsonNode row = list.get("data").get(0);
        assertEquals(soId, row.get("id").asText());
        assertEquals(4, row.get("_count").get("items").asInt());
        assertEquals(2, row.get("itemsWithDiff").asInt());
        assertNum("3500", row.get("totalVariance"));
        assertEquals(wh, row.get("warehouses").get(0).get("id").asText());
        assertEquals(1, row.get("warehouses").size());
        JsonNode legacy = ok(get("/api/erp/stock-opname?warehouseId=" + wh));
        assertFalse(legacy.get("data").get(0).has("warehouses"));
        JsonNode detail = ok(get("/api/erp/stock-opname/" + soId));
        assertEquals(wh, detail.get("warehouse").get("id").asText());
        assertTrue(detail.get("items").get(0).get("item").has("averageCost"));
        Result badStatus = call(get("/api/erp/stock-opname?status=Posted"));
        assertEquals("Status stok opname tidak dikenal.", badStatus.error());

        // Batal → stok kembali, gerakan & jurnal dibatalkan, saldo kembali sesuai ledger.
        asUser("staf.produksi.opname@test.local", "ERP_PRODUCTION");
        assertEquals(403, call(put("/api/erp/stock-opname/" + soId + "/cancel", Map.of())).status());
        asAdmin();
        JsonNode cancelled = ok(put("/api/erp/stock-opname/" + soId + "/cancel", Map.of()));
        assertEquals("Stok opname dibatalkan.", cancelled.get("message").asText());
        assertNum("10", itemStock(ia));
        assertNum("10", whStock(ia, wh));
        assertNum("10", itemStock(ib));
        assertNum("10", whStock(ib, wh));
        assertEquals("Cancelled", journalStatus(jeId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE reference_type = 'StockOpname' "
                + "AND reference_id = ? AND is_cancelled = false", Integer.class, soId));
        assertBalanceMatchesLedger("1.4.0");
        assertBalanceMatchesLedger("6.38");
        Result again = call(put("/api/erp/stock-opname/" + soId + "/cancel", Map.of()));
        assertEquals("Stok opname sudah dibatalkan.", again.error());
    }

    @Test
    void opname_validation_rules() throws Exception {
        asAdmin();
        JsonNode item = createItem("Opname Validasi", "Kg");
        Map<String, Object> neg = new LinkedHashMap<>();
        neg.put("date", today());
        neg.put("items", List.of(line(item.get("id").asText(), -1, null)));
        Result r = call(post("/api/erp/stock-opname", neg));
        assertEquals(400, r.status());
        assertEquals("Stok aktual \"Opname Validasi\" tidak boleh negatif.", r.error());

        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("date", today());
        empty.put("items", List.of());
        assertEquals("Minimal satu item stok opname.", call(post("/api/erp/stock-opname", empty)).error());

        // Tanpa selisih & tanpa gudang → gudang default tetap tercatat di header dan baris.
        Map<String, Object> noDiff = new LinkedHashMap<>();
        noDiff.put("date", today());
        noDiff.put("items", List.of(line(item.get("id").asText(), 0, null)));
        JsonNode so = ok(post("/api/erp/stock-opname", noDiff));
        assertEquals(defaultWarehouseId(), so.get("warehouseId").asText());
        assertEquals(defaultWarehouseId(), so.get("items").get(0).get("warehouseId").asText());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM journal_entries WHERE entry_number = ?",
                Integer.class, "JV-" + so.get("opnameNumber").asText()));
    }
}
