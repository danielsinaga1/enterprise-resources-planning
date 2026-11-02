package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jadwal maintenance jatuh tempo: km jatuh tempo dari odometer, notifikasi harian {@code maintenance_due}
 * (tanggal & km, tanpa jadwal yang sudah punya WO aktif, deduplikasi 24 jam, penerima pemegang izin
 * ga_maintenance edit), saran Asisten AI, dan km jadwal bergulir saat WO selesai.
 */
class MaintenanceDueFlowTest extends GaOpsTestSupport {

    private static final String CHECK = "/api/erp/notifications/check";

    private void odometer(String assetId, int km) {
        int updated = jdbc.update("UPDATE ga_vehicle_data SET odometer_terakhir = ? WHERE asset_id = ?", km, assetId);
        if (updated == 0) {
            jdbc.update("INSERT INTO ga_vehicle_data (id, asset_id, odometer_terakhir, created_at, updated_at) "
                    + "VALUES (?, ?, ?, now(), now())", UUID.randomUUID().toString(), assetId, km);
        }
    }

    private String schedule(Object... kv) throws Exception {
        Result r = call(post(MT + "/schedules", body(kv)));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.data().get("id").asText();
    }

    private List<Map<String, Object>> dueNotices(String userId, String scheduleId) {
        return jdbc.queryForList("SELECT title, message, metadata->>'state' AS state FROM notifications "
                + "WHERE type = 'maintenance_due' AND user_id = ? AND metadata->>'scheduleId' = ?", userId, scheduleId);
    }

    private Integer intCol(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void km_tracking_notifications_suggestions_and_rolling() throws Exception {
        String admin = adminId();
        String techRole = customRole(Map.of("ga_maintenance", "edit"));
        String techEmail = email("teknisi");
        String tech = provision(techEmail, techRole);
        String viewer = provision(email("viewer"), customRole(Map.of("ga_maintenance", "view")));
        asAdmin();
        String t = tag();

        // Km jatuh tempo awal = odometer terakhir + interval; tanpa odometer tetap kosong (pakai tanggal).
        String truck = asset(t + "K", "MESIN_PRODUKSI");
        odometer(truck, 10_000);
        String kmSchedule = schedule("assetId", truck, "intervalKm", 5000, "nextDueAt", "2099-01-01");
        assertEquals(15_000, intCol("SELECT next_due_at_km FROM ga_maintenance_schedules WHERE id = ?", kmSchedule));
        JsonNode listed = ok(get(MT + "/schedules?assetId=" + truck));
        assertEquals(10_000, listed.get(0).get("currentKm").asInt());
        assertEquals(15_000, listed.get(0).get("nextDueAtKm").asInt());

        String noOdo = asset(t + "N", "MESIN_PRODUKSI");
        String manualKm = schedule("assetId", noOdo, "intervalKm", 1000, "nextDueAtKm", 8000, "nextDueAt", "2099-01-01");
        assertEquals(8000, intCol("SELECT next_due_at_km FROM ga_maintenance_schedules WHERE id = ?", manualKm));
        assertTrue(ok(get(MT + "/schedules?assetId=" + noOdo)).get(0).get("currentKm").isNull());

        // Jadwal tanggal: terlambat, belum jatuh tempo, dan terlambat tetapi sudah punya WO aktif.
        String overdue = schedule("assetId", asset(t + "D", "MESIN_PRODUKSI"), "intervalDays", 30, "nextDueAt", "2020-01-01");
        String later = schedule("assetId", asset(t + "L", "MESIN_PRODUKSI"), "intervalDays", 30, "nextDueAt", "2099-01-01");
        String withWoAsset = asset(t + "W", "MESIN_PRODUKSI");
        String withWo = schedule("assetId", withWoAsset, "intervalDays", 30, "nextDueAt", "2020-01-01");
        ok(post(MT + "/work-orders", body("assetId", withWoAsset, "type", "PREVENTIVE", "title", "PM " + t, "scheduleId", withWo)));

        // Odometer mendekati km jatuh tempo (sisa 300 km ≤ 500) → segera jatuh tempo.
        odometer(truck, 14_700);

        ok(post(CHECK, Map.of()));
        List<Map<String, Object>> overdueNotices = dueNotices(admin, overdue);
        assertEquals(1, overdueNotices.size());
        assertEquals("Maintenance Terlambat", overdueNotices.get(0).get("title"));
        assertEquals("overdue", overdueNotices.get(0).get("state"));
        assertEquals("Jadwal maintenance Aset " + t + "D (AST-" + t + "D) sudah lewat, jatuh tempo 1 Januari 2020. "
                + "Buat Work Order dari tab Jadwal Preventif.", overdueNotices.get(0).get("message"));
        List<Map<String, Object>> kmNotices = dueNotices(admin, kmSchedule);
        assertEquals(1, kmNotices.size());
        assertEquals("Maintenance Segera Jatuh Tempo", kmNotices.get(0).get("title"));
        assertEquals("dueSoon", kmNotices.get(0).get("state"));
        assertTrue(String.valueOf(kmNotices.get(0).get("message")).contains("jatuh tempo di 15.000 km (odometer terakhir 14.700 km)"),
                String.valueOf(kmNotices.get(0).get("message")));
        assertEquals(1, dueNotices(tech, overdue).size());
        assertEquals(0, dueNotices(viewer, overdue).size());
        assertEquals(0, dueNotices(admin, later).size());
        assertEquals(0, dueNotices(admin, withWo).size());
        assertEquals(0, dueNotices(admin, manualKm).size());

        // Deduplikasi 24 jam.
        ok(post(CHECK, Map.of()));
        assertEquals(1, dueNotices(admin, overdue).size());
        assertEquals(1, dueNotices(admin, kmSchedule).size());

        // Saran Asisten AI (tanpa token AI).
        JsonNode suggestions = ok(get("/api/erp/assistant/suggestions?route=/ga/maintenance")).get("suggestions");
        JsonNode due = null;
        for (JsonNode s : suggestions) {
            if ("maintenanceDue".equals(s.get("key").asText())) {
                due = s;
            }
        }
        assertTrue(due != null && due.get("count").asInt() >= 2, suggestions.toString());
        assertEquals("/ga/maintenance?tab=schedules", due.get("link").asText());

        // WO dari jadwal km selesai → km bergulir dari odometer terakhir.
        String woId = ok(post(MT + "/work-orders", body("assetId", truck, "type", "PREVENTIVE", "title", "Servis " + t,
                "scheduleId", kmSchedule))).get("id").asText();
        ok(patch(MT + "/work-orders/" + woId + "/assign", body("assignedToId", tech)));
        asUser(techEmail, techRole);
        boolean mine = false;
        for (JsonNode s : ok(get("/api/erp/assistant/suggestions?route=/ga/maintenance")).get("suggestions")) {
            mine |= "myWorkOrders".equals(s.get("key").asText()) && s.get("count").asInt() == 1;
        }
        assertTrue(mine, "saran myWorkOrders untuk teknisi");
        ok(patch(MT + "/work-orders/" + woId + "/start", Map.of()));
        ok(patch(MT + "/work-orders/" + woId + "/complete", body("resolution", "Ganti oli")));
        assertEquals(14_700, intCol("SELECT last_done_at_km FROM ga_maintenance_schedules WHERE id = ?", kmSchedule));
        assertEquals(19_700, intCol("SELECT next_due_at_km FROM ga_maintenance_schedules WHERE id = ?", kmSchedule));

        // Interval km dihapus → km jatuh tempo ikut kosong.
        asAdmin();
        ok(put(MT + "/schedules/" + kmSchedule, body("intervalKm", null, "intervalDays", 90)));
        assertNull(jdbc.queryForObject("SELECT next_due_at_km FROM ga_maintenance_schedules WHERE id = ?", Integer.class, kmSchedule));
    }

    @Test
    void roll_km_without_odometer_uses_previous_due_point() {
        var s = new id.neravoxa.erp.entity.GaMaintenanceSchedule();
        s.setIntervalKm(1000);
        s.setNextDueAtKm(8000);
        ErpGaWorkOrderService.rollKm(s, null);
        assertEquals(8000, s.getLastDoneAtKm());
        assertEquals(9000, s.getNextDueAtKm());

        ErpGaWorkOrderService.rollKm(s, 8600);
        assertEquals(8600, s.getLastDoneAtKm());
        assertEquals(9600, s.getNextDueAtKm());

        var days = new id.neravoxa.erp.entity.GaMaintenanceSchedule();
        days.setIntervalDays(30);
        ErpGaWorkOrderService.rollKm(days, 5000);
        assertNull(days.getNextDueAtKm());
    }
}
