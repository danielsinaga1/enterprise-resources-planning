package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hour meter: pembacaan (validasi tidak mundur), jam jatuh tempo jadwal ber-interval jam dari pembacaan terakhir,
 * notifikasi {@code maintenance_due} berbasis jam, pembacaan saat WO selesai + jadwal bergulir, dan fallback ke
 * tanggal bila meter belum pernah dicatat.
 */
class MeterHoursFlowTest extends GaOpsTestSupport {

    private static final String CHECK = "/api/erp/notifications/check";
    private static final String METER = MT + "/meter-readings";

    private String schedule(Object... kv) throws Exception {
        Result r = call(post(MT + "/schedules", body(kv)));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.data().get("id").asText();
    }

    private Integer intCol(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private List<Map<String, Object>> dueNotices(String userId, String scheduleId) {
        return jdbc.queryForList("SELECT title, message FROM notifications WHERE type = 'maintenance_due' "
                + "AND user_id = ? AND metadata->>'scheduleId' = ?", userId, scheduleId);
    }

    @Test
    void readings_drive_hour_schedules_notifications_and_rolling() throws Exception {
        String admin = adminId();
        asAdmin();
        String t = tag();
        String forklift = asset(t + "F", "MESIN_PRODUKSI");

        // Tanpa pembacaan: jam jatuh tempo kosong, jadwal memakai tanggal.
        String early = schedule("assetId", forklift, "intervalHours", 250, "nextDueAt", "2099-01-01");
        assertNull(jdbc.queryForObject("SELECT next_due_at_hours FROM ga_maintenance_schedules WHERE id = ?", Integer.class, early));

        // Validasi pembacaan.
        ok(post(METER, body("assetId", forklift, "hours", 1000, "readingAt", "2026-01-10")));
        expectError(400, "Jam meter tidak boleh lebih kecil dari pembacaan sebelumnya (1.000 jam).",
                post(METER, body("assetId", forklift, "hours", 900)));
        expectError(400, "Jam meter tidak boleh lebih besar dari pembacaan sesudahnya (1.000 jam).",
                post(METER, body("assetId", forklift, "hours", 1200, "readingAt", "2026-01-01")));
        expectError(400, "Jam meter harus bilangan bulat.", post(METER, body("assetId", forklift, "hours", 1000.5)));
        expectError(400, "Tanggal pembacaan tidak boleh di masa depan.",
                post(METER, body("assetId", forklift, "hours", 1100, "readingAt", "2099-01-01")));
        expectError(400, "Aset tidak ditemukan.", post(METER, body("assetId", "tidak-ada", "hours", 1)));

        // Jadwal baru / jadwal lama yang diperbarui: jam jatuh tempo = meter terakhir + interval.
        String hourly = schedule("assetId", forklift, "intervalHours", 250, "nextDueAt", "2099-01-01");
        assertEquals(1250, intCol("SELECT next_due_at_hours FROM ga_maintenance_schedules WHERE id = ?", hourly));
        ok(put(MT + "/schedules/" + early, body("isActive", false)));
        assertEquals(1250, intCol("SELECT next_due_at_hours FROM ga_maintenance_schedules WHERE id = ?", early));
        JsonNode listed = ok(get(MT + "/schedules?assetId=" + forklift + "&active=true"));
        assertEquals(1000, listed.get(0).get("currentHours").asInt());
        assertEquals(1250, listed.get(0).get("nextDueAtHours").asInt());

        // Sisa 40 jam (≤ 50) → segera jatuh tempo, pesan memakai jam operasi.
        ok(post(METER, body("assetId", forklift, "hours", 1210, "notes", "Cek harian")));
        ok(post(CHECK, Map.of()));
        List<Map<String, Object>> soon = dueNotices(admin, hourly);
        assertEquals(1, soon.size());
        assertEquals("Maintenance Segera Jatuh Tempo", soon.get(0).get("title"));
        assertTrue(String.valueOf(soon.get(0).get("message"))
                .contains("jatuh tempo di 1.250 jam operasi (hour meter terakhir 1.210 jam)"), String.valueOf(soon.get(0).get("message")));

        // Lewat jam jatuh tempo → terlambat.
        ok(post(METER, body("assetId", forklift, "hours", 1262)));
        jdbc.update("DELETE FROM notifications WHERE type = 'maintenance_due' AND metadata->>'scheduleId' = ?", hourly);
        ok(post(CHECK, Map.of()));
        assertEquals("Maintenance Terlambat", dueNotices(admin, hourly).get(0).get("title"));

        JsonNode readings = ok(get(METER + "?assetId=" + forklift));
        assertEquals(3, readings.size());
        assertEquals(1262, readings.get(0).get("hours").asInt());
        assertEquals("Cek harian", readings.get(1).get("notes").asText());

        // WO dari jadwal selesai dengan jam meter: pembacaan tertaut ke WO, jadwal bergulir dari jam itu.
        String woId = ok(post(MT + "/work-orders", body("assetId", forklift, "type", "PREVENTIVE", "title", "Servis 250 jam " + t,
                "scheduleId", hourly))).get("id").asText();
        ok(patch(MT + "/work-orders/" + woId + "/assign", body("assignedToId", admin)));
        expectError(400, "Jam meter tidak boleh lebih kecil dari pembacaan sebelumnya (1.262 jam).",
                patch(MT + "/work-orders/" + woId + "/complete", body("resolution", "Selesai", "meterHours", 1200)));
        assertEquals("ASSIGNED", jdbc.queryForObject("SELECT status FROM ga_work_orders WHERE id = ?", String.class, woId));
        ok(patch(MT + "/work-orders/" + woId + "/complete", body("resolution", "Oli & filter diganti", "meterHours", 1275)));
        assertEquals(woId, jdbc.queryForObject("SELECT work_order_id FROM ga_meter_readings WHERE asset_id = ? AND hours = 1275",
                String.class, forklift));
        assertEquals(1275, intCol("SELECT last_done_at_hours FROM ga_maintenance_schedules WHERE id = ?", hourly));
        assertEquals(1525, intCol("SELECT next_due_at_hours FROM ga_maintenance_schedules WHERE id = ?", hourly));

        // Koreksi salah input.
        String wrong = ok(post(METER, body("assetId", forklift, "hours", 9999))).get("id").asText();
        ok(delete(METER + "/" + wrong));
        assertEquals(4, ok(get(METER + "?assetId=" + forklift)).size());
        expectError(400, "Pembacaan meter tidak ditemukan.", delete(METER + "/" + wrong));

        // Interval jam dihapus → jam jatuh tempo ikut kosong.
        ok(put(MT + "/schedules/" + hourly, body("intervalHours", null, "intervalDays", 30)));
        assertNull(jdbc.queryForObject("SELECT next_due_at_hours FROM ga_maintenance_schedules WHERE id = ?", Integer.class, hourly));
    }

    @Test
    void hour_schedule_without_meter_falls_back_to_date() throws Exception {
        String admin = adminId();
        asAdmin();
        String genset = asset(tag() + "G", "MESIN_PRODUKSI");
        String s = schedule("assetId", genset, "intervalHours", 500, "nextDueAt", "2020-01-01");
        ok(post(CHECK, Map.of()));
        List<Map<String, Object>> notices = dueNotices(admin, s);
        assertEquals(1, notices.size());
        assertEquals("Maintenance Terlambat", notices.get(0).get("title"));
        assertTrue(String.valueOf(notices.get(0).get("message")).contains("jatuh tempo 1 Januari 2020"));
    }

    @Test
    void roll_hours_uses_meter_or_previous_due_point() {
        var s = new id.neravoxa.erp.entity.GaMaintenanceSchedule();
        s.setIntervalHours(250);
        s.setNextDueAtHours(1250);
        ErpGaWorkOrderService.rollHours(s, null);
        assertEquals(1250, s.getLastDoneAtHours());
        assertEquals(1500, s.getNextDueAtHours());
        ErpGaWorkOrderService.rollHours(s, 1480);
        assertEquals(1730, s.getNextDueAtHours());

        var days = new id.neravoxa.erp.entity.GaMaintenanceSchedule();
        days.setIntervalDays(30);
        ErpGaWorkOrderService.rollHours(days, 1000);
        assertNull(days.getNextDueAtHours());
    }
}
