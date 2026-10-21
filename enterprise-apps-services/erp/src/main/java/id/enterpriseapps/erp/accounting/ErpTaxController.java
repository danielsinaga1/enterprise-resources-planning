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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Pajak — port {@code routes/tax.ts} (mount {@code /api/tax}, gerbang requireWrite('settings')
 * dijalankan {@code ErpRouteGuard}).
 */
@RestController
@RequestMapping("/api/erp/tax")
@RequiredArgsConstructor
public class ErpTaxController {

    private final ErpTaxService tax;
    private final ErpAccess access;

    @GetMapping("/config")
    public JsonNode listConfigs() {
        return tax.listConfigs();
    }

    @PostMapping("/config")
    public ResponseEntity<JsonNode> createConfig(@Valid @RequestBody AccountingRequests.TaxConfigCreate body) {
        return ResponseEntity.status(201).body(tax.createConfig(body));
    }

    @PutMapping("/config/{id}")
    public JsonNode updateConfig(@PathVariable String id, @Valid @RequestBody AccountingRequests.TaxConfigUpdate body) {
        return tax.updateConfig(id, body);
    }

    /** Nonaktifkan konfigurasi pajak: butuh capability doc.cancel. */
    @DeleteMapping("/config/{id}")
    public JsonNode deleteConfig(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return tax.deactivateConfig(id);
    }

    @GetMapping("/report")
    public JsonNode report(@RequestParam Map<String, String> query) {
        return tax.report(query.get("startDate"), query.get("endDate"));
    }
}
