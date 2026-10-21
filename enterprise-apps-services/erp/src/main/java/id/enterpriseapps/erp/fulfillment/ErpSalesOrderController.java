package id.enterpriseapps.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.security.ErpAccess;
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

import java.util.Map;

/**
 * Pesanan Penjualan — port {@code routes/salesOrders.ts} (mount {@code /api/sales-orders}).
 * Gerbang modul {@code sales_order} dijalankan ErpRouteGuard; bypass stok minus = capability
 * {@code inventory.negative_stock}.
 */
@RestController
@RequestMapping("/api/erp/sales-orders")
@RequiredArgsConstructor
public class ErpSalesOrderController {

    private final ErpAccess access;
    private final ErpSalesOrderService orders;
    private final ErpTemporaryDeliveryNoteSchema temporaryNoteSchema;

    /** Query: page, limit (≤200), status, partyId, startDate, endDate, search, sort → { data, total, page, limit }. */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return orders.list(query);
    }

    /** KPI untuk filter yang sama dengan daftar. */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return orders.summary(query);
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        return orders.detail(id);
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode body) {
        return ResponseEntity.status(201).body(orders.create(body, access.userId()));
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return orders.update(id, body);
    }

    @PostMapping("/{id}/submit")
    public Object submit(@PathVariable String id) {
        return orders.submit(id);
    }

    @PostMapping("/{id}/cancel")
    public Object cancel(@PathVariable String id) {
        return orders.cancel(id);
    }

    /** Buat Surat Jalan dari SO (201). */
    @PostMapping("/{id}/generate-do")
    public ResponseEntity<Object> generateDeliveryOrder(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        temporaryNoteSchema.ensure();
        return ResponseEntity.status(201).body(orders.generateDeliveryOrder(id, body, access.userId(),
                access.hasCap("inventory.negative_stock")));
    }
}
