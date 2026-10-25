package id.neravoxa.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.approval.ErpApprovalGateService;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.core.security.ErpActor;
import id.neravoxa.erp.production.ProductionRequests.ProductionRunRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
 * Data Produksi — port {@code routes/inventory/productionRuns.ts}. Gerbang {@code requirePerm('production')}
 * dijalankan ErpRouteGuard. POST selalu menyimpan DRAFT (posting lewat /drafts/:id/post); PUT /:id = edit
 * (diantrekan untuk role tanpa approval.bypass); PUT /:id/cancel butuh {@code doc.cancel}.
 */
@RestController
@RequestMapping("/api/erp/inventory/production-runs")
@RequiredArgsConstructor
public class ErpProductionRunController {

    static final String NEG_STOCK_FORBIDDEN = "Anda tidak berwenang bypass validasi stok.";

    private final ErpProductionRunQueryService queries;
    private final ErpProductionRunDraftService drafts;
    private final ErpProductionRunEditService editor;
    private final ErpProductionRunCancelService canceller;
    private final ErpProductionBodyParser parser;
    private final ErpApprovalGateService approvalGate;
    private final ErpAccess access;

    /** Query: page, limit (default 50, ≤ 200), status, machineId, startDate, endDate, search, sort. */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return queries.list(query);
    }

    /** KPI untuk filter daftar (dideklarasikan sebelum /:id). */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return queries.summary(query);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        return queries.detail(id);
    }

    /** Simpan draft (tanpa efek stok/GL/HPP/OEE) → 201 {@code { draft, warnings }}. */
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode raw) {
        ProductionRunRequest body = parser.parseProductionRun(raw);
        if (body.negativeStock() && !access.hasCap("inventory.negative_stock")) {
            throw ErpException.forbidden(NEG_STOCK_FORBIDDEN);
        }
        return ResponseEntity.status(201).body(drafts.createDraft(body, raw, access.userId()));
    }

    /**
     * Edit: batal + terapkan ulang dengan nomor run yang sama, jurnal revisi JV-{runNo}-R{n}. Edit mengubah
     * stok/GL/WAC sehingga role non-bypass hanya mengantrekan (202).
     */
    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable String id, @RequestBody(required = false) JsonNode raw) {
        ProductionRunRequest body = parser.parseProductionRun(raw);
        if (body.negativeStock() && !access.hasCap("inventory.negative_stock")) {
            throw ErpException.forbidden(NEG_STOCK_FORBIDDEN);
        }
        ErpActor actor = access.actor();
        if (approvalGate.needsApproval(actor)) {
            return ResponseEntity.status(202).body(editor.queueForApproval(id, body, raw, actor));
        }
        return ResponseEntity.ok(editor.editAndLoad(id, body, actor.userId()));
    }

    /** Batal. Bypass stok minus via {@code ?allowNegativeStock=true} atau body, wajib alasan ≥ 10 karakter. */
    @PutMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id,
                                      @RequestParam(required = false) String allowNegativeStock,
                                      @RequestBody(required = false) JsonNode body) {
        access.requireCap("doc.cancel");
        boolean allowNeg = "true".equals(allowNegativeStock)
                || (body != null && body.path("allowNegativeStock").isBoolean() && body.get("allowNegativeStock").booleanValue());
        String reason = body != null && body.path("negativeStockReason").isTextual() ? body.get("negativeStockReason").asText() : null;
        if (allowNeg && (reason == null || reason.trim().length() < 10)) {
            throw ErpException.business("Alasan bypass stok minus wajib diisi (minimal 10 karakter).");
        }
        canceller.cancel(id, allowNeg, reason);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Proses produksi dibatalkan.");
        return out;
    }
}
