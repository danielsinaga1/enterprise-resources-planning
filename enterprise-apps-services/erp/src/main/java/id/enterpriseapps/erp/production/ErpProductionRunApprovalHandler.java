package id.enterpriseapps.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.approval.ErpApprovalHandler;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import id.enterpriseapps.erp.production.ErpProductionRunPostingSupport.PostedRun;
import id.enterpriseapps.erp.production.ProductionRequests.ProductionRunRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Eksekusi antrian "ProductionRun" — cabang ProductionRun di {@code services/approvalDecision.ts}.
 *
 * <p>Approve: payload tersimpan divalidasi ulang (CreateProductionRunSchema) lalu disubmit atas nama
 * PENGAJU (efek stok/GL/WAC baru terjadi sekarang) dan draft yang tertaut menjadi Posted (antrian lama
 * tanpa draft = no-op). Tolak / minta revisi: draft tertaut kembali ke Draft dengan catatan
 * ("Ditolak: …" / "Perlu revisi: …") agar bisa diperbaiki & diposting ulang.
 */
@Component
@RequiredArgsConstructor
public class ErpProductionRunApprovalHandler implements ErpApprovalHandler {

    private final ErpProductionBodyParser parser;
    private final ErpProductionRunSubmitService submitter;
    private final ErpProductionRunDraftService drafts;

    @Override
    public String documentType() {
        return ErpProductionRunDraftService.DOCUMENT_TYPE;
    }

    @Override
    @ErpTx(timeout = 30)
    public ApprovedDocument approve(ApprovalRequest request, JsonNode body, JsonNode before, String approverUserId,
                                    String note) {
        ProductionRunRequest parsed;
        try {
            parsed = parser.parseProductionRun(body != null ? body : request.getPayload());
        } catch (ErpException e) {
            throw ErpException.business("Payload produksi tidak valid: " + e.getMessage());
        }
        PostedRun run = submitter.submit(parsed, request.getRequestedBy());
        drafts.markDraftPostedByApproval(request.getId(), run, approverUserId);
        return new ApprovedDocument(run.id(), run.runNumber());
    }

    /**
     * {@code action}: "reject" → catatan "Ditolak", selain itu (minta revisi) → "Perlu revisi" — revisi
     * wajib beralasan. Draft tertaut dikembalikan ke Draft; halaman Approval memakai
     * {@link #declineReturningSource} agar revisi atas draft dicatat Rejected seperti ENTARIS
     * (yang diperbaiki adalah draft-nya, bukan pengajuan ini).
     */
    @Override
    @ErpTx
    public void onDecline(ApprovalRequest request, String action, String approverUserId, String note) {
        declineReturningSource(request, action, approverUserId, note);
    }

    /** Sama dengan {@link #onDecline}; {@code true} bila draf tertaut dikembalikan ke Draft. */
    @Override
    @ErpTx
    public boolean declineReturningSource(ApprovalRequest request, String action, String approverUserId, String note) {
        String n = note == null ? "" : note.trim();
        boolean reject = "reject".equals(action);
        if (!reject && n.isEmpty()) {
            throw ErpException.business("Alasan revisi wajib diisi.");
        }
        return drafts.returnDraftToEditable(request.getId(), n, reject ? "Ditolak" : "Perlu revisi");
    }

    /** Pengajuan ulang: payload divalidasi CreateProductionRunSchema; amount = Σ kuantitas input. */
    @Override
    public BigDecimal resubmitAmount(ApprovalRequest request, JsonNode payload) {
        ProductionRunRequest parsed;
        try {
            parsed = parser.parseProductionRun(payload);
        } catch (ErpException e) {
            throw ErpException.business("Data produksi tidak valid: " + e.getMessage());
        }
        BigDecimal amount = BigDecimal.ZERO;
        for (ProductionRequests.InputLine in : parsed.inputs()) {
            amount = amount.add(Money.nz(in.quantity()));
        }
        return amount;
    }
}
