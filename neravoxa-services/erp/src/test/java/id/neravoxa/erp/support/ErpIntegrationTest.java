package id.neravoxa.erp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Arrays;

/**
 * Basis uji integrasi endpoint ERP (MockMvc + PostgreSQL embedded berisi skema & seed).
 *
 * <pre>{@code
 * class SalesInvoiceFlowTest extends ErpIntegrationTest {
 *   @Test void submit_posts_balanced_journal() throws Exception {
 *     asUser("staff@kpi.co.id", "ERP_ACCOUNTANT");
 *     JsonNode inv = ok(post("/api/erp/sales/invoices", Map.of(...)));   // data respons (sudah dibuka)
 *     assertEquals("Submitted", inv.get("status").asText());
 *   }
 * }
 * }</pre>
 * Respons ERP dibungkus {@code Response}; {@link #ok} / {@link #call} mengembalikan isi {@code data}.
 * Data antar test TIDAK di-rollback (satu database per JVM) — pakai nama/kode unik per test.
 */
@SpringBootTest(classes = ErpTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
public abstract class ErpIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    @Qualifier("erpJdbcTemplate")
    protected JdbcTemplate jdbc;

    protected final ObjectMapper json = new ObjectMapper();

    /** Login sebagai pengguna platform (email = subject JWT) dengan kode role tertentu. */
    protected void asUser(String email, String... roleCodes) {
        var auth = new UsernamePasswordAuthenticationToken(email, null,
                Arrays.stream(roleCodes).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** Super-admin ERP. */
    protected void asAdmin() {
        asUser("erp.admin@test.local", "SUPER_ADMIN");
    }

    protected MockHttpServletRequestBuilder get(String url) {
        return MockMvcRequestBuilders.get(url);
    }

    protected MockHttpServletRequestBuilder post(String url, Object body) throws Exception {
        return MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    protected MockHttpServletRequestBuilder put(String url, Object body) throws Exception {
        return MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    protected MockHttpServletRequestBuilder patch(String url, Object body) throws Exception {
        return MockMvcRequestBuilders.patch(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    protected MockHttpServletRequestBuilder delete(String url) {
        return MockMvcRequestBuilders.delete(url);
    }

    /** Hasil mentah: status + seluruh body Response. */
    protected record Result(int status, JsonNode body) {
        /** Isi {@code data} (body asli ENTARIS). */
        public JsonNode data() {
            return body.get("data");
        }

        /** Pesan error (errorMessage). */
        public String error() {
            return body.hasNonNull("errorMessage") ? body.get("errorMessage").asText() : null;
        }
    }

    protected Result call(MockHttpServletRequestBuilder req) throws Exception {
        SecurityContextHolder.getContext(); // pastikan konteks thread test dipakai
        MvcResult r = mvc.perform(req.with(request -> {
            request.setUserPrincipal(SecurityContextHolder.getContext().getAuthentication());
            return request;
        })).andReturn();
        String body = r.getResponse().getContentAsString();
        return new Result(r.getResponse().getStatus(), body.isEmpty() ? json.nullNode() : json.readTree(body));
    }

    /** Panggil dan pastikan 2xx; kembalikan {@code data}. */
    protected JsonNode ok(MockHttpServletRequestBuilder req) throws Exception {
        Result r = call(req);
        if (r.status() < 200 || r.status() >= 300) {
            throw new AssertionError("HTTP " + r.status() + ": " + r.body());
        }
        return r.data();
    }
}
