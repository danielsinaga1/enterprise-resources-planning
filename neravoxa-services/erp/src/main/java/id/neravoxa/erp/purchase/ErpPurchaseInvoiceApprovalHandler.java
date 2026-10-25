package id.neravoxa.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.approval.ErpApprovalHandler;
import id.neravoxa.erp.entity.ApprovalRequest;
import id.neravoxa.erp.entity.PurchaseInvoice;
import id.neravoxa.erp.purchase.PurchaseRequests.CreatePurchaseInvoiceRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Eksekusi antrian "PurchaseInvoice" saat disetujui — cabang PurchaseInvoice di
 * {@code approvalDecision.ts}: payload tersimpan divalidasi ulang (CreatePurchaseInvoiceSchema) lalu
 * disubmit atas nama PENGAJU; efek GL/stok/WAC baru terjadi sekarang. Lampiran yang diunggah saat
 * pengajuan dipindah ke invoice yang baru dibuat.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpPurchaseInvoiceApprovalHandler implements ErpApprovalHandler {

    private final ErpPurchaseBodyParser parser;
    private final ErpPurchaseInvoiceSubmitService submitter;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @Override
    public String documentType() {
        return ErpPurchaseInvoiceSubmitService.DOCUMENT_TYPE;
    }

    @Override
    @ErpTx
    public ApprovedDocument approve(ApprovalRequest request, JsonNode body, JsonNode before, String approverUserId,
                                    String note) {
        CreatePurchaseInvoiceRequest parsed;
        try {
            parsed = parser.parse(body != null ? body : request.getPayload(), CreatePurchaseInvoiceRequest.class);
        } catch (ErpException e) {
            throw ErpException.business("Payload invoice tidak valid: " + e.getMessage());
        }
        PurchaseInvoice inv = submitter.submitInTx(parsed, request.getRequestedBy()).invoice();
        // Lampiran pengajuan → dokumen yang baru dibuat (idempoten).
        int moved = em.createQuery("update TransactionAttachment a set a.referenceType = 'purchase_invoice', "
                        + "a.referenceId = :doc where a.referenceType = 'approval_request' and a.referenceId = :req")
                .setParameter("doc", inv.getId()).setParameter("req", request.getId()).executeUpdate();
        if (moved > 0) {
            log.info("Approval attachments moved to document requestId={} docId={} n={}", request.getId(), inv.getId(), moved);
        }
        return new ApprovedDocument(inv.getId(), inv.getInvoiceNumber());
    }

    /**
     * Pengajuan ulang: payload divalidasi CreatePurchaseInvoiceSchema. Amount = grand total dengan rumus
     * yang sama seperti saat antri ({@code queueForApproval}); rute resubmit ENTARIS menjumlah
     * {@code it.amount} yang tidak ada di payload sehingga nilainya jadi Rp 0 (+ biaya lain).
     */
    @Override
    public BigDecimal resubmitAmount(ApprovalRequest request, JsonNode payload) {
        CreatePurchaseInvoiceRequest parsed;
        try {
            parsed = parser.parse(payload, CreatePurchaseInvoiceRequest.class);
        } catch (ErpException e) {
            throw ErpException.business("Data invoice tidak valid: " + e.getMessage());
        }
        BigDecimal amount = BigDecimal.ZERO;
        for (PurchaseRequests.PurchaseInvoiceItemInput it : parsed.items()) {
            amount = amount.add(PurchaseCalc.compute(it).hargaAkhir());
        }
        return amount.subtract(Money.nz(parsed.potongan())).add(Money.nz(parsed.biayaLain()));
    }
}
