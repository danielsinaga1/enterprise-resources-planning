package id.neravoxa.erp.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rute publik (branding, form inquiry + honeypot + rate limit) dan master layanan. */
class PublicAndServiceItemTest extends SalesTestSupport {

    private Map<String, Object> publicBody(String name) {
        Map<String, Object> b = new HashMap<>();
        b.put("prospectName", name);
        b.put("prospectPhone", "0812345678");
        b.put("prospectEmail", "calon@contoh.co.id");
        b.put("itemsText", "Mohon penawaran beras 2 ton");
        b.put("channel", "WhatsApp");
        return b;
    }

    private MvcResult postPublic(String ip, Object body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/erp/public/inquiry")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn();
    }

    private static String error(MvcResult r) throws Exception {
        JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(r.getResponse().getContentAsString());
        return body.path("errorMessage").asText(null);
    }

    @Test
    void public_branding_and_inquiry_with_honeypot_and_rate_limit() throws Exception {
        String adminId = adminUserId();
        SecurityContextHolder.clearContext();

        JsonNode branding = ok(get("/api/erp/public/branding"));
        assertTrue(branding.has("companyName") && branding.has("logoUrl") && branding.has("logoUrlDark")
                && branding.has("phone") && branding.has("email"));

        String ip = "10.77." + (int) (Math.random() * 200) + "." + (int) (Math.random() * 200);
        MvcResult shortName = postPublic(ip, publicBody("A"));
        assertEquals(400, shortName.getResponse().getStatus());
        assertEquals("Nama minimal 2 karakter.", error(shortName));

        Map<String, Object> bot = publicBody("Bot Spam");
        bot.put("website", "http://spam");
        MvcResult honeypot = postPublic(ip, bot);
        assertEquals(400, honeypot.getResponse().getStatus());
        assertEquals("Bot detected.", error(honeypot));

        String name = uniq("Calon Pembeli");
        MvcResult okResult = postPublic(ip, publicBody(name));
        assertEquals(200, okResult.getResponse().getStatus(), okResult.getResponse().getContentAsString());
        assertEquals("5", okResult.getResponse().getHeader("RateLimit-Limit"));
        assertEquals("2", okResult.getResponse().getHeader("RateLimit-Remaining"));
        JsonNode data = json.readTree(okResult.getResponse().getContentAsString()).get("data");
        assertTrue(data.get("success").asBoolean());
        String number = data.get("inquiryNumber").asText();
        assertTrue(number.startsWith("INQ-"));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, channel, party_id, items_mode, notes, created_by FROM inquiries WHERE inquiry_number = ?", number);
        assertEquals("Baru", row.get("status"));
        assertEquals("WhatsApp", row.get("channel"));
        assertEquals(null, row.get("party_id"));
        assertEquals("freetext", row.get("items_mode"));
        assertEquals("Submitted via public form from IP " + ip, row.get("notes"));
        assertEquals(adminId, row.get("created_by"));

        postPublic(ip, publicBody(uniq("Calon")));
        postPublic(ip, publicBody(uniq("Calon")));
        MvcResult limited = postPublic(ip, publicBody(uniq("Calon")));
        assertEquals(429, limited.getResponse().getStatus());
        assertEquals("Terlalu banyak permintaan dari IP ini. Coba lagi dalam 1 jam.", error(limited));
        assertTrue(limited.getResponse().getHeader("Retry-After") != null);

        // IP lain tidak terpengaruh.
        assertEquals(200, postPublic(ip + "1", publicBody(uniq("Calon"))).getResponse().getStatus());
    }

    @Test
    void service_items_crud_list_modes_and_errors() throws Exception {
        adminUserId();
        String code = uniq("SVC");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", "Jasa Giling " + code);
        body.put("defaultRate", 250);
        body.put("accountId", accountId("4.2"));
        Result created = call(post("/api/erp/service-items", body));
        assertEquals(201, created.status(), created.body().toString());
        JsonNode svc = created.data();
        assertEquals("Jasa", svc.get("unit").asText());
        assertEquals("4.2", svc.get("account").get("accountNumber").asText());
        assertTrue(svc.get("isActive").asBoolean());
        String id = svc.get("id").asText();

        Result dup = call(post("/api/erp/service-items", body));
        assertEquals(400, dup.status());
        assertEquals("Data duplikat terdeteksi. Silakan coba lagi.", dup.error());
        Map<String, Object> noAccount = new LinkedHashMap<>(body);
        noAccount.put("code", uniq("SVC"));
        noAccount.remove("accountId");
        assertEquals("Akun pendapatan wajib dipilih.", call(post("/api/erp/service-items", noAccount)).error());
        noAccount.put("accountId", UUID.randomUUID().toString());
        Result badFk = call(post("/api/erp/service-items", noAccount));
        assertEquals(400, badFk.status());
        assertEquals("Referensi data tidak valid. Periksa data yang dipilih.", badFk.error());

        JsonNode patched = ok(patch("/api/erp/service-items/" + id, Map.of("name", "Jasa Giling Baru", "defaultRate", 300)));
        assertEquals("Jasa Giling Baru", patched.get("name").asText());
        assertAmount("300", patched.get("defaultRate"));
        assertEquals("Jasa", patched.get("unit").asText());
        Result missing = call(patch("/api/erp/service-items/" + UUID.randomUUID(), Map.of("name", "X")));
        assertEquals("Data terkait tidak ditemukan. Periksa referensi yang dipilih.", missing.error());

        JsonNode legacy = ok(get("/api/erp/service-items?isActive=true&search=" + code));
        assertEquals(1, legacy.get("data").size());
        assertTrue(!legacy.has("total"));
        JsonNode paged = ok(get("/api/erp/service-items?page=1&limit=5&search=" + code + "&sort=-defaultRate"));
        assertEquals(1, paged.get("total").asInt());
        assertEquals(5, paged.get("limit").asInt());
        assertEquals("Filter isActive harus true atau false.", call(get("/api/erp/service-items?page=1&isActive=ya")).error());

        // Soft delete butuh doc.cancel; GET terbuka untuk pengguna tanpa izin edit.
        asUser(uniq("viewer") + "@test.local", "ERP_VIEWER");
        assertEquals(200, call(get("/api/erp/service-items")).status());
        assertEquals(403, call(delete("/api/erp/service-items/" + id)).status());
        asAdmin();
        assertTrue(ok(delete("/api/erp/service-items/" + id)).get("success").asBoolean());
        assertEquals(0, ok(get("/api/erp/service-items?isActive=true&search=" + code)).get("data").size());
    }
}
