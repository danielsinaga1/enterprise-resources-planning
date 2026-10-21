package id.enterpriseapps.erp.eam.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Laporan kendaraan &amp; armada: TCO per kategori biaya (servis, BBM, pajak dibayar, premi asuransi,
 * insiden, WO + MR PAID), trend bulanan kontinu, kepatuhan dan kondisi (ban, oli).
 */
class FleetReportTest extends VehicleTestSupport {

    @Test
    void asset_and_fleet_report_shapes() throws Exception {
        String userId = provision(email("veh.rpt"), "SUPER_ADMIN");
        String t = tag();
        String category = newCategory(t);
        String asset = newAsset("RPT-" + t, category);
        String day = today().toString();
        String a = BASE + "/assets/" + asset;

        ok(put(a, body("noPlat", "R " + t, "brand", "Isuzu", "tipe", "Elf", "tahunProduksi", 2020)));
        ok(post(a + "/services", body("tanggal", day, "odometer", 10000, "jenisService", "OLI", "deskripsi", "Oli",
                "biaya", 500000)));
        ok(post(a + "/fuel-logs", body("tanggal", day, "odometer", 10400, "liters", 40, "totalCost", 300000)));
        JsonNode tax = ok(post(a + "/taxes", body("type", "PAJAK_KENDARAAN", "periodYear", 2026, "amount", 180000,
                "dueDate", today().plusDays(5).toString())));
        // Dibayar lewat Tagihan EAM: nominal riil di paidAmount.
        jdbc.update("UPDATE ga_vehicle_taxes SET paid_at = CAST(? AS timestamp), paid_amount = 200000 WHERE id = ?",
                day + " 03:00:00", tax.get("id").asText());
        ok(post(a + "/insurances", body("type", "TLO", "provider", "Asuransi " + t, "premium", 1000000,
                "startDate", day, "endDate", today().plusDays(365).toString())));
        ok(post(a + "/incidents", body("tanggal", day, "description", "Penyok", "severity", "SEDANG",
                "estimatedCost", 300000, "actualCost", 250000)));
        ok(post(a + "/tires", body("serialNumber", "SN-" + t, "brand", "GT", "position", "DEPAN_KIRI",
                "installedAt", day, "installedOdometer", 9000)));
        ok(post(a + "/oil-logs", body("tanggal", day, "odometer", 10000, "nextOdometerDue", 10300, "brand", "Shell", "volume", 5)));
        ok(post(a + "/inspections", body("tanggal", day, "checklist", java.util.Map.of(), "overallResult", "BAIK")));

        String wo = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_work_orders (id, code, asset_id, type, status, priority, title, reported_at, closed_at, "
                        + "labor_cost, external_cost, created_at, updated_at) VALUES (?, ?, ?, 'CORRECTIVE', 'CLOSED', 'MEDIUM', "
                        + "'Perbaikan', CAST(? AS timestamp), CAST(? AS timestamp), 100000, 25000, now(), now())",
                wo, "WO-T-" + t, asset, day + " 02:00:00", day + " 04:00:00");
        String woOpen = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_work_orders (id, code, asset_id, type, status, priority, title, reported_at, created_at, "
                        + "updated_at) VALUES (?, ?, ?, 'CORRECTIVE', 'OPEN', 'HIGH', 'Cek AC', CAST(? AS timestamp), now(), now())",
                woOpen, "WO-U-" + t, asset, day + " 02:00:00");
        jdbc.update("INSERT INTO ga_material_requests (id, code, status, work_order_id, requested_by_id, requested_at, "
                        + "paid_at, paid_amount, created_at, updated_at) VALUES (?, ?, 'PAID', ?, ?, CAST(? AS timestamp), "
                        + "CAST(? AS timestamp), 50000, now(), now())",
                UUID.randomUUID().toString(), "MR-T-" + t, wo, userId, day + " 01:00:00", day + " 05:00:00");

        JsonNode r = ok(get(a + "/report"));
        assertEquals(12, r.get("period").get("months").asInt());
        assertEquals(12, r.get("monthlyTrend").size());
        assertEquals("R " + t, r.get("asset").get("noPlat").asText());
        assertEquals(10400, r.get("asset").get("odometerTerakhir").asInt());
        assertEquals("Kategori uji " + t, r.get("asset").get("categoryName").asText());
        JsonNode b = r.get("tco").get("breakdown");
        assertEquals(500000, b.get("service").asLong());
        assertEquals(300000, b.get("fuel").asLong());
        assertEquals(200000, b.get("tax").asLong());
        assertEquals(1000000, b.get("insurance").asLong());
        assertEquals(250000, b.get("incident").asLong());
        assertEquals(0, b.get("workorder").asLong());
        assertEquals(2250000, r.get("tco").get("totalCost").asLong());
        assertTrue(r.get("tco").get("totalCost").isIntegralNumber());
        assertEquals(400, r.get("tco").get("distanceKm").asInt());
        assertEquals(10, r.get("tco").get("kmPerLiter").asInt());
        assertEquals(1, r.get("tco").get("counts").get("tax").asInt());
        JsonNode month = r.get("monthlyTrend").get(11);
        assertEquals(today().toString().substring(0, 7), month.get("month").asText());
        assertEquals(2250000, month.get("total").asLong());
        assertEquals(1, r.get("compliance").get("taxes").size());
        assertEquals("EXPIRING", r.get("compliance").get("taxes").get(0).get("state").asText());
        assertEquals("OK", r.get("compliance").get("insurances").get(0).get("state").asText());
        assertEquals(1, r.get("compliance").get("openIncidents").size());
        assertEquals(250000, r.get("compliance").get("openIncidents").get(0).get("cost").asLong());
        assertEquals(2, r.get("compliance").get("issueCount").asInt());
        JsonNode cond = r.get("condition");
        assertEquals("BAIK", cond.get("lastInspection").get("overallResult").asText());
        assertEquals(1, cond.get("activeTireCount").asInt());
        assertEquals(1400, cond.get("activeTires").get(0).get("usageKm").asInt());
        assertEquals(-100, cond.get("lastOil").get("remainingKm").asInt());
        assertTrue(cond.get("lastOil").get("isDue").asBoolean());
        expectError(404, "Aset tidak ditemukan.", get(BASE + "/assets/tidak-ada/report"));

        // Periode eksplisit di luar data → biaya 0, trend 1 bulan.
        JsonNode old = ok(get(a + "/report?from=2020-01-01&to=2020-01-31"));
        assertEquals("2020-01-01T00:00:00.000Z", old.get("period").get("from").asText());
        assertEquals("2020-01-31T23:59:59.999Z", old.get("period").get("to").asText());
        assertEquals(1, old.get("monthlyTrend").size());
        assertEquals(0, old.get("tco").get("totalCost").asLong());

        JsonNode f = ok(get(BASE + "/fleet-report?categoryId=" + category));
        assertEquals(1, f.get("vehicles").size());
        JsonNode v = f.get("vehicles").get(0);
        assertEquals(asset, v.get("assetId").asText());
        // WO CLOSED: jasa 100.000 + vendor 25.000; MR PAID 50.000 → satu kategori workorder.
        assertEquals(175000, v.get("breakdown").get("workorder").asLong());
        assertEquals(2425000, v.get("totalCost").asLong());
        // WO terbuka tanpa biaya tetap dihitung (reportedAt dalam periode).
        assertEquals(2, v.get("workOrderCount").asInt());
        assertEquals(1, v.get("openWorkOrderCount").asInt());
        assertEquals(1, v.get("materialRequestCount").asInt());
        assertEquals("EXPIRING", v.get("taxState").asText());
        assertEquals("OK", v.get("insuranceState").asText());
        // Pajak EXPIRING + insiden terbuka + WO terbuka.
        assertEquals(3, v.get("issueCount").asInt());
        assertEquals(40, v.get("totalLiters").asInt());
        assertEquals(2425000, f.get("totals").get("totalCost").asLong());
        assertEquals(1, f.get("totals").get("vehicleCount").asInt());
        assertEquals(6, f.get("distribution").size());
        assertEquals("service", f.get("distribution").get(0).get("category").asText());
        assertEquals(1, f.get("topSpenders").size());
        assertEquals(1, f.get("compliance").get("openWorkOrders").asInt());
        assertEquals(1, f.get("compliance").get("vehiclesWithIssues").asInt());
        assertEquals(12, f.get("monthlyTrend").size());

        JsonNode empty = ok(get(BASE + "/fleet-report?categoryId=tidak-ada"));
        assertEquals(0, empty.get("vehicles").size());
        assertEquals(0, empty.get("totals").get("vehicleCount").asInt());
        assertFalse(empty.get("compliance").has("openWorkOrders"));
        assertEquals(12, empty.get("monthlyTrend").size());
    }
}
