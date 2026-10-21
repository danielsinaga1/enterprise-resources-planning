package id.enterpriseapps.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.security.ErpAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transaksi berulang — port {@code routes/recurring.ts} (mount {@code /api/recurring}, gerbang
 * requirePerm('recurring') dijalankan {@code ErpRouteGuard}).
 */
@RestController
@RequestMapping("/api/erp/recurring")
@RequiredArgsConstructor
public class ErpRecurringController {

    private final ErpRecurringService recurring;
    private final ErpAccess access;

    @GetMapping({"", "/"})
    public JsonNode list() {
        return recurring.list();
    }

    @PostMapping({"", "/"})
    public ResponseEntity<JsonNode> create(@Valid @RequestBody AccountingRequests.RecurringCreate body) {
        return ResponseEntity.status(201).body(recurring.create(body, access.userId()));
    }

    /** Jalankan semua template jatuh tempo: butuh capability doc.cancel. */
    @PostMapping("/run-due")
    public JsonNode runDue() {
        access.requireCap("doc.cancel");
        return recurring.runDue(access.userId());
    }

    @PutMapping("/{id}")
    public JsonNode update(@PathVariable String id, @Valid @RequestBody AccountingRequests.RecurringUpdate body) {
        return recurring.update(id, body);
    }

    @DeleteMapping("/{id}")
    public JsonNode delete(@PathVariable String id) {
        return recurring.deactivate(id);
    }

    @PostMapping("/{id}/execute")
    public JsonNode execute(@PathVariable String id) {
        return recurring.execute(id, access.userId());
    }
}
