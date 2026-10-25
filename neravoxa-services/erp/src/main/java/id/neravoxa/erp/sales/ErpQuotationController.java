package id.neravoxa.erp.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.security.ErpAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Penawaran harga — port {@code routes/quotations.ts} (mount {@code /api/quotations}).
 * Gerbang modul {@code quotation} (view/edit) dijalankan ErpRouteGuard.
 */
@RestController
@RequestMapping("/api/erp/quotations")
@RequiredArgsConstructor
@Tag(name = "ERP - Penawaran", description = "Penawaran harga: daftar, siklus status, konversi ke invoice")
public class ErpQuotationController {

    private final ErpQuotationService quotations;
    private final ErpSalesRequestValidator validator;
    private final ErpAccess access;

    /** Query: page, limit (≤500), status, partyId, startDate, endDate (YYYY-MM-DD), search, sort. */
    @GetMapping
    @Operation(summary = "Daftar penawaran → { data, total, page, limit }")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return quotations.list(query);
    }

    @GetMapping("/summary")
    @Operation(summary = "Ringkasan KPI penawaran (filter sama dengan daftar)")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return quotations.summary(query);
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistik tingkat konversi penawaran")
    public Map<String, Object> stats(@RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to) {
        return quotations.stats(from, to);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail penawaran")
    public ObjectNode detail(@PathVariable String id) {
        return quotations.detail(id);
    }

    @PostMapping
    @Operation(summary = "Buat penawaran (Draft)")
    public ResponseEntity<ObjectNode> create(@RequestBody QuotationRequests.Upsert body) {
        validator.check(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(quotations.create(body, access.userId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit penawaran (hanya Draft)")
    public ObjectNode update(@PathVariable String id, @RequestBody QuotationRequests.Upsert body) {
        validator.check(body);
        return quotations.update(id, body);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Hapus penawaran Draft")
    public Map<String, Object> delete(@PathVariable String id) {
        return quotations.delete(id);
    }

    @PatchMapping("/{id}/send")
    @Operation(summary = "Draft → Sent")
    public JsonNode send(@PathVariable String id) {
        return quotations.send(id);
    }

    @PatchMapping("/{id}/accept")
    @Operation(summary = "Sent → Accepted")
    public JsonNode accept(@PathVariable String id) {
        return quotations.accept(id);
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Sent → Rejected")
    public JsonNode reject(@PathVariable String id, @RequestBody QuotationRequests.Reject body) {
        validator.check(body);
        return quotations.reject(id, body.reason());
    }

    /** Status apa pun → Cancelled, diblok bila sudah dikonversi (butuh capability doc.cancel). */
    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Batalkan penawaran")
    public JsonNode cancel(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return quotations.cancel(id);
    }

    @GetMapping("/{id}/convert-payload")
    @Operation(summary = "Data prefill konversi penawaran → invoice penjualan")
    public Map<String, Object> convertPayload(@PathVariable String id) {
        return quotations.convertPayload(id);
    }

    @PostMapping("/{id}/link")
    @Operation(summary = "Tautkan invoice penjualan hasil konversi")
    public ObjectNode link(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        JsonNode v = body == null ? null : body.get("salesInvoiceId");
        if (v == null || !v.isTextual() || v.asText().isEmpty()) {
            throw ErpException.business("salesInvoiceId wajib diisi.");
        }
        return quotations.link(id, v.asText());
    }
}
