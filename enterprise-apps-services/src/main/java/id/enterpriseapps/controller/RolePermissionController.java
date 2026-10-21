package id.enterpriseapps.controller;

import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.RolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rbac")
@RequiredArgsConstructor
@Tag(name = "Role & Permission", description = "Role-Based Access Control (RBAC) API")
public class RolePermissionController {

        private final RolePermissionService rolePermissionService;

        // =====================================================
        // STRUKTUR ORGANISASI (opsi dropdown form pengguna)
        // =====================================================

        @GetMapping("/org/{type}")
        @Operation(summary = "Daftar struktur organisasi",
                        description = "type: departments | divisions | job-titles (db_auth, tidak termasuk yang dihapus)")
        public ResponseEntity<Response> getOrgOptions(@PathVariable String type) {
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(rolePermissionService.getOrgOptions(type))
                                .build());
        }

        // =====================================================
        // ROLE ENDPOINTS
        // =====================================================

        @GetMapping("/roles")
        @Operation(summary = "Get all roles", description = "Ambil semua roles yang aktif")
        public ResponseEntity<Response> getAllRoles() {
                List<Map<String, Object>> roles = rolePermissionService.getAllRoles();
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(roles)
                                .build());
        }

        @GetMapping("/roles/{id}")
        @Operation(summary = "Get role by ID", description = "Ambil role berdasarkan ID")
        public ResponseEntity<Response> getRoleById(@PathVariable Long id) {
                Map<String, Object> role = rolePermissionService.getRoleById(id);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(role)
                                .build());
        }

        @GetMapping("/roles/department/{departmentId}")
        @Operation(summary = "Get roles by department", description = "Ambil roles berdasarkan departemen")
        public ResponseEntity<Response> getRolesByDepartment(@PathVariable Long departmentId) {
                List<Map<String, Object>> roles = rolePermissionService.getRolesByDepartment(departmentId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(roles)
                                .build());
        }

        @PostMapping("/roles")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Create role", description = "Buat role baru")
        public ResponseEntity<Response> createRole(@RequestBody Map<String, Object> request) {
                Map<String, Object> role = rolePermissionService.createRole(request);
                return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.CREATED)
                                .data(role)
                                .build());
        }

        @PutMapping("/roles/{id}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Update role", description = "Update role yang ada")
        public ResponseEntity<Response> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> request) {
                Map<String, Object> role = rolePermissionService.updateRole(id, request);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(role)
                                .build());
        }

        @DeleteMapping("/roles/{id}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Delete role", description = "Hapus role (soft delete)")
        public ResponseEntity<Response> deleteRole(@PathVariable Long id) {
                rolePermissionService.deleteRole(id);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .message("Role berhasil dihapus")
                                .build());
        }

        // =====================================================
        // PERMISSION ENDPOINTS
        // =====================================================

        @GetMapping("/permissions")
        @Operation(summary = "Get all permissions", description = "Ambil semua permissions yang aktif")
        public ResponseEntity<Response> getAllPermissions() {
                List<Map<String, Object>> permissions = rolePermissionService.getAllPermissions();
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(permissions)
                                .build());
        }

        @GetMapping("/permissions/module/{module}")
        @Operation(summary = "Get permissions by module", description = "Ambil permissions berdasarkan module")
        public ResponseEntity<Response> getPermissionsByModule(@PathVariable String module) {
                List<Map<String, Object>> permissions = rolePermissionService.getPermissionsByModule(module);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(permissions)
                                .build());
        }

        @GetMapping("/permissions/modules")
        @Operation(summary = "Get distinct modules", description = "Ambil daftar module yang tersedia")
        public ResponseEntity<Response> getDistinctModules() {
                List<String> modules = rolePermissionService.getDistinctModules();
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(modules)
                                .build());
        }

        @PostMapping("/permissions")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Create permission", description = "Buat permission baru")
        public ResponseEntity<Response> createPermission(@RequestBody Map<String, Object> request) {
                Map<String, Object> permission = rolePermissionService.createPermission(request);
                return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.CREATED)
                                .data(permission)
                                .build());
        }

        // =====================================================
        // USER ROLE ASSIGNMENT ENDPOINTS
        // =====================================================

        @GetMapping("/users")
        @Operation(summary = "Get all users (paginated)", description = "Ambil semua users dengan pagination, search, dan sorting")
        public ResponseEntity<Response> getAllUsers(
                        @RequestParam(required = false, defaultValue = "1") String page,
                        @RequestParam(required = false, defaultValue = "10") String limit,
                        @RequestParam(required = false, defaultValue = "id") String sortBy,
                        @RequestParam(required = false, defaultValue = "asc") String sortOrder,
                        @RequestParam(required = false) String search,
                        @RequestParam(required = false) String deptId,
                        @RequestParam(required = false) String status) {
                Response response = rolePermissionService.getUsersPaginated(page, limit, sortBy, sortOrder, search, deptId, status);
                return ResponseEntity.ok(response);
        }

        @GetMapping("/users/by-job-title")
        @Operation(summary = "Find one user by job title",
                description = "Returns the lowest-id active user whose job title matches (case-insensitive). "
                        + "Used by COA export to fetch the Laboratory Superintendent / Technology Manager name "
                        + "without paginating through every user in the system.")
        public ResponseEntity<Response> findUserByJobTitle(@RequestParam("title") String title) {
                Map<String, Object> user = rolePermissionService.findUserByJobTitle(title);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(user)
                                .build());
        }

        @PostMapping("/users")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ADMIN_IT')")
        @Operation(summary = "Create user", description = "Buat user baru")
        public ResponseEntity<Response> createUser(@RequestBody Map<String, Object> request) {
                Map<String, Object> user = rolePermissionService.createUser(request);
                return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.CREATED)
                                .data(user)
                                .build());
        }

        @PutMapping("/users/{userId}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ADMIN_IT')")
        @Operation(summary = "Update user", description = "Update data user")
        public ResponseEntity<Response> updateUser(@PathVariable Long userId,
                        @RequestBody Map<String, Object> request) {
                Map<String, Object> user = rolePermissionService.updateUser(userId, request);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(user)
                                .build());
        }

        @DeleteMapping("/users/{userId}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ADMIN_IT')")
        @Operation(summary = "Delete user", description = "Hapus user")
        public ResponseEntity<Response> deleteUser(@PathVariable Long userId) {
                rolePermissionService.deleteUser(userId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data("User berhasil dihapus")
                                .build());
        }

        @PatchMapping("/users/{userId}/status")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ADMIN_IT')")
        @Operation(summary = "Toggle user status", description = "Aktifkan/nonaktifkan user")
        public ResponseEntity<Response> toggleUserStatus(@PathVariable Long userId) {
                Map<String, Object> user = rolePermissionService.toggleUserStatus(userId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(user)
                                .build());
        }

        @PostMapping("/users/{userId}/reset-password")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ADMIN_IT')")
        @Operation(summary = "Reset user password", description = "Reset password user ke default")
        public ResponseEntity<Response> resetUserPassword(@PathVariable Long userId) {
                Map<String, Object> result = rolePermissionService.resetUserPassword(userId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(result)
                                .build());
        }

        @GetMapping("/users/{userId}/subordinates")
        @Operation(summary = "Get subordinates", description = "Ambil daftar bawahan dari user")
        public ResponseEntity<Response> getSubordinates(@PathVariable Long userId) {
                List<Map<String, Object>> subordinates = rolePermissionService.getSubordinates(userId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(subordinates)
                                .build());
        }

        @GetMapping("/users/dropdown")
        @Operation(summary = "Get users dropdown", description = "Ambil daftar user aktif untuk dropdown (id, name, npk)")
        public ResponseEntity<Response> getUsersDropdown() {
                List<Map<String, Object>> users = rolePermissionService.getUsersDropdown();
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(users)
                                .build());
        }

        @GetMapping("/users/dropdown/by-role")
        @Operation(summary = "Get users dropdown by role",
                   description = "Ambil daftar user berdasarkan role title untuk dropdown (id, name). "
                               + "Contoh: ?role=Leader JSO atau ?role=Member JSO")
        public ResponseEntity<Response> getUsersDropdownByRole(
                        @RequestParam("role") String roleTitle) {
                List<Map<String, Object>> users = rolePermissionService.getUsersDropdownByRole(roleTitle);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(users)
                                .build());
        }

        @GetMapping("/users/dropdown/by-role-code")
        @Operation(summary = "Get users dropdown by role code",
                   description = "Ambil daftar user aktif berdasarkan kode role (AuthRole.code) untuk dropdown (id, name). "
                               + "Contoh: ?role=ANALIS_LAB atau ?role=SAMPLER_LAB")
        public ResponseEntity<Response> getUsersDropdownByRoleCode(
                        @RequestParam("role") String roleCode) {
                List<Map<String, Object>> users = rolePermissionService.getUsersDropdownByRoleCode(roleCode);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(users)
                                .build());
        }

        @GetMapping("/users/{userId}/roles")
        @Operation(summary = "Get user roles", description = "Ambil roles yang dimiliki user")
        public ResponseEntity<Response> getUserRoles(@PathVariable Long userId) {
                List<Map<String, Object>> roles = rolePermissionService.getUserRoles(userId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(roles)
                                .build());
        }

        @PostMapping("/users/{userId}/roles/{roleId}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Assign role to user", description = "Assign role ke user")
        public ResponseEntity<Response> assignRoleToUser(
                        @PathVariable Long userId,
                        @PathVariable Long roleId,
                        @RequestParam(required = false, defaultValue = "SYSTEM") String assignedBy) {
                Map<String, Object> userRole = rolePermissionService.assignRoleToUser(userId, roleId, assignedBy);
                return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.CREATED)
                                .data(userRole)
                                .build());
        }

        @DeleteMapping("/users/{userId}/roles/{roleId}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Remove role from user", description = "Hapus role dari user")
        public ResponseEntity<Response> removeRoleFromUser(
                        @PathVariable Long userId,
                        @PathVariable Long roleId) {
                rolePermissionService.removeRoleFromUser(userId, roleId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data("Role berhasil dihapus dari user")
                                .build());
        }

        @PutMapping("/users/{userId}/roles/{roleId}/primary")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Set primary role", description = "Set role sebagai primary role user")
        public ResponseEntity<Response> setPrimaryRole(
                        @PathVariable Long userId,
                        @PathVariable Long roleId) {
                rolePermissionService.setPrimaryRole(userId, roleId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data("Primary role berhasil diset")
                                .build());
        }

        // =====================================================
        // ROLE PERMISSION ASSIGNMENT ENDPOINTS
        // =====================================================

        @GetMapping("/roles/{roleId}/permissions")
        @Operation(summary = "Get role permissions", description = "Ambil permissions yang dimiliki role")
        public ResponseEntity<Response> getRolePermissions(@PathVariable Long roleId) {
                List<Map<String, Object>> permissions = rolePermissionService.getRolePermissions(roleId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(permissions)
                                .build());
        }

        @PostMapping("/roles/permissions")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Assign permission to role", description = "Assign permission ke role dengan akses CRUD")
        public ResponseEntity<Response> assignPermissionToRole(@RequestBody Map<String, Object> request) {
                Map<String, Object> rolePermission = rolePermissionService.assignPermissionToRole(request);
                return ResponseEntity.status(HttpStatus.CREATED).body(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.CREATED)
                                .data(rolePermission)
                                .build());
        }

        @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Remove permission from role", description = "Hapus permission dari role")
        public ResponseEntity<Response> removePermissionFromRole(
                        @PathVariable Long roleId,
                        @PathVariable Long permissionId) {
                rolePermissionService.removePermissionFromRole(roleId, permissionId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data("Permission berhasil dihapus dari role")
                                .build());
        }

        @PutMapping("/roles/{roleId}/permissions/bulk")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Bulk save role permissions", description = "Simpan semua permission role sekaligus (replace all)")
        public ResponseEntity<Response> bulkSaveRolePermissions(
                        @PathVariable Long roleId,
                        @RequestBody List<Map<String, Object>> permissions) {
                List<Map<String, Object>> result = rolePermissionService.bulkSaveRolePermissions(roleId,
                                permissions);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(result)
                                .build());
        }

        @GetMapping("/users/{userId}/menu")
        @Operation(summary = "Get user sidebar menu", description = "Ambil menu sidebar berdasarkan permission user")
        public ResponseEntity<Response> getUserMenu(@PathVariable Long userId) {
                List<Map<String, Object>> menu = rolePermissionService.getUserMenu(userId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(menu)
                                .build());
        }

        // =====================================================
        // USER PERMISSION CHECK ENDPOINTS
        // =====================================================

        @GetMapping("/users/{userId}/permissions")
        @Operation(summary = "Get user permissions", description = "Ambil semua permissions yang dimiliki user berdasarkan roles-nya")
        public ResponseEntity<Response> getUserPermissions(@PathVariable Long userId) {
                Map<String, Object> permissions = rolePermissionService.getUserPermissions(userId);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(permissions)
                                .build());
        }

        @GetMapping("/users/{userId}/check")
        @Operation(summary = "Check permission", description = "Cek apakah user memiliki permission tertentu")
        public ResponseEntity<Response> checkPermission(
                        @PathVariable Long userId,
                        @RequestParam String permissionCode,
                        @RequestParam String action) {
                boolean hasPermission = rolePermissionService.hasPermission(userId, permissionCode, action);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(Map.of(
                                                "hasPermission", hasPermission,
                                                "permissionCode", permissionCode,
                                                "action", action))
                                .build());
        }

        @GetMapping("/users/{userId}/module-access")
        @Operation(summary = "Check module access", description = "Cek apakah user memiliki akses ke module tertentu")
        public ResponseEntity<Response> checkModuleAccess(
                        @PathVariable Long userId,
                        @RequestParam String module) {
                boolean hasAccess = rolePermissionService.hasModuleAccess(userId, module);
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(Map.of(
                                                "hasAccess", hasAccess,
                                                "module", module))
                                .build());
        }

        // =====================================================
        // INITIALIZATION ENDPOINT
        // =====================================================

        @PostMapping("/init")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Initialize default roles and permissions", description = "Initialize default roles dan permissions (hanya sekali)")
        public ResponseEntity<Response> initializeDefaults() {
                rolePermissionService.initializeDefaultRolesAndPermissions();
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data("Default roles dan permissions berhasil diinisialisasi")
                                .build());
        }

        @PostMapping("/sync")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
        @Operation(summary = "Sync role permissions", description = "Sinkronisasi ulang role-permissions dari definisi code ke database. Aman dipanggil berkali-kali.")
        public ResponseEntity<Response> syncRolePermissions() {
                rolePermissionService.syncRolePermissions();
                return ResponseEntity.ok(Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data("Role permissions berhasil disinkronisasi")
                                .build());
        }
}

