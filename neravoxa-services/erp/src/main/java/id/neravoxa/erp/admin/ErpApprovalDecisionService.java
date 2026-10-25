package id.neravoxa.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.approval.ErpApprovalGateService;
import id.neravoxa.erp.core.approval.ErpApprovalHandler;
import id.neravoxa.erp.core.approval.ErpApprovalHandler.ApprovedDocument;
import id.neravoxa.erp.core.approval.ErpApprovalHandlerRegistry;
import id.neravoxa.erp.core.notification.ErpNotificationService;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.entity.ApprovalRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keputusan approval — port {@code services/approvalDecision.ts} + {@code PUT /requests/:id/resubmit}.
 *
 * <p>Logika per tipe dokumen TIDAK ditulis di sini: approve memanggil {@link ErpApprovalHandler#approve},
 * tolak/minta-revisi memanggil {@link ErpApprovalHandler#declineReturningSource} dan ajukan-ulang memanggil
 * {@link ErpApprovalHandler#resubmitAmount} dari bean modul domain yang terdaftar di
 * {@link ErpApprovalHandlerRegistry}. Tipe ber-payload tanpa handler ditolak dengan pesan sumber untuk tipe
 * yang tidak dikenal; tolak/revisi tetap bisa tanpa handler.
 *
 * <p>Satu transaksi per keputusan: baris pengajuan dikunci ({@code SELECT … FOR UPDATE}) sebelum efek
 * dokumen dijalankan, lalu status ditulis dengan update bersyarat {@code status = 'Pending'}. Dua keputusan
 * bersamaan atas pengajuan yang sama: yang kedua menunggu kunci lalu mendapat "Pengajuan sudah diproses."
 * — efek GL/stok tidak pernah jalan dua kali. Gagal di tengah = rollback total (dokumen tidak dibuat,
 * status tetap Pending, notifikasi tidak terkirim karena ErpNotificationService menunggu COMMIT).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpApprovalDecisionService {

    public static final String APPROVE = "approve";
    public static final String REJECT = "reject";
    public static final String REVISE = "revise";

    private static final String PENDING = "Pending";
    private static final String NEEDS_REVISION = "NeedsRevision";

    private final ErpApprovalHandlerRegistry handlers;
    private final ErpNotificationService notifications;
    private final ErpAccess access;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * Putuskan pengajuan. Hanya Pending yang diproses (idempoten). Approve menjalankan payload tersimpan
     * (efek baru terjadi sekarang); revise wajib catatan. Pengaju diberi notifikasi.
     *
     * @return {@code { ok, status, docNumber?, documentType }}
     * @throws ErpException pesan untuk {@code { ok: false, error }} (controller menjawab 400)
     */
    @ErpTx(timeout = 30)
    public Map<String, Object> decide(String requestId, String action, String actorUserId, String rawNote) {
        String note = rawNote == null ? "" : rawNote.strip();
        ApprovalRequest request = em.find(ApprovalRequest.class, requestId, LockModeType.PESSIMISTIC_WRITE);
        if (request == null) {
            throw ErpException.business("Pengajuan tidak ditemukan.");
        }
        if (!PENDING.equals(request.getStatus())) {
            throw ErpException.business("Pengajuan sudah diproses.");
        }
        ErpApprovalHandler handler = handlers.find(request.getDocumentType()).orElse(null);
        if (APPROVE.equals(action)) {
            return approve(request, handler, actorUserId, note);
        }
        return decline(request, handler, action, actorUserId, note);
    }

    private Map<String, Object> approve(ApprovalRequest request, ErpApprovalHandler handler, String actorUserId, String note) {
        String type = request.getDocumentType();
        boolean edit = ErpApprovalGateService.isEditDocumentType(type);
        JsonNode payload = request.getPayload();
        ApprovedDocument created = null;
        if (payload != null && !payload.isNull() && !payload.isMissingNode()) {
            // Gate "approve-before-effect": dokumen baru dibuat di sini, atas nama pengaju.
            if (handler == null) {
                throw ErpException.business("Tipe dokumen tidak didukung untuk eksekusi: " + type);
            }
            if (edit && request.getDocumentId() == null) {
                throw ErpException.business("Pengajuan tidak memiliki dokumen target.");
            }
            // Mutasi dokumen existing: payload = body asli + snapshot _before (dipisah).
            JsonNode[] split = ErpApprovalGateService.splitStoredPayload(payload);
            created = handler.approve(request, split[0], split[1], actorUserId, note);
        } else if (request.getDocumentId() != null && handler != null
                && !ErpApprovalGateService.GATED_LABELS.containsKey(type)) {
            // Antrian berbasis status (PO: documentId terisi, tanpa payload) → handler mengubah status dokumen.
            created = handler.approve(request, null, null, actorUserId, note);
        }

        Map<String, Object> set = new LinkedHashMap<>();
        set.put("approvedBy", actorUserId);
        if (!note.isEmpty()) {
            set.put("notes", note);
        }
        if (created != null) {
            set.put("documentId", created.id());
            set.put("documentNumber", created.number());
        }
        markDecided(request.getId(), "Approved", set);

        String message = created != null && edit
                ? "Pengajuan disetujui. Perubahan pada " + created.number() + " telah diterapkan."
                : created != null
                ? "Pengajuan disetujui. Dokumen " + created.number() + " telah dibuat."
                : "Pengajuan Anda telah disetujui.";
        Map<String, Object> meta = ErpJson.map("kind", "approval", "requestId", request.getId(), "documentType", type);
        if (created != null) {
            meta.put("documentId", created.id());
        }
        notifications.notifyUsers(List.of(request.getRequestedBy()), new ErpNotificationService.Notice(
                "approval_approved", "Pengajuan Anda disetujui", message, meta));
        log.info("Approval approved + document executed requestId={} documentType={} doc={} approvedBy={}",
                request.getId(), type, created == null ? null : created.number(), actorUserId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("status", "Approved");
        if (created != null) {
            out.put("docNumber", created.number());
        }
        out.put("documentType", type);
        return out;
    }

    private Map<String, Object> decline(ApprovalRequest request, ErpApprovalHandler handler, String action,
                                        String actorUserId, String note) {
        String type = request.getDocumentType();
        // Sumber memeriksa ini sebelum efek apa pun (draft produksi dikembalikan) dan sebelum NeedsRevision.
        if (REVISE.equals(action) && note.isEmpty()) {
            throw ErpException.business("Alasan revisi wajib diisi.");
        }
        // Efek tolak/revisi milik modul domain (PO kembali Draf, draft produksi kembali Draft, …);
        // true = dokumen sumber dikembalikan ke pengaju → dicatat Rejected seperti sumber.
        boolean draftReturned = handler != null && handler.declineReturningSource(request, action, actorUserId, note);

        if (REJECT.equals(action) || draftReturned) {
            Map<String, Object> set = new LinkedHashMap<>();
            set.put("rejectedBy", actorUserId);
            if (!note.isEmpty()) {
                set.put("notes", note);
            }
            markDecided(request.getId(), "Rejected", set);
            String reason = note.isEmpty() ? "-" : note;
            notifications.notifyUsers(List.of(request.getRequestedBy()), new ErpNotificationService.Notice(
                    "approval_rejected", "Pengajuan ditolak",
                    draftReturned
                            ? "Pengajuan " + (REVISE.equals(action) ? "perlu revisi" : "ditolak") + ": " + reason
                            + ". Draft dikembalikan. Perbaiki lalu posting ulang."
                            : "Pengajuan ditolak: " + reason,
                    ErpJson.map("kind", "approval", "requestId", request.getId(), "documentType", type)));
            log.info("Approval rejected requestId={} by={}", request.getId(), actorUserId);
            return ErpJson.map("ok", true, "status", "Rejected", "documentType", type);
        }

        Map<String, Object> set = new LinkedHashMap<>();
        set.put("rejectedBy", actorUserId); // kolom yang sama mencatat siapa yang meminta revisi
        set.put("notes", note);
        markDecided(request.getId(), NEEDS_REVISION, set);
        notifications.notifyUsers(List.of(request.getRequestedBy()), new ErpNotificationService.Notice(
                "approval_revision", "Pengajuan perlu revisi", "Pengajuan perlu revisi: " + note,
                ErpJson.map("kind", "approval", "requestId", request.getId(), "documentType", type)));
        log.info("Approval request-revision requestId={} by={}", request.getId(), actorUserId);
        return ErpJson.map("ok", true, "status", NEEDS_REVISION, "documentType", type);
    }

    /** Tulis keputusan hanya bila masih Pending; 0 baris = sudah diputuskan pihak lain → batalkan semuanya. */
    private void markDecided(String id, String status, Map<String, Object> set) {
        StringBuilder jpql = new StringBuilder("update ApprovalRequest r set r.status = :status, r.decidedAt = :now");
        set.keySet().forEach(k -> jpql.append(", r.").append(k).append(" = :").append(k));
        jpql.append(" where r.id = :id and r.status = :pending");
        Query q = em.createQuery(jpql.toString())
                .setParameter("status", status).setParameter("now", Instant.now())
                .setParameter("id", id).setParameter("pending", PENDING);
        set.forEach(q::setParameter);
        if (q.executeUpdate() == 0) {
            throw ErpException.business("Pengajuan sudah diproses.");
        }
    }

    /**
     * {@code PUT /requests/:id/resubmit} — pengaju memperbaiki payload pengajuan berstatus NeedsRevision dan
     * mengajukan ulang (kembali Pending). Dokumen tetap baru dibuat saat approve. Skema & nilai antrian per
     * tipe (sumber: PurchaseInvoice / ProductionRun) milik handler; tipe lain → "Tipe dokumen tidak didukung.".
     */
    @ErpTx
    public ObjectNode resubmit(String id, JsonNode body) {
        ApprovalRequest request = em.find(ApprovalRequest.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (request == null) {
            throw ErpException.notFound("Request tidak ditemukan.");
        }
        if (!request.getRequestedBy().equals(access.userId()) && !access.hasCap(ErpApprovalQueryService.VIEW_ALL)) {
            throw ErpException.forbidden("Bukan pengajuan Anda.");
        }
        if (!NEEDS_REVISION.equals(request.getStatus())) {
            throw ErpException.business("Hanya pengajuan berstatus Perlu Revisi yang dapat diajukan ulang.");
        }
        JsonNode raw = body == null ? null : body.get("payload");
        if (raw == null || !(raw.isObject() || raw.isArray())) {
            throw ErpException.business("Payload tidak valid.");
        }

        // Validasi ulang payload + nilai antrian baru oleh modul pemilik tipe dokumen (skema submit-nya).
        ErpApprovalHandler handler = handlers.find(request.getDocumentType()).orElse(null);
        if (handler == null) {
            throw ErpException.business("Tipe dokumen tidak didukung.");
        }
        BigDecimal amount = handler.resubmitAmount(request, raw);

        request.setPayload(raw.deepCopy());
        request.setAmount(Money.round2(amount));
        request.setStatus(PENDING);
        request.setRejectedBy(null);
        request.setDecidedAt(null);
        em.flush();
        log.info("Approval resubmitted requestId={} by={}", request.getId(), access.userId());
        return AdminShape.scalars(request);
    }
}
