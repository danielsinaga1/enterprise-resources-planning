package id.enterpriseapps.erp.eam.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data kendaraan (upsert + driver), pajak/STNK/KIR + status jatuh tempo, pengajuan pajak (approve →
 * record IN_PROCESS terkunci), asuransi, serta daftar &amp; ringkasan armada.
 */
class VehicleFleetTest extends VehicleTestSupport {

    @Test
    void vehicle_data_upsert_validation_and_driver() throws Exception {
        provision(email("veh.admin"), "SUPER_ADMIN");
        String t = tag();
        // Driver = pengguna ERP biasa (snapshot users).
        String driverId = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users (id, username, full_name, email, role, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 'Viewer', true, now(), now())", driverId, "drv" + t, "Supir " + t, "drv" + t + "@test.local");
        String asset = newVehicle("KDR-" + t);

        assertTrue(ok(get(BASE + "/assets/" + asset)).isNull());

        expectError(400, "Invalid input: expected object, received undefined",
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(BASE + "/assets/" + asset));
        expectError(400, "Too small: expected number to be >=1900", put(BASE + "/assets/" + asset, body("tahunProduksi", 1800)));
        expectError(400, "Invalid input: expected int, received number", put(BASE + "/assets/" + asset, body("tahunProduksi", 2020.5)));
        expectError(400, "Too big: expected string to have <=20 characters",
                put(BASE + "/assets/" + asset, body("noPlat", "X".repeat(21))));
        expectError(400, "Invalid option: expected one of \"BENSIN\"|\"SOLAR\"|\"PERTAMAX\"|\"PERTALITE\"|\"DEX\"|\"LPG\"|\"LISTRIK\"|\"HYBRID\"|\"LAINNYA\"",
                put(BASE + "/assets/" + asset, body("jenisBBM", "AVTUR")));
        expectError(400, "Invalid input: expected number, received string",
                put(BASE + "/assets/" + asset, body("kapasitasTangki", "45")));

        JsonNode v = ok(put(BASE + "/assets/" + asset, body("noPlat", "B " + t, "brand", "Toyota", "tipe", "Avanza",
                "tahunProduksi", 2022, "jenisBBM", "PERTALITE", "kapasitasTangki", 45.555, "odometerTerakhir", 1200)));
        assertEquals("B " + t, v.get("noPlat").asText());
        assertEquals(0, v.get("kapasitasTangki").decimalValue().compareTo(new java.math.BigDecimal("45.56")));
        assertFalse(v.has("asset"));
        String vid = v.get("id").asText();

        // Upsert kedua: baris sama, field yang tidak dikirim dikosongkan.
        JsonNode v2 = ok(put(BASE + "/assets/" + asset, body("noPlat", "B " + t, "brand", "Daihatsu")));
        assertEquals(vid, v2.get("id").asText());
        assertEquals("Daihatsu", v2.get("brand").asText());
        assertTrue(v2.get("tipe").isNull());
        assertEquals("B " + t, ok(get(BASE + "/assets/" + asset)).get("noPlat").asText());

        expectError(400, "Too small: expected string to have >=1 characters",
                patch(BASE + "/assets/" + asset + "/driver", body("picUserId", "")));
        JsonNode d = ok(patch(BASE + "/assets/" + asset + "/driver", body("picUserId", driverId)));
        assertEquals(driverId, d.get("picUserId").asText());
        assertEquals("Supir " + t, d.get("picName").asText());
        JsonNode row = ok(get(BASE + "/fleet?id=" + asset)).get("data").get(0);
        assertEquals("Supir " + t, row.get("driver").get("fullName").asText());
        // Pencarian juga mencakup nama driver.
        String driverToken = "SOPIR" + tag();
        jdbc.update("UPDATE users SET full_name = ? WHERE id = ?", driverToken, driverId);
        assertEquals(1, ok(get(BASE + "/fleet?search=" + driverToken.toLowerCase())).get("total").asInt());

        JsonNode cleared = ok(patch(BASE + "/assets/" + asset + "/driver", body("picUserId", null)));
        assertTrue(cleared.get("picUserId").isNull());
        assertTrue(cleared.get("picName").isNull());
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.",
                patch(BASE + "/assets/tidak-ada/driver", body("picUserId", null)));
    }

    @Test
    void taxes_due_states_requests_and_insurance_in_fleet() throws Exception {
        String owner = email("veh.owner");
        provision(owner, "SUPER_ADMIN");
        String other = email("veh.other");
        provision(other, "SUPER_ADMIN");
        asUser(owner, "SUPER_ADMIN");
        String t = tag();
        String code = "KDR-" + t;
        String asset = newVehicle(code);
        ok(put(BASE + "/assets/" + asset, body("noPlat", "D " + t)));

        String url = BASE + "/assets/" + asset + "/taxes";
        expectError(400, "Invalid option: expected one of \"STNK_TAHUNAN\"|\"STNK_5_TAHUNAN\"|\"KIR\"|\"PAJAK_KENDARAAN\"",
                post(url, body("periodYear", 2026, "dueDate", "2026-01-01")));
        expectError(400, "Too small: expected number to be >=2000", post(url, body("type", "KIR", "periodYear", 1999)));
        expectError(400, "Invalid input: expected string, received undefined", post(url, body("type", "KIR", "periodYear", 2026)));
        expectError(400, "Jatuh tempo wajib diisi.", post(url, body("type", "KIR", "periodYear", 2026, "dueDate", "")));

        String overdue = today().minusDays(5).toString();
        String soon = today().plusDays(10).toString();
        String later = today().plusDays(200).toString();
        JsonNode kir = ok(post(url, body("type", "KIR", "periodYear", 2026, "dueDate", overdue, "amount", 150000)));
        assertEquals("ACTIVE", kir.get("status").asText());
        assertEquals(overdue + "T00:00:00.000Z", kir.get("dueDate").asText());
        ok(post(url, body("type", "PAJAK_KENDARAAN", "periodYear", 2026, "dueDate", soon, "amount", 2500000)));
        // Record lama yang sudah lewat untuk jenis yang sama tidak mempengaruhi (yang terbaru berlaku).
        ok(post(url, body("type", "PAJAK_KENDARAAN", "periodYear", 2025, "dueDate", today().minusYears(1).toString())));
        ok(post(url, body("type", "STNK_5_TAHUNAN", "periodYear", 2026, "dueDate", later, "paidAt", "")));

        JsonNode list = ok(get(url));
        assertEquals(4, list.size());
        assertEquals(later + "T00:00:00.000Z", list.get(0).get("dueDate").asText());
        assertTrue(list.get(0).has("document") && list.get(0).get("document").isNull());

        JsonNode row = ok(get(BASE + "/fleet?id=" + asset)).get("data").get(0);
        assertEquals("overdue", row.get("due").get("kir").get("state").asText());
        assertEquals("dueSoon", row.get("due").get("tax").get("state").asText());
        assertEquals("ok", row.get("due").get("stnk").get("state").asText());
        assertTrue(row.get("due").get("insurance").get("state").isNull());
        assertEquals("overdue", row.get("attention").asText());
        assertEquals(overdue + "T00:00:00.000Z", row.get("nextDueDate").asText());
        assertEquals("D " + t, row.get("noPlat").asText());

        assertEquals(1, ok(get(BASE + "/fleet?due=overdue&search=" + code)).get("total").asInt());
        assertEquals(1, ok(get(BASE + "/fleet?due=attention&doc=tax&search=" + code)).get("total").asInt());
        assertEquals(0, ok(get(BASE + "/fleet?due=overdue&doc=stnk&search=" + code)).get("total").asInt());
        assertEquals(0, ok(get(BASE + "/fleet?doc=insurance&search=" + code)).get("total").asInt());
        expectError(400, "categoryKind tidak valid.", get(BASE + "/fleet?categoryKind=kapal"));
        expectError(400, "Status tidak valid.", get(BASE + "/fleet?status=HILANG"));
        expectError(400, "Filter jatuh tempo tidak valid.", get(BASE + "/fleet?due=besok"));
        expectError(400, "Jenis dokumen tidak valid.", get(BASE + "/fleet?doc=sim"));

        // Asuransi
        String insUrl = BASE + "/assets/" + asset + "/insurances";
        expectError(400, "Tanggal berakhir wajib diisi.", post(insUrl, body("type", "TLO", "provider", "Asuransi A",
                "startDate", today().toString(), "endDate", "")));
        JsonNode ins = ok(post(insUrl, body("type", "ALL_RISK", "provider", "Asuransi " + t, "premium", 3500000,
                "startDate", today().toString(), "endDate", today().plusDays(20).toString())));
        assertEquals("ACTIVE", ins.get("status").asText());
        assertEquals("dueSoon", ok(get(BASE + "/fleet?id=" + asset)).get("data").get(0).get("due").get("insurance").get("state").asText());
        assertEquals(1, ok(get(BASE + "/fleet?doc=insurance&search=" + code)).get("total").asInt());
        JsonNode insPatched = ok(patch(insUrl + "/" + ins.get("id").asText(), body("status", "CANCELLED", "policyNumber", "POL-" + t)));
        assertEquals("CANCELLED", insPatched.get("status").asText());
        assertEquals("POL-" + t, insPatched.get("policyNumber").asText());
        assertEquals(1, ok(get(insUrl)).size());
        assertTrue(ok(get(BASE + "/fleet?id=" + asset)).get("data").get(0).get("due").get("insurance").get("state").isNull());
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.",
                patch(insUrl + "/" + ins.get("id").asText(), body("documentId", "dok-tidak-ada")));
        expectStatus(204, delete(insUrl + "/" + ins.get("id").asText()));
        assertEquals(0, ok(get(insUrl)).size());

        // Pengajuan pajak → approve → record IN_PROCESS (terkunci untuk perubahan status).
        String trUrl = BASE + "/assets/" + asset + "/tax-requests";
        JsonNode tr = ok(post(trUrl, body("type", "STNK_TAHUNAN", "periodYear", 2027, "estimatedAmount", 1800000,
                "dueDate", today().plusDays(60).toString(), "deskripsi", "Perpanjangan STNK")));
        assertEquals("SUBMITTED", tr.get("status").asText());
        String trId = tr.get("id").asText();
        JsonNode trList = ok(get(BASE + "/tax-requests?assetId=" + asset));
        assertEquals(1, trList.size());
        assertEquals(code, trList.get(0).get("asset").get("code").asText());
        assertTrue(trList.get(0).get("tax").isNull());
        expectError(500, "Gagal mengambil pengajuan pajak.", get(BASE + "/tax-requests?status=XYZ"));

        expectError(400, "Alasan wajib diisi.", patch(BASE + "/tax-requests/" + trId + "/reject", body("reason", "")));
        JsonNode approved = ok(patch(BASE + "/tax-requests/" + trId + "/approve", Map.of()));
        assertEquals("APPROVED", approved.get("status").asText());
        String taxId = approved.get("taxId").asText();
        expectError(400, "Tidak bisa approve dari status APPROVED.", patch(BASE + "/tax-requests/" + trId + "/approve", Map.of()));
        expectError(400, "Pengajuan tidak ditemukan.", patch(BASE + "/tax-requests/tidak-ada/approve", Map.of()));
        expectError(404, "Pengajuan tidak ditemukan.", get(BASE + "/tax-requests/tidak-ada"));

        JsonNode detail = ok(get(BASE + "/tax-requests/" + trId));
        assertEquals("IN_PROCESS", detail.get("tax").get("status").asText());
        assertEquals("Dari pengajuan: Perpanjangan STNK", detail.get("tax").get("notes").asText());
        assertEquals("D " + t, detail.get("asset").get("vehicleData").get("noPlat").asText());
        assertEquals(1, ok(get(BASE + "/tax-requests?assetId=" + asset + "&status=APPROVED")).size());

        expectError(409, "Status tidak bisa diubah ke ACTIVE: data pajak ini sudah masuk antrian Tagihan GA (IN_PROCESS). "
                        + "Batalkan dulu jurnal/invoice terkait di menu Tagihan GA, baru status bisa diubah.",
                patch(url + "/" + taxId, body("status", "ACTIVE")));
        JsonNode kept = ok(patch(url + "/" + taxId, body("status", "IN_PROCESS", "notes", "Diproses biro jasa")));
        assertEquals("IN_PROCESS", kept.get("status").asText());
        assertEquals("Diproses biro jasa", kept.get("notes").asText());
        expectError(404, "Data pajak tidak ditemukan.", patch(url + "/tidak-ada", body("status", "ACTIVE")));

        // Batal hanya oleh pengaju.
        JsonNode tr2 = ok(post(trUrl, body("type", "KIR", "periodYear", 2027, "dueDate", today().plusDays(90).toString())));
        String tr2Id = tr2.get("id").asText();
        asUser(other, "SUPER_ADMIN");
        expectError(403, "Hanya pengaju yang bisa membatalkan.", patch(BASE + "/tax-requests/" + tr2Id + "/cancel", Map.of()));
        asUser(owner, "SUPER_ADMIN");
        assertEquals("CANCELLED", ok(patch(BASE + "/tax-requests/" + tr2Id + "/cancel", Map.of())).get("status").asText());
        expectError(400, "Tidak bisa reject dari status CANCELLED.",
                patch(BASE + "/tax-requests/" + tr2Id + "/reject", body("reason", "dobel")));
        JsonNode mine = ok(get(BASE + "/tax-requests?mine=true&assetId=" + asset));
        // Urutan: status asc (SUBMITTED, APPROVED, ..., CANCELLED).
        assertEquals(List.of("APPROVED", "CANCELLED"), List.of(mine.get(0).get("status").asText(), mine.get(1).get("status").asText()));

        // Hapus record pajak.
        expectStatus(204, delete(url + "/" + kir.get("id").asText()));
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.", delete(url + "/" + kir.get("id").asText()));

        // Ringkasan armada (hitungan global — cukup batas bawah).
        JsonNode summary = ok(get(BASE + "/fleet-summary?categoryKind=vehicle"));
        assertTrue(summary.get("totalVehicles").asInt() >= 1);
        assertTrue(summary.get("taxExpiringSoon").asInt() >= 1);
        assertTrue(summary.get("inspectionNeverDone").asInt() >= 1);
        assertTrue(summary.has("serviceCostThisMonth") && summary.has("fuelCostThisMonth"));
        expectError(400, "categoryKind tidak valid.", get(BASE + "/fleet-summary?categoryKind=kapal"));
        String isolated = newCategory(t);
        newAsset("ISO-" + t, isolated);
        assertEquals(1, ok(get(BASE + "/fleet-summary?categoryId=" + isolated)).get("totalVehicles").asInt());
    }
}
