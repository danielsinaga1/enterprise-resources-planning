package id.enterpriseapps.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.security.ErpIdentityPort.PlatformUser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/** /api/erp/users: daftar/filter/ringkasan, provisi dari platform, sinkron, ubah/toggle/nonaktif, bahasa. */
class UserAdminTest extends AdminSetupTestSupport {

    private static final String DENIED = "Akses ditolak: Tidak memiliki izin.";
    private static final List<String> ROW_KEYS = List.of("id", "username", "fullName", "email", "npk", "role", "roles",
            "isActive", "lastLoginAt", "createdAt", "supervisorId", "supervisor", "platformUserId", "language");

    private String provision(PlatformUser p) throws Exception {
        return ok(post("/api/erp/users", Map.of("platformUserId", p.id()))).get("id").asText();
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode n : array) {
            if (value.equals(n.path(field).asText(null))) {
                return n;
            }
        }
        throw new AssertionError(field + "=" + value + " tidak ada di " + array);
    }

    @Test
    void provision_from_platform_links_supervisor_and_guards() throws Exception {
        adminId();
        String tag = uniq("prov").toLowerCase();
        PlatformUser boss = platformUser("Atasan " + tag, "NPKB" + tag, "boss." + tag + "@corp.test", null, true, "ERP_ACCOUNTANT");
        PlatformUser staff = platformUser("Staf " + tag, "NPKS" + tag, "staf." + tag + "@corp.test", boss.id(), true,
                "LIMS_ANALYST", "ERP_VIEWER");
        PlatformUser off = platformUser("Nonaktif " + tag, null, "off." + tag + "@corp.test", null, false, "ERP_VIEWER");

        assertEquals(0, ok(get("/api/erp/users/platform-search?q=a")).size());
        JsonNode found = ok(get("/api/erp/users/platform-search?q=" + tag));
        assertEquals(2, found.size(), found.toString());
        JsonNode hit = find(found, "email", staff.email());
        assertEquals(staff.id().longValue(), hit.get("id").asLong());
        assertEquals("NPKS" + tag, hit.get("npk").asText());
        assertEquals("Staf " + tag, hit.get("name").asText());
        assertEquals(List.of("LIMS_ANALYST", "ERP_VIEWER"), texts(hit.get("roles")));
        assertTrue(hit.get("active").asBoolean());
        assertFalse(hit.get("provisioned").asBoolean());
        assertTrue(hit.get("erpUserId").isNull());

        Result created = call(post("/api/erp/users", Map.of("platformUserId", staff.id())));
        assertEquals(201, created.status(), created.body().toString());
        JsonNode row = created.data();
        ROW_KEYS.forEach(k -> assertTrue(row.has(k), "UserRow tanpa " + k));
        assertEquals("Staf " + tag, row.get("fullName").asText());
        assertEquals("NPKS" + tag, row.get("username").asText());
        assertEquals("NPKS" + tag, row.get("npk").asText());
        assertEquals(staff.email(), row.get("email").asText());
        assertEquals("ERP_VIEWER", row.get("role").asText());
        assertEquals(List.of("LIMS_ANALYST", "ERP_VIEWER"), texts(row.get("roles")));
        assertTrue(row.get("isActive").asBoolean());
        assertTrue(row.get("lastLoginAt").isNull());
        assertTrue(row.get("language").isNull());
        assertEquals(staff.id().longValue(), row.get("platformUserId").asLong());
        String bossId = erpUserId(boss.email());
        assertEquals(bossId, row.get("supervisorId").asText());
        assertEquals(bossId, row.get("supervisor").get("id").asText());
        assertEquals("Atasan " + tag, row.get("supervisor").get("fullName").asText());
        Map<String, Object> bossRow = jdbc.queryForMap(
                "SELECT platform_user_id, is_active, roles, role, last_login_at FROM users WHERE id = ?", bossId);
        assertEquals(boss.id(), ((Number) bossRow.get("platform_user_id")).longValue());
        assertEquals(true, bossRow.get("is_active"));
        assertEquals("ERP_ACCOUNTANT", bossRow.get("roles"));
        assertEquals("ERP_ACCOUNTANT", bossRow.get("role"));
        assertEquals(null, bossRow.get("last_login_at"));

        assertError(call(post("/api/erp/users", Map.of("platformUserId", staff.id()))), 400, "User sudah terdaftar di ERP.");
        assertError(call(post("/api/erp/users", Map.of("email", boss.email().toUpperCase()))), 400, "User sudah terdaftar di ERP.");
        assertError(call(post("/api/erp/users", Map.of("platformUserId", 987654321L))), 404, "User platform tidak ditemukan.");
        assertError(call(post("/api/erp/users", Map.of("email", "nobody." + tag + "@corp.test"))), 404,
                "User platform tidak ditemukan.");
        assertError(call(post("/api/erp/users", Map.of("platformUserId", off.id()))), 400, "User platform tidak aktif.");
        assertError(call(post("/api/erp/users", Map.of())), 400, "platformUserId atau email wajib diisi.");

        PlatformUser byMail = platformUser("Lewat Email " + tag, null, "mail." + tag + "@corp.test", null, true, "ERP_ACCOUNTANT");
        JsonNode viaEmail = ok(post("/api/erp/users", Map.of("email", byMail.email())));
        assertEquals("mail." + tag, viaEmail.get("username").asText());
        assertEquals("ERP_ACCOUNTANT", viaEmail.get("role").asText());
        assertTrue(viaEmail.get("supervisor").isNull());

        JsonNode again = ok(get("/api/erp/users/platform-search?q=" + tag));
        JsonNode hit2 = find(again, "email", staff.email());
        assertTrue(hit2.get("provisioned").asBoolean());
        assertEquals(row.get("id").asText(), hit2.get("erpUserId").asText());
    }

    @Test
    void list_filters_sort_paging_and_summary() throws Exception {
        adminId();
        String tag = uniq("lst").toLowerCase();
        PlatformUser a = platformUser("Alfa " + tag, "A" + tag, "alfa." + tag + "@corp.test", null, true, "ERP_ACCOUNTANT");
        PlatformUser b = platformUser("Bravo " + tag, "B" + tag, "bravo." + tag + "@corp.test", null, true, "ERP_VIEWER");
        PlatformUser c = platformUser("Charlie " + tag, "C" + tag, "charlie." + tag + "@corp.test", null, true,
                "ERP_VIEWER", "ERP_PRODUCTION");
        provision(a);
        provision(b);
        String idc = provision(c);
        assertFalse(ok(patch("/api/erp/users/" + idc + "/toggle", Map.of())).get("isActive").asBoolean());
        login(a.email(), "ERP_ACCOUNTANT"); // lastLoginAt terisi hanya untuk Alfa
        adminId();

        JsonNode asc = ok(get("/api/erp/users?search=" + tag + "&sort=fullName"));
        assertEquals(3, asc.get("total").asInt());
        assertEquals(1, asc.get("page").asInt());
        assertEquals(50, asc.get("limit").asInt());
        assertEquals(List.of("Alfa " + tag, "Bravo " + tag, "Charlie " + tag), texts(asc.get("data"), "fullName"));
        ROW_KEYS.forEach(k -> assertTrue(asc.get("data").get(0).has(k), "UserRow tanpa " + k));
        assertEquals(List.of("Charlie " + tag, "Bravo " + tag, "Alfa " + tag),
                texts(ok(get("/api/erp/users?search=" + tag + "&sort=-fullName")).get("data"), "fullName"));

        JsonNode byNpk = ok(get("/api/erp/users?search=" + ("B" + tag).toUpperCase()));
        assertEquals(List.of("Bravo " + tag), texts(byNpk.get("data"), "fullName"));

        assertEquals(List.of("Bravo " + tag, "Charlie " + tag),
                texts(ok(get("/api/erp/users?search=" + tag + "&role=ERP_VIEWER&sort=fullName")).get("data"), "fullName"));
        assertEquals(0, ok(get("/api/erp/users?search=" + tag + "&role=ERP_VIEW")).get("total").asInt());
        assertEquals(List.of("Charlie " + tag),
                texts(ok(get("/api/erp/users?search=" + tag + "&role=ERP_PRODUCTION")).get("data"), "fullName"));

        assertEquals(List.of("Charlie " + tag),
                texts(ok(get("/api/erp/users?search=" + tag + "&status=inactive")).get("data"), "fullName"));
        assertEquals(List.of("Alfa " + tag, "Bravo " + tag),
                texts(ok(get("/api/erp/users?search=" + tag + "&status=active&sort=fullName")).get("data"), "fullName"));
        assertError(call(get("/api/erp/users?status=deleted")), 400, "Status pengguna tidak dikenal.");

        // lastLoginAt: nulls selalu di akhir, baik asc maupun desc.
        assertEquals("Alfa " + tag, ok(get("/api/erp/users?search=" + tag + "&sort=-lastLoginAt")).get("data").get(0)
                .get("fullName").asText());
        assertEquals("Alfa " + tag, ok(get("/api/erp/users?search=" + tag + "&sort=lastLoginAt")).get("data").get(0)
                .get("fullName").asText());

        JsonNode page2 = ok(get("/api/erp/users?search=" + tag + "&sort=fullName&limit=2&page=2"));
        assertEquals(3, page2.get("total").asInt());
        assertEquals(List.of("Charlie " + tag), texts(page2.get("data"), "fullName"));
        assertEquals(200, ok(get("/api/erp/users?limit=500&search=" + tag)).get("limit").asInt());

        JsonNode summary = ok(get("/api/erp/users/summary?search=" + tag));
        assertEquals(3, summary.get("total").asInt());
        assertEquals(2, summary.get("active").asInt());
        assertEquals(1, summary.get("inactive").asInt());
        JsonNode viewers = ok(get("/api/erp/users/summary?search=" + tag + "&role=ERP_VIEWER"));
        assertEquals(2, viewers.get("total").asInt());
        assertEquals(1, viewers.get("inactive").asInt());
        assertError(call(get("/api/erp/users/summary?status=x")), 400, "Status pengguna tidak dikenal.");
    }

    @Test
    void update_toggle_and_deactivate_rules() throws Exception {
        String adminId = adminId();
        String tag = uniq("upd").toLowerCase();
        PlatformUser u = platformUser("Uji Ubah " + tag, null, "ubah." + tag + "@corp.test", null, true, "ERP_VIEWER");
        PlatformUser v = platformUser("Atasan Baru " + tag, null, "atasan." + tag + "@corp.test", null, true, "ERP_VIEWER");
        String uid = provision(u);
        String vid = provision(v);
        String url = "/api/erp/users/" + uid;

        assertError(call(put(url, Map.of("supervisorId", uid))), 400, "Atasan langsung tidak boleh diri sendiri.");
        assertError(call(put(url, Map.of("supervisorId", "tidak-ada"))), 400, "Atasan langsung tidak ditemukan.");
        assertError(call(put(url, Map.of("supervisorId", ""))), 400, "Too small: expected string to have >=1 characters");
        assertError(call(put(url, Map.of("language", "fr"))), 400, "Bahasa harus id atau en.");
        assertError(call(put(url, Map.of("isActive", "ya"))), 400, "Invalid input: expected boolean, received string");
        assertError(call(put(url, Map.of("fullName", "Hanya Ini"))), 400, "Minimal satu field harus diisi.");
        assertError(call(put("/api/erp/users/" + UUID.randomUUID(), Map.of("language", "en"))), 404, "User tidak ditemukan.");

        Map<String, Object> body = new HashMap<>();
        body.put("supervisorId", vid);
        body.put("language", "en");
        body.put("fullName", "Diabaikan");
        body.put("role", "SUPER_ADMIN");
        JsonNode row = ok(put(url, body));
        assertEquals(vid, row.get("supervisorId").asText());
        assertEquals("Atasan Baru " + tag, row.get("supervisor").get("fullName").asText());
        assertEquals("en", row.get("language").asText());
        assertEquals("Uji Ubah " + tag, row.get("fullName").asText());
        assertEquals("ERP_VIEWER", row.get("role").asText());

        Map<String, Object> clear = new HashMap<>();
        clear.put("supervisorId", null);
        clear.put("language", null);
        JsonNode cleared = ok(put(url, clear));
        assertTrue(cleared.get("supervisorId").isNull());
        assertTrue(cleared.get("supervisor").isNull());
        assertTrue(cleared.get("language").isNull());

        assertError(call(patch("/api/erp/users/" + adminId + "/toggle", Map.of())), 400, "Tidak dapat menonaktifkan akun sendiri.");
        assertError(call(delete("/api/erp/users/" + adminId)), 400, "Tidak dapat menonaktifkan akun sendiri.");
        assertError(call(put("/api/erp/users/" + adminId, Map.of("isActive", false))), 400,
                "Tidak dapat menonaktifkan akun sendiri.");
        assertError(call(patch("/api/erp/users/" + UUID.randomUUID() + "/toggle", Map.of())), 404, "User tidak ditemukan.");
        assertError(call(delete("/api/erp/users/" + UUID.randomUUID())), 404, "User tidak ditemukan.");

        // Aktor yang sudah ter-cache langsung terblokir setelah dinonaktifkan (cache di-evict).
        login(u.email(), "ERP_VIEWER");
        adminId();
        assertFalse(ok(patch(url + "/toggle", Map.of())).get("isActive").asBoolean());
        asUser(u.email(), "ERP_VIEWER");
        assertError(call(get("/api/erp/me")), 403, "Akun ERP Anda dinonaktifkan. Hubungi administrator.");
        adminId();
        assertTrue(ok(patch(url + "/toggle", Map.of())).get("isActive").asBoolean());
        login(u.email(), "ERP_VIEWER");

        adminId();
        assertTrue(ok(delete(url)).get("success").asBoolean());
        assertEquals(false, jdbc.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, uid));
        asUser(u.email(), "ERP_VIEWER");
        assertError(call(get("/api/erp/me")), 403, "Akun ERP Anda dinonaktifkan. Hubungi administrator.");
    }

    @Test
    void users_permission_and_own_language() throws Exception {
        String tag = uniq("lang").toLowerCase();
        String email = "viewer." + tag + "@corp.test";
        platformUser("Viewer " + tag, null, email, null, true, "ERP_VIEWER");
        login(email, "ERP_VIEWER");
        assertError(call(get("/api/erp/users")), 403, DENIED);
        assertError(call(get("/api/erp/users/summary")), 403, DENIED);
        assertError(call(get("/api/erp/users/platform-search?q=ab")), 403, DENIED);
        assertError(call(post("/api/erp/users", Map.of("email", email))), 403, DENIED);
        assertError(call(post("/api/erp/users/sync", Map.of())), 403, DENIED);
        assertError(call(patch("/api/erp/users/x/toggle", Map.of())), 403, DENIED);
        assertError(call(get("/api/erp/roles/assignable")), 403, DENIED);

        JsonNode en = ok(put("/api/erp/users/me/language", Map.of("language", "en")));
        assertEquals("en", en.get("language").asText());
        assertEquals("en", en.get("languagePreference").asText());
        assertEquals("en", ok(get("/api/erp/me")).get("user").get("languagePreference").asText());
        Map<String, Object> reset = new HashMap<>();
        reset.put("language", null);
        JsonNode def = ok(put("/api/erp/users/me/language", reset));
        String company = jdbc.queryForObject("SELECT default_language FROM company_settings WHERE slug = 'default'", String.class);
        assertEquals("en".equals(company) ? "en" : "id", def.get("language").asText());
        assertTrue(def.get("languagePreference").isNull());
        assertError(call(put("/api/erp/users/me/language", Map.of())), 400, "Bahasa harus id atau en.");
        assertError(call(put("/api/erp/users/me/language", Map.of("language", "jp"))), 400, "Bahasa harus id atau en.");
    }

    @Test
    void sync_refreshes_snapshots_and_deactivates_missing_users() throws Exception {
        adminId();
        String tag = uniq("sync").toLowerCase();
        PlatformUser a = platformUser("Sinkron A " + tag, "SA" + tag, "sa." + tag + "@corp.test", null, true, "ERP_VIEWER");
        PlatformUser b = platformUser("Sinkron B " + tag, null, "sb." + tag + "@corp.test", null, true, "ERP_VIEWER");
        PlatformUser d = platformUser("Sinkron D " + tag, null, "sd." + tag + "@corp.test", null, true, "ERP_VIEWER");
        PlatformUser x = platformUser("Atasan X " + tag, null, "sx." + tag + "@corp.test", null, true, "ERP_VIEWER");
        PlatformUser e = platformUser("Sinkron E " + tag, null, "se." + tag + "@corp.test", x.id(), true, "ERP_VIEWER");
        String ida = provision(a);
        String idb = provision(b);
        String idd = provision(d);
        String ide = provision(e);
        String idx = erpUserId(x.email());
        // Database dipakai bersama test lain: pengguna lain dianggap ada & aktif di platform.
        mirrorUnknownErpUsers();

        PlatformUser boss = platformUser("Bos A " + tag, null, "bos." + tag + "@corp.test", null, true, "ERP_ACCOUNTANT");
        changePlatform(a, "Sinkron A Baru " + tag, "SA2" + tag, "sa.baru." + tag + "@corp.test", boss.id(), true,
                "ERP_ACCOUNTANT", "LIMS_ANALYST");
        DIRECTORY.remove(b.id());
        changePlatform(d, d.name(), null, d.email(), null, false, "ERP_VIEWER");
        PlatformUser y = platformUser("Atasan Y " + tag, null, "sy." + tag + "@corp.test", null, true, "ERP_VIEWER");
        changePlatform(e, e.name(), null, e.email(), y.id(), true, "ERP_VIEWER");

        long before = jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
        JsonNode r = ok(post("/api/erp/users/sync", Map.of()));
        assertEquals(before, r.get("total").asLong());
        assertTrue(r.get("updated").asInt() >= 1, r.toString());
        assertTrue(r.get("deactivated").asInt() >= 2, r.toString());

        Map<String, Object> ra = jdbc.queryForMap(
                "SELECT full_name, npk, email, roles, role, supervisor_id, is_active, platform_user_id FROM users WHERE id = ?", ida);
        assertEquals("Sinkron A Baru " + tag, ra.get("full_name"));
        assertEquals("SA2" + tag, ra.get("npk"));
        assertEquals("sa.baru." + tag + "@corp.test", ra.get("email"));
        assertEquals("ERP_ACCOUNTANT,LIMS_ANALYST", ra.get("roles"));
        assertEquals("ERP_ACCOUNTANT", ra.get("role"));
        assertEquals(true, ra.get("is_active"));
        String bossId = erpUserId(boss.email());
        assertEquals(bossId, ra.get("supervisor_id"));
        assertEquals(a.id(), ((Number) ra.get("platform_user_id")).longValue());
        assertEquals(false, jdbc.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, idb));
        assertEquals(false, jdbc.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, idd));
        assertEquals(idx, jdbc.queryForObject("SELECT supervisor_id FROM users WHERE id = ?", String.class, ide));
        assertEquals(true, jdbc.queryForObject("SELECT is_active FROM users WHERE email = ?", Boolean.class, ADMIN_EMAIL));

        // Sinkron kedua: tidak ada lagi yang berubah untuk pengguna di atas; yang nonaktif tidak diaktifkan ulang.
        JsonNode r2 = ok(post("/api/erp/users/sync", Map.of()));
        assertEquals(0, r2.get("deactivated").asInt(), r2.toString());
        assertEquals(false, jdbc.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, idd));
    }

    @Test
    void platform_directory_failure_returns_503() throws Exception {
        adminId();
        when(identity.search(any(), anyInt())).thenThrow(new IllegalStateException("db_auth down"));
        when(identity.findById(any())).thenThrow(new IllegalStateException("db_auth down"));
        assertError(call(get("/api/erp/users/platform-search?q=abc")), 503, "Direktori pengguna platform tidak tersedia.");
        assertError(call(post("/api/erp/users", Map.of("platformUserId", 1L))), 503, "Direktori pengguna platform tidak tersedia.");
        assertError(call(post("/api/erp/users/sync", Map.of())), 503, "Direktori pengguna platform tidak tersedia.");
        assertNotNull(jdbc.queryForObject("SELECT id FROM users WHERE email = ?", String.class, ADMIN_EMAIL));
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new java.util.ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
