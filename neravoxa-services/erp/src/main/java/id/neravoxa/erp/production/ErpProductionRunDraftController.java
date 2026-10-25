package id.neravoxa.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.approval.ErpApprovalGateService;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.core.security.ErpActor;
import id.neravoxa.erp.production.ErpProductionRunDraftService.DraftRef;
import id.neravoxa.erp.production.ProductionRequests.ProductionRunRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draft Data Produksi — port {@code routes/inventory/productionRunDrafts.ts} (modul 'production',
 * gerbang {@code requirePerm} dijalankan ErpRouteGuard). Draft tidak berefek ke stok/GL/HPP/OEE/laporan;
 * posting = POST /:id/post. Edit/hapus/posting hanya pembuat draft atau pemegang {@code approval.bypass}.
 */
@RestController
@RequestMapping("/api/erp/inventory/production-runs/drafts")
@RequiredArgsConstructor
public class ErpProductionRunDraftController {

    private final ErpProductionRunDraftService drafts;
    private final ErpProductionBodyParser parser;
    private final ErpApprovalGateService approvalGate;
    private final ErpAccess access;

    /** Pembuat draft atau pemegang approval.bypass (Admin). */
    private boolean canManage(String createdById) {
        return createdById.equals(access.userId()) || access.hasCap("approval.bypass");
    }

    /** Query: page, limit, status (Draft|PendingApproval|Posted), search, startDate, endDate. */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return drafts.list(query);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return drafts.get(id);
    }

    /** Ubah draft (hanya selagi Draft). */
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody(required = false) JsonNode raw) {
        ProductionRunRequest body = parser.parseProductionRun(raw);
        if (body.negativeStock() && !access.hasCap("inventory.negative_stock")) {
            throw ErpException.forbidden(ErpProductionRunController.NEG_STOCK_FORBIDDEN);
        }
        DraftRef existing = drafts.ref(id);
        if (existing == null) {
            throw ErpException.notFound(ErpProductionRunDraftService.NOT_FOUND);
        }
        if (!canManage(existing.createdById())) {
            throw ErpException.forbidden("Hanya pembuat draft yang boleh mengubahnya.");
        }
        return drafts.updateDraft(id, body, raw, access.userId());
    }

    /** Buang draft (soft: Discarded; hanya selagi Draft). */
    @DeleteMapping("/{id}")
    public Map<String, Object> discard(@PathVariable String id) {
        DraftRef existing = drafts.ref(id);
        if (existing == null) {
            throw ErpException.notFound(ErpProductionRunDraftService.NOT_FOUND);
        }
        if (!canManage(existing.createdById())) {
            throw ErpException.forbidden("Hanya pembuat draft yang boleh menghapusnya.");
        }
        drafts.discardDraft(id, access.userId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Draft produksi dihapus.");
        return out;
    }

    /**
     * Posting draft. Bypass: langsung jadi Data Produksi (201). Non-bypass: antrian approval (202), efek
     * terjadi setelah disetujui. Posting gagal karena aturan bisnis (mis. stok kurang): draft tetap Draft
     * dan alasannya disimpan di {@code lastError}.
     */
    @PostMapping("/{id}/post")
    public ResponseEntity<Object> post(@PathVariable String id) {
        DraftRef existing = drafts.ref(id);
        if (existing == null) {
            throw ErpException.notFound(ErpProductionRunDraftService.NOT_FOUND);
        }
        if (!canManage(existing.createdById())) {
            throw ErpException.forbidden("Hanya pembuat draft yang boleh memostingnya.");
        }
        JsonNode payload = existing.payload();
        boolean negStock = payload != null && payload.isObject() && ProdJson.truthy(payload.get("allowNegativeStock"));
        if (negStock && !access.hasCap("inventory.negative_stock")) {
            throw ErpException.forbidden(ErpProductionRunController.NEG_STOCK_FORBIDDEN);
        }
        ErpActor actor = access.actor();
        try {
            if (approvalGate.needsApproval(actor)) {
                return ResponseEntity.status(202).body(drafts.postDraftForApproval(id, actor));
            }
            return ResponseEntity.status(201).body(drafts.postDraftDirect(id, actor.userId()));
        } catch (ErpException e) {
            if (e.getStatus() == HttpStatus.BAD_REQUEST) {
                try {
                    drafts.recordDraftError(id, e.getMessage());
                } catch (RuntimeException ignored) {
                    // best-effort, sama seperti .catch(() => undefined) di sumber
                }
            }
            throw e;
        }
    }
}
