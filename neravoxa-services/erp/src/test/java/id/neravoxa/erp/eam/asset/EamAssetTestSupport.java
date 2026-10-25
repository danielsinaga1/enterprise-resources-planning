package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.support.ErpIntegrationTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fixture bersama uji EAM aset & aset tetap (data unik per test; DB tidak di-rollback antar test). */
abstract class EamAssetTestSupport extends ErpIntegrationTest {

    protected static final String ASSETS = "/api/erp/ga/assets";
    protected static final String MASTER = "/api/erp/ga/master";
    protected static final String DOCS = "/api/erp/ga/documents";
    protected static final String FA = "/api/erp/fixed-assets";

    protected static final byte[] PDF = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);
    protected static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 0x49, 0x48};

    protected static String uniq(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    protected static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    protected void expectError(int status, String message, MockHttpServletRequestBuilder req) throws Exception {
        Result r = call(req);
        assertEquals(status, r.status(), String.valueOf(r.body()));
        if (message != null) {
            assertEquals(message, r.error());
        }
    }

    /** Respons mentah (unduhan berkas) dengan principal pengguna saat ini. */
    protected MvcResult raw(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(request -> {
            request.setUserPrincipal(SecurityContextHolder.getContext().getAuthentication());
            return request;
        })).andReturn();
    }

    protected String categoryId(String code) {
        return jdbc.queryForObject("SELECT id FROM ga_asset_categories WHERE code = ?", String.class, code);
    }

    protected String accountId(String number) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", String.class, number);
    }

    protected BigDecimal balance(String number) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE account_number = ?", BigDecimal.class, number);
    }

    /** Cabang baru dengan kode unik; kembalikan baris JSON. */
    protected JsonNode createBranch(String code) throws Exception {
        return ok(post(MASTER + "/branches", map("code", code, "name", "Cabang " + code)));
    }

    /** Aset baru (kode auto); kembalikan {id, code}. */
    protected JsonNode createAsset(String categoryCode, String branchId, String name) throws Exception {
        return ok(post(ASSETS, map("name", name, "categoryId", categoryId(categoryCode), "branchId", branchId)));
    }
}
