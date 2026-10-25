package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.storage.ErpFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Dokumen GA: kode otomatis, status masa berlaku, daftar/ringkasan, versi PDF privat + unduhan, update & hapus. */
class GaDocumentFlowTest extends EamAssetTestSupport {

    @Autowired
    ErpFileStorage storage;

    private static String day(int plusDays) {
        return LocalDate.now(ZoneOffset.UTC).plusDays(plusDays).toString();
    }

    @Test
    void documents_expiry_versions_and_download() throws Exception {
        asAdmin();
        String typeCode = uniq("SIUP");
        String typeId = ok(post(MASTER + "/document-types", map("code", typeCode, "name", "Izin Usaha"))).get("id").asText();
        String branchId = createBranch(uniq("D")).get("id").asText();
        JsonNode asset = createAsset("GUDANG", branchId, "Gudang Dokumen");
        String assetId = asset.get("id").asText();

        // Kode otomatis {TIPE≤12}-{TAHUN}-{NNNN}.
        Result created = call(post(DOCS, map("title", "Izin A", "typeId", typeId, "assetId", assetId,
                "issuedBy", "Dinas PTSP", "issuedNumber", "NO-123", "expiryDate", day(10))));
        assertEquals(201, created.status());
        String prefix = typeCode.substring(0, 12) + "-" + LocalDate.now(ZoneOffset.UTC).getYear() + "-";
        assertEquals(prefix + "0001", created.data().get("code").asText());
        String expiringId = created.data().get("id").asText();
        String expiredId = ok(post(DOCS, map("title", "Izin B", "typeId", typeId, "expiryDate", day(-2)))).get("id").asText();
        String validId = ok(post(DOCS, map("title", "Izin C", "typeId", typeId, "expiryDate", day(100)))).get("id").asText();
        JsonNode revoked = ok(post(DOCS, map("title", "Izin D", "typeId", typeId, "status", "REVOKED")));
        assertEquals(prefix + "0004", revoked.get("code").asText());

        expectError(400, "Too small: expected string to have >=1 characters", post(DOCS, map("title", "", "typeId", typeId)));
        expectError(500, "Gagal membuat dokumen.", post(DOCS, map("title", "X", "typeId", "tidak-ada")));

        // Daftar berpaginasi: expiryState per dokumen.
        JsonNode page = ok(get(DOCS + "?page=1&limit=10&typeId=" + typeId));
        assertEquals(4, page.get("total").asInt());
        Map<String, String> states = new HashMap<>();
        for (JsonNode d : page.get("data")) {
            states.put(d.get("id").asText(), d.get("expiryState").isNull() ? null : d.get("expiryState").asText());
        }
        assertEquals("expiring", states.get(expiringId));
        assertEquals("expired", states.get(expiredId));
        assertEquals("valid", states.get(validId));
        assertTrue(states.containsKey(revoked.get("id").asText()));
        assertEquals(null, states.get(revoked.get("id").asText()));
        // Urutan default: expiryDate asc, kosong di akhir.
        assertEquals(expiredId, page.at("/data/0/id").asText());
        assertEquals(revoked.get("id").asText(), page.at("/data/3/id").asText());

        JsonNode summary = ok(get(DOCS + "/summary?typeId=" + typeId));
        assertEquals(4, summary.get("count").asInt());
        assertEquals(1, summary.get("expiredCount").asInt());
        assertEquals(1, summary.get("expiringCount").asInt());
        assertEquals(1, summary.get("validCount").asInt());
        JsonNode expiringOnly = ok(get(DOCS + "?page=1&typeId=" + typeId + "&expiry=expiring"));
        assertEquals(1, expiringOnly.get("total").asInt());
        JsonNode searchAsset = ok(get(DOCS + "?page=1&typeId=" + typeId + "&search=DOKUMEN"));
        assertEquals(1, searchAsset.get("total").asInt());
        expectError(400, "Status dokumen tidak dikenal.", get(DOCS + "?page=1&status=X"));
        expectError(400, "Filter masa berlaku tidak dikenal.", get(DOCS + "/summary?expiry=soon"));

        // Daftar lama (tanpa page) → array dengan daysToExpiry; drawer aset memakai assetId.
        JsonNode byAsset = ok(get(DOCS + "?assetId=" + assetId));
        assertTrue(byAsset.isArray());
        assertEquals(1, byAsset.size());
        JsonNode row = byAsset.get(0);
        assertTrue(row.get("daysToExpiry").asInt() >= 9 && row.get("daysToExpiry").asInt() <= 10);
        assertFalse(row.has("expiryState"));
        assertEquals(typeCode, row.at("/type/code").asText());
        assertEquals("Gudang Dokumen", row.at("/asset/name").asText());
        assertEquals(0, row.get("versions").size());
        JsonNode expiring30 = ok(get(DOCS + "?typeId=" + typeId + "&expiringDays=30"));
        assertEquals(1, expiring30.size());
        assertEquals(expiringId, expiring30.get(0).get("id").asText());

        // Versi PDF (privat) → versionNo bertambah; non-PDF ditolak.
        MvcResult v1 = raw(multipart(DOCS + "/" + expiringId + "/versions")
                .file(new MockMultipartFile("file", "izin.pdf", "application/pdf", PDF)).param("notes", "awal"));
        assertEquals(201, v1.getResponse().getStatus(), v1.getResponse().getContentAsString());
        JsonNode ver1 = json.readTree(v1.getResponse().getContentAsString()).get("data");
        assertEquals(1, ver1.get("versionNo").asInt());
        assertEquals(PDF.length, ver1.get("fileSize").asInt());
        assertFalse(ver1.get("filePath").asText().startsWith("erp/"));
        MvcResult v2 = raw(multipart(DOCS + "/" + expiringId + "/versions")
                .file(new MockMultipartFile("file", "izin2.pdf", "application/pdf", PDF)));
        JsonNode ver2 = json.readTree(v2.getResponse().getContentAsString()).get("data");
        assertEquals(2, ver2.get("versionNo").asInt());
        MvcResult notPdf = raw(multipart(DOCS + "/" + expiringId + "/versions")
                .file(new MockMultipartFile("file", "x.png", "image/png", PNG)));
        assertEquals(400, notPdf.getResponse().getStatus());
        MvcResult noDoc = raw(multipart(DOCS + "/tidak-ada/versions")
                .file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF)));
        assertEquals(404, noDoc.getResponse().getStatus());

        // Detail: semua versi desc; daftar memuat versi terbaru saja.
        JsonNode detail = ok(get(DOCS + "/" + expiringId));
        assertEquals(2, detail.get("versions").size());
        assertEquals(2, detail.at("/versions/0/versionNo").asInt());
        assertEquals(typeCode, detail.at("/type/code").asText());
        assertEquals(1, ok(get(DOCS + "?assetId=" + assetId)).get(0).get("versions").size());
        expectError(404, "Dokumen tidak ditemukan.", get(DOCS + "/tidak-ada"));

        // Unduh (scope versi ke dokumen untuk mencegah IDOR).
        String docCode = created.data().get("code").asText();
        MvcResult file = raw(get(DOCS + "/" + expiringId + "/versions/" + ver2.get("id").asText() + "/file"));
        assertEquals(200, file.getResponse().getStatus());
        assertEquals("application/pdf", file.getResponse().getContentType());
        assertTrue(file.getResponse().getHeader("Content-Disposition").contains(docCode + "-v2.pdf"));
        assertArrayEquals(PDF, file.getResponse().getContentAsByteArray());
        MvcResult idor = raw(get(DOCS + "/" + expiredId + "/versions/" + ver2.get("id").asText() + "/file"));
        assertEquals(404, idor.getResponse().getStatus());

        // PUT: field nullable yang absen ikut dikosongkan (perilaku sumber).
        assertEquals(expiringId, ok(put(DOCS + "/" + expiringId, map("title", "Izin A Rev"))).get("id").asText());
        detail = ok(get(DOCS + "/" + expiringId));
        assertEquals("Izin A Rev", detail.get("title").asText());
        assertTrue(detail.get("asset").isNull());
        assertTrue(detail.get("expiryDate").isNull());
        assertEquals(docCode, detail.get("code").asText());

        // Hapus: versi ikut terhapus; berkas fisik dibiarkan.
        String storedPath = ver1.get("filePath").asText();
        assertEquals("Dokumen dihapus.", ok(delete(DOCS + "/" + expiringId)).get("message").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ga_document_versions WHERE document_id = ?",
                Integer.class, expiringId));
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.", delete(DOCS + "/" + expiringId));
        Files.deleteIfExists(storage.resolve(storedPath));
        Files.deleteIfExists(storage.resolve(ver2.get("filePath").asText()));
    }
}
