package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GET /api/erp/dashboard/maintenance: bentuk respons + gerbang per sumber (maintenance/vehicle/documents). */
class MaintenanceDashboardTest extends GaOpsTestSupport {

    private static String day(int offsetDays) {
        return LocalDate.now(ZoneOffset.UTC).plusDays(offsetDays).toString();
    }

    private Set<String> keys(JsonNode arr, String field) {
        Set<String> out = new HashSet<>();
        arr.forEach(n -> out.add(n.get(field).asText()));
        return out;
    }

    @Test
    void shape_and_per_source_gating() throws Exception {
        asAdmin();
        String t = tag();
        String machine = asset(t, "MESIN_PRODUKSI");
        String vehicle = asset(t + "V", "KENDARAAN");

        // Jadwal sangat lama → terlambat; WO aktif lewat target.
        ok(post(MT + "/schedules", body("assetId", machine, "intervalDays", 30, "nextDueAt", "2001-01-01")));
        ok(post(MT + "/work-orders", body("assetId", machine, "title", "Overdue " + t, "dueAt", "2001-01-02")));
        // Pajak kendaraan aktif habis 5 hari lagi (segera), asuransi lewat (kedaluwarsa).
        jdbc.update("INSERT INTO ga_vehicle_taxes (id, asset_id, type, period_year, amount, due_date, status, created_at, updated_at) "
                + "VALUES (?, ?, 'KIR', 2026, 100000, ?::timestamp, 'ACTIVE', now(), now())",
                UUID.randomUUID().toString(), vehicle, day(5));
        jdbc.update("INSERT INTO ga_vehicle_insurances (id, asset_id, type, provider, start_date, end_date, status, created_at, updated_at) "
                + "VALUES (?, ?, 'ALL_RISK', ?, '2000-01-01', '2001-01-03', 'ACTIVE', now(), now())",
                UUID.randomUUID().toString(), vehicle, "Asuransi " + t);
        // Dokumen legal kedaluwarsa (status EXPIRED).
        String docType = jdbc.queryForObject("SELECT id FROM ga_document_types WHERE code = 'STNK'", String.class);
        jdbc.update("INSERT INTO ga_documents (id, code, title, type_id, asset_id, expiry_date, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, '2001-01-04', 'EXPIRED', now(), now())",
                UUID.randomUUID().toString(), "DOC-" + t, "STNK " + t, docType, vehicle);

        JsonNode all = ok(get(DASH));
        assertTrue(all.get("access").get("maintenance").asBoolean());
        assertTrue(all.get("access").get("vehicle").asBoolean());
        assertTrue(all.get("access").get("documents").asBoolean());
        JsonNode kpi = all.get("kpi");
        for (String k : List.of("overdue", "dueThisWeek", "overdueSchedules", "overdueWorkOrders", "expired", "expiring")) {
            assertTrue(kpi.has(k), k);
        }
        assertTrue(kpi.get("overdueSchedules").asInt() >= 1);
        assertTrue(kpi.get("overdueWorkOrders").asInt() >= 1);
        assertTrue(kpi.get("expired").asInt() >= 2);
        assertTrue(kpi.get("expiring").asInt() >= 1);
        assertTrue(kpi.get("dueThisWeek").asInt() >= 1);
        assertEquals(kpi.get("overdue").asInt(), kpi.get("overdueSchedules").asInt()
                + kpi.get("overdueWorkOrders").asInt() + kpi.get("expired").asInt());
        Set<String> attention = keys(all.get("attention"), "key");
        assertTrue(attention.containsAll(List.of("mt-schedule", "mt-wo", "mt-veh-expired", "mt-veh-expiring", "mt-doc-expired")),
                attention.toString());
        JsonNode upcoming = all.get("upcoming");
        assertTrue(upcoming.size() <= 8 && upcoming.size() >= 1);
        String prev = "";
        for (JsonNode u : upcoming) {
            for (String f : List.of("key", "kind", "kindLabel", "title", "assetName", "dueAt", "daysLeft", "state", "href")) {
                assertTrue(u.has(f), f);
            }
            assertTrue(u.get("dueAt").asText().compareTo(prev) >= 0, "urut dueAt naik");
            prev = u.get("dueAt").asText();
        }
        JsonNode first = upcoming.get(0);
        assertEquals("overdue", first.get("state").asText());
        assertTrue(first.get("daysLeft").asLong() < 0);
        Instant.parse(first.get("dueAt").asText());

        // Hanya izin kendaraan → sumber lain tidak dimuat.
        String vehicleOnly = customRole(Map.of("ga_vehicle", "view"));
        provision(email("veh.viewer"), vehicleOnly);
        JsonNode veh = ok(get(DASH));
        assertFalse(veh.get("access").get("maintenance").asBoolean());
        assertTrue(veh.get("access").get("vehicle").asBoolean());
        assertFalse(veh.get("access").get("documents").asBoolean());
        assertEquals(0, veh.get("kpi").get("overdueSchedules").asInt());
        assertEquals(0, veh.get("kpi").get("overdueWorkOrders").asInt());
        for (JsonNode u : veh.get("upcoming")) {
            assertTrue(Set.of("vehicleTax", "vehicleInsurance").contains(u.get("kind").asText()), u.toString());
        }
        Set<String> vehAttention = keys(veh.get("attention"), "key");
        assertFalse(vehAttention.contains("mt-schedule"));
        assertFalse(vehAttention.contains("mt-doc-expired"));
        assertTrue(vehAttention.contains("mt-veh-expired"));

        // Hanya dokumen.
        String docsOnly = customRole(Map.of("ga_documents", "view"));
        provision(email("doc.viewer"), docsOnly);
        JsonNode docs = ok(get(DASH));
        assertTrue(docs.get("access").get("documents").asBoolean());
        for (JsonNode u : docs.get("upcoming")) {
            assertEquals("document", u.get("kind").asText());
            assertEquals("Dokumen", u.get("kindLabel").asText());
        }
        assertTrue(docs.get("kpi").get("expired").asInt() >= 1);

        // Tanpa izin modul pemeliharaan apa pun → 403 (rute OPEN, gerbang di handler).
        String none = customRole(Map.of("ga_inventory", "view"));
        provision(email("no.maint"), none);
        Result denied = call(get(DASH));
        assertEquals(403, denied.status());
        assertEquals("Akses ditolak: tidak punya izin modul pemeliharaan.", denied.error());
    }
}
