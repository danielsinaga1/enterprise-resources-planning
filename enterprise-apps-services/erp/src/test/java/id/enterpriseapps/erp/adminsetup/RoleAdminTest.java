package id.enterpriseapps.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.security.ErpIdentityPort.PlatformRole;
import id.enterpriseapps.erp.core.security.ErpModules;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /api/erp/roles: katalog, role platform, CRUD berkunci kode platform, dan efek invalidasi cache izin. */
class RoleAdminTest extends AdminSetupTestSupport {

    private static final String DENIED = "Akses ditolak: Tidak memiliki izin.";

    private static String code(String prefix) {
        return (prefix + "_" + UUID.randomUUID().toString().substring(0, 6)).toUpperCase(Locale.ROOT);
    }

    private static JsonNode findOrNull(JsonNode array, String field, String value) {
        for (JsonNode n : array) {
            if (value.equals(n.path(field).asText(null))) {
                return n;
            }
        }
        return null;
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        JsonNode n = findOrNull(array, field, value);
        if (n == null) {
            throw new AssertionError(field + "=" + value + " tidak ada di " + array);
        }
        return n;
    }

    private String roleId(String key) {
        return jdbc.queryForObject("SELECT id FROM roles WHERE key = ?", String.class, key);
    }

    private int count(String table, String roleId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE role_id = ?", Integer.class, roleId);
    }

    @Test
    void catalog_platform_roles_and_crud_keyed_by_platform_code() throws Exception {
        adminId();
        String code = code("GUDANG");
        PLATFORM_ROLES.add(new PlatformRole(code, "Staf Gudang " + code));

        JsonNode catalog = ok(get("/api/erp/roles/catalog"));
        assertEquals(ErpModules.MODULES.size(), catalog.get("modules").size());
        JsonNode parties = find(catalog.get("modules"), "key", "parties");
        assertEquals("Pelanggan & Vendor", parties.get("label").asText());
        assertEquals("Master Data", parties.get("group").asText());
        assertEquals("Kelola Role & Hak Akses", find(catalog.get("capabilities"), "key", "rbac.manage").get("label").asText());

        JsonNode platform = ok(get("/api/erp/roles/platform"));
        JsonNode viewer = find(platform, "code", "ERP_VIEWER");
        assertTrue(viewer.get("configured").asBoolean());
        assertEquals(roleId("ERP_VIEWER"), viewer.get("erpRoleId").asText());
        JsonNode mine = find(platform, "code", code);
        assertFalse(mine.get("configured").asBoolean());
        assertTrue(mine.get("erpRoleId").isNull());
        assertEquals("Staf Gudang " + code, mine.get("name").asText());

        assertError(call(post("/api/erp/roles", Map.of("key", "TIDAK_ADA_" + code))), 400, "Role platform tidak ditemukan.");
        assertError(call(post("/api/erp/roles", Map.of("key", "ERP_VIEWER"))), 400, "Role dengan key \"ERP_VIEWER\" sudah ada.");
        assertError(call(post("/api/erp/roles", Map.of())), 400, "Key role tidak valid.");
        assertError(call(post("/api/erp/roles", Map.of("key", code, "name", "X"))), 400, "Nama role minimal 2 karakter.");

        Map<String, Object> body = new HashMap<>();
        body.put("key", code);
        body.put("description", "  Akses gudang  ");
        body.put("permissions", List.of(
                Map.of("module", "parties", "canEdit", true),
                Map.of("module", "reports", "canView", true),
                Map.of("module", "bogus", "canView", true),
                Map.of("module", "inventory")));
        body.put("caps", List.of("doc.cancel", "bogus", "doc.cancel"));
        Result created = call(post("/api/erp/roles", body));
        assertEquals(201, created.status(), created.body().toString());
        JsonNode role = created.data();
        String id = role.get("id").asText();
        assertEquals(code, role.get("key").asText());
        assertEquals("Staf Gudang " + code, role.get("name").asText());
        assertEquals("Akses gudang", role.get("description").asText());
        assertFalse(role.get("isSystem").asBoolean());
        assertFalse(role.get("isSuperAdmin").asBoolean());
        assertTrue(role.get("isActive").asBoolean());
        assertEquals(2, role.get("permissions").size());
        JsonNode partiesPerm = find(role.get("permissions"), "module", "parties");
        assertTrue(partiesPerm.get("canView").asBoolean());
        assertTrue(partiesPerm.get("canEdit").asBoolean());
        JsonNode reportsPerm = find(role.get("permissions"), "module", "reports");
        assertTrue(reportsPerm.get("canView").asBoolean());
        assertFalse(reportsPerm.get("canEdit").asBoolean());
        assertEquals(1, role.get("capabilities").size());
        assertEquals("doc.cancel", role.get("capabilities").get(0).get("capability").asText());

        JsonNode roles = ok(get("/api/erp/roles"));
        assertTrue(roles.get(0).get("isSystem").asBoolean());
        JsonNode listed = find(roles, "key", code);
        assertEquals(id, listed.get("id").asText());
        assertEquals(0, listed.get("userCount").asInt());
        assertEquals(List.of("doc.cancel"), List.of(listed.get("caps").get(0).asText()));
        assertEquals(2, listed.get("permissions").size());
        assertEquals(3, listed.get("permissions").get(0).size()); // { module, canView, canEdit }

        JsonNode configured = find(ok(get("/api/erp/roles/platform")), "code", code);
        assertTrue(configured.get("configured").asBoolean());
        assertEquals(id, configured.get("erpRoleId").asText());
        assertEquals("Staf Gudang " + code, find(ok(get("/api/erp/roles/assignable")), "key", code).get("name").asText());

        // Nama terlalu pendek diabaikan, deskripsi di-trim, role custom boleh dinonaktifkan; matriks tetap.
        Map<String, Object> upd = new HashMap<>();
        upd.put("name", "A");
        upd.put("description", "  Baru  ");
        upd.put("isActive", false);
        assertTrue(ok(put("/api/erp/roles/" + id, upd)).get("success").asBoolean());
        Map<String, Object> row = jdbc.queryForMap("SELECT name, description, is_active FROM roles WHERE id = ?", id);
        assertEquals("Staf Gudang " + code, row.get("name"));
        assertEquals("Baru", row.get("description"));
        assertEquals(false, row.get("is_active"));
        assertNull(findOrNull(ok(get("/api/erp/roles/assignable")), "key", code));
        assertEquals(2, count("role_permissions", id));
        assertEquals(1, count("role_capabilities", id));

        ok(put("/api/erp/roles/" + id, Map.of("permissions", List.of(), "caps", List.of())));
        assertEquals(0, count("role_permissions", id));
        assertEquals(0, count("role_capabilities", id));

        // Role sistem tidak bisa dinonaktifkan; matriks super-admin diabaikan.
        String viewerId = roleId("ERP_VIEWER");
        ok(put("/api/erp/roles/" + viewerId, Map.of("isActive", false)));
        assertEquals(true, jdbc.queryForObject("SELECT is_active FROM roles WHERE id = ?", Boolean.class, viewerId));
        String superId = roleId("SUPER_ADMIN");
        int superPerms = count("role_permissions", superId);
        int superCaps = count("role_capabilities", superId);
        ok(put("/api/erp/roles/" + superId, Map.of(
                "permissions", List.of(Map.of("module", "parties", "canView", true)), "caps", List.of("doc.cancel"))));
        assertEquals(superPerms, count("role_permissions", superId));
        assertEquals(superCaps, count("role_capabilities", superId));

        assertError(call(delete("/api/erp/roles/" + viewerId)), 400, "Role bawaan tidak dapat dihapus.");
        assertError(call(delete("/api/erp/roles/" + UUID.randomUUID())), 404, "Role tidak ditemukan.");
        assertError(call(put("/api/erp/roles/" + UUID.randomUUID(), Map.of())), 404, "Role tidak ditemukan.");
        ok(put("/api/erp/roles/" + id, Map.of("permissions", List.of(Map.of("module", "parties", "canView", true)))));
        assertTrue(ok(delete("/api/erp/roles/" + id)).get("success").asBoolean());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM roles WHERE id = ?", Integer.class, id));
        assertEquals(0, count("role_permissions", id));
        assertFalse(find(ok(get("/api/erp/roles/platform")), "code", code).get("configured").asBoolean());
    }

    @Test
    void permission_change_applies_on_next_request() throws Exception {
        adminId();
        String code = code("KASIR");
        PLATFORM_ROLES.add(new PlatformRole(code, "Kasir " + code));
        String id = ok(post("/api/erp/roles", Map.of("key", code))).get("id").asText();
        String email = "kasir." + code.toLowerCase(Locale.ROOT) + "@corp.test";
        platformUser("Kasir " + code, null, email, null, true, code);
        String userId = login(email, code);

        Map<String, Object> party = Map.of("name", "Mitra " + code, "partyType", "Customer");
        assertError(call(post("/api/erp/parties", party)), 403, DENIED);
        assertError(call(get("/api/erp/users")), 403, DENIED);

        adminId();
        ok(put("/api/erp/roles/" + id, Map.of("permissions", List.of(
                Map.of("module", "parties", "canEdit", true),
                Map.of("module", "users", "canView", true)))));

        asUser(email, code);
        assertEquals(201, call(post("/api/erp/parties", party)).status());
        JsonNode me = ok(get("/api/erp/users?search=" + code.toLowerCase(Locale.ROOT)));
        assertEquals(userId, me.get("data").get(0).get("id").asText());
        assertError(call(put("/api/erp/users/" + userId, Map.of("language", "en"))), 403, DENIED);
        ok(get("/api/erp/roles/assignable"));
        assertError(call(get("/api/erp/roles")), 403, DENIED);
        JsonNode perms = ok(get("/api/erp/me")).get("user").get("permissions");
        assertTrue(perms.get("parties").get("edit").asBoolean());

        adminId();
        assertEquals(1, find(ok(get("/api/erp/roles")), "key", code).get("userCount").asInt());
        ok(put("/api/erp/roles/" + id, Map.of("isActive", false)));
        asUser(email, code);
        assertError(call(post("/api/erp/parties", party)), 403, DENIED);

        adminId();
        ok(put("/api/erp/roles/" + id, Map.of("isActive", true, "caps", List.of("rbac.manage"))));
        asUser(email, code);
        ok(get("/api/erp/roles"));
        ok(get("/api/erp/roles/catalog"));

        // Hapus konfigurasi → kode platform tidak lagi memberi akses ERP.
        adminId();
        ok(delete("/api/erp/roles/" + id));
        asUser(email, code);
        assertError(call(get("/api/erp/roles")), 403, DENIED);
        assertError(call(post("/api/erp/parties", party)), 403, DENIED);
    }

    @Test
    void rbac_manage_capability_required() throws Exception {
        String email = "akuntan." + UUID.randomUUID().toString().substring(0, 8) + "@corp.test";
        platformUser("Akuntan Uji", null, email, null, true, "ERP_ACCOUNTANT");
        login(email, "ERP_ACCOUNTANT");
        assertError(call(get("/api/erp/roles")), 403, DENIED);
        assertError(call(get("/api/erp/roles/catalog")), 403, DENIED);
        assertError(call(get("/api/erp/roles/platform")), 403, DENIED);
        assertError(call(post("/api/erp/roles", Map.of("key", "ERP_VIEWER"))), 403, DENIED);
        assertError(call(put("/api/erp/roles/" + roleId("ERP_VIEWER"), Map.of("isActive", false))), 403, DENIED);
        assertError(call(delete("/api/erp/roles/" + roleId("ERP_VIEWER"))), 403, DENIED);
        assertError(call(get("/api/erp/roles/assignable")), 403, DENIED);
    }
}
