package id.neravoxa.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.security.ErpAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Surat Jalan (DO) — port {@code routes/deliveryOrders.ts} (mount {@code /api/delivery-orders}).
 * Gerbang modul {@code delivery_order} dijalankan ErpRouteGuard; batal = capability {@code doc.cancel};
 * bypass stok minus = capability {@code inventory.negative_stock}.
 */
@RestController
@RequestMapping("/api/erp/delivery-orders")
@RequiredArgsConstructor
public class ErpDeliveryOrderController {

    private static final String NEGATIVE_STOCK_CAP = "inventory.negative_stock";

    private final ErpAccess access;
    private final ErpDeliveryOrderService deliveryOrders;
    private final ErpTemporaryDeliveryNoteSchema temporaryNoteSchema;

    /** Query: page, limit (≤200), status, partyId, startDate, endDate, search, sort → { data, total, page, limit }. */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return deliveryOrders.list(query);
    }

    /** DO yang masih perlu ditagih (modal invoice). */
    @GetMapping("/pending")
    public List<JsonNode> pending(@RequestParam Map<String, String> query) {
        return deliveryOrders.pending(query.get("partyId"));
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        return deliveryOrders.detail(id);
    }

    /** 201 DO dibuat; 202 bila masuk antrian approval (stok minus tanpa capability). */
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode body) {
        temporaryNoteSchema.ensure();
        ErpDeliveryOrderService.Outcome r = deliveryOrders.create(body, access.actor(), access.hasCap(NEGATIVE_STOCK_CAP));
        return ResponseEntity.status(r.pending() ? 202 : 201).body(r.body());
    }

    /** DO retroaktif untuk invoice penjualan langsung (tanpa gerakan stok). */
    @PostMapping("/from-invoice/{invoiceId}")
    public ResponseEntity<Object> fromInvoice(@PathVariable String invoiceId, @RequestBody(required = false) JsonNode body) {
        return ResponseEntity.status(201).body(deliveryOrders.createFromInvoice(invoiceId, body, access.userId()));
    }

    /** Edit header saja (notes, nomorMobil, driverName, recipientName). */
    @PatchMapping("/{id}")
    public Object patch(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return deliveryOrders.patchHeader(id, body);
    }

    /** Edit penuh: balik gerakan lama, terapkan baris baru (nomor DO tetap). */
    @PutMapping("/{id}")
    public Object edit(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return deliveryOrders.edit(id, body, access.userId(), access.hasCap(NEGATIVE_STOCK_CAP));
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        access.requireCap("doc.cancel");
        temporaryNoteSchema.ensure();
        return deliveryOrders.cancel(id, access.userId());
    }
}
