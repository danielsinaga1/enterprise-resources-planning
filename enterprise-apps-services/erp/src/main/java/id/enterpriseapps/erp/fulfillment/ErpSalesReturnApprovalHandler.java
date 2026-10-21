package id.enterpriseapps.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.approval.ErpApprovalHandler;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import id.enterpriseapps.erp.entity.SalesReturn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Eksekusi antrian "SalesReturn" saat disetujui — cabang SalesReturn di {@code approvalDecision.ts}:
 * payload tersimpan divalidasi ulang ({@code CreateSalesReturnSchema}) lalu disubmit atas nama PENGAJU;
 * efek GL/stok baru terjadi sekarang dan nilainya dihitung ulang dari invoice.
 */
@Component
@RequiredArgsConstructor
public class ErpSalesReturnApprovalHandler implements ErpApprovalHandler {

    private final ErpSalesReturnSubmitService submitter;

    @Override
    public String documentType() {
        return ErpSalesReturnService.DOCUMENT_TYPE;
    }

    @Override
    @ErpTx
    public ApprovedDocument approve(ApprovalRequest request, JsonNode body, JsonNode before, String approverUserId,
                                    String note) {
        FulfillmentRequests.ReturnInput parsed;
        try {
            parsed = FulfillmentRequests.salesReturn(body != null ? body : request.getPayload());
        } catch (ErpException e) {
            throw ErpException.business("Payload retur tidak valid: " + e.getMessage());
        }
        SalesReturn ret = submitter.submitInTx(parsed, request.getRequestedBy());
        return new ApprovedDocument(ret.getId(), ret.getReturnNumber());
    }
}
