package id.neravoxa.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.security.ErpAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Approval — port {@code routes/approvals.ts}. Gerbang modul {@code approvals} (view untuk GET, edit untuk
 * tulis) dijalankan ErpRouteGuard; di sini hanya {@code requireCap('approval.decide')} per rute.
 * Tipe {@code ai_tool:*} (permintaan agen MCP) tidak dipindahkan.
 */
@Slf4j
@RestController
@RequestMapping("/api/erp/approvals")
@RequiredArgsConstructor
public class ErpApprovalController {

    private static final String DECIDE = "approval.decide";

    private final ErpAccess access;
    private final ErpApprovalRuleService rules;
    private final ErpApprovalQueryService queries;
    private final ErpApprovalDecisionService decisions;

    // ─────────────── Aturan approval (semua butuh approval.decide) ───────────────

    @GetMapping("/rules")
    public Object listRules() {
        access.requireCap(DECIDE);
        return rules.list();
    }

    @PostMapping("/rules")
    public ResponseEntity<Object> createRule(@RequestBody(required = false) JsonNode body) {
        access.requireCap(DECIDE);
        return ResponseEntity.status(201).body(rules.create(body));
    }

    @PutMapping("/rules/{id}")
    public Object updateRule(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        access.requireCap(DECIDE);
        return rules.update(id, body);
    }

    @DeleteMapping("/rules/{id}")
    public Object deleteRule(@PathVariable String id) {
        access.requireCap(DECIDE);
        rules.delete(id);
        return Map.of("success", true);
    }

    // ─────────────── Pengajuan ───────────────

    @GetMapping("/requests")
    public Object listRequests(@RequestParam(required = false) String status) {
        return queries.list(status);
    }

    @GetMapping("/requests/{id}")
    public Object getRequest(@PathVariable String id) {
        return queries.detail(id);
    }

    @GetMapping("/requests/{id}/context")
    public Object getContext(@PathVariable String id) {
        return queries.context(id);
    }

    @GetMapping("/pending")
    public Object pending(@RequestParam(required = false) String documentType,
                          @RequestParam(required = false) String documentId) {
        return queries.pending(documentType, documentId);
    }

    @PostMapping("/requests")
    public ResponseEntity<Object> submitRequest(@RequestBody(required = false) JsonNode body) {
        Object[] result = rules.submitRequest(body, access.userId());
        return ResponseEntity.status((Integer) result[0]).body(result[1]);
    }

    /** Eksekusi dokumen dari payload (atas nama pengaju), lalu tandai Approved. */
    @PostMapping("/requests/{id}/approve")
    public Object approve(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        access.requireCap(DECIDE);
        return decide(id, ErpApprovalDecisionService.APPROVE, body);
    }

    @PostMapping("/requests/{id}/reject")
    public Object reject(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        access.requireCap(DECIDE);
        return decide(id, ErpApprovalDecisionService.REJECT, body);
    }

    /** Minta pengaju memperbaiki pengajuan (NeedsRevision, wajib alasan). Dokumen TIDAK dibuat. */
    @PostMapping("/requests/{id}/request-revision")
    public Object requestRevision(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        access.requireCap(DECIDE);
        return decide(id, ErpApprovalDecisionService.REVISE, body);
    }

    @PutMapping("/requests/{id}/resubmit")
    public Object resubmit(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return decisions.resubmit(id, body);
    }

    @GetMapping("/check")
    public Object check(@RequestParam(required = false) String documentType,
                        @RequestParam(required = false) String amount) {
        return rules.check(documentType, amount);
    }

    /**
     * {@code decideApproval} tidak pernah melempar: kegagalan apa pun (termasuk dari handler domain dan
     * commit) menjadi {@code 400 { error }}. Transaksi keputusan sudah di-rollback saat sampai di sini.
     */
    private Object decide(String id, String action, JsonNode body) {
        JsonNode notes = body == null ? null : body.get("notes");
        String note = notes == null || notes.isNull() || notes.isContainerNode() ? null : notes.asText();
        String userId = access.userId();
        try {
            return decisions.decide(id, action, userId, note);
        } catch (ErpException e) {
            throw ErpException.business(e.getMessage());
        } catch (RuntimeException e) {
            log.error("decideApproval error requestId={} action={}", id, action, e);
            throw ErpException.business("Gagal memproses keputusan approval.");
        }
    }
}
