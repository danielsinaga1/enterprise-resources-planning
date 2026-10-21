package id.enterpriseapps.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.security.ErpAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tahun fiskal — port {@code routes/fiscalYears.ts} (mount {@code /api/fiscal-years}, gerbang
 * requireWrite('settings') dijalankan {@code ErpRouteGuard}).
 */
@RestController
@RequestMapping("/api/erp/fiscal-years")
@RequiredArgsConstructor
public class ErpFiscalYearController {

    private final ErpFiscalYearClosingService fiscalYears;
    private final ErpAccess access;

    @GetMapping({"", "/"})
    public JsonNode list() {
        return fiscalYears.list();
    }

    @PostMapping({"", "/"})
    public ResponseEntity<JsonNode> create(@Valid @RequestBody AccountingRequests.FiscalYearCreate body) {
        return ResponseEntity.status(201).body(fiscalYears.create(body));
    }

    /** Tutup buku: selain izin edit settings, butuh capability doc.cancel. */
    @PostMapping("/{id}/close")
    public JsonNode close(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return fiscalYears.close(id, access.userId());
    }

    /** Buka kembali tahun buku: butuh capability doc.cancel. */
    @PostMapping("/{id}/reopen")
    public JsonNode reopen(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return fiscalYears.reopen(id);
    }
}
