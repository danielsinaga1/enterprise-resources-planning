package id.neravoxa.erp.eam.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Log BBM/oli/inspeksi menaikkan odometer (tidak pernah mundur), inspeksi + foto bukti (berkas publik
 * {@code erp/ga/inspections}, dibaca lewat endpoint ber-RBAC), ban (satu ban aktif per posisi), insiden.
 */
class VehicleLogTest extends VehicleTestSupport {

    private static final byte[] PNG = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 0x49, 0x48};

    private int odometer(String asset) throws Exception {
        return ok(get(BASE + "/assets/" + asset)).get("odometerTerakhir").asInt();
    }

    @Test
    void fuel_oil_inspection_bump_odometer_and_photos() throws Exception {
        provision(email("veh.log"), "SUPER_ADMIN");
        String t = tag();
        String asset = newVehicle("KDR-" + t);
        String day = today().toString();

        // BBM — data kendaraan dibuat otomatis saat odometer pertama masuk.
        String fuelUrl = BASE + "/assets/" + asset + "/fuel-logs";
        expectError(400, "Tanggal wajib diisi.", post(fuelUrl, body("tanggal", "", "liters", 10, "totalCost", 100000)));
        expectError(400, "Liter harus lebih dari 0.", post(fuelUrl, body("tanggal", day, "liters", 0, "totalCost", 100000)));
        expectError(400, "Invalid input: expected number, received undefined", post(fuelUrl, body("tanggal", day, "liters", 10)));
        expectError(400, "Too small: expected number to be >=0",
                post(fuelUrl, body("tanggal", day, "odometer", -1, "liters", 10, "totalCost", 1)));
        JsonNode fuel = ok(post(fuelUrl, body("tanggal", day, "odometer", 1000, "liters", 30.5, "pricePerLiter", 10000,
                "totalCost", 305000, "spbu", "SPBU 34", "driverName", "Budi")));
        assertEquals(0, fuel.get("liters").decimalValue().compareTo(new java.math.BigDecimal("30.5")));
        assertFalse(fuel.has("asset"));
        assertEquals(1000, odometer(asset));
        // Pembacaan lebih kecil tidak memundurkan odometer.
        ok(post(fuelUrl, body("tanggal", day, "odometer", 900, "liters", 5, "totalCost", 50000)));
        assertEquals(1000, odometer(asset));
        assertEquals(2, ok(get(fuelUrl)).size());

        // Oli
        String oilUrl = BASE + "/assets/" + asset + "/oil-logs";
        expectError(400, "Merek oli wajib diisi.", post(oilUrl, body("tanggal", day, "odometer", 1500, "nextOdometerDue", 6500,
                "brand", "", "volume", 4)));
        expectError(400, "Volume harus lebih dari 0.", post(oilUrl, body("tanggal", day, "odometer", 1500, "nextOdometerDue", 6500,
                "brand", "Shell", "volume", -1)));
        JsonNode oil = ok(post(oilUrl, body("tanggal", day, "odometer", 1500, "nextOdometerDue", 6500, "brand", "Shell", "volume", 4)));
        assertEquals(1500, odometer(asset));
        expectStatus(204, delete(oilUrl + "/" + oil.get("id").asText()));
        assertEquals(0, ok(get(oilUrl)).size());
        assertEquals(1500, odometer(asset));

        // Kategori & checkpoint checklist
        JsonNode cats = ok(get(BASE + "/inspection-categories"));
        assertEquals(6, cats.size());
        assertEquals("mesin", cats.get(0).get("key").asText());
        assertEquals("Oli Mesin", cats.get(0).get("items").get(0).get("label").asText());
        assertEquals(12, ok(get(BASE + "/inspection-checkpoints")).size());

        // Inspeksi
        String inspUrl = BASE + "/assets/" + asset + "/inspections";
        expectError(400, "Invalid input: expected record, received undefined",
                post(inspUrl, body("tanggal", day, "overallResult", "BAIK")));
        expectError(400, "Invalid option: expected one of \"OK\"|\"PERLU_PERHATIAN\"|\"RUSAK\"",
                post(inspUrl, body("tanggal", day, "checklist", Map.of("oli_mesin", "BAGUS"), "overallResult", "BAIK")));
        expectError(400, "Too big: expected number to be <=730",
                post(inspUrl, body("tanggal", day, "checklist", Map.of(), "overallResult", "BAIK", "intervalDays", 900)));
        JsonNode insp = ok(post(inspUrl, body("tanggal", day, "odometer", 2000,
                "checklist", Map.of("oli_mesin", "OK", "ban_depan", "PERLU_PERHATIAN"), "overallResult", "PERLU_PERHATIAN",
                "inspectorName", "Andi", "fuelLevel", "SETENGAH", "intervalDays", 14)));
        assertEquals(today().plusDays(14) + "T00:00:00.000Z", insp.get("nextDueDate").asText());
        assertEquals("PERLU_PERHATIAN", insp.get("checklist").get("ban_depan").asText());
        assertEquals(0, insp.get("photos").size());
        assertEquals(2000, odometer(asset));
        JsonNode insp2 = ok(post(inspUrl, body("tanggal", day, "checklist", Map.of(), "overallResult", "BAIK",
                "nextDueDate", "bukan-tanggal")));
        // nextDueDate tidak valid diabaikan → tanggal + 30 hari (default).
        assertEquals(today().plusDays(30) + "T00:00:00.000Z", insp2.get("nextDueDate").asText());

        String iid = insp.get("id").asText();
        String photoUrl = inspUrl + "/" + iid + "/photos";
        expectError(400, "File wajib diupload.", multipart(photoUrl).param("category", "mesin"));
        expectError(400, "File wajib diupload.", multipart(photoUrl)
                .file(new MockMultipartFile("photo", "a.txt", "text/plain", "halo".getBytes())).param("category", "mesin"));
        expectError(400, "Kategori foto wajib diisi.", multipart(photoUrl)
                .file(new MockMultipartFile("photo", "a.png", "image/png", PNG)).param("category", "  "));
        expectError(400, "Kategori foto tidak dikenal.", multipart(photoUrl)
                .file(new MockMultipartFile("photo", "a.png", "image/png", PNG)).param("category", "kabin"));
        expectError(404, "Data inspeksi tidak ditemukan.", multipart(BASE + "/assets/lain/inspections/" + iid + "/photos")
                .file(new MockMultipartFile("photo", "a.png", "image/png", PNG)).param("category", "mesin"));
        JsonNode photo = ok(multipart(photoUrl).file(new MockMultipartFile("photo", "a.png", "image/png", PNG))
                .param("category", "mesin").param("caption", "Oli bocor"));
        String filePath = photo.get("filePath").asText();
        assertTrue(filePath.startsWith("erp/ga/inspections/") && filePath.endsWith(".png"), filePath);
        assertEquals("Oli bocor", photo.get("caption").asText());
        Path stored = Paths.get(System.getProperty("user.dir"), "uploads", filePath);
        assertTrue(Files.exists(stored), stored.toString());

        String pid = photo.get("id").asText();
        MvcResult file = raw(get(photoUrl + "/" + pid + "/file"));
        assertEquals(200, file.getResponse().getStatus());
        assertEquals("image/png", file.getResponse().getContentType());
        assertEquals("nosniff", file.getResponse().getHeader("X-Content-Type-Options"));
        assertArrayEquals(PNG, file.getResponse().getContentAsByteArray());
        expectError(404, "Foto tidak ditemukan.", get(BASE + "/assets/lain/inspections/" + iid + "/photos/" + pid + "/file"));

        JsonNode list = ok(get(inspUrl));
        assertEquals(2, list.size());
        JsonNode withPhoto = list.get(0).get("id").asText().equals(iid) ? list.get(0) : list.get(1);
        assertEquals(1, withPhoto.get("photos").size());
        assertEquals("mesin", withPhoto.get("photos").get(0).get("category").asText());

        expectStatus(204, delete(photoUrl + "/" + pid));
        assertFalse(Files.exists(stored));
        expectStatus(204, delete(photoUrl + "/" + pid)); // idempoten
        JsonNode photo2 = ok(multipart(photoUrl).file(new MockMultipartFile("photo", "b.jpg", "image/jpeg", PNG))
                .param("category", "ban_roda"));
        Path stored2 = Paths.get(System.getProperty("user.dir"), "uploads", photo2.get("filePath").asText());
        assertTrue(Files.exists(stored2));
        expectStatus(204, delete(inspUrl + "/" + iid));
        assertFalse(Files.exists(stored2));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ga_vehicle_inspection_photos WHERE inspection_id = ?",
                Integer.class, iid));
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.", delete(inspUrl + "/" + iid));
        assertEquals(1, ok(get(inspUrl)).size());
    }

    @Test
    void tires_guard_one_active_per_position_and_incidents() throws Exception {
        provision(email("veh.tire"), "SUPER_ADMIN");
        String t = tag();
        String asset = newVehicle("KDR-" + t);
        String day = today().toString();
        String tireUrl = BASE + "/assets/" + asset + "/tires";

        expectError(400, "Nomor seri wajib diisi.", post(tireUrl, body("serialNumber", "", "brand", "GT")));
        expectError(400, "Merek ban wajib diisi.", post(tireUrl, body("serialNumber", "SN1", "brand", "")));
        JsonNode a = ok(post(tireUrl, body("serialNumber", "SN-A-" + t, "brand", "Bridgestone", "position", "DEPAN_KIRI",
                "installedAt", day, "installedOdometer", 1000)));
        assertEquals("ACTIVE", a.get("status").asText());
        expectError(400, "Posisi DEPAN_KIRI sudah diisi oleh ban aktif (SN: SN-A-" + t + "). Ganti status ban lama terlebih dahulu.",
                post(tireUrl, body("serialNumber", "SN-X-" + t, "brand", "GT", "position", "DEPAN_KIRI", "installedAt", day)));
        JsonNode b = ok(post(tireUrl, body("serialNumber", "SN-B-" + t, "brand", "GT", "position", "BELAKANG_KANAN",
                "installedAt", day)));
        JsonNode c = ok(post(tireUrl, body("serialNumber", "SN-C-" + t, "brand", "GT", "position", "CADANGAN",
                "installedAt", day, "status", "SPARE")));
        assertEquals("SPARE", c.get("status").asText());

        // Urutan posisi = urutan enum (DEPAN_KIRI, ..., BELAKANG_KANAN, ..., CADANGAN).
        JsonNode tires = ok(get(tireUrl));
        assertEquals(3, tires.size());
        assertEquals("DEPAN_KIRI", tires.get(0).get("position").asText());
        assertEquals("BELAKANG_KANAN", tires.get(1).get("position").asText());
        assertEquals("CADANGAN", tires.get(2).get("position").asText());

        String aId = a.get("id").asText();
        JsonNode archived = ok(patch(tireUrl + "/" + aId, body("status", "ARCHIVED", "notes", "Aus")));
        assertEquals("ARCHIVED", archived.get("status").asText());
        assertEquals("Aus", archived.get("notes").asText());
        expectError(400, "Posisi BELAKANG_KANAN sudah diisi oleh ban aktif lain (SN: SN-B-" + t + ").",
                patch(tireUrl + "/" + aId, body("status", "ACTIVE", "position", "BELAKANG_KANAN")));
        expectError(404, "Ban tidak ditemukan.", patch(tireUrl + "/tidak-ada", body("status", "ACTIVE")));
        expectStatus(204, delete(tireUrl + "/" + b.get("id").asText()));
        JsonNode moved = ok(patch(tireUrl + "/" + aId, body("status", "ACTIVE", "position", "BELAKANG_KANAN")));
        assertEquals("BELAKANG_KANAN", moved.get("position").asText());
        assertEquals("ACTIVE", moved.get("status").asText());

        // Insiden
        String incUrl = BASE + "/assets/" + asset + "/incidents";
        expectError(400, "Invalid option: expected one of \"RINGAN\"|\"SEDANG\"|\"BERAT\"",
                post(incUrl, body("tanggal", day, "description", "Serempet", "severity", "PARAH")));
        JsonNode inc = ok(post(incUrl, body("tanggal", day, "description", "Serempet tiang", "severity", "RINGAN",
                "estimatedCost", 500000)));
        assertEquals("DILAPORKAN", inc.get("status").asText());
        assertEquals(1, ok(get(BASE + "/fleet?id=" + asset)).get("data").get(0).get("openIncidentCount").asInt());
        JsonNode upd = ok(patch(incUrl + "/" + inc.get("id").asText(), body("status", "SELESAI", "actualCost", 450000)));
        assertEquals("SELESAI", upd.get("status").asText());
        assertEquals(0, upd.get("actualCost").decimalValue().compareTo(new java.math.BigDecimal("450000")));
        assertEquals("Serempet tiang", upd.get("description").asText());
        assertEquals(0, ok(get(BASE + "/fleet?id=" + asset)).get("data").get(0).get("openIncidentCount").asInt());
        assertEquals(1, ok(get(incUrl)).size());
        expectStatus(204, delete(incUrl + "/" + inc.get("id").asText()));
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.", delete(incUrl + "/" + inc.get("id").asText()));
    }
}
