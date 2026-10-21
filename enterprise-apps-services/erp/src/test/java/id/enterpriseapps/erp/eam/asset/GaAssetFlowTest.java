package id.enterpriseapps.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.storage.ErpFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Registri aset GA: kode otomatis, daftar & filter, update parsial, bulk, kondisi, foto publik, QR publik. */
class GaAssetFlowTest extends EamAssetTestSupport {

    @Autowired
    ErpFileStorage storage;

    @Test
    void create_list_update_bulk_and_condition() throws Exception {
        asAdmin();
        String brCode = uniq("B");
        String branchId = createBranch(brCode).get("id").asText();
        String brPrefix = brCode.substring(0, 6);

        // Kode otomatis {KND}-{CABANG≤6}-{NNNN} + saran kode berikutnya.
        JsonNode next = ok(get(ASSETS + "/_next-code?categoryId=" + categoryId("KENDARAAN") + "&branchId=" + branchId));
        assertEquals("KND-" + brPrefix + "-0001", next.get("code").asText());
        Result created = call(post(ASSETS, map("name", "Truk Uji", "categoryId", categoryId("KENDARAAN"),
                "branchId", branchId, "acquisitionCost", 250000000, "location", "Pool A", "serialNumber", "SN-1")));
        assertEquals(201, created.status());
        assertEquals(Set.of("id", "code"), fieldNames(created.data()));
        assertEquals("KND-" + brPrefix + "-0001", created.data().get("code").asText());
        String id = created.data().get("id").asText();
        JsonNode second = createAsset("KENDARAAN", branchId, "Truk Dua");
        assertEquals("KND-" + brPrefix + "-0002", second.get("code").asText());
        JsonNode bld = createAsset("BANGUNAN", branchId, "Gudang Uji");
        assertEquals("BLD-" + brPrefix + "-0001", bld.get("code").asText());

        expectError(400, "categoryId & branchId wajib diisi.", get(ASSETS + "/_next-code?categoryId=x"));
        expectError(404, "Kategori atau cabang tidak ditemukan.", get(ASSETS + "/_next-code?categoryId=x&branchId=y"));
        expectError(400, "Invalid input: expected string, received undefined",
                post(ASSETS, map("categoryId", categoryId("KENDARAAN"), "branchId", branchId)));
        expectError(400, "Invalid option: expected one of \"ACTIVE\"|\"IN_REPAIR\"|\"RETIRED\"|\"LOST\"",
                post(ASSETS, map("name", "X", "categoryId", categoryId("KENDARAAN"), "branchId", branchId, "status", "X")));
        expectError(500, "Gagal membuat aset.",
                post(ASSETS, map("name", "X", "categoryId", "tidak-ada", "branchId", branchId)));

        // Detail: relasi lengkap, qr, fixedAsset null, pic null, photos [].
        JsonNode detail = ok(get(ASSETS + "/" + id));
        assertEquals("KENDARAAN", detail.at("/category/code").asText());
        assertEquals(brCode, detail.at("/branch/code").asText());
        assertTrue(detail.get("qr").get("qrCode").asText().startsWith("GA-"));
        assertTrue(detail.get("fixedAsset").isNull());
        assertTrue(detail.get("pic").isNull());
        assertEquals(0, detail.get("photos").size());
        assertEquals("ACTIVE", detail.get("status").asText());
        assertEquals("GOOD", detail.get("condition").asText());
        expectError(404, "Aset tidak ditemukan.", get(ASSETS + "/tidak-ada"));

        // Daftar berpaginasi + thumb + filter cabang & categoryKind.
        JsonNode page = ok(get(ASSETS + "?paginated=1&thumb=1&branchId=" + branchId + "&sort=-code&limit=2"));
        assertEquals(3, page.get("total").asInt());
        assertEquals(2, page.get("limit").asInt());
        assertEquals(2, page.get("data").size());
        JsonNode row = page.get("data").get(0);
        assertEquals("KND-" + brPrefix + "-0002", row.get("code").asText());
        assertEquals(Set.of("code", "name"), fieldNames(row.get("category")));
        assertEquals(0, row.get("photos").size());
        assertTrue(row.get("qr").has("qrCode"));
        JsonNode vehicles = ok(get(ASSETS + "?categoryKind=vehicle&branchId=" + branchId));
        assertTrue(vehicles.isArray());
        assertEquals(2, vehicles.size());
        JsonNode buildings = ok(get(ASSETS + "?categoryKind=building&branchId=" + branchId));
        assertEquals(1, buildings.size());
        JsonNode multi = ok(get(ASSETS + "?branchId=" + branchId + "&categoryId=" + categoryId("BANGUNAN") + ","
                + categoryId("KENDARAAN")));
        assertEquals(3, multi.size());
        JsonNode search = ok(get(ASSETS + "?branchId=" + branchId + "&q=POOL"));
        assertEquals(1, search.size());
        expectError(400, "categoryKind tidak valid.", get(ASSETS + "?categoryKind=kapal"));
        expectError(500, "Gagal mengambil data aset.", get(ASSETS + "?status=RUSAK"));

        // PUT parsial: PIC tetap bila tidak dikirim; '' mengosongkan.
        String picId = jdbc.queryForObject("SELECT id FROM users WHERE lower(email) = 'erp.admin@test.local'", String.class);
        assertEquals(id, ok(put(ASSETS + "/" + id, map("picUserId", picId))).get("id").asText());
        ok(put(ASSETS + "/" + id, map("name", "Truk Ganti", "notes", "catatan")));
        detail = ok(get(ASSETS + "/" + id));
        assertEquals("Truk Ganti", detail.get("name").asText());
        assertEquals(picId, detail.at("/pic/id").asText());
        assertTrue(detail.at("/pic").has("fullName"));
        ok(put(ASSETS + "/" + id, map("location", "")));
        assertTrue(ok(get(ASSETS + "/" + id)).get("location").isNull());
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.",
                put(ASSETS + "/tidak-ada", map("name", "X")));

        // Kondisi oleh PIC.
        JsonNode cond = ok(patch(ASSETS + "/" + id + "/condition", map("condition", "POOR", "notes", "Ban aus")));
        assertEquals("POOR", cond.get("condition").asText());
        assertEquals("Ban aus", ok(get(ASSETS + "/" + id)).get("notes").asText());
        expectError(400, "Kondisi tidak valid.", patch(ASSETS + "/" + id + "/condition", map("condition", "OK")));
        expectError(404, "Aset tidak ditemukan.", patch(ASSETS + "/tidak-ada/condition", map("condition", "GOOD")));

        // Bulk update / delete.
        List<String> ids = List.of(id, second.get("id").asText());
        expectError(400, "ids wajib berupa array non-kosong.", patch(ASSETS + "/bulk/update", map("ids", List.of())));
        expectError(400, "data wajib berisi field yang akan diupdate.", patch(ASSETS + "/bulk/update", map("ids", ids)));
        expectError(400, "Field 'name' tidak diizinkan untuk bulk update.",
                patch(ASSETS + "/bulk/update", map("ids", ids, "data", map("name", "X"))));
        JsonNode bulk = ok(patch(ASSETS + "/bulk/update", map("ids", ids, "data", map("status", "IN_REPAIR", "location", "Bengkel"))));
        assertEquals(2, bulk.get("updated").asInt());
        JsonNode inRepair = ok(get(ASSETS + "?branchId=" + branchId + "&status=IN_REPAIR"));
        assertEquals(2, inRepair.size());
        assertEquals("Bengkel", inRepair.get(0).get("location").asText());

        JsonNode del = ok(post(ASSETS + "/bulk/delete", map("ids", List.of(bld.get("id").asText()))));
        assertEquals(1, del.get("deleted").asInt());
        expectError(404, "Aset tidak ditemukan.", get(ASSETS + "/" + bld.get("id").asText()));
    }

    @Test
    void photo_upload_public_storage_and_public_qr() throws Exception {
        asAdmin();
        String branchId = createBranch(uniq("Q")).get("id").asText();
        JsonNode asset = ok(post(ASSETS, map("name", "Timbangan QR", "categoryId", categoryId("EQUIPMENT"),
                "branchId", branchId, "acquisitionCost", 5000000, "notes", "rahasia internal",
                "customFields", map("vendor", "PT X"), "location", "Lab")));
        String id = asset.get("id").asText();

        MvcResult up = raw(multipart(ASSETS + "/" + id + "/photos")
                .file(new MockMultipartFile("photo", "foto.png", "image/png", PNG)).param("caption", "Depan"));
        assertEquals(201, up.getResponse().getStatus(), up.getResponse().getContentAsString());
        JsonNode photo = json.readTree(up.getResponse().getContentAsString()).get("data");
        String filePath = photo.get("filePath").asText();
        assertTrue(filePath.startsWith("erp/ga/assets/"), filePath);
        assertTrue(filePath.endsWith(".png"));
        assertEquals("Depan", photo.get("caption").asText());
        Path stored = storage.resolvePublic(filePath);
        assertArrayEquals(PNG, Files.readAllBytes(stored));

        MvcResult bad = raw(multipart(ASSETS + "/" + id + "/photos")
                .file(new MockMultipartFile("photo", "x.pdf", "application/pdf", PDF)));
        assertEquals(400, bad.getResponse().getStatus());
        assertEquals("File wajib diupload.", json.readTree(bad.getResponse().getContentAsString()).get("errorMessage").asText());

        // Thumbnail di daftar.
        JsonNode page = ok(get(ASSETS + "?paginated=1&thumb=1&branchId=" + branchId));
        assertEquals(filePath, page.at("/data/0/photos/0/filePath").asText());

        // QR publik TANPA login: hanya field non-sensitif.
        String qr = ok(get(ASSETS + "/" + id)).at("/qr/qrCode").asText();
        SecurityContextHolder.clearContext();
        Result pub = call(get("/api/erp/ga/public/" + qr));
        assertEquals(200, pub.status(), String.valueOf(pub.body()));
        JsonNode info = pub.data();
        assertEquals(Set.of("code", "name", "category", "categoryCode", "branch", "status", "condition", "location", "photos"),
                fieldNames(info));
        assertEquals("Timbangan QR", info.get("name").asText());
        assertEquals("EQUIPMENT", info.get("categoryCode").asText());
        assertEquals("Lab", info.get("location").asText());
        assertFalse(info.toString().contains("rahasia"));
        assertFalse(info.toString().contains("5000000"));
        String url = info.at("/photos/0/url").asText();
        assertEquals("/api/erp/ga/public/" + qr + "/photos/" + photo.get("id").asText() + ".jpg", url);
        MvcResult img = raw(get(url));
        assertEquals(200, img.getResponse().getStatus());
        assertArrayEquals(PNG, img.getResponse().getContentAsByteArray());
        assertEquals("nosniff", img.getResponse().getHeader("X-Content-Type-Options"));
        assertEquals(404, raw(get("/api/erp/ga/public/" + qr + "/photos/lain.jpg")).getResponse().getStatus());
        assertEquals(404, raw(get("/api/erp/ga/public/GA-tidak-ada/photos/x.jpg")).getResponse().getStatus());
        Result unknown = call(get("/api/erp/ga/public/GA-tidak-ada"));
        assertEquals(404, unknown.status());
        assertEquals("Aset tidak ditemukan.", unknown.error());

        // Hapus foto: 204, berkas ikut terhapus.
        asAdmin();
        MvcResult delRes = raw(delete(ASSETS + "/" + id + "/photos/" + photo.get("id").asText()));
        assertEquals(204, delRes.getResponse().getStatus());
        assertFalse(Files.exists(stored));
        assertEquals(0, ok(get(ASSETS + "/" + id)).get("photos").size());
        assertNotNull(ok(post(ASSETS + "/bulk/delete", map("ids", List.of(id)))));
    }

    private static Set<String> fieldNames(JsonNode n) {
        List<String> names = new ArrayList<>();
        n.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }
}
