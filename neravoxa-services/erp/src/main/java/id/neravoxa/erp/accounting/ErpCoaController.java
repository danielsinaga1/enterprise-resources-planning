package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.security.ErpAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bagan akun — port {@code routes/coa.ts} (mount {@code /api/coa}, gerbang requireWrite('coa')
 * dijalankan {@code ErpRouteGuard}).
 */
@RestController
@RequestMapping("/api/erp/coa")
@RequiredArgsConstructor
public class ErpCoaController {

    private final ErpCoaService coa;
    private final ErpAccess access;

    @GetMapping({"", "/"})
    public JsonNode tree() {
        return coa.tree();
    }

    @GetMapping("/flat")
    public JsonNode flat() {
        return coa.flat();
    }

    @PostMapping({"", "/"})
    public ResponseEntity<JsonNode> create(@Valid @RequestBody AccountingRequests.CoaCreate body) {
        return ResponseEntity.status(201).body(coa.create(body));
    }

    @PutMapping("/{id}")
    public JsonNode update(@PathVariable String id, @Valid @RequestBody AccountingRequests.CoaUpdate body) {
        return coa.update(id, body);
    }

    @PatchMapping("/{id}/balance")
    public JsonNode setBalance(@PathVariable String id, @RequestBody AccountingRequests.CoaBalance body) {
        return coa.setOpeningBalance(id, body.balance(), access.userId());
    }

    /** Hapus akun: selain izin edit coa, butuh capability doc.cancel. */
    @DeleteMapping("/{id}")
    public JsonNode delete(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return coa.delete(id);
    }
}
