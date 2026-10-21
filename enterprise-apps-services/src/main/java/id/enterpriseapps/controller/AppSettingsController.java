package id.enterpriseapps.controller;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import id.enterpriseapps.dto.GeneralSettingsDto;
import id.enterpriseapps.service.AppSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "API untuk Pengaturan Umum aplikasi")
public class AppSettingsController {

    private final AppSettingsService appSettingsService;

    /**
     * Terbuka tanpa autentikasi: halaman Login menampilkan nama & deskripsi
     * aplikasi sebelum pengguna punya token. Isinya memang untuk ditampilkan,
     * bukan data sensitif. Penulisan tetap butuh token (lihat SecurityConfig).
     */
    @GetMapping("/general")
    @Operation(summary = "Get general settings", description = "Mengambil Pengaturan Umum (branding & kontak)")
    public ResponseEntity<GeneralSettingsDto> getGeneralSettings() {
        return ResponseEntity.ok(appSettingsService.getGeneralSettings());
    }

    @PutMapping("/general")
    @Operation(summary = "Update general settings", description = "Memperbarui Pengaturan Umum")
    public ResponseEntity<GeneralSettingsDto> updateGeneralSettings(
            @RequestBody GeneralSettingsDto request,
            Principal principal) {
        String updatedBy = principal != null ? principal.getName() : "system";
        return ResponseEntity.ok(appSettingsService.saveGeneralSettings(request, updatedBy));
    }
}
