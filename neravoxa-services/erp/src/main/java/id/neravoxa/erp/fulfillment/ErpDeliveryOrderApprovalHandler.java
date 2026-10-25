package id.neravoxa.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.approval.ErpApprovalHandler;
import id.neravoxa.erp.entity.ApprovalRequest;
import id.neravoxa.erp.entity.DeliveryOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Eksekusi antrian "DeliveryOrder" saat disetujui — cabang DeliveryOrder di {@code approvalDecision.ts}.
 * Surat Jalan masuk antrian karena stok minus tanpa capability; disetujui = payload tersimpan divalidasi
 * ulang ({@code CreateDeliveryOrderSchema}) lalu disubmit atas nama PENGAJU DENGAN bypass stok minus.
 */
@Component
@RequiredArgsConstructor
public class ErpDeliveryOrderApprovalHandler implements ErpApprovalHandler {

    private final ErpDeliveryOrderSubmitService submitter;
    private final ErpTemporaryDeliveryNoteSchema temporaryNoteSchema;

    @Override
    public String documentType() {
        return ErpDeliveryOrderService.DOCUMENT_TYPE;
    }

    @Override
    @ErpTx
    public ApprovedDocument approve(ApprovalRequest request, JsonNode body, JsonNode before, String approverUserId,
                                    String note) {
        FulfillmentRequests.DoInput parsed;
        try {
            parsed = FulfillmentRequests.deliveryOrder(body != null ? body : request.getPayload());
        } catch (ErpException e) {
            throw ErpException.business("Payload surat jalan tidak valid: " + e.getMessage());
        }
        if (parsed.temporaryNoteIds() != null && !parsed.temporaryNoteIds().isEmpty()) {
            // Tabel SJS biasanya sudah ada (dibuat saat boot); di sini hanya cek information_schema.
            temporaryNoteSchema.ensure();
        }
        DeliveryOrder d = submitter.submitInTx(parsed.withAllowNegativeStock(true), request.getRequestedBy()).deliveryOrder();
        return new ApprovedDocument(d.getId(), d.getDoNumber());
    }
}
