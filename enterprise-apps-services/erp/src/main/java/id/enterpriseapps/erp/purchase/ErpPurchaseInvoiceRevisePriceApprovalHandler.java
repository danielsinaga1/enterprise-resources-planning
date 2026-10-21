package id.enterpriseapps.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.approval.ErpApprovalGateService;
import id.enterpriseapps.erp.core.approval.ErpApprovalHandler;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import id.enterpriseapps.erp.entity.PurchaseInvoice;
import id.enterpriseapps.erp.purchase.PurchaseRequests.RevisePurchaseInvoiceRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Eksekusi antrian "PurchaseInvoiceRevisePrice" saat disetujui: body asli (tanpa {@code _before})
 * divalidasi ulang (RevisePurchaseInvoiceSchema) lalu revisi dijalankan atas nama PENGAJU pada
 * invoice target ({@code documentId}).
 */
@Component
@RequiredArgsConstructor
public class ErpPurchaseInvoiceRevisePriceApprovalHandler implements ErpApprovalHandler {

    private final ErpPurchaseBodyParser parser;
    private final ErpPurchaseInvoiceReviseService reviser;

    @Override
    public String documentType() {
        return ErpPurchaseInvoiceReviseService.DOCUMENT_TYPE;
    }

    @Override
    @ErpTx
    public ApprovedDocument approve(ApprovalRequest request, JsonNode body, JsonNode before, String approverUserId,
                                    String note) {
        String targetId = request.getDocumentId();
        if (targetId == null) {
            throw ErpException.business("Pengajuan tidak memiliki dokumen target.");
        }
        // Buang _before bila pemanggil mengirim payload utuh.
        JsonNode clean = ErpApprovalGateService.splitStoredPayload(body != null ? body : request.getPayload())[0];
        RevisePurchaseInvoiceRequest parsed;
        try {
            parsed = parser.parse(clean, RevisePurchaseInvoiceRequest.class);
        } catch (ErpException e) {
            throw ErpException.business("Payload revisi harga tidak valid: " + e.getMessage());
        }
        PurchaseInvoice inv = reviser.revise(targetId, parsed.lines(), parsed.reason(), request.getRequestedBy());
        return new ApprovedDocument(inv.getId(), inv.getInvoiceNumber());
    }
}
