package id.enterpriseapps.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
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

import java.util.List;
import java.util.Map;

/**
 * Surat Jalan Sementara (SJS) — port {@code routes/temporaryDeliveryNotes.ts}
 * (mount {@code /api/temporary-delivery-notes}). Gerbang modul {@code delivery_order} dijalankan
 * ErpRouteGuard; batal = capability {@code doc.cancel}.
 */
@RestController
@RequestMapping("/api/erp/temporary-delivery-notes")
@RequiredArgsConstructor
public class ErpTemporaryDeliveryNoteController {

    private final ErpAccess access;
    private final ErpTemporaryDeliveryNoteService notes;
    private final ErpTemporaryDeliveryNoteSchema schema;

    /** Query: page, limit (≤200), status, partyId, startDate/endDate (dateFrom/dateTo), search, sort. */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        schema.ensure();
        return notes.list(query);
    }

    /** SJS Terbuka milik pelanggan (untuk form DO). */
    @GetMapping("/linkable")
    public List<Map<String, Object>> linkable(@RequestParam Map<String, String> query) {
        schema.ensure();
        return notes.linkable(query.get("partyId"));
    }

    /** Jumlah Terbuka + yang melewati batas umur (default 7 hari). */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        schema.ensure();
        return notes.summary(query.get("days"));
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        schema.ensure();
        Map<String, Object> note = notes.get(id);
        if (note == null) {
            throw ErpException.notFound("Surat jalan sementara tidak ditemukan.");
        }
        return note;
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode body) {
        FulfillmentRequests.TdnInput input = FulfillmentRequests.temporaryNote(body);
        schema.ensure();
        return ResponseEntity.status(201).body(notes.create(input, access.userId()));
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        FulfillmentRequests.TdnInput input = FulfillmentRequests.temporaryNote(body);
        schema.ensure();
        return notes.update(id, input);
    }

    /** Tautkan SJS Terbuka ke DO resmi yang sudah ada. */
    @PostMapping("/{id}/link")
    public Object link(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        String deliveryOrderId = FulfillmentRequests.linkBody(body);
        schema.ensure();
        return notes.linkToExistingDeliveryOrder(id, deliveryOrderId);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        access.requireCap("doc.cancel");
        schema.ensure();
        return notes.cancel(id);
    }
}
