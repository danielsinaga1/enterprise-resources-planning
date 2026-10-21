package id.enterpriseapps.erp.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Transfer antar gudang: stok pindah, total item tetap, pasangan gerakan tanpa GL, pembatalan memulihkan. */
class StockTransferFlowTest extends InventoryTestSupport {

    private Map<String, Object> transfer(String from, String to, String itemId, Object qty) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("itemId", itemId);
        line.put("quantity", qty);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", today());
        body.put("fromWarehouseId", from);
        body.put("toWarehouseId", to);
        body.put("notes", "uji transfer");
        body.put("driverName", "Joko");
        body.put("items", List.of(line));
        return body;
    }

    @Test
    void transfer_moves_stock_between_warehouses_and_cancel_restores() throws Exception {
        asAdmin();
        String whA = createWarehouse("Gudang Transfer A");
        String whB = createWarehouse("Gudang Transfer B");
        JsonNode item = createItem("Beras Transfer", "Kg");
        String itemId = item.get("id").asText();
        movement(itemId, whA, "In", "100", "12000", null);
        long glBefore = jdbc.queryForObject("SELECT count(*) FROM journal_entries", Long.class);

        Result created = call(post("/api/erp/stock-transfers", transfer(whA, whB, itemId, 30)));
        assertEquals(201, created.status(), created.body().toString());
        JsonNode t = created.data();
        String tid = t.get("id").asText();
        assertEquals("Submitted", t.get("status").asText());
        assertTrue(t.get("transferNumber").asText().matches("^ST-\\d{6}-\\d{4}$"));
        assertEquals(1, t.get("items").size());
        assertNum("70", whStock(itemId, whA));
        assertNum("30", whStock(itemId, whB));
        assertNum("100", itemStock(itemId));
        // Tanpa dampak GL.
        assertEquals(glBefore, jdbc.queryForObject("SELECT count(*) FROM journal_entries", Long.class));

        List<Map<String, Object>> movs = jdbc.queryForList("SELECT movement_type, warehouse_id, quantity, unit_cost, "
                + "is_cancelled FROM stock_movements WHERE reference_type = 'StockTransfer' AND reference_id = ? "
                + "ORDER BY movement_type", tid);
        assertEquals(2, movs.size());
        assertEquals("TransferIn", movs.get(0).get("movement_type"));
        assertEquals(whB, movs.get(0).get("warehouse_id"));
        assertEquals("TransferOut", movs.get(1).get("movement_type"));
        assertEquals(whA, movs.get(1).get("warehouse_id"));
        assertNum("12000", (java.math.BigDecimal) movs.get(1).get("unit_cost"));

        // Stok asal tidak cukup / gudang sama.
        Result low = call(post("/api/erp/stock-transfers", transfer(whA, whB, itemId, 500)));
        assertEquals(400, low.status());
        assertEquals("Stok item \"Beras Transfer\" di Gudang Transfer A hanya 70.000 Kg, tidak cukup untuk transfer 500.",
                low.error());
        Result same = call(post("/api/erp/stock-transfers", transfer(whA, whA, itemId, 1)));
        assertEquals(400, same.status());
        assertEquals("Gudang asal dan tujuan harus berbeda.", same.error());

        // Daftar (filter gudang asal/tujuan + search nama gudang), detail, edit header, tandai cetak.
        JsonNode list = ok(get("/api/erp/stock-transfers?warehouseId=" + whB + "&search=transfer a&sort=-date"));
        assertEquals(1, list.get("total").asInt());
        JsonNode row = list.get("data").get(0);
        assertEquals(3, row.get("fromWarehouse").size());
        assertEquals("Beras Transfer", row.get("items").get(0).get("item").get("name").asText());
        assertTrue(row.get("user").has("fullName"));
        JsonNode detail = ok(get("/api/erp/stock-transfers/" + tid));
        assertTrue(detail.get("toWarehouse").has("isDefault"));
        assertTrue(detail.get("items").get(0).get("item").has("averageCost"));
        JsonNode patched = ok(patch("/api/erp/stock-transfers/" + tid, Map.of("nomorMobil", "B 1234 XY")));
        assertEquals("B 1234 XY", patched.get("nomorMobil").asText());
        assertEquals("Joko", patched.get("driverName").asText());
        JsonNode printed = ok(post("/api/erp/stock-transfers/" + tid + "/mark-printed", Map.of()));
        assertTrue(printed.hasNonNull("printedAt"));

        // Batal → kedua gudang pulih, gerakan berpasangan dibatalkan.
        JsonNode cancelled = ok(patch("/api/erp/stock-transfers/" + tid + "/cancel", Map.of()));
        assertEquals("Cancelled", cancelled.get("status").asText());
        assertNum("100", whStock(itemId, whA));
        assertNum("0", whStock(itemId, whB));
        assertNum("100", itemStock(itemId));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE reference_type = 'StockTransfer' "
                + "AND reference_id = ? AND is_cancelled = true", Integer.class, tid));
        Result again = call(patch("/api/erp/stock-transfers/" + tid + "/cancel", Map.of()));
        assertEquals(400, again.status());
        assertEquals("Transfer sudah dibatalkan.", again.error());
        Result editCancelled = call(patch("/api/erp/stock-transfers/" + tid, Map.of("notes", "x")));
        assertEquals("Hanya transfer status Submitted yang bisa diedit.", editCancelled.error());
    }
}
