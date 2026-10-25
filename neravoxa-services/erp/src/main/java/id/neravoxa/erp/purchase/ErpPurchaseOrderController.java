package id.neravoxa.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.purchase.PurchaseRequests.ClosePurchaseOrderRequest;
import id.neravoxa.erp.purchase.PurchaseRequests.CreatePurchaseOrderRequest;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Purchase Order — port {@code routes/purchaseOrders.ts} (mount {@code /api/purchase-orders}).
 * Gerbang modul {@code purchase_order} dijalankan ErpRouteGuard.
 */
@Slf4j
@RestController
@RequestMapping("/api/erp/purchase-orders")
@RequiredArgsConstructor
public class ErpPurchaseOrderController {

    private final ErpAccess access;
    private final ErpPurchaseBodyParser parser;
    private final ErpPurchaseOrderService orders;
    private final ErpPurchaseOrderQueryService queries;

    /**
     * Query: page, limit (≤200), status, partyId, startDate, endDate, search, receipt
     * (unreceived|partial|full|closed), open=1, due=overdue, sort.
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return queries.list(query);
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        return queries.detail(id);
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode body) {
        CreatePurchaseOrderRequest req = parser.parse(body, CreatePurchaseOrderRequest.class);
        String userId = access.userId();
        Object result = orders.create(req, userId);
        log.info("PO submitted via API userId={}", userId);
        return ResponseEntity.status(201).body(result);
    }

    /** "Ajukan" draf: aturan approval cocok → tetap Draf + Pending; selain itu → Submitted. */
    @PostMapping("/{id}/submit")
    public Object submit(@PathVariable String id) {
        return orders.submitDraft(id, access.userId());
    }

    /** Hapus draf. */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.putAll(orders.deleteDraft(id));
        return out;
    }

    @PostMapping("/{id}/cancel")
    public Object cancel(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return orders.cancel(id);
    }

    /** Edit — hanya selama PO terbuka dan belum ada penerimaan. */
    @PutMapping("/{id}")
    public Object update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        CreatePurchaseOrderRequest req = parser.parse(body, CreatePurchaseOrderRequest.class);
        return orders.update(id, req);
    }

    /** Short-close: status tetap Submitted, closedAt menandai sisa qty tidak lagi diharapkan. */
    @PostMapping("/{id}/close")
    public Object close(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        access.requireCap("doc.cancel");
        ClosePurchaseOrderRequest req = parser.parse(body, ClosePurchaseOrderRequest.class);
        // ClosePurchaseOrderSchema: z.string().trim().min(5, ...).max(500)
        String reason = req.reason().trim();
        if (reason.length() < PurchaseOrderRules.CLOSE_REASON_MIN) {
            throw ErpException.business("Alasan penutupan minimal 5 karakter.");
        }
        if (reason.length() > PurchaseOrderRules.CLOSE_REASON_MAX) {
            throw ErpException.business("Too big: expected string to have <=500 characters");
        }
        return orders.close(id, reason);
    }
}
