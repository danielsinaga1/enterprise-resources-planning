package id.enterpriseapps.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Permintaan barang GA: create → edit (DRAFT) → submit → approve → pengadaan → terima barang (stok GA
 * bertambah atomik, movement IN) → FULFILLED; jalur reject &amp; cancel; daftar/ringkasan/detail.
 */
class GaMaterialRequestFlowTest extends GaOpsTestSupport {

    private static Map<String, Object> item(String name, String uom, Object qty, String inventoryItemId) {
        Map<String, Object> m = body("itemName", name, "uom", uom, "quantity", qty);
        if (inventoryItemId != null) {
            m.put("inventoryItemId", inventoryItemId);
        }
        return m;
    }

    @Test
    void create_approve_receive_into_stock_and_fulfill() throws Exception {
        String me = adminId();
        String t = tag();
        String asset = asset(t, "MESIN_PRODUKSI");
        String cat = invCategory(t);
        String loc = location(t, "G");
        String linked = ok(post(INV + "/items", body("sku", "MR-SKU-" + t, "name", "Bearing " + t, "categoryId", cat,
                "uom", "pcs"))).get("id").asText();

        // Validasi body.
        expectError(400, "Minimal satu item.", post(MR, body("assetId", asset, "items", List.of())));
        expectError(400, "Invalid input: expected array, received undefined", post(MR, body("assetId", asset)));
        expectError(400, "Satuan (UOM) wajib diisi.", post(MR, body("items", List.of(item("Baut", "", 1, null)))));
        expectError(400, "Qty harus lebih dari 0.", post(MR, body("items", List.of(item("Baut", "pcs", 0, null)))));
        expectError(400, "Aset tidak ditemukan.", post(MR, body("assetId", "tidak-ada", "items", List.of(item("Baut", "pcs", 1, null)))));
        expectError(400, "Sebagian item inventory tidak ditemukan.",
                post(MR, body("items", List.of(item("Baut", "pcs", 1, "tidak-ada")))));

        Result created = call(post(MR, body("assetId", asset, "machineTarget", "Huller " + t, "items",
                List.of(item("Bearing " + t, "pcs", 4, linked), item("Sabuk karet " + t, "roll", 2, null)))));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        String mrId = created.data().get("id").asText();
        String code = created.data().get("code").asText();
        assertTrue(code.matches("MR-" + Instant.now().atZone(ZoneOffset.UTC).getYear() + "-\\d{4}"), code);

        // Edit hanya DRAFT (ganti seluruh item).
        ok(put(MR + "/" + mrId, body("assetId", asset, "machineTarget", "Huller " + t, "notes", "Urgent", "items",
                List.of(item("Bearing " + t, "pcs", 5, linked), item("Sabuk karet " + t, "roll", 2, null)))));
        JsonNode d = ok(get(MR + "/" + mrId));
        assertEquals("DRAFT", d.get("status").asText());
        assertEquals("Urgent", d.get("notes").asText());
        assertEquals(2, d.get("items").size());
        assertEquals(me, d.get("requester").get("id").asText());
        assertEquals("AST-" + t, d.get("asset").get("code").asText());
        assertDec("5", d.get("items").get(0).get("quantity").asText());
        assertEquals("MR-SKU-" + t, d.get("items").get(0).get("inventoryItem").get("sku").asText());
        String bearingItem = d.get("items").get(0).get("id").asText();
        String beltItem = d.get("items").get(1).get("id").asText();

        expectError(400, "Barang hanya bisa diterima saat MR berstatus APPROVED (sekarang DRAFT).",
                patch(MR + "/" + mrId + "/items/" + bearingItem + "/receive", body("receivedQty", 5, "locationId", loc)));
        expectError(400, "Tidak bisa approve MR berstatus DRAFT.", patch(MR + "/" + mrId + "/approve", Map.of()));
        assertEquals("SUBMITTED", ok(patch(MR + "/" + mrId + "/submit", Map.of())).get("status").asText());
        expectError(400, "Hanya MR berstatus DRAFT yang bisa diubah.",
                put(MR + "/" + mrId, body("items", List.of(item("X", "pcs", 1, null)))));
        assertEquals("APPROVED", ok(patch(MR + "/" + mrId + "/approve", Map.of())).get("status").asText());

        // Pengadaan per item (non-stok).
        JsonNode proc = ok(patch(MR + "/" + mrId + "/items/" + beltItem + "/procurement",
                body("vendorName", "Toko Teknik", "unitPrice", 125000, "poNumber", "PO-" + t, "procurementStatus", "ORDERED")));
        assertEquals("ORDERED", proc.get("procurementStatus").asText());
        expectError(400, "Status RECEIVED hanya bisa lewat aksi Terima Barang (receive).",
                patch(MR + "/" + mrId + "/items/" + beltItem + "/procurement", body("procurementStatus", "RECEIVED")));
        expectError(400, "Invalid input: expected number, received null",
                patch(MR + "/" + mrId + "/items/" + beltItem + "/procurement", body("unitPrice", null)));

        // Terima item tertaut → stok GA bertambah atomik + movement IN.
        expectError(400, "Qty diterima harus lebih dari 0.",
                patch(MR + "/" + mrId + "/items/" + bearingItem + "/receive", body("receivedQty", 0, "locationId", loc)));
        expectError(400, "Lokasi gudang tidak ditemukan.",
                patch(MR + "/" + mrId + "/items/" + bearingItem + "/receive", body("receivedQty", 5, "locationId", "x")));
        JsonNode r1 = ok(patch(MR + "/" + mrId + "/items/" + bearingItem + "/receive", body("receivedQty", 5, "locationId", loc)));
        assertEquals(linked, r1.get("inventoryItemId").asText());
        assertFalse(r1.get("fulfilled").asBoolean());
        assertDec("5", stockAt(linked, loc));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ga_inventory_movements WHERE item_id = ? AND type = 'IN' "
                + "AND reference_no = ? AND to_location_id = ?", Integer.class, linked, "MR_" + code, loc));
        expectError(400, "Item ini sudah diterima.",
                patch(MR + "/" + mrId + "/items/" + bearingItem + "/receive", body("receivedQty", 1, "locationId", loc)));

        // Item tanpa tautan: wajib pilih item atau isi SKU + kategori.
        expectError(400, "Item belum tertaut ke inventaris GA. Pilih item inventaris yang ada, atau isi SKU + kategori untuk membuat item baru.",
                patch(MR + "/" + mrId + "/items/" + beltItem + "/receive", body("receivedQty", 2, "locationId", loc)));
        expectError(400, "SKU \"MR-SKU-" + t + "\" sudah dipakai item lain. Pilih item itu atau gunakan SKU lain.",
                patch(MR + "/" + mrId + "/items/" + beltItem + "/receive", body("receivedQty", 2, "locationId", loc,
                        "newItem", body("sku", "MR-SKU-" + t, "categoryId", cat))));
        expectError(400, "SKU wajib diisi.",
                patch(MR + "/" + mrId + "/items/" + beltItem + "/receive", body("receivedQty", 2, "locationId", loc,
                        "newItem", body("sku", "", "categoryId", cat))));
        JsonNode r2 = ok(patch(MR + "/" + mrId + "/items/" + beltItem + "/receive", body("receivedQty", 2, "locationId", loc,
                "newItem", body("sku", "BELT-" + t, "categoryId", cat))));
        assertTrue(r2.get("fulfilled").asBoolean());
        String newItem = r2.get("inventoryItemId").asText();
        assertNotEquals(linked, newItem);
        assertEquals("Sabuk karet " + t, jdbc.queryForObject("SELECT name FROM ga_inventory_items WHERE id = ?", String.class, newItem));
        assertDec("2", stockAt(newItem, loc));
        assertEquals("FULFILLED", jdbc.queryForObject("SELECT status FROM ga_material_requests WHERE id = ?", String.class, mrId));
        expectError(400, "Tidak bisa membatalkan MR berstatus FULFILLED.", patch(MR + "/" + mrId + "/cancel", Map.of()));

        // Daftar lama, berpaginasi, ringkasan.
        JsonNode legacy = ok(get(MR + "?workOrderId="));
        assertTrue(legacy.isArray());
        JsonNode paged = ok(get(MR + "?page=1&search=Huller " + t));
        assertEquals(1, paged.get("total").asInt());
        JsonNode row = paged.get("data").get(0);
        assertEquals(me, row.get("requester").get("id").asText());
        assertEquals(2, row.get("_count").get("items").asInt());
        assertEquals("RECEIVED", row.get("items").get(0).get("procurementStatus").asText());
        assertEquals(1, ok(get(MR + "?page=1&search=sabuk karet " + t.toLowerCase())).get("total").asInt());
        // Sortir status mengikuti urutan enum (DRAFT < SUBMITTED < … < CANCELLED), bukan abjad.
        JsonNode byStatus = ok(get(MR + "?page=1&limit=200&sort=status"));
        String prevRank = null;
        java.util.List<String> order = GaMaterialRequestList.STATUSES;
        for (JsonNode r : byStatus.get("data")) {
            String st = r.get("status").asText();
            if (prevRank != null) {
                assertTrue(order.indexOf(st) >= order.indexOf(prevRank), prevRank + " -> " + st);
            }
            prevRank = st;
        }
        ok(get(MR + "?page=1&sort=-code"));
        expectError(400, "Status permintaan barang tidak dikenal.", get(MR + "?page=1&status=FOO"));
        expectError(500, "Gagal mengambil daftar permintaan barang.", get(MR + "?status=FOO"));
        JsonNode sum = ok(get(MR + "/summary?status=FOO&search=Huller " + t));
        assertEquals(1, sum.get("total").asInt());
        assertEquals(1, sum.get("byStatus").get("FULFILLED").asInt());
        assertEquals(7, sum.get("byStatus").size());
        expectError(404, "Permintaan barang tidak ditemukan.", get(MR + "/tidak-ada"));
    }

    @Test
    void reject_cancel_and_from_work_order() throws Exception {
        String me = adminId();
        String t = tag();
        String asset = asset(t, "EQUIPMENT");

        // Reject: SUBMITTED → REJECTED (alasan wajib).
        String mr1 = ok(post(MR, body("items", List.of(item("Oli " + t, "liter", 4, null))))).get("id").asText();
        expectError(400, "Tidak bisa reject MR berstatus DRAFT.", patch(MR + "/" + mr1 + "/reject", body("reason", "x")));
        ok(patch(MR + "/" + mr1 + "/submit", Map.of()));
        expectError(400, "Alasan wajib diisi.", patch(MR + "/" + mr1 + "/reject", body("reason", "")));
        expectError(400, "Invalid input: expected object, received undefined",
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(MR + "/" + mr1 + "/reject"));
        JsonNode rej = ok(patch(MR + "/" + mr1 + "/reject", body("reason", "Stok masih ada")));
        assertEquals("REJECTED", rej.get("status").asText());
        assertEquals("Stok masih ada", jdbc.queryForObject("SELECT reject_reason FROM ga_material_requests WHERE id = ?",
                String.class, mr1));
        assertEquals(me, jdbc.queryForObject("SELECT rejected_by_id FROM ga_material_requests WHERE id = ?", String.class, mr1));
        String itemId = ok(get(MR + "/" + mr1)).get("items").get(0).get("id").asText();
        expectError(400, "Tidak bisa mengubah procurement pada MR berstatus REJECTED.",
                patch(MR + "/" + mr1 + "/items/" + itemId + "/procurement", body("vendorName", "X")));
        expectError(400, "Item permintaan tidak ditemukan.",
                patch(MR + "/" + mr1 + "/items/tidak-ada/procurement", body("vendorName", "X")));

        // Cancel: DRAFT → CANCELLED; dua kali ditolak.
        String mr2 = ok(post(MR, body("items", List.of(item("Grease " + t, "kg", 1, null))))).get("id").asText();
        assertEquals("CANCELLED", ok(patch(MR + "/" + mr2 + "/cancel", Map.of())).get("status").asText());
        expectError(400, "Tidak bisa membatalkan MR berstatus CANCELLED.", patch(MR + "/" + mr2 + "/cancel", Map.of()));
        expectError(400, "Permintaan barang tidak ditemukan.", patch(MR + "/tidak-ada/submit", Map.of()));

        // Dari WO: workOrderId + aset diisi dari WO.
        JsonNode wo = ok(post(MT + "/work-orders", body("assetId", asset, "title", "Perbaikan pompa " + t)));
        expectError(404, "Work order tidak ditemukan.", post(MR + "/from-work-order/tidak-ada",
                body("items", List.of(item("Seal", "pcs", 1, null)))));
        Result fromWo = call(post(MR + "/from-work-order/" + wo.get("id").asText(),
                body("notes", "Untuk WO", "items", List.of(item("Seal " + t, "pcs", 2, null)))));
        assertEquals(201, fromWo.status(), String.valueOf(fromWo.body()));
        String mr3 = fromWo.data().get("id").asText();
        JsonNode d3 = ok(get(MR + "/" + mr3));
        assertEquals(wo.get("code").asText(), d3.get("workOrder").get("code").asText());
        assertEquals("OPEN", d3.get("workOrder").get("status").asText());
        assertEquals(asset, d3.get("assetId").asText());
        JsonNode byWo = ok(get(MR + "?workOrderId=" + wo.get("id").asText()));
        assertEquals(1, byWo.size());
        assertEquals(wo.get("code").asText(), byWo.get(0).get("workOrder").get("code").asText());
        // MR tampil di detail WO.
        JsonNode woDetail = ok(get(MT + "/work-orders/" + wo.get("id").asText()));
        assertEquals(1, woDetail.get("materialRequests").size());
        assertEquals(1, woDetail.get("materialRequests").get(0).get("_count").get("items").asInt());
    }
}
