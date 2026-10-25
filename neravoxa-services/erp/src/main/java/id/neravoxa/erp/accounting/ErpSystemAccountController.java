package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.security.ErpAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Akun sistem — port {@code routes/systemAccounts.ts} (mount {@code /api/system-accounts},
 * gerbang requireWrite('settings') dijalankan {@code ErpRouteGuard}).
 */
@RestController
@RequestMapping("/api/erp/system-accounts")
@RequiredArgsConstructor
public class ErpSystemAccountController {

    private final ErpSystemAccountMappingService mappings;
    private final ErpAccess access;

    @GetMapping({"", "/"})
    public JsonNode overview() {
        return mappings.overview();
    }

    /** Hanya Admin (super-admin ERP, padanan role 'Admin' ENTARIS) yang boleh mengubah pemetaan. */
    @PutMapping({"", "/"})
    public JsonNode update(@RequestBody JsonNode body) {
        if (!access.isSuperAdmin()) {
            throw ErpException.forbidden("Hanya Admin yang dapat mengubah konfigurasi akun sistem.");
        }
        return mappings.update(body);
    }
}
