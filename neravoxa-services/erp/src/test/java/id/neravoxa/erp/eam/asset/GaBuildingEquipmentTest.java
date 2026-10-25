package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gedung & sewa (upsert data bangunan, kontrak sewa) dan peralatan & kalibrasi (sinkron jatuh tempo). */
class GaBuildingEquipmentTest extends EamAssetTestSupport {

    private static final String BUILDING = "/api/erp/ga/building/assets/";
    private static final String EQUIPMENT = "/api/erp/ga/equipment/assets/";

    @Test
    void building_data_and_rentals() throws Exception {
        asAdmin();
        String branchId = createBranch(uniq("G")).get("id").asText();
        String assetId = createAsset("BANGUNAN", branchId, "Kantor Pusat").get("id").asText();

        Result empty = call(get(BUILDING + assetId));
        assertEquals(200, empty.status());
        assertTrue(empty.data() == null || empty.data().isNull());

        expectError(400, "Invalid option: expected one of \"KANTOR\"|\"GUDANG\"|\"PABRIK\"|\"RUMAH\"|\"MESS\"|\"RUKO\"|\"LAINNYA\"",
                put(BUILDING + assetId, map("luasTanah", 100)));
        expectError(400, "Too small: expected number to be >=1800",
                put(BUILDING + assetId, map("jenis", "KANTOR", "tahunBangun", 1700)));
        JsonNode saved = ok(put(BUILDING + assetId, map("jenis", "KANTOR", "luasTanah", 500.5, "jumlahLantai", 3,
                "kota", "Surabaya", "certificateType", "HGB")));
        assertEquals("KANTOR", saved.get("jenis").asText());
        assertEquals("MILIK_SENDIRI", saved.get("ownership").asText());
        assertEquals(0, saved.get("luasTanah").decimalValue().compareTo(new java.math.BigDecimal("500.5")));
        String dataId = saved.get("id").asText();
        // Upsert kedua memperbarui baris yang sama; field absen menjadi null.
        JsonNode again = ok(put(BUILDING + assetId, map("jenis", "GUDANG", "ownership", "SEWA_MASUK")));
        assertEquals(dataId, again.get("id").asText());
        assertEquals("GUDANG", again.get("jenis").asText());
        assertTrue(again.get("kota").isNull());
        assertEquals("SEWA_MASUK", ok(get(BUILDING + assetId)).get("ownership").asText());

        // Sewa: dokumen kontrak opsional (include {id, code, title}).
        String typeId = ok(post(MASTER + "/document-types", map("code", uniq("SEWA"), "name", "Kontrak Sewa")))
                .get("id").asText();
        String docId = ok(post(DOCS, map("title", "Kontrak Gedung", "typeId", typeId))).get("id").asText();
        Result rental = call(post(BUILDING + assetId + "/rentals", map("arah", "SEWA_MASUK", "pihak", "PT Pemilik",
                "nilaiSewa", 12000000, "periode", "BULANAN", "tanggalMulai", "2026-01-01", "tanggalAkhir", "2026-12-31",
                "documentId", docId)));
        assertEquals(201, rental.status());
        String rid = rental.data().get("id").asText();
        assertEquals("AKTIF", rental.data().get("status").asText());
        expectError(400, "Invalid input: expected number, received undefined",
                post(BUILDING + assetId + "/rentals", map("arah", "SEWA_MASUK", "pihak", "X", "periode", "BULANAN",
                        "tanggalMulai", "2026-01-01", "tanggalAkhir", "2026-02-01")));

        JsonNode list = ok(get(BUILDING + assetId + "/rentals"));
        assertEquals(1, list.size());
        assertEquals("Kontrak Gedung", list.at("/0/document/title").asText());
        assertEquals(3, list.at("/0/document").size());

        JsonNode patched = ok(patch(BUILDING + assetId + "/rentals/" + rid, map("pihak", "PT Baru", "documentId", null,
                "nilaiSewa", 15000000)));
        assertEquals("PT Baru", patched.get("pihak").asText());
        assertTrue(patched.get("documentId").isNull());
        assertEquals(0, patched.get("nilaiSewa").decimalValue().compareTo(new java.math.BigDecimal("15000000")));
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.",
                patch(BUILDING + assetId + "/rentals/" + rid, map("documentId", "tidak-ada")));

        assertEquals("BERAKHIR", ok(patch(BUILDING + assetId + "/rentals/" + rid + "/end", map())).get("status").asText());
        MvcResult del = raw(delete(BUILDING + assetId + "/rentals/" + rid));
        assertEquals(204, del.getResponse().getStatus());
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.",
                delete(BUILDING + assetId + "/rentals/" + rid));
    }

    @Test
    void equipment_data_and_calibration_logs() throws Exception {
        asAdmin();
        String branchId = createBranch(uniq("E")).get("id").asText();
        String assetId = createAsset("EQUIPMENT", branchId, "Timbangan 60T").get("id").asText();

        JsonNode none = call(get(EQUIPMENT + assetId)).data();
        assertTrue(none == null || none.isNull());
        // 31 Jan + 1 bulan → 3 Mar (setMonth JavaScript), interval default 12 bila absen.
        JsonNode saved = ok(put(EQUIPMENT + assetId, map("maxCapacity", 60000, "unit", "KG", "intervalMonths", 1,
                "lastCalibratedAt", "2025-01-31")));
        assertEquals("2025-03-03T00:00:00.000Z", saved.get("nextCalibrationDue").asText());
        assertEquals(1, saved.get("intervalMonths").asInt());
        JsonNode def = ok(put(EQUIPMENT + assetId, map("lastCalibratedAt", "2025-06-15")));
        assertEquals(12, def.get("intervalMonths").asInt());
        assertEquals("2026-06-15T00:00:00.000Z", def.get("nextCalibrationDue").asText());
        assertEquals(saved.get("id").asText(), def.get("id").asText());
        expectError(400, "Invalid option: expected one of \"KG\"|\"GRAM\"|\"TON\"|\"LBS\"",
                put(EQUIPMENT + assetId, map("unit", "ONS")));
        expectError(400, "Too small: expected number to be >0", put(EQUIPMENT + assetId, map("intervalMonths", 0)));

        Result log = call(post(EQUIPMENT + assetId + "/calibration-logs", map("tanggal", "2026-02-10",
                "lembaga", "BSML", "noSertifikat", "CERT-1", "hasil", "PASS", "biaya", 1500000, "validUntil", "2027-02-10")));
        assertEquals(201, log.status());
        assertEquals("ON_SITE", log.data().get("mode").asText());
        JsonNode synced = ok(get(EQUIPMENT + assetId));
        assertEquals("2026-02-10T00:00:00.000Z", synced.get("lastCalibratedAt").asText());
        assertEquals("2027-02-10T00:00:00.000Z", synced.get("nextCalibrationDue").asText());
        expectError(400, "Invalid option: expected one of \"PASS\"|\"FAIL\"|\"CONDITIONAL\"",
                post(EQUIPMENT + assetId + "/calibration-logs", map("tanggal", "2026-02-10", "lembaga", "BSML",
                        "noSertifikat", "C", "validUntil", "2027-02-10")));

        JsonNode logs = ok(get(EQUIPMENT + assetId + "/calibration-logs"));
        assertEquals(1, logs.size());
        assertTrue(logs.at("/0/document").isNull());
        assertTrue(logs.at("/0/workOrder").isNull());
        assertEquals(204, raw(delete(EQUIPMENT + assetId + "/calibration-logs/" + log.data().get("id").asText()))
                .getResponse().getStatus());
        assertEquals(0, ok(get(EQUIPMENT + assetId + "/calibration-logs")).size());
    }
}
