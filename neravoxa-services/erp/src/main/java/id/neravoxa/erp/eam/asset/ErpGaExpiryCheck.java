package id.neravoxa.erp.eam.asset;

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pengingat tanggal GA: kalibrasi alat ({@code calibration_due}), dokumen aset ({@code document_expiring}) dan
 * kontrak sewa gedung ({@code rental_expiring}) yang segera jatuh tempo atau sudah lewat. Jendela "segera" sama
 * dengan UI (kalibrasi 30, dokumen 30, sewa 60 hari). Penerima = pemegang izin edit modulnya. Tiap item hanya
 * diingatkan sekali per keadaan (segera / lewat) per tanggal jatuh tempo, jadi tanggal yang diperbarui memicu
 * pengingat baru tanpa notifikasi harian berulang.
 */
@Slf4j
@Component
public class ErpGaExpiryCheck implements ErpNotificationCheck {

    static final int CALIBRATION_SOON_DAYS = 30;
    static final int DOCUMENT_SOON_DAYS = ErpDates.DUE_SOON_DAYS;
    static final int RENTAL_SOON_DAYS = 60;

    private static final Locale ID = Locale.forLanguageTag("id-ID");
    /** Kolom tanggal saja disimpan sebagai tengah malam UTC. */
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", ID);

    /**
     * Satu jenis pengingat. {@code sql} memilih {@code id, due, code, name} (+ kolom tambahan) dengan satu
     * parameter: batas akhir jendela "segera" (eksklusif).
     */
    private record Rule(String type, String module, String idKey, int soonDays, String sql) {
    }

    private static final Rule CALIBRATION = new Rule("calibration_due", "ga_equipment", "assetId", CALIBRATION_SOON_DAYS,
            "SELECT a.id, e.next_calibration_due AS due, a.code, a.name FROM ga_equipment_data e "
                    + "JOIN ga_assets a ON a.id = e.asset_id WHERE a.status NOT IN ('RETIRED', 'LOST') "
                    + "AND e.next_calibration_due IS NOT NULL AND e.next_calibration_due < ? ORDER BY e.next_calibration_due, a.code");
    private static final Rule DOCUMENT = new Rule("document_expiring", "ga_documents", "documentId", DOCUMENT_SOON_DAYS,
            "SELECT d.id, d.expiry_date AS due, d.code, d.title AS name, d.asset_id FROM ga_documents d "
                    + "WHERE d.status = 'ACTIVE' AND d.expiry_date IS NOT NULL AND d.expiry_date < ? ORDER BY d.expiry_date, d.code");
    private static final Rule RENTAL = new Rule("rental_expiring", "ga_building", "rentalId", RENTAL_SOON_DAYS,
            "SELECT r.id, r.tanggal_akhir AS due, a.code, a.name, r.pihak, a.id AS asset_id FROM ga_building_rentals r "
                    + "JOIN ga_assets a ON a.id = r.asset_id WHERE r.status = 'AKTIF' AND r.tanggal_akhir < ? "
                    + "ORDER BY r.tanggal_akhir, a.code");

    private final JdbcTemplate jdbc;
    private final ErpNotificationService notifications;
    private final ErpPermissionService permissions;

    public ErpGaExpiryCheck(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc, ErpNotificationService notifications,
                            ErpPermissionService permissions) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.permissions = permissions;
    }

    @Override
    public int run(List<String> userIds, Instant now, Instant since) {
        int created = 0;
        for (Rule r : List.of(CALIBRATION, DOCUMENT, RENTAL)) {
            try {
                created += check(r, now);
            } catch (RuntimeException e) {
                log.error("{} check gagal (diabaikan): {}", r.type(), e.getMessage());
            }
        }
        return created;
    }

    private int check(Rule r, Instant now) {
        LocalDate today = now.atZone(ErpDates.businessZone()).toLocalDate();
        Instant todayStart = today.atStartOfDay(ErpDates.businessZone()).toInstant();
        List<Map<String, Object>> due = jdbc.queryForList(r.sql(), utc(todayStart.plus(Duration.ofDays(r.soonDays() + 1L))));
        if (due.isEmpty()) {
            return 0;
        }
        List<String> recipients = notifications.activeUserIdsWithRoles(new LinkedHashSet<>(permissions.roleKeysWithPerm(r.module(), "edit")));
        if (recipients.isEmpty()) {
            return 0;
        }
        Set<String> sent = notifications.sentKeys(r.type(), r.idKey(), "state", "dueDate");
        int created = 0;
        for (Map<String, Object> row : due) {
            LocalDate dueDate = ((Timestamp) row.get("due")).toLocalDateTime().toLocalDate();
            long days = ChronoUnit.DAYS.between(today, dueDate);
            String state = days < 0 ? "overdue" : "dueSoon";
            String id = (String) row.get("id");
            if (!sent.add(ErpNotificationService.key(id, state, dueDate))) {
                continue;
            }
            String[] text = text(r, row, days, LONG_DATE.format(dueDate));
            Map<String, Object> meta = ErpJson.map(r.idKey(), id, "state", state, "dueDate", dueDate.toString(),
                    "code", row.get("code"));
            if (row.get("asset_id") != null) {
                meta.put("assetId", row.get("asset_id"));
            }
            created += notifications.notifyUsers(recipients, new ErpNotificationService.Notice(r.type(), text[0], text[1], meta));
        }
        return created;
    }

    /** Judul & pesan; {@code days} &lt; 0 = sudah lewat. */
    static String[] text(Rule r, Map<String, Object> row, long days, String date) {
        String item = row.get("name") + " (" + row.get("code") + ")";
        String left = days == 0 ? " (hari ini)" : days > 0 ? " (" + days + " hari lagi)" : "";
        boolean late = days < 0;
        return switch (r.type()) {
            case "calibration_due" -> late
                    ? new String[]{"Kalibrasi Terlambat", "Kalibrasi " + item + " sudah lewat jatuh tempo " + date
                    + ". Jadwalkan kalibrasi dan catat hasilnya di Peralatan."}
                    : new String[]{"Kalibrasi Segera Jatuh Tempo", "Kalibrasi " + item + " jatuh tempo " + date + left + "."};
            case "document_expiring" -> late
                    ? new String[]{"Dokumen Kedaluwarsa", "Dokumen " + item + " sudah kedaluwarsa sejak " + date
                    + ". Perbarui dokumen atau ubah statusnya."}
                    : new String[]{"Dokumen Segera Kedaluwarsa", "Dokumen " + item + " kedaluwarsa " + date + left + "."};
            default -> late
                    ? new String[]{"Sewa Sudah Berakhir", "Kontrak sewa " + item + " dengan " + row.get("pihak")
                    + " berakhir " + date + " tetapi masih berstatus Aktif. Perpanjang atau tandai berakhir."}
                    : new String[]{"Sewa Segera Berakhir", "Kontrak sewa " + item + " dengan " + row.get("pihak")
                    + " berakhir " + date + left + "."};
        };
    }

    /** Kolom timestamp tanpa zona berisi UTC. */
    private static LocalDateTime utc(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}
