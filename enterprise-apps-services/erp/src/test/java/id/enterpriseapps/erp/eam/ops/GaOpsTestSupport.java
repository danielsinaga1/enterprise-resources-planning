package id.enterpriseapps.erp.eam.ops;

import id.enterpriseapps.erp.core.security.ErpPermissionService;
import id.enterpriseapps.erp.support.ErpIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fixture bersama uji GA ops (data unik per test; DB tidak di-rollback antar test). */
abstract class GaOpsTestSupport extends ErpIntegrationTest {

    protected static final String INV = "/api/erp/ga/inventory";
    protected static final String MT = "/api/erp/ga/maintenance";
    protected static final String MR = "/api/erp/ga/material-requests";
    protected static final String DASH = "/api/erp/dashboard/maintenance";

    @Autowired
    protected ErpPermissionService permissions;

    protected static String tag() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    protected static String email(String who) {
        return who + "." + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    protected static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Login lalu pastikan snapshot users ada; kembalikan id user ERP. */
    protected String provision(String email, String... roles) throws Exception {
        asUser(email, roles);
        ok(get("/api/erp/me"));
        return jdbc.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?)", String.class, email);
    }

    protected String adminId() throws Exception {
        return provision("erp.admin@test.local", "SUPER_ADMIN");
    }

    /** Role uji non-super-admin dengan izin modul tertentu ("view"/"edit"). */
    protected String customRole(Map<String, String> modules) {
        String key = "TEST_GAOPS_" + tag();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO roles (id, key, name, is_system, is_super_admin, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, false, false, true, now(), now())", id, key, key);
        modules.forEach((module, action) -> jdbc.update(
                "INSERT INTO role_permissions (id, role_id, module, can_view, can_edit) VALUES (?, ?, ?, true, ?)",
                UUID.randomUUID().toString(), id, module, "edit".equals(action)));
        permissions.invalidate();
        return key;
    }

    protected void expectError(int status, String message, MockHttpServletRequestBuilder req) throws Exception {
        Result r = call(req);
        assertEquals(status, r.status(), String.valueOf(r.body()));
        if (message != null) {
            assertEquals(message, r.error());
        }
    }

    // ─────────────── seed langsung ke tabel ga_* ───────────────

    protected String branch(String tag) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_branches (id, code, name, is_active, created_at) VALUES (?, ?, ?, true, now())",
                id, "BR-" + tag, "Cabang " + tag);
        return id;
    }

    protected String asset(String tag, String categoryCode) {
        String category = jdbc.queryForObject("SELECT id FROM ga_asset_categories WHERE code = ?", String.class, categoryCode);
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_assets (id, code, name, category_id, branch_id, status, condition, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'GOOD', now(), now())", id, "AST-" + tag, "Aset " + tag, category, branch(tag));
        return id;
    }

    protected String location(String tag, String suffix) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_locations (id, code, name, branch_id) VALUES (?, ?, ?, ?)",
                id, "LOC-" + tag + "-" + suffix, "Gudang " + tag + " " + suffix, branch(tag + suffix));
        return id;
    }

    protected String invCategory(String tag) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_inventory_categories (id, code, name, type) VALUES (?, ?, ?, 'SPAREPART')",
                id, "IC-" + tag, "Sparepart " + tag);
        return id;
    }

    protected BigDecimal stockAt(String itemId, String locationId) {
        return jdbc.queryForList("SELECT quantity FROM ga_stock_by_location WHERE item_id = ? AND location_id = ?",
                        BigDecimal.class, itemId, locationId).stream().findFirst().orElse(BigDecimal.ZERO);
    }

    protected static void assertDec(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(String.valueOf(actual))),
                "expected " + expected + " but was " + actual);
    }
}
