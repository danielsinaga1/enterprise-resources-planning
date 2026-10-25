package id.neravoxa.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.security.ErpAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Rekonsiliasi bank — port {@code routes/reconciliation.ts} (mount {@code /api/reconciliation}, gerbang modul
 * {@code reconciliation} dijalankan ErpRouteGuard). Tambahan: unggah mutasi CSV/Excel ke sesi Draft.
 */
@RestController
@RequestMapping("/api/erp/reconciliation")
@RequiredArgsConstructor
public class ErpReconciliationController {

    private final ErpAccess access;
    private final ErpReconciliationService reconciliations;

    /**
     * Query: page, limit (default 50, maks. 200), accountId, status (Draft | Completed), startDate/endDate
     * (tanggal statement), search (nama/nomor akun, catatan), sort (statementDate | account | bookBalance |
     * statementBalance | createdAt; "-" = turun; default -statementDate). → { data, total, page, limit }.
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return reconciliations.list(query);
    }

    @PostMapping
    public ResponseEntity<ObjectNode> create(@RequestBody(required = false) JsonNode body) {
        CashBankRequests.CreateReconciliation req = CashBankRequests.CreateReconciliation.parse(body);
        return ResponseEntity.status(201).body(reconciliations.create(req, access.userId()));
    }

    @GetMapping("/{id}")
    public ObjectNode detail(@PathVariable String id) {
        return reconciliations.detail(id);
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ArrayNode> addItems(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        CashBankRequests.AddStatementItems req = CashBankRequests.AddStatementItems.parse(body);
        return ResponseEntity.status(201).body(reconciliations.addItems(id, req.items()));
    }

    /**
     * Tambahan (tidak ada di ENTARIS): unggah mutasi rekening CSV/XLSX/XLS (field multipart {@code file}).
     * Baris dibaca {@link BankStatementParser} lalu disimpan seperti POST /:id/items. 201 → item yang dibuat.
     */
    @PostMapping(value = "/{id}/items/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArrayNode> importItems(@PathVariable String id,
                                                 @RequestPart(value = "file", required = false) MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw ErpException.business("Berkas mutasi bank wajib diunggah.");
        }
        var items = BankStatementParser.parse(file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(201).body(reconciliations.addItems(id, items));
    }

    @PatchMapping("/{id}/match")
    public ObjectNode match(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return reconciliations.match(id, CashBankRequests.MatchItem.parse(body));
    }

    @PatchMapping("/{id}/unmatch")
    public ObjectNode unmatch(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return reconciliations.unmatch(id, CashBankRequests.UnmatchItem.parse(body).itemId());
    }

    @PostMapping("/{id}/complete")
    public ObjectNode complete(@PathVariable String id) {
        return reconciliations.complete(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        return reconciliations.delete(id);
    }
}
