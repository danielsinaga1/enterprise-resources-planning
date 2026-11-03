package id.neravoxa.erp.eam.ops;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.notification.ErpNotificationCheck;
import id.neravoxa.erp.core.notification.ErpNotificationService;
import id.neravoxa.erp.core.security.ErpPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pemeriksaan "jadwal maintenance jatuh tempo": jadwal preventif aktif yang terlambat atau segera jatuh tempo
 * (aturan {@link MaintenanceDue}) dan belum punya WO aktif. Penerima = pemegang izin {@code ga_maintenance}
 * edit (termasuk super-admin), bukan daftar penerima keuangan dari runner. Deduplikasi
 * {@code metadata.scheduleId} 24 jam; WO tetap dibuat manual dari tab Jadwal.
 */
@Slf4j
@Component
public class ErpMaintenanceDueCheck implements ErpNotificationCheck {

    static final String TYPE = "maintenance_due";
    static final String MODULE = "ga_maintenance";

    private static final Locale ID = Locale.forLanguageTag("id-ID");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", ID);

    private final JdbcTemplate jdbc;
    private final ErpNotificationService notifications;
    private final ErpPermissionService permissions;

    public ErpMaintenanceDueCheck(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc, ErpNotificationService notifications,
                                  ErpPermissionService permissions) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.permissions = permissions;
    }

    @Override
    public int run(List<String> userIds, Instant now, Instant since) {
        try {
            return check(now, since);
        } catch (RuntimeException e) {
            log.error("maintenance_due check gagal (diabaikan): {}", e.getMessage());
            return 0;
        }
    }

    private int check(Instant now, Instant since) {
        List<Map<String, Object>> due = jdbc.queryForList(
                "SELECT s.id, s.next_due_at, s.next_due_at_km, s.next_due_at_hours, a.id AS asset_id, a.code, a.name, "
                        + MaintenanceDue.CURRENT_KM + " AS current_km, " + MaintenanceDue.CURRENT_HOURS + " AS current_hours, "
                        + MaintenanceDue.OVERDUE_EXPR + " AS overdue "
                        + MaintenanceDue.FROM_WHERE + " ORDER BY s.next_due_at, a.code",
                utc(now), utc(now.plus(Duration.ofDays(MaintenanceDue.DUE_SOON_DAYS))));
        if (due.isEmpty()) {
            return 0;
        }
        List<String> recipients = notifications.activeUserIdsWithRoles(new LinkedHashSet<>(permissions.roleKeysWithPerm(MODULE, "edit")));
        if (recipients.isEmpty()) {
            return 0;
        }
        Set<String> notified = notifiedScheduleIds(since);
        int created = 0;
        for (Map<String, Object> s : due) {
            String id = (String) s.get("id");
            if (notified.contains(id)) {
                continue;
            }
            boolean overdue = Boolean.TRUE.equals(s.get("overdue"));
            String asset = s.get("name") + " (" + s.get("code") + ")";
            Integer currentKm = (Integer) s.get("current_km");
            Integer dueKm = (Integer) s.get("next_due_at_km");
            Integer currentHours = (Integer) s.get("current_hours");
            Integer dueHours = (Integer) s.get("next_due_at_hours");
            String when = dueHours != null && currentHours != null
                    ? "jatuh tempo di " + num(dueHours) + " jam operasi (hour meter terakhir " + num(currentHours) + " jam)"
                    : dueKm != null && currentKm != null
                    ? "jatuh tempo di " + num(dueKm) + " km (odometer terakhir " + num(currentKm) + " km)"
                    : "jatuh tempo " + LONG_DATE.format(instant(s.get("next_due_at")).atZone(ErpDates.businessZone()));
            created += notifications.notifyUsers(recipients, new ErpNotificationService.Notice(
                    TYPE,
                    overdue ? "Maintenance Terlambat" : "Maintenance Segera Jatuh Tempo",
                    "Jadwal maintenance " + asset + " " + (overdue ? "sudah lewat, " : "") + when
                            + ". Buat Work Order dari tab Jadwal Preventif.",
                    ErpJson.map("scheduleId", id, "assetId", s.get("asset_id"), "assetCode", s.get("code"),
                            "state", overdue ? "overdue" : "dueSoon")));
        }
        return created;
    }

    private Set<String> notifiedScheduleIds(Instant since) {
        return new HashSet<>(jdbc.queryForList("SELECT metadata->>'scheduleId' FROM notifications "
                + "WHERE type = ? AND created_at >= ? AND metadata->>'scheduleId' IS NOT NULL", String.class, TYPE, utc(since)));
    }

    private static String num(int v) {
        return NumberFormat.getIntegerInstance(ID).format(v);
    }

    /** Kolom timestamp tanpa zona berisi UTC. */
    private static LocalDateTime utc(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static Instant instant(Object v) {
        return v instanceof Timestamp t ? t.toLocalDateTime().toInstant(ZoneOffset.UTC) : Instant.now();
    }
}
