package id.enterpriseapps.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.security.ErpAccess;
import id.enterpriseapps.erp.core.security.ErpPermissionService;
import id.enterpriseapps.erp.entity.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Role & hak akses ERP — port {@code routes/roles.ts} (mount {@code /api/roles}). ErpRouteGuard
 * memasang {@code roles} sebagai OPEN: {@code /assignable} cukup izin modul {@code users} (view);
 * selainnya butuh capability {@code rbac.manage} (padanan {@code router.use(requireCap(...))}).
 */
@RestController
@RequestMapping("/api/erp/roles")
@RequiredArgsConstructor
@Tag(name = "ERP - Role & Hak Akses", description = "Matriks izin ERP per kode role platform")
public class ErpRoleController {

    private static final String RBAC_MANAGE = "rbac.manage";

    private final ErpRoleService roles;
    private final ErpAccess access;

    @GetMapping("/assignable")
    @Operation(summary = "Role ERP aktif (key + nama) untuk filter pengguna")
    public List<Map<String, Object>> assignable() {
        access.requireCan("users", ErpPermissionService.VIEW);
        return roles.assignable();
    }

    @GetMapping("/catalog")
    @Operation(summary = "Katalog modul & capability")
    public Map<String, Object> catalog() {
        access.requireCap(RBAC_MANAGE);
        return roles.catalog();
    }

    @GetMapping("/platform")
    @Operation(summary = "Role platform + status konfigurasi di ERP")
    public List<Map<String, Object>> platform() {
        access.requireCap(RBAC_MANAGE);
        return roles.platformRoles();
    }

    @GetMapping
    @Operation(summary = "Daftar role + matriks izin + jumlah pengguna")
    public List<Map<String, Object>> list() {
        access.requireCap(RBAC_MANAGE);
        return roles.list();
    }

    @PostMapping
    @Operation(summary = "Konfigurasi hak akses ERP untuk kode role platform")
    public ResponseEntity<Role> create(@RequestBody(required = false) JsonNode body) {
        access.requireCap(RBAC_MANAGE);
        return ResponseEntity.status(HttpStatus.CREATED).body(roles.create(body));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Ubah nama/deskripsi/status + matriks izin role")
    public Map<String, Object> update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        access.requireCap(RBAC_MANAGE);
        return roles.update(id, body);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Hapus konfigurasi role custom")
    public Map<String, Object> delete(@PathVariable String id) {
        access.requireCap(RBAC_MANAGE);
        return roles.delete(id);
    }
}
