package id.enterpriseapps.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.approval.ErpApprovalGateService;
import id.enterpriseapps.erp.core.approval.ErpApprovalHandler;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import id.enterpriseapps.erp.production.ErpProductionRunPostingSupport.PostedRun;
import id.enterpriseapps.erp.production.ProductionRequests.ProductionRunRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Eksekusi antrian "ProductionRunEdit" saat disetujui — cabang ProductionRunEdit di
 * {@code approvalDecision.ts}: body asli (tanpa snapshot {@code _before}) divalidasi ulang
 * (UpdateProductionRunSchema) lalu edit dijalankan atas nama PENGAJU pada run target
 * ({@code documentId}): balik gerakan & jurnal lama, terapkan ulang, jurnal revisi JV-…-R{n}.
 */
@Component
@RequiredArgsConstructor
public class ErpProductionRunEditApprovalHandler implements ErpApprovalHandler {

    private final ErpProductionBodyParser parser;
    private final ErpProductionRunEditService editor;

    @Override
    public String documentType() {
        return ErpProductionRunEditService.DOCUMENT_TYPE;
    }

    @Override
    @ErpTx(timeout = 30)
    public ApprovedDocument approve(ApprovalRequest request, JsonNode body, JsonNode before, String approverUserId,
                                    String note) {
        String targetId = request.getDocumentId();
        if (targetId == null) {
            throw ErpException.business("Pengajuan tidak memiliki dokumen target.");
        }
        // Buang _before bila pemanggil mengirim payload utuh.
        JsonNode clean = ErpApprovalGateService.splitStoredPayload(body != null ? body : request.getPayload())[0];
        ProductionRunRequest parsed;
        try {
            parsed = parser.parseProductionRun(clean);
        } catch (ErpException e) {
            throw ErpException.business("Payload edit produksi tidak valid: " + e.getMessage());
        }
        PostedRun run = editor.edit(targetId, parsed, request.getRequestedBy());
        return new ApprovedDocument(run.id(), run.runNumber());
    }
}
