package id.neravoxa.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.security.ErpAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Retur Penjualan — port {@code routes/salesReturns.ts} (mount {@code /api/sales-returns}).
 * Gerbang modul {@code sales_return} dijalankan ErpRouteGuard; batal = capability {@code doc.cancel};
 * role tanpa {@code approval.bypass} → antrian approval.
 */
@RestController
@RequestMapping("/api/erp/sales-returns")
@RequiredArgsConstructor
public class ErpSalesReturnController {

    private final ErpAccess access;
    private final ErpSalesReturnService returns;

    /** Query: page, limit, status, partyId, salesInvoiceId, startDate/endDate, search, sort. */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return returns.list(query);
    }

    /** Baris invoice + sisa qty yang bisa diretur. */
    @GetMapping("/eligible/{invoiceId}")
    public Map<String, Object> eligible(@PathVariable String invoiceId) {
        return returns.eligible(invoiceId);
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        return returns.detail(id);
    }

    /** 201 retur dibuat; 202 bila masuk antrian approval. */
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode body) {
        ErpSalesReturnService.Outcome r = returns.create(body, access.actor(), access.needsApproval());
        return ResponseEntity.status(r.pending() ? 202 : 201).body(r.body());
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return returns.cancel(id);
    }
}
