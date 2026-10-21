package id.enterpriseapps.erp.admin;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.security.ErpAccess;
import id.enterpriseapps.erp.core.security.ErpActor;
import id.enterpriseapps.erp.entity.PaymentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RBAC lampiran transaksi — port {@code services/attachmentAccess.ts}: lampiran mengikuti matriks izin
 * dokumen induknya (referenceType → modul). Lihat butuh {@code view}, unggah/hapus butuh {@code edit};
 * super-admin selalu lolos lewat ErpPermissionService.
 */
@Service
@RequiredArgsConstructor
public class ErpAttachmentAccessService {

    public static final List<String> REF_TYPES = List.of(
            "payment", "journal", "purchase_invoice", "sales_invoice", "sales_order", "delivery_order", "goods_receipt",
            "approval_request");

    static final String VIEW = "view";
    static final String EDIT = "edit";
    static final String DENIED = "Akses ditolak: Tidak memiliki izin.";

    /** Modul per tipe dokumen; modul mana pun yang terdaftar memberi aksi tersebut. */
    private static final Map<String, Map<String, List<String>>> MODULES = Map.of(
            "payment", Map.of(VIEW, List.of("payment"), EDIT, List.of("payment")),
            // Baris buku kas (Transaksi Bank/Kas) juga menampilkan jurnal transfer/biaya.
            "journal", Map.of(VIEW, List.of("journal", "payment"), EDIT, List.of("journal")),
            "purchase_invoice", Map.of(VIEW, List.of("purchase_invoice"), EDIT, List.of("purchase_invoice")),
            "sales_invoice", Map.of(VIEW, List.of("sales_invoice"), EDIT, List.of("sales_invoice")),
            "sales_order", Map.of(VIEW, List.of("sales_order"), EDIT, List.of("sales_order")),
            "delivery_order", Map.of(VIEW, List.of("delivery_order"), EDIT, List.of("delivery_order")),
            "goods_receipt", Map.of(VIEW, List.of("goods_receipt"), EDIT, List.of("goods_receipt")));

    /** Pembayaran uang muka (dan refund-nya) dikelola dari modul uang muka. */
    private static final Map<PaymentType, String> DEPOSIT_MODULE = Map.of(
            PaymentType.CustomerDeposit, "customer_deposit",
            PaymentType.CustomerRefund, "customer_deposit",
            PaymentType.VendorDeposit, "vendor_deposit",
            PaymentType.VendorRefund, "vendor_deposit");

    private final ErpAccess access;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public static boolean isRefType(String value) {
        return value != null && REF_TYPES.contains(value);
    }

    /**
     * Pastikan aktor boleh {@code action} ("view"/"edit") atas lampiran dokumen; 403 (atau 404 untuk
     * pengajuan yang tidak ada) bila tidak. {@code referenceId} null = cek level modul (hitungan massal).
     */
    @ErpTx(readOnly = true)
    public void require(String referenceType, String referenceId, String action) {
        ErpActor actor = access.actor();
        if (actor == null || !isRefType(referenceType)) {
            throw ErpException.forbidden(DENIED);
        }
        if ("approval_request".equals(referenceType)) {
            approvalRequestAccess(actor, referenceId, action);
            return;
        }
        for (String module : modules(referenceType, referenceId, action)) {
            if (access.can(actor, module, action)) {
                return;
            }
        }
        throw ErpException.forbidden(DENIED);
    }

    /**
     * Modul yang memberi aksi atas lampiran {@code referenceType}. Dengan id pembayaran, pembayaran uang muka
     * juga menerima modul uang mukanya; tanpa id (hitungan massal) kedua modul uang muka diterima.
     */
    private List<String> modules(String referenceType, String referenceId, String action) {
        List<String> base = MODULES.get(referenceType).get(action);
        if (!"payment".equals(referenceType)) {
            return base;
        }
        List<String> out = new ArrayList<>(base);
        if (referenceId == null) {
            out.add("customer_deposit");
            out.add("vendor_deposit");
            return out;
        }
        List<PaymentType> type = em.createQuery("select p.paymentType from Payment p where p.id = :id", PaymentType.class)
                .setParameter("id", referenceId).getResultList();
        String deposit = type.isEmpty() || type.get(0) == null ? null : DEPOSIT_MODULE.get(type.get(0));
        if (deposit != null) {
            out.add(deposit);
        }
        return out;
    }

    /** Lampiran pengajuan: pengaju, pemegang approval.view_all, atau (lihat saja) yang punya izin lihat approvals. */
    private void approvalRequestAccess(ErpActor actor, String referenceId, String action) {
        if (access.hasCap(actor, ErpApprovalQueryService.VIEW_ALL)) {
            return;
        }
        if (VIEW.equals(action) && access.can(actor, "approvals", VIEW)) {
            return;
        }
        if (referenceId == null) {
            throw ErpException.forbidden(DENIED);
        }
        List<String> requester = em.createQuery("select r.requestedBy from ApprovalRequest r where r.id = :id", String.class)
                .setParameter("id", referenceId).getResultList();
        if (requester.isEmpty()) {
            throw ErpException.notFound("Pengajuan tidak ditemukan.");
        }
        if (!actor.userId().equals(requester.get(0))) {
            throw ErpException.forbidden(DENIED);
        }
    }
}
