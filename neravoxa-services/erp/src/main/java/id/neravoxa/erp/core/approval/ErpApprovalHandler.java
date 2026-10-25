package id.neravoxa.erp.core.approval;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.entity.ApprovalRequest;

import java.math.BigDecimal;

/**
 * Eksekutor per {@code documentType} untuk keputusan approval — pengganti cabang
 * if/else di {@code services/approvalDecision.ts}.
 *
 * <p>Modul domain mendaftarkan bean implementasi (mis. PurchaseInvoice, ProductionRun,
 * SalesReturn, DeliveryOrder, PurchaseOrder, ProductionRunEdit, PurchaseInvoiceRevisePrice);
 * halaman Approval memanggilnya lewat {@link ErpApprovalHandlerRegistry}. Semua method
 * dipanggil DI DALAM transaksi keputusan — lempar exception untuk membatalkan.
 */
public interface ErpApprovalHandler {

    /** Nilai {@code approval_requests.document_type} yang ditangani. */
    String documentType();

    /**
     * Jalankan efek dokumen saat disetujui (posting GL/stok dsb.) atas nama PENGAJU.
     *
     * @param request       baris antrian (documentId terisi untuk tipe edit / PO)
     * @param body          payload tersimpan tanpa kunci {@code _before}
     * @param before        snapshot {@code _before} (tipe edit) atau null
     * @param approverUserId pengguna yang menyetujui
     * @param note          catatan keputusan (boleh kosong)
     */
    ApprovedDocument approve(ApprovalRequest request, JsonNode body, JsonNode before, String approverUserId, String note);

    /** Efek samping saat ditolak / diminta revisi (opsional), mis. draft kembali ke Draft. */
    default void onDecline(ApprovalRequest request, String action, String approverUserId, String note) {
    }

    /**
     * Varian {@link #onDecline} yang melaporkan apakah dokumen sumber dikembalikan ke pengaju untuk
     * diperbaiki (mis. draf Data Produksi). Di ENTARIS, "minta revisi" yang mengembalikan draf dicatat
     * sebagai {@code Rejected} (bukan {@code NeedsRevision}) dengan pesan "Draft dikembalikan…".
     * Halaman Approval memanggil method ini; default = {@link #onDecline} lalu {@code false}.
     *
     * @param action "reject" atau "revise"
     */
    default boolean declineReturningSource(ApprovalRequest request, String action, String approverUserId, String note) {
        onDecline(request, action, approverUserId, note);
        return false;
    }

    /**
     * Validasi payload pengajuan ulang (status NeedsRevision → Pending, {@code PUT /requests/:id/resubmit})
     * dan hitung nilai {@code amount} barunya. Lempar {@link ErpException} 400 bila payload tidak valid.
     * Default: tipe ini tidak mendukung pengajuan ulang.
     */
    default BigDecimal resubmitAmount(ApprovalRequest request, JsonNode payload) {
        throw ErpException.business("Tipe dokumen tidak didukung.");
    }

    /** Dokumen hasil approve: id + nomor untuk notifikasi & respons. */
    record ApprovedDocument(String id, String number) {
    }
}
