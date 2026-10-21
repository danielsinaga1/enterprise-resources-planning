package id.enterpriseapps.erp.paymentrequest;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.notification.ErpNotificationService;
import id.enterpriseapps.erp.core.notification.ErpNotificationService.Notice;
import id.enterpriseapps.erp.core.security.ErpPermissionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notifikasi Pengajuan Pembayaran — port {@code services/paymentRequestNotify.ts} (tanpa Telegram). Dipanggil
 * SETELAH transaksi COMMIT dan tidak pernah melempar. Setiap notifikasi menaut ke halaman detail lewat
 * {@code metadata.link} (rute dalam aplikasi ERP, sama seperti sumber).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpPaymentRequestNotifier {

    static final String REMINDER_TYPE = "payment_request_settlement_due";

    private final ErpNotificationService notifications;
    private final ErpPermissionService permissions;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private record Plan(List<String> userIds, String type, String title, String message) {
    }

    private static String rupiah(BigDecimal n) {
        return "Rp " + Money.idr(n);
    }

    private List<String> financeUserIds() {
        return new ArrayList<>(notifications.activeUserIdsWithRoles(permissions.roleKeysWithPerm(PrRules.PR_MODULE, "edit")));
    }

    private static List<String> list(String... ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    private Plan planFor(PrEvent e) {
        String amount = rupiah(e.amount());
        String note = e.note() != null && !e.note().isEmpty() ? " Catatan: " + e.note() : "";
        switch (e.kind()) {
            case "pending_supervisor":
                return e.supervisorId() == null ? null : new Plan(list(e.supervisorId()), "payment_request_pending",
                        "Pengajuan pembayaran menunggu persetujuan Anda",
                        e.number() + " (" + amount + ") menunggu persetujuan atasan.");
            case "pending_finance":
                return new Plan(financeUserIds(), "payment_request_pending", "Pengajuan pembayaran perlu verifikasi Keuangan",
                        e.number() + " (" + amount + ") menunggu verifikasi Keuangan.");
            case "pending_director":
                return new Plan(e.directorRole() == null ? List.of()
                        : notifications.activeUserIdsWithRoles(List.of(e.directorRole())),
                        "payment_request_pending", "Pengajuan pembayaran menunggu persetujuan Direktur",
                        e.number() + " (" + amount + ") melewati batas nilai dan menunggu persetujuan Anda.");
            case "approved": {
                List<String> ids = list(e.requesterId());
                ids.addAll(financeUserIds());
                return new Plan(ids, "payment_request_approved", "Pengajuan pembayaran disetujui",
                        e.number() + " (" + amount + ") disetujui dan siap dibayar." + note);
            }
            case "rejected":
                return new Plan(list(e.requesterId()), "payment_request_rejected", "Pengajuan pembayaran ditolak",
                        e.number() + " ditolak." + note);
            case "revision":
                return new Plan(list(e.requesterId()), "payment_request_revision", "Pengajuan pembayaran perlu revisi",
                        e.number() + " dikembalikan untuk direvisi." + note);
            case "paid":
                return new Plan(list(e.requesterId()), "payment_request_paid", "Pengajuan pembayaran sudah dibayar",
                        e.number() + " (" + amount + ") sudah dibayarkan.");
            case "payment_cancelled":
                return new Plan(list(e.requesterId()), "payment_request_revision", "Pembayaran pengajuan dibatalkan",
                        "Pembayaran " + e.number() + " dibatalkan Keuangan dan kembali ke Siap Dibayar." + note);
            case "settlement_pending_supervisor":
                return e.supervisorId() == null ? null : new Plan(list(e.supervisorId()), "payment_request_pending",
                        "Pertanggungjawaban uang muka menunggu persetujuan Anda",
                        "Pertanggungjawaban " + e.number() + " (realisasi " + amount + ") menunggu persetujuan atasan.");
            case "settlement_pending_finance":
                return new Plan(financeUserIds(), "payment_request_pending",
                        "Pertanggungjawaban uang muka perlu diposting Keuangan",
                        "Pertanggungjawaban " + e.number() + " (realisasi " + amount
                                + ") menunggu verifikasi dan posting Keuangan." + note);
            case "settlement_revision":
                return new Plan(list(e.requesterId()), "payment_request_revision", "Pertanggungjawaban perlu revisi",
                        "Pertanggungjawaban " + e.number() + " dikembalikan untuk direvisi." + note);
            case "settlement_settled":
                return new Plan(list(e.requesterId()), "payment_request_paid", "Pertanggungjawaban uang muka selesai",
                        "Pertanggungjawaban " + e.number() + " (realisasi " + amount + ") sudah diposting.");
            case "settlement_posting_cancelled":
                return new Plan(list(e.requesterId()), "payment_request_revision", "Posting pertanggungjawaban dibatalkan",
                        "Posting pertanggungjawaban " + e.number() + " dibatalkan Keuangan dan kembali ke verifikasi." + note);
            case "cancelled": {
                List<String> ids = list(e.supervisorId());
                ids.addAll(financeUserIds());
                return new Plan(ids, "payment_request_cancelled", "Pengajuan pembayaran dibatalkan pengaju",
                        e.number() + " (" + amount + ") dibatalkan oleh pengaju." + note);
            }
            default:
                return null;
        }
    }

    /** Kirim notifikasi untuk event transisi. Tidak pernah melempar. */
    public void dispatch(List<PrEvent> events) {
        if (events == null) {
            return;
        }
        for (PrEvent e : events) {
            try {
                Plan plan = planFor(e);
                if (plan == null) {
                    continue;
                }
                // Jangan beri tahu aktor tentang tindakannya sendiri, dan jangan dua kali.
                Set<String> ids = new LinkedHashSet<>(plan.userIds());
                ids.remove(e.actorId());
                notifications.notifyUsers(new ArrayList<>(ids), new Notice(plan.type(), plan.title(), plan.message(),
                        metadata(e.requestId(), e.number())));
            } catch (RuntimeException ex) {
                log.error("payment request notification gagal (diabaikan) kind={} requestId={}: {}", e.kind(),
                        e.requestId(), ex.getMessage());
            }
        }
    }

    private static Map<String, Object> metadata(String requestId, String number) {
        return ErpJson.map("paymentRequestId", requestId, "number", number, "link", "/payment-requests/" + requestId);
    }

    /**
     * Pengingat harian ke pengaju yang uang mukanya belum dipertanggungjawabkan setelah
     * {@link PrSettlementRules#SETTLEMENT_OVERDUE_DAYS} hari (atau melewati batas waktu yang ia isi). Paling banyak
     * satu pengingat per pengajuan per 3 hari; tidak pernah melempar.
     */
    @ErpTx
    public int runSettlementReminders(Instant now) {
        try {
            List<Object[]> rows = em.createQuery("select p.id, p.number, p.requesterId, p.totalAmount, p.paymentDate, "
                            + "p.settlementDeadline from PaymentRequest p where p.status = 'PAID' "
                            + "and p.settlementStatus in ('OPEN', 'REVISION')", Object[].class)
                    .getResultList();
            List<Object[]> due = rows.stream()
                    .filter(r -> PrSettlementRules.isAdvanceOverdue((Instant) r[4], (Instant) r[5], now,
                            PrSettlementRules.SETTLEMENT_OVERDUE_DAYS))
                    .toList();
            if (due.isEmpty()) {
                return 0;
            }
            Set<String> already = new HashSet<>();
            em.createQuery("select n.metadata from Notification n where n.type = :type and n.createdAt >= :since",
                            JsonNode.class)
                    .setParameter("type", REMINDER_TYPE)
                    .setParameter("since", now.minus(Duration.ofDays(3)))
                    .getResultList()
                    .forEach(m -> {
                        String id = ErpJson.text(m, "paymentRequestId");
                        if (id != null && !id.isEmpty()) {
                            already.add(id);
                        }
                    });
            int created = 0;
            for (Object[] r : due) {
                String id = (String) r[0];
                if (already.contains(id)) {
                    continue;
                }
                int days = PrSettlementRules.advanceAgeDays((Instant) r[4], now);
                created += notifications.notifyUsers(List.of((String) r[2]), new Notice(REMINDER_TYPE,
                        "Uang muka belum dipertanggungjawabkan",
                        r[1] + " (" + rupiah((BigDecimal) r[3]) + ") sudah " + days
                                + " hari sejak dibayar. Segera isi pertanggungjawaban.",
                        metadata(id, (String) r[1])));
            }
            return created;
        } catch (RuntimeException e) {
            log.error("settlement reminder gagal (diabaikan): {}", e.getMessage());
            return 0;
        }
    }
}
