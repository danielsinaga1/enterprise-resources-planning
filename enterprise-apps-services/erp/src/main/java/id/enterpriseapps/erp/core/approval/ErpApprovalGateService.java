package id.enterpriseapps.erp.core.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.notification.ErpNotificationService;
import id.enterpriseapps.erp.core.security.ErpActor;
import id.enterpriseapps.erp.core.security.ErpPermissionService;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import id.enterpriseapps.erp.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gerbang approval — port {@code services/approvalGate.ts} + bagian antrian
 * {@code approvalEditRequests.ts}.
 *
 * <p>Role tanpa capability {@code approval.bypass} tidak langsung membuat dokumen: payload disimpan
 * di {@code approval_requests} dan dieksekusi saat disetujui (lihat {@link ErpApprovalHandler}).
 * Efek GL/stok/WAC baru terjadi setelah approve.
 */
@Service
@RequiredArgsConstructor
public class ErpApprovalGateService {

    public static final String BEFORE_KEY = "_before";
    public static final String PENDING_EXISTS_MESSAGE =
            "Sudah ada pengajuan yang menunggu persetujuan untuk dokumen ini. Tunggu keputusan manajer terlebih dahulu.";

    /** Label tipe dokumen yang di-gate. */
    public static final Map<String, String> GATED_LABELS = Map.of(
            "PurchaseInvoice", "Invoice Pembelian",
            "ProductionRun", "Data Produksi",
            "SalesReturn", "Retur Penjualan",
            "DeliveryOrder", "Surat Jalan",
            "ProductionRunEdit", "Edit Data Produksi",
            "PurchaseInvoiceRevisePrice", "Revisi Harga Invoice Pembelian");

    public static final List<String> EDIT_DOCUMENT_TYPES = List.of("ProductionRunEdit", "PurchaseInvoiceRevisePrice");

    private final ErpPermissionService permissions;
    private final ErpNotificationService notifications;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Role ini perlu approval? (tidak punya {@code approval.bypass}). */
    public boolean needsApproval(ErpActor actor) {
        return !permissions.hasCap(actor.roleKeys(), "approval.bypass");
    }

    /** Tautan halaman Approval untuk notifikasi; null bila app.base-url tidak di-set. */
    public String approvalsLink(String requestId) {
        return notifications.appLink(requestId == null ? "/approvals" : "/approvals?request=" + requestId);
    }

    public static boolean isEditDocumentType(String type) {
        return EDIT_DOCUMENT_TYPES.contains(type);
    }

    /**
     * Simpan antrian approval (payload = body submit asli; dokumen BELUM dibuat).
     * Port {@code queueApproval}.
     */
    @ErpTx
    public ApprovalRequest queueApproval(String documentType, Object payload, BigDecimal amount, String requestedBy,
                                         String label) {
        ApprovalRequest r = new ApprovalRequest();
        r.setDocumentType(documentType);
        r.setDocumentId(null);
        r.setDocumentNumber(label != null ? label
                : "(menunggu approval) " + GATED_LABELS.getOrDefault(documentType, documentType));
        r.setAmount(Money.round2(amount));
        r.setPayload(ErpJson.tree(payload));
        r.setRequestedBy(requestedBy);
        r.setStatus("Pending");
        em.persist(r);
        em.flush();
        return r;
    }

    /** Pengajuan Pending untuk target (tipe edit). */
    @ErpTx(readOnly = true)
    public ApprovalRequest findPendingForTarget(String documentType, String documentId) {
        List<ApprovalRequest> rows = em.createQuery(
                        "select r from ApprovalRequest r where r.documentType = :t and r.documentId = :id and r.status = 'Pending'",
                        ApprovalRequest.class)
                .setParameter("t", documentType).setParameter("id", documentId).setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Antrian untuk mutasi dokumen yang sudah ada (ProductionRunEdit, PurchaseInvoiceRevisePrice) — port
     * {@code queueEditApproval}. Validasi target ("prepare") dilakukan pemanggil di modul domain.
     *
     * @param before   snapshot sebelum perubahan (disimpan di payload {@code _before})
     * @param summary  ringkasan untuk notifikasi (mis. "selisih Rp 1.000")
     * @return body 202 {@code { pendingApproval: true, request, message }}; 409 bila sudah ada Pending
     */
    @ErpTx
    public Map<String, Object> queueEditApproval(String documentType, String targetId, String documentNumber,
                                                 BigDecimal amount, JsonNode rawBody, Object before,
                                                 String summary, ErpActor requester) {
        if (findPendingForTarget(documentType, targetId) != null) {
            throw ErpException.conflict(PENDING_EXISTS_MESSAGE);
        }
        ObjectNode payload = rawBody != null && rawBody.isObject() ? ((ObjectNode) rawBody).deepCopy() : ErpJson.object();
        payload.set(BEFORE_KEY, ErpJson.tree(before));
        ApprovalRequest r = new ApprovalRequest();
        r.setDocumentType(documentType);
        r.setDocumentId(targetId);
        r.setDocumentNumber(documentNumber);
        r.setAmount(Money.round2(amount));
        r.setPayload(payload);
        r.setRequestedBy(requester.userId());
        r.setStatus("Pending");
        em.persist(r);
        em.flush();

        String label = GATED_LABELS.getOrDefault(documentType, documentType);
        User u = em.find(User.class, requester.userId());
        String link = approvalsLink(r.getId());
        notifications.notifyAdmins(new ErpNotificationService.Notice(
                "approval_pending",
                "🔔 Persetujuan: " + label,
                label + " " + documentNumber + " (" + summary + ") dari " + (u != null ? u.getFullName() : "staf")
                        + " menunggu persetujuan Anda." + (link != null ? "\n\n🔗 Buka & proses: " + link : ""),
                ErpJson.map("kind", "approval", "requestId", r.getId(), "documentType", documentType)),
                requester.userId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pendingApproval", true);
        body.put("request", r);
        body.put("message", "ProductionRunEdit".equals(documentType)
                ? "Perubahan data produksi menunggu persetujuan manajer sebelum diproses."
                : "Revisi harga menunggu persetujuan manajer sebelum diproses.");
        return body;
    }

    /** Pisahkan payload tersimpan → body asli + snapshot {@code _before}. */
    public static JsonNode[] splitStoredPayload(JsonNode payload) {
        ObjectNode body = payload != null && payload.isObject() ? ((ObjectNode) payload).deepCopy() : ErpJson.object();
        JsonNode before = body.remove(BEFORE_KEY);
        return new JsonNode[]{body, before};
    }

    /**
     * Notifikasi standar "pengajuan baru menunggu persetujuan" untuk dokumen baru yang di-gate
     * (dipanggil route POST setelah {@link #queueApproval}).
     */
    public void notifyPending(ApprovalRequest r, ErpActor requester, String summary) {
        String label = GATED_LABELS.getOrDefault(r.getDocumentType(), r.getDocumentType());
        String link = approvalsLink(r.getId());
        notifications.notifyAdmins(new ErpNotificationService.Notice(
                "approval_pending",
                "🔔 Persetujuan: " + label,
                label + (summary == null || summary.isBlank() ? "" : " (" + summary + ")") + " dari "
                        + (requester.fullName() != null ? requester.fullName() : "staf")
                        + " menunggu persetujuan Anda." + (link != null ? "\n\n🔗 Buka & proses: " + link : ""),
                ErpJson.map("kind", "approval", "requestId", r.getId(), "documentType", r.getDocumentType())),
                requester.userId());
    }
}
