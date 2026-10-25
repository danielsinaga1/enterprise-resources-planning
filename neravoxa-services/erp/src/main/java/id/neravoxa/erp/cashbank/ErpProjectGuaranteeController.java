package id.neravoxa.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.security.ErpAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Uang Jaminan Proyek — port {@code routes/projectGuarantees.ts} (mount {@code /api/project-guarantees},
 * gerbang modul {@code project_guarantee} dijalankan ErpRouteGuard).
 */
@RestController
@RequestMapping("/api/erp/project-guarantees")
@RequiredArgsConstructor
public class ErpProjectGuaranteeController {

    private final ErpAccess access;
    private final ErpProjectGuaranteeQueryService queries;
    private final ErpProjectGuaranteeService guarantees;

    /**
     * Tanpa {@code page}: semua jaminan (filter partyId, status, guaranteeType) → { data, summary }.
     * Dengan {@code page}: page, limit (≤200), status, due (overdue | dueSoon), partyId, guaranteeType,
     * startDate/endDate, search, sort (mis. "-outstanding") → { data, total, page, limit } + dueState per baris.
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return queries.list(query);
    }

    /** KPI untuk filter yang sama dengan daftar berhalaman. */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return queries.summary(query);
    }

    @GetMapping("/{id}")
    public ObjectNode detail(@PathVariable String id) {
        return queries.detail(id);
    }

    @PostMapping
    public ResponseEntity<ObjectNode> create(@RequestBody(required = false) JsonNode body) {
        CashBankRequests.CreateGuarantee req = CashBankRequests.CreateGuarantee.parse(body);
        return ResponseEntity.status(201).body(guarantees.create(req, access.userId()));
    }

    @PostMapping("/{id}/settle")
    public ResponseEntity<Map<String, Object>> settle(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        CashBankRequests.SettleGuarantee req = CashBankRequests.SettleGuarantee.parse(body);
        return ResponseEntity.status(201).body(guarantees.settle(id, req, access.userId()));
    }

    /** Batalkan setoran yang belum punya settlement aktif (hak {@code doc.cancel}). */
    @PatchMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return guarantees.cancel(id);
    }

    /** Batalkan satu settlement (hak {@code doc.cancel}). */
    @PostMapping("/settlements/{id}/cancel")
    public Map<String, Object> cancelSettlement(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return guarantees.cancelSettlement(id);
    }
}
