package id.neravoxa.erp.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CRUD item stok + mode daftar/summary + gudang (master, detail, snapshot, hapus). */
class InventoryItemFlowTest extends InventoryTestSupport {

    @Test
    void item_crud_list_modes_and_summary() throws Exception {
        asAdmin();
        String code = uniq("BRS");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", "Beras Premium Uji");
        body.put("unit", "Kg");
        body.put("category", "Beras Uji " + code);
        body.put("minimumStock", 5);
        Result created = call(post("/api/erp/inventory/items", body));
        assertEquals(201, created.status());
        JsonNode item = created.data();
        String id = item.get("id").asText();
        assertEquals(code, item.get("code").asText());
        assertTrue(item.has("account") && item.get("account").isNull(), "include account → null tanpa akun");
        assertFalse(item.has("movements"));
        assertNum("5", item.get("minimumStock"));

        // Kode duplikat & validasi body.
        Result dup = call(post("/api/erp/inventory/items", body));
        assertEquals(400, dup.status());
        assertEquals("Kode item '" + code + "' sudah digunakan.", dup.error());
        Map<String, Object> noCode = new HashMap<>(body);
        noCode.put("code", "");
        assertEquals("Kode item wajib diisi.", call(post("/api/erp/inventory/items", noCode)).error());

        // Update parsial: kategori dikosongkan (clearable), akun diisi, nama tetap bila tidak dikirim.
        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("category", "  ");
        upd.put("accountId", accountId("1.4.0"));
        JsonNode updated = ok(put("/api/erp/inventory/items/" + id, upd));
        assertTrue(updated.get("category").isNull());
        assertEquals("Beras Premium Uji", updated.get("name").asText());
        assertEquals("1.4.0", updated.get("account").get("accountNumber").asText());
        assertEquals(3, updated.get("account").size(), "account select { id, name, accountNumber }");
        Result missing = call(put("/api/erp/inventory/items/00000000-0000-4000-8000-000000000000", upd));
        assertEquals(404, missing.status());
        assertEquals("Item stok tidak ditemukan.", missing.error());

        // Mode lama (picker) dan mode daftar (sort/stockStatus/category).
        JsonNode legacy = ok(get("/api/erp/inventory/items?search=" + code.toLowerCase() + "&isActive=true"));
        assertEquals(1, legacy.get("total").asInt());
        assertEquals(100, legacy.get("limit").asInt());
        assertFalse(legacy.get("data").get(0).has("stockStatus"));
        JsonNode listMode = ok(get("/api/erp/inventory/items?search=" + code + "&sort=code&stockStatus=out"));
        assertEquals(1, listMode.get("total").asInt());
        assertEquals("out", listMode.get("data").get(0).get("stockStatus").asText());
        Result badStatus = call(get("/api/erp/inventory/items?stockStatus=empty"));
        assertEquals(400, badStatus.status());
        assertEquals("Status stok tidak dikenal.", badStatus.error());

        // Stok di gudang lain → per gudang currentStock dari inventory_stocks.
        String wh = createWarehouse("Gudang Item Uji");
        movement(id, wh, "In", "3", "100", null);
        JsonNode perWh = ok(get("/api/erp/inventory/items?search=" + code + "&warehouseId=" + wh));
        assertNum("3", perWh.get("data").get(0).get("currentStock"));
        JsonNode otherWh = ok(get("/api/erp/inventory/items?search=" + code + "&warehouseId=" + defaultWarehouseId()));
        assertNum("0", otherWh.get("data").get(0).get("currentStock"));

        JsonNode summary = ok(get("/api/erp/inventory/items/summary?search=" + code));
        assertEquals(1, summary.get("count").asInt());
        assertEquals(1, summary.get("lowCount").asInt());
        assertNum("300", summary.get("stockValue"));
        assertTrue(summary.get("categories").isArray());

        // Hapus ditolak selama ada mutasi aktif.
        Result blocked = call(delete("/api/erp/inventory/items/" + id));
        assertEquals(400, blocked.status());
        assertEquals("Item memiliki 1 mutasi stok aktif. Batalkan semua mutasi terlebih dahulu atau nonaktifkan item.",
                blocked.error());

        // Item tanpa mutasi bisa dihapus.
        JsonNode other = createItem("Item Hapus", "Pcs");
        assertEquals("Item stok dihapus.", ok(delete("/api/erp/inventory/items/" + other.get("id").asText()))
                .get("message").asText());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM inventory_items WHERE id = ?", Integer.class,
                other.get("id").asText()));
    }

    @Test
    void warehouse_master_detail_snapshot_and_delete_rules() throws Exception {
        asAdmin();
        String wh = createWarehouse("Gudang Master Uji");
        JsonNode list = ok(get("/api/erp/warehouses?active=true"));
        JsonNode row = null;
        for (JsonNode n : list.get("data")) {
            if (wh.equals(n.get("id").asText())) {
                row = n;
            }
        }
        assertTrue(row != null && row.has("itemCount") && row.has("totalStock"));
        assertEquals(row.get("code").asText().toUpperCase(), row.get("code").asText());

        Result badCode = call(post("/api/erp/warehouses", Map.of("code", "A B", "name", "X")));
        assertEquals(400, badCode.status());
        assertEquals("Kode hanya huruf, angka, dash, underscore.", badCode.error());

        // Update parsial tidak mereset field lain.
        JsonNode updated = ok(put("/api/erp/warehouses/" + wh, Map.of("location", "Karawang")));
        assertEquals("Karawang", updated.get("location").asText());
        assertTrue(updated.get("isActive").asBoolean());
        assertFalse(updated.get("isDefault").asBoolean());

        JsonNode item = createItem("Item Gudang Uji", "Kg");
        movement(item.get("id").asText(), wh, "In", "7", "1000", null);
        JsonNode detail = ok(get("/api/erp/warehouses/" + wh));
        assertEquals(1, detail.get("stocks").size());
        assertNum("7", detail.get("stocks").get(0).get("currentStock"));
        assertEquals(item.get("id").asText(), detail.get("stocks").get(0).get("item").get("id").asText());
        assertEquals(404, call(get("/api/erp/warehouses/00000000-0000-4000-8000-000000000000")).status());

        JsonNode snap = ok(get("/api/erp/warehouses/stock-snapshot?date=" + today() + "&warehouseIds=" + wh));
        assertEquals(today(), snap.get("asOfDate").asText());
        assertNum("7", snap.get("warehouses").get(0).get("items").get(0).get("qty"));
        JsonNode past = ok(get("/api/erp/warehouses/stock-snapshot?date=2000-01-01&warehouseIds=" + wh));
        assertEquals(0, past.get("warehouses").get(0).get("items").size());
        Result badDate = call(get("/api/erp/warehouses/stock-snapshot?date=bukan-tanggal"));
        assertEquals(400, badDate.status());
        assertEquals("Parameter date tidak valid.", badDate.error());

        // Hapus: masih ada saldo → ditolak; gudang default → ditolak; gudang kosong → sukses.
        Result withStock = call(delete("/api/erp/warehouses/" + wh));
        assertEquals(400, withStock.status());
        assertEquals("Gudang masih punya 1 item dengan saldo. Pindahkan stok ke gudang lain dulu.", withStock.error());
        Result def = call(delete("/api/erp/warehouses/" + defaultWarehouseId()));
        assertEquals("Gudang default tidak bisa dihapus. Set gudang lain sebagai default dulu.", def.error());
        String empty = createWarehouse("Gudang Kosong Uji");
        assertTrue(ok(delete("/api/erp/warehouses/" + empty)).get("success").asBoolean());
    }
}
