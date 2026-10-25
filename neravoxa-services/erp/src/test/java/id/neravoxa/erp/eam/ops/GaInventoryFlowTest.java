package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inventaris GA: CRUD item, mutasi IN/OUT/TRANSFER/ADJUSTMENT, opname, laporan, gambar item. */
class GaInventoryFlowTest extends GaOpsTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};

    private MvcResult raw(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(r -> {
            r.setUserPrincipal(SecurityContextHolder.getContext().getAuthentication());
            return r;
        })).andReturn();
    }

    @Test
    void item_crud_movements_stock_set_and_reports() throws Exception {
        asAdmin();
        String t = tag();
        String cat = invCategory(t);
        String locA = location(t, "A");
        String locB = location(t, "B");

        // Validasi Zod (pesan issue pertama).
        expectError(400, "Invalid input: expected string, received undefined",
                post(INV + "/items", body("name", "Filter", "categoryId", cat, "uom", "pcs")));
        expectError(400, "Too small: expected number to be >=0",
                post(INV + "/items", body("sku", "X", "name", "Filter", "categoryId", cat, "uom", "pcs", "minStock", -1)));
        expectError(400, "Too big: expected string to have <=20 characters",
                post(INV + "/items", body("sku", "X", "name", "Filter", "categoryId", cat, "uom", "x".repeat(21))));

        Result created = call(post(INV + "/items", body("sku", "SKU-" + t, "name", "Filter Oli " + t,
                "categoryId", cat, "uom", "pcs", "minStock", 5)));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        String itemId = created.data().get("id").asText();
        expectError(400, "Data duplikat terdeteksi. Silakan coba lagi.",
                post(INV + "/items", body("sku", "SKU-" + t, "name", "Dup", "categoryId", cat, "uom", "pcs")));
        expectError(400, "Referensi data tidak valid. Periksa data yang dipilih.",
                post(INV + "/items", body("sku", "SKU2-" + t, "name", "X", "categoryId", "tidak-ada", "uom", "pcs")));

        // Mutasi: syarat lokasi per tipe.
        expectError(400, "IN butuh toLocation.", post(INV + "/movements", body("itemId", itemId, "type", "IN", "quantity", 3)));
        expectError(400, "OUT butuh fromLocation.", post(INV + "/movements", body("itemId", itemId, "type", "OUT", "quantity", 3)));
        expectError(400, "Qty harus lebih dari 0.",
                post(INV + "/movements", body("itemId", itemId, "type", "IN", "quantity", 0, "toLocationId", locA)));
        expectError(400, "Invalid option: expected one of \"IN\"|\"OUT\"|\"TRANSFER\"|\"ADJUSTMENT\"",
                post(INV + "/movements", body("itemId", itemId, "type", "MOVE", "quantity", 1)));

        Result in = call(post(INV + "/movements", body("itemId", itemId, "type", "IN", "quantity", 10,
                "toLocationId", locA, "referenceNo", "PO-" + t)));
        assertEquals(201, in.status(), String.valueOf(in.body()));
        assertDec("10", stockAt(itemId, locA));
        ok(post(INV + "/movements", body("itemId", itemId, "type", "OUT", "quantity", 3, "fromLocationId", locA)));
        assertDec("7", stockAt(itemId, locA));
        // OUT melebihi stok: Error biasa di sumber → 500 + pesan cadangan; stok tidak berubah (rollback).
        expectError(500, "Gagal mencatat movement.",
                post(INV + "/movements", body("itemId", itemId, "type", "OUT", "quantity", 100, "fromLocationId", locA)));
        assertDec("7", stockAt(itemId, locA));
        ok(post(INV + "/movements", body("itemId", itemId, "type", "TRANSFER", "quantity", 2,
                "fromLocationId", locA, "toLocationId", locB)));
        assertDec("5", stockAt(itemId, locA));
        assertDec("2", stockAt(itemId, locB));
        ok(post(INV + "/movements", body("itemId", itemId, "type", "ADJUSTMENT", "quantity", 1, "toLocationId", locB)));
        assertDec("3", stockAt(itemId, locB));

        // Detail: stok per lokasi.
        JsonNode detail = ok(get(INV + "/items/" + itemId));
        assertEquals("SKU-" + t, detail.get("sku").asText());
        assertDec("5", detail.get("minStock").asText());
        assertEquals("SPAREPART", detail.get("category").get("type").asText());
        assertEquals(2, detail.get("stocks").size());
        for (JsonNode s : detail.get("stocks")) {
            assertTrue(s.get("location").get("code").asText().startsWith("LOC-" + t));
        }
        expectError(404, "Item tidak ditemukan.", get(INV + "/items/tidak-ada"));

        // Daftar lama (array) & berpaginasi.
        JsonNode arr = ok(get(INV + "/items?q=SKU-" + t));
        assertTrue(arr.isArray());
        assertEquals(1, arr.size());
        assertDec("8", arr.get(0).get("totalStock").asText());
        assertFalse(arr.get(0).get("lowStock").asBoolean());
        JsonNode paged = ok(get(INV + "/items?paginated=1&search=" + t + "&locationId=" + locB));
        assertEquals(1, paged.get("total").asInt());
        assertDec("3", paged.get("data").get(0).get("locationStock").asText());
        ok(get(INV + "/items?paginated=1&sort=-totalStock"));
        ok(get(INV + "/items?paginated=1&sort=category&stockStatus=ok"));
        expectError(400, "Filter status stok tidak dikenal.", get(INV + "/items?paginated=1&stockStatus=foo"));
        expectError(400, "Filter status item tidak dikenal.", get(INV + "/items/summary?isActive=ya"));
        JsonNode summary = ok(get(INV + "/items/summary?search=" + t));
        assertEquals(1, summary.get("count").asInt());
        assertEquals(1, summary.get("okCount").asInt());

        // Opname: set absolut, selisih jadi ADJUSTMENT (boleh minus).
        Result set = call(post(INV + "/stock-set", body("itemId", itemId, "locationId", locA, "quantity", 1)));
        assertEquals(201, set.status(), String.valueOf(set.body()));
        assertTrue(set.data().get("changed").asBoolean());
        assertDec("-4", set.data().get("delta").asText());
        assertDec("5", set.data().get("before").asText());
        assertDec("1", stockAt(itemId, locA));
        JsonNode same = ok(post(INV + "/stock-set", body("itemId", itemId, "locationId", locA, "quantity", 1)));
        assertFalse(same.get("changed").asBoolean());
        expectError(400, "Stok hasil opname tidak boleh negatif.",
                post(INV + "/stock-set", body("itemId", itemId, "locationId", locA, "quantity", -1)));
        String notes = jdbc.queryForObject("SELECT notes FROM ga_inventory_movements WHERE item_id = ? AND reference_no = 'OPNAME'",
                String.class, itemId);
        assertEquals("Opname: 5 → 1", notes);

        // Total 4 < min 5 → stok rendah.
        JsonNode low = ok(get(INV + "/low-stock"));
        boolean found = false;
        for (JsonNode l : low) {
            if (itemId.equals(l.get("id").asText())) {
                found = true;
                assertDec("1", l.get("shortfall").asText());
            }
        }
        assertTrue(found);

        // Mutasi berpaginasi + filter.
        JsonNode mv = ok(get(INV + "/movements?page=1&itemId=" + itemId + "&type=ADJUSTMENT"));
        assertEquals(2, mv.get("total").asInt());
        assertEquals("ADJUSTMENT", mv.get("data").get(0).get("type").asText());
        JsonNode mvSearch = ok(get(INV + "/movements?page=1&search=PO-" + t));
        assertEquals(1, mvSearch.get("total").asInt());
        assertEquals("LOC-" + t + "-A", mvSearch.get("data").get(0).get("toLocation").get("code").asText());
        expectError(400, "Tipe pergerakan tidak dikenal.", get(INV + "/movements?page=1&type=FOO"));
        expectError(400, "Format tanggal harus YYYY-MM-DD.", get(INV + "/movements?page=1&startDate=2026-13-01"));
        JsonNode legacy = ok(get(INV + "/movements?itemId=" + itemId));
        assertEquals(5, legacy.size()); // OUT gagal di-rollback; opname tanpa selisih tidak mencatat movement
        expectError(500, "Gagal mengambil data movement.", get(INV + "/movements?type=FOO"));

        // Dashboard & ekspor.
        JsonNode dash = ok(get(INV + "/dashboard"));
        assertTrue(dash.get("summary").get("totalItems").asInt() >= 1);
        assertTrue(dash.get("movementTotals").has("TRANSFER"));
        assertTrue(dash.get("recentMovements").size() <= 8);
        MvcResult csv = raw(get(INV + "/export?type=items"));
        assertEquals(200, csv.getResponse().getStatus());
        String text = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(text.startsWith("﻿SKU;Nama;Kategori"), text.substring(0, 30));
        assertTrue(text.contains("SKU-" + t + ";Filter Oli " + t), "baris item ada di CSV");
        assertTrue(csv.getResponse().getHeader("Content-Disposition").startsWith("attachment; filename=\"ga-inventory-items-"));

        // PUT parsial (Zod 4: default minStock/isActive tetap diterapkan).
        JsonNode upd = ok(put(INV + "/items/" + itemId, body("name", "Filter Oli Baru " + t)));
        assertEquals(itemId, upd.get("id").asText());
        assertEquals("Filter Oli Baru " + t, jdbc.queryForObject("SELECT name FROM ga_inventory_items WHERE id = ?",
                String.class, itemId));
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.",
                put(INV + "/items/tidak-ada", body("name", "X")));
    }

    @Test
    void item_image_upload_serve_and_delete() throws Exception {
        asAdmin();
        String t = tag();
        String itemId = ok(post(INV + "/items", body("sku", "IMG-" + t, "name", "Item gambar", "categoryId", invCategory(t),
                "uom", "pcs"))).get("id").asText();

        // Tanpa berkas / MIME ditolak fileFilter → 400.
        MockHttpServletRequestBuilder noFile = MockMvcRequestBuilders.multipart(INV + "/items/" + itemId + "/image");
        expectError(400, "File gambar wajib diupload (JPG/PNG/WEBP, maks 5 MB).", noFile);
        MockHttpServletRequestBuilder pdf = MockMvcRequestBuilders.multipart(INV + "/items/" + itemId + "/image")
                .file(new MockMultipartFile("image", "a.pdf", "application/pdf", new byte[]{1, 2, 3}));
        expectError(400, "File gambar wajib diupload (JPG/PNG/WEBP, maks 5 MB).", pdf);
        expectError(404, "Gambar tidak ditemukan.", get(INV + "/items/" + itemId + "/image"));

        Result up = call(MockMvcRequestBuilders.multipart(INV + "/items/" + itemId + "/image")
                .file(new MockMultipartFile("image", "foto.png", "image/png", PNG)));
        assertEquals(201, up.status(), String.valueOf(up.body()));
        String path = up.data().get("imagePath").asText();
        assertTrue(path.startsWith("erp/ga/items/") && path.endsWith(".png"), path);

        MvcResult img = raw(get(INV + "/items/" + itemId + "/image"));
        assertEquals(200, img.getResponse().getStatus());
        assertEquals("image/png", img.getResponse().getContentType());
        assertEquals("nosniff", img.getResponse().getHeader("X-Content-Type-Options"));
        assertArrayEquals(PNG, img.getResponse().getContentAsByteArray());

        // Ganti gambar: berkas lama dihapus.
        Result up2 = call(MockMvcRequestBuilders.multipart(INV + "/items/" + itemId + "/image")
                .file(new MockMultipartFile("image", "foto2.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0})));
        assertEquals(201, up2.status());
        String path2 = up2.data().get("imagePath").asText();
        assertTrue(path2.endsWith(".jpg"));
        assertFalse(java.nio.file.Files.exists(java.nio.file.Paths.get(System.getProperty("user.dir"), "uploads", path)));
        assertTrue(java.nio.file.Files.exists(java.nio.file.Paths.get(System.getProperty("user.dir"), "uploads", path2)));

        JsonNode row = ok(get(INV + "/items?q=IMG-" + t)).get(0);
        assertEquals(path2, row.get("imagePath").asText());

        Result del = call(delete(INV + "/items/" + itemId + "/image"));
        assertEquals(204, del.status());
        assertFalse(java.nio.file.Files.exists(java.nio.file.Paths.get(System.getProperty("user.dir"), "uploads", path2)));
        expectError(404, "Gambar tidak ditemukan.", get(INV + "/items/" + itemId + "/image"));
        expectError(404, "Item tidak ditemukan.", delete(INV + "/items/tidak-ada/image"));
    }

    @Test
    void route_gate_requires_ga_inventory_permission() throws Exception {
        String viewer = customRole(Map.of("ga_inventory", "view"));
        String other = customRole(Map.of("ga_maintenance", "view"));
        provision(email("inv.viewer"), viewer);
        ok(get(INV + "/items"));
        expectError(403, "Akses ditolak: Tidak memiliki izin.", post(INV + "/items", body("sku", "x")));
        provision(email("inv.none"), other);
        expectError(403, "Akses ditolak: Tidak memiliki izin.", get(INV + "/items"));
        assertNotNull(other);
    }
}
