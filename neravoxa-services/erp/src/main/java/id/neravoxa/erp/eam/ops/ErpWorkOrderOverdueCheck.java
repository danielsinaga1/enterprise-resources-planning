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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pemeriksaan "WO terlambat" ({@code wo_overdue}): work order OPEN/ASSIGNED/IN_PROGRESS yang melewati tenggat.
 * Penerima = teknisi yang ditugaskan + pemegang izin {@code ga_maintenance} edit. Sekali per WO per tenggat
 * (tenggat yang diubah memicu pengingat baru).
 */
@Slf4j
@Component
public class ErpWorkOrderOverdueCheck implements ErpNotificationCheck {

    static final String TYPE = "wo_overdue";

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("id-ID"));

    private final JdbcTemplate jdbc;
    private final ErpNotificationService notifications;
    private final ErpPermissionService permissions;

    public ErpWorkOrderOverdueCheck(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc, ErpNotificationService notifications,
                                    ErpPermissionService permissions) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.permissions = permissions;
    }

    @Override
    public int run(List<String> userIds, Instant now, Instant since) {
        try {
            return check(now);
        } catch (RuntimeException e) {
            log.error("wo_overdue check gagal (diabaikan): {}", e.getMessage());
            return 0;
        }
    }

    private int check(Instant now) {
        List<Map<String, Object>> late = jdbc.queryForList(
                "SELECT w.id, w.code, w.title, w.due_at, w.assigned_to_id, a.id AS asset_id, a.code AS asset_code, "
                        + "a.name AS asset_name, coalesce(u.full_name, u.username) AS assignee FROM ga_work_orders w "
                        + "JOIN ga_assets a ON a.id = w.asset_id LEFT JOIN users u ON u.id = w.assigned_to_id "
                        + "WHERE w.status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS') AND w.due_at IS NOT NULL AND w.due_at < ? "
                        + "ORDER BY w.due_at, w.code",
                LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        if (late.isEmpty()) {
            return 0;
        }
        List<String> editors = notifications.activeUserIdsWithRoles(new LinkedHashSet<>(permissions.roleKeysWithPerm(ErpMaintenanceDueCheck.MODULE, "edit")));
        Set<String> sent = notifications.sentKeys(TYPE, "woId", "dueAt");
        int created = 0;
        for (Map<String, Object> w : late) {
            String id = (String) w.get("id");
            Instant dueAt = ((Timestamp) w.get("due_at")).toLocalDateTime().toInstant(ZoneOffset.UTC);
            if (!sent.add(ErpNotificationService.key(id, dueAt))) {
                continue;
            }
            List<String> recipients = new ArrayList<>(editors);
            if (w.get("assigned_to_id") != null) {
                recipients.add((String) w.get("assigned_to_id"));
            }
            String who = w.get("assignee") != null ? "dikerjakan " + w.get("assignee") : "belum ditugaskan ke teknisi";
            created += notifications.notifyUsers(recipients, new ErpNotificationService.Notice(
                    TYPE,
                    "Work Order Terlambat",
                    w.get("code") + " — " + w.get("title") + " (" + w.get("asset_name") + ", " + w.get("asset_code")
                            + ") melewati tenggat " + LONG_DATE.format(dueAt.atZone(ErpDates.businessZone())) + ", " + who + ".",
                    ErpJson.map("woId", id, "woCode", w.get("code"), "assetId", w.get("asset_id"), "dueAt", dueAt.toString())));
        }
        return created;
    }
}
