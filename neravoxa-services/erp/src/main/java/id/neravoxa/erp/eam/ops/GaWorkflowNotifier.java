package id.neravoxa.erp.eam.ops;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.notification.ErpNotificationService;
import id.neravoxa.erp.core.notification.ErpNotificationService.Notice;
import id.neravoxa.erp.entity.GaAsset;
import id.neravoxa.erp.entity.GaMaterialRequest;
import id.neravoxa.erp.entity.GaWorkOrder;
import id.neravoxa.erp.entity.GaWorkOrderPriority;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Notifikasi alur kerja EAM: WO ditugaskan ({@code wo_assigned}, ke teknisi), WO selesai ({@code wo_completed},
 * ke pelapor & yang menugaskan), MR diajukan ({@code mr_pending}, ke pemegang izin edit MR yang berhak
 * menyetujui) dan MR disetujui/ditolak ({@code mr_approved}/{@code mr_rejected}, ke pengaju). Pelaku tidak
 * pernah menerima notifikasi atas tindakannya sendiri. Dipanggil di dalam transaksi; notifikasi baru ditulis
 * setelah COMMIT.
 */
@Component
public class GaWorkflowNotifier {

    static final String MR_MODULE = "ga_material_request";

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("id-ID"));
    private static final Map<GaWorkOrderPriority, String> PRIORITY = Map.of(
            GaWorkOrderPriority.LOW, "rendah", GaWorkOrderPriority.MEDIUM, "sedang",
            GaWorkOrderPriority.HIGH, "tinggi", GaWorkOrderPriority.URGENT, "mendesak");

    private final JdbcTemplate jdbc;
    private final ErpNotificationService notifications;

    public GaWorkflowNotifier(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc, ErpNotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    void woAssigned(GaWorkOrder wo, String actorId) {
        if (wo.getAssignedToId() == null || wo.getAssignedToId().equals(actorId)) {
            return;
        }
        String due = wo.getDueAt() == null ? "" : ", tenggat " + LONG_DATE.format(wo.getDueAt().atZone(ErpDates.businessZone()));
        notifications.notifyUsers(List.of(wo.getAssignedToId()), new Notice("wo_assigned", "Work Order Ditugaskan",
                woLabel(wo) + " ditugaskan kepada Anda oleh " + name(actorId) + ". Prioritas "
                        + PRIORITY.get(wo.getPriority()) + due + ".", woMeta(wo)));
    }

    void woCompleted(GaWorkOrder wo, String actorId) {
        Set<String> to = new LinkedHashSet<>();
        if (wo.getReportedById() != null) {
            to.add(wo.getReportedById());
        }
        if (wo.getAssignedById() != null) {
            to.add(wo.getAssignedById());
        }
        to.remove(actorId);
        notifications.notifyUsers(to, new Notice("wo_completed", "Work Order Selesai",
                woLabel(wo) + " diselesaikan oleh " + name(actorId)
                        + (Boolean.TRUE.equals(wo.getWasLate()) ? " (melewati tenggat)" : "")
                        + ". Periksa hasilnya lalu tutup WO.", woMeta(wo)));
    }

    void mrSubmitted(GaMaterialRequest mr, String actorId) {
        notifications.notifyModule(MR_MODULE, "edit", new Notice("mr_pending", "Permintaan Material Menunggu Persetujuan",
                "Permintaan material " + mr.getCode() + mrTarget(mr) + " dari " + name(mr.getRequestedById())
                        + " menunggu persetujuan.", mrMeta(mr)), actorId);
    }

    void mrApproved(GaMaterialRequest mr, String actorId) {
        notifyRequester(mr, actorId, new Notice("mr_approved", "Permintaan Material Disetujui",
                "Permintaan material " + mr.getCode() + mrTarget(mr) + " disetujui oleh " + name(actorId)
                        + ". Pengadaan barang bisa dimulai.", mrMeta(mr)));
    }

    void mrRejected(GaMaterialRequest mr, String actorId) {
        notifyRequester(mr, actorId, new Notice("mr_rejected", "Permintaan Material Ditolak",
                "Permintaan material " + mr.getCode() + mrTarget(mr) + " ditolak oleh " + name(actorId)
                        + ": " + mr.getRejectReason(), mrMeta(mr)));
    }

    private void notifyRequester(GaMaterialRequest mr, String actorId, Notice n) {
        if (mr.getRequestedById() != null && !mr.getRequestedById().equals(actorId)) {
            notifications.notifyUsers(List.of(mr.getRequestedById()), n);
        }
    }

    private static String woLabel(GaWorkOrder wo) {
        GaAsset a = wo.getAsset();
        return wo.getCode() + " — " + wo.getTitle() + (a == null ? "" : " (" + a.getName() + ", " + a.getCode() + ")");
    }

    private static Map<String, Object> woMeta(GaWorkOrder wo) {
        return ErpJson.map("woId", wo.getId(), "woCode", wo.getCode(), "assetId", wo.getAssetId());
    }

    private static String mrTarget(GaMaterialRequest mr) {
        GaAsset a = mr.getAsset();
        if (a != null) {
            return " untuk " + a.getName() + " (" + a.getCode() + ")";
        }
        return mr.getMachineTarget() == null || mr.getMachineTarget().isBlank() ? "" : " untuk " + mr.getMachineTarget();
    }

    private static Map<String, Object> mrMeta(GaMaterialRequest mr) {
        return ErpJson.map("materialRequestId", mr.getId(), "code", mr.getCode());
    }

    /** Nama tampilan user ERP (nama lengkap, lalu username), "seseorang" bila tidak ditemukan. */
    private String name(String userId) {
        if (userId == null) {
            return "seseorang";
        }
        List<String> rows = jdbc.queryForList("SELECT coalesce(full_name, username) FROM users WHERE id = ?", String.class, userId);
        return rows.isEmpty() || rows.get(0) == null ? "seseorang" : rows.get(0);
    }
}
