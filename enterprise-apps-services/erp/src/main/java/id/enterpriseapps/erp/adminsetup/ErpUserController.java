package id.enterpriseapps.erp.adminsetup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.security.ErpAccess;
import id.enterpriseapps.erp.core.security.ErpPermissionService;
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

import java.util.List;
import java.util.Map;

/**
 * Pengguna ERP — port {@code routes/users.ts} (mount {@code /api/users}), diadaptasi ke identitas
 * platform. ErpRouteGuard memasang {@code users} sebagai OPEN, jadi {@code requirePerm('users')}
 * (view untuk GET, edit untuk tulis) dicek di sini; {@code PUT /me/language} terbuka untuk semua
 * pengguna login.
 */
@RestController
@RequestMapping("/api/erp/users")
@RequiredArgsConstructor
@Tag(name = "ERP - Pengguna", description = "Akses pengguna ERP (identitas & role milik platform)")
public class ErpUserController {

    private static final String MODULE = "users";

    private final ErpUserService users;
    private final ErpUserProvisionService provisioning;
    private final ErpAccess access;

    /** Body POST /users: salah satu dari id akun platform atau email. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProvisionRequest(Long platformUserId, String email) {
    }

    @GetMapping
    @Operation(summary = "Daftar pengguna ERP (page, limit, search, role, status, sort)")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        access.requireCan(MODULE, ErpPermissionService.VIEW);
        return users.list(query);
    }

    @GetMapping("/summary")
    @Operation(summary = "Ringkasan jumlah pengguna (filter sama dengan daftar)")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        access.requireCan(MODULE, ErpPermissionService.VIEW);
        return users.summary(query);
    }

    @GetMapping("/platform-search")
    @Operation(summary = "Cari akun platform untuk ditambahkan ke ERP")
    public List<Map<String, Object>> platformSearch(@RequestParam(name = "q", required = false) String q) {
        access.requireCan(MODULE, ErpPermissionService.VIEW);
        return provisioning.platformSearch(q);
    }

    @PostMapping
    @Operation(summary = "Provisi pengguna ERP dari akun platform")
    public ResponseEntity<Map<String, Object>> provision(@RequestBody(required = false) ProvisionRequest body) {
        access.requireCan(MODULE, ErpPermissionService.EDIT);
        ProvisionRequest b = body == null ? new ProvisionRequest(null, null) : body;
        return ResponseEntity.status(HttpStatus.CREATED).body(provisioning.provision(b.platformUserId(), b.email()));
    }

    @PostMapping("/sync")
    @Operation(summary = "Sinkronkan snapshot pengguna ERP dari platform")
    public Map<String, Object> sync() {
        access.requireCan(MODULE, ErpPermissionService.EDIT);
        return provisioning.sync();
    }

    @PutMapping("/me/language")
    @Operation(summary = "Ubah bahasa UI sendiri ('id' | 'en' | null)")
    public Map<String, Object> myLanguage(@RequestBody(required = false) JsonNode body) {
        return users.updateMyLanguage(access.userId(), body);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Ubah status aktif / atasan langsung / bahasa pengguna")
    public Map<String, Object> update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        access.requireCan(MODULE, ErpPermissionService.EDIT);
        return users.update(id, body, access.userId());
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Aktifkan/nonaktifkan pengguna ERP")
    public Map<String, Object> toggle(@PathVariable String id) {
        access.requireCan(MODULE, ErpPermissionService.EDIT);
        return users.toggle(id, access.userId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Nonaktifkan pengguna ERP (identitas milik platform)")
    public Map<String, Object> deactivate(@PathVariable String id) {
        access.requireCan(MODULE, ErpPermissionService.EDIT);
        return users.deactivate(id, access.userId());
    }
}
