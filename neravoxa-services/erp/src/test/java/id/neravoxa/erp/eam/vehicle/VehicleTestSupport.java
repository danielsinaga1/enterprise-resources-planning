package id.neravoxa.erp.eam.vehicle;

import id.neravoxa.erp.support.ErpIntegrationTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fixture bersama uji kendaraan (data unik per test; DB tidak di-rollback antar test). */
abstract class VehicleTestSupport extends ErpIntegrationTest {

    protected static final String BASE = "/api/erp/ga/vehicle";

    protected static String tag() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    protected static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
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
        ok(get(BASE + "/inspection-checkpoints"));
        ok(get(BASE + "/service-requests?mine=true"));
        return jdbc.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?)", String.class, email);
    }

    protected String categoryId(String code) {
        return jdbc.queryForObject("SELECT id FROM ga_asset_categories WHERE code = ?", String.class, code);
    }

    protected String newCategory(String tag) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_asset_categories (id, code, name) VALUES (?, ?, ?)", id, "CAT-" + tag, "Kategori uji " + tag);
        return id;
    }

    /** Aset GA baru (kategori tertentu) + cabang unik; kembalikan id aset. */
    protected String newAsset(String code, String categoryId) {
        String branch = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_branches (id, code, name, is_active, created_at) VALUES (?, ?, ?, true, now())",
                branch, "BR-" + code, "Cabang " + code);
        String asset = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_assets (id, code, name, category_id, branch_id, status, condition, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'GOOD', now(), now())", asset, code, "Mobil " + code, categoryId, branch);
        return asset;
    }

    protected String newVehicle(String code) {
        return newAsset(code, categoryId("KENDARAAN"));
    }

    protected void expectError(int status, String message, MockHttpServletRequestBuilder req) throws Exception {
        Result r = call(req);
        assertEquals(status, r.status(), () -> "body: " + r.body());
        assertEquals(message, r.error(), () -> "body: " + r.body());
    }

    protected void expectStatus(int status, MockHttpServletRequestBuilder req) throws Exception {
        Result r = call(req);
        assertEquals(status, r.status(), () -> "body: " + r.body());
    }

    /** Respons mentah (unduhan berkas, bukan JSON). */
    protected MvcResult raw(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(request -> {
            request.setUserPrincipal(SecurityContextHolder.getContext().getAuthentication());
            return request;
        })).andReturn();
    }

    protected BigDecimal journalSum(String journalId, String column) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(" + column + "), 0) FROM journal_entry_accounts WHERE journal_entry_id = ?",
                BigDecimal.class, journalId);
    }

    protected BigDecimal balance(String accountNumber) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE account_number = ?", BigDecimal.class, accountNumber);
    }
}
