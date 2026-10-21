package id.enterpriseapps.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.security.ErpAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Jurnal umum — port {@code routes/journals.ts} (mount {@code /api/journals}, gerbang
 * requirePerm('journal') dijalankan {@code ErpRouteGuard}).
 */
@RestController
@RequestMapping("/api/erp/journals")
@RequiredArgsConstructor
public class ErpJournalController {

    private final ErpJournalService journals;
    private final ErpAccess access;

    @GetMapping({"", "/"})
    public JsonNode list(@RequestParam Map<String, String> query) {
        return journals.list(query);
    }

    @PostMapping({"", "/"})
    public ResponseEntity<JsonNode> create(@Valid @RequestBody AccountingRequests.JournalCreate body) {
        return ResponseEntity.status(201).body(journals.create(body, access.userId()));
    }

    @GetMapping("/{id}")
    public JsonNode get(@PathVariable String id) {
        return journals.get(id);
    }

    @PatchMapping("/{id}/cancel")
    public JsonNode cancel(@PathVariable String id) {
        return journals.cancel(id);
    }
}
