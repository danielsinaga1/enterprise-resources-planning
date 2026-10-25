package id.neravoxa.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.core.security.ErpPermissionService;
import id.neravoxa.erp.entity.CompanySettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Pengaturan perusahaan — port {@code routes/settings.ts} (mount {@code /api/settings}).
 * ErpRouteGuard memasang {@code settings} sebagai OPEN: {@code GET /company} terbuka untuk semua
 * pengguna login (header PDF, branding), tulis butuh izin edit modul {@code settings}.
 */
@RestController
@RequestMapping("/api/erp/settings")
@RequiredArgsConstructor
@Tag(name = "ERP - Pengaturan", description = "Profil perusahaan, logo, bahasa default, format invoice")
public class ErpCompanySettingsController {

    private final ErpCompanySettingsService settings;
    private final ErpAccess access;

    @GetMapping("/company")
    @Operation(summary = "Profil perusahaan")
    public JsonNode company() {
        return settings.company();
    }

    @PutMapping("/company")
    @Operation(summary = "Simpan profil perusahaan (logo sebagai data URL)")
    public CompanySettings update(@RequestBody(required = false) JsonNode body) {
        access.requireCan("settings", ErpPermissionService.EDIT);
        return settings.update(body);
    }

    @PostMapping("/company/onboarding")
    @Operation(summary = "Tandai penyiapan awal selesai/dilewati")
    public Map<String, Object> onboarding() {
        access.requireCan("settings", ErpPermissionService.EDIT);
        return settings.onboarding();
    }
}
