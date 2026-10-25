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
 * Inquiry (lead) — port {@code routes/inquiries.ts} (mount {@code /api/inquiries}).
 * Gerbang modul {@code quotation} (view/edit) dijalankan ErpRouteGuard.
 */
@RestController
@RequestMapping("/api/erp/inquiries")
@RequiredArgsConstructor
@Tag(name = "ERP - Inquiry", description = "Inquiry customer/prospek sebelum penawaran")
public class ErpInquiryController {

    private final ErpInquiryService inquiries;
    private final ErpSalesRequestValidator validator;
    private final ErpAccess access;

    /** Query: page, limit (≤500), status, channel, partyId, startDate, endDate, search, sort. */
    @GetMapping
    @Operation(summary = "Daftar inquiry → { data, total, page, limit }")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return inquiries.list(query);
    }

    @GetMapping("/summary")
    @Operation(summary = "Ringkasan KPI inquiry (filter sama dengan daftar)")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return inquiries.summary(query);
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistik inquiry per status")
    public Map<String, Object> stats(@RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to) {
        return inquiries.stats(from, to);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail inquiry")
    public ObjectNode detail(@PathVariable String id) {
        return inquiries.detail(id);
    }

    @PostMapping
    @Operation(summary = "Buat inquiry")
    public ResponseEntity<ObjectNode> create(@RequestBody InquiryRequests.Upsert body) {
        validator.check(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(inquiries.create(body, access.userId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit inquiry")
    public ObjectNode update(@PathVariable String id, @RequestBody InquiryRequests.Upsert body) {
        validator.check(body);
        return inquiries.update(id, body);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Hapus inquiry (belum dikonversi)")
    public Map<String, Object> delete(@PathVariable String id) {
        return inquiries.delete(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Ubah status inquiry")
    public JsonNode updateStatus(@PathVariable String id, @RequestBody InquiryRequests.StatusUpdate body) {
        validator.check(body);
        return inquiries.updateStatus(id, body);
    }

    @GetMapping("/{id}/convert-payload")
    @Operation(summary = "Data prefill konversi inquiry → penawaran (buat Party bila prospek)")
    public Map<String, Object> convertPayload(@PathVariable String id) {
        return inquiries.convertPayload(id);
    }

    @PostMapping("/{id}/link")
    @Operation(summary = "Tautkan penawaran hasil konversi")
    public ObjectNode link(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        JsonNode v = body == null ? null : body.get("quotationId");
        if (v == null || !v.isTextual() || v.asText().isEmpty()) {
            throw ErpException.business("quotationId wajib diisi.");
        }
        return inquiries.link(id, v.asText());
    }
}
