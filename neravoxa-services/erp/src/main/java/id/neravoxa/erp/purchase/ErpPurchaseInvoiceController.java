package id.neravoxa.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.security.ErpAccess;
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
 * Invoice pembelian — port {@code routes/purchase.ts} (mount {@code /api/purchase/invoices}).
 * Gerbang modul {@code purchase_invoice} dijalankan ErpRouteGuard.
 */
@RestController
@RequestMapping("/api/erp/purchase/invoices")
@RequiredArgsConstructor
public class ErpPurchaseInvoiceController {

    private final ErpAccess access;
    private final ErpPurchaseInvoiceService invoices;
    private final ErpPurchaseInvoiceQueryService queries;
    private final ErpPurchaseInvoiceCancelService canceller;

    /** Query: page, limit (≤500), status, partyId, startDate, endDate, search, sort → { data, total, page, limit }. */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return queries.list(query);
    }

    /** KPI untuk filter yang sama dengan daftar. */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return queries.summary(query);
    }

    /** Three-way match (PO ↔ STT ↔ faktur) untuk payload belum disimpan; hanya saran. */
    @PostMapping("/match-preview")
    public Map<String, Object> matchPreview(@RequestBody(required = false) JsonNode body) {
        return invoices.matchPreview(body);
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        return invoices.detail(id);
    }

    /** 201 dokumen dibuat; 202 bila masuk antrian approval. */
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode body) {
        ErpPurchaseInvoiceService.Outcome r = invoices.create(body, access.actor());
        return ResponseEntity.status(r.pending() ? 202 : 201).body(r.body());
    }

    /** Edit notes, dueDate, terms. */
    @PutMapping("/{id}")
    public Object update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return invoices.updateMeta(id, body);
    }

    /** Revisi harga (supplier koreksi); 202 bila masuk antrian approval. */
    @PostMapping("/{id}/revise-price")
    public ResponseEntity<Object> revisePrice(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        ErpPurchaseInvoiceService.Outcome r = invoices.revisePrice(id, body, access.actor());
        return ResponseEntity.status(r.pending() ? 202 : 200).body(r.body());
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return canceller.cancel(id);
    }
}
