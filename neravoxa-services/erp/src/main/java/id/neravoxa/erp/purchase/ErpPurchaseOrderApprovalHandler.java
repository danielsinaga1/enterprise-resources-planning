package id.neravoxa.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.approval.ErpApprovalHandler;
import id.neravoxa.erp.entity.ApprovalRequest;
import id.neravoxa.erp.entity.PurchaseOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keputusan antrian "PurchaseOrder" (berbasis status, documentId = PO, payload null):
 * approve → PO Draf menjadi Submitted; tolak / minta revisi (dengan catatan) → PO kembali Draf
 * (approvalStatus Rejected) sehingga bisa diedit & diajukan ulang.
 */
@Component
@RequiredArgsConstructor
public class ErpPurchaseOrderApprovalHandler implements ErpApprovalHandler {

    private final ErpPurchaseOrderService orders;

    @Override
    public String documentType() {
        return ErpPurchaseOrderService.DOCUMENT_TYPE;
    }

    @Override
    @ErpTx
    public ApprovedDocument approve(ApprovalRequest request, JsonNode body, JsonNode before, String approverUserId,
                                    String note) {
        if (request.getDocumentId() == null) {
            // Antrian PO tanpa dokumen target: tidak ada yang dieksekusi (seperti sumber).
            return null;
        }
        PurchaseOrder po = orders.applyApprovalDecision(request.getDocumentId(), "approve", trim(note));
        return new ApprovedDocument(po.getId(), po.getPoNumber());
    }

    @Override
    @ErpTx
    public void onDecline(ApprovalRequest request, String action, String approverUserId, String note) {
        String n = trim(note);
        // Sumber: hanya tolak, atau minta-revisi yang disertai catatan.
        if (request.getDocumentId() != null && ("reject".equals(action) || !n.isEmpty())) {
            orders.applyApprovalDecision(request.getDocumentId(), action, n);
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
