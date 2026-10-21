package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpDates;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Jejak audit yang ditulis kernel untuk request tulis: daftar + filter + paging + urutan, ringkasan, opsi filter, detail. */
class AuditLogFlowTest extends AdminTestSupport {

    @Test
    void writes_are_listed_filtered_and_detailed() throws Exception {
        String adminId = loginAdmin();
        String u = uid();
        JsonNode rule = ok(post("/api/erp/approvals/rules", map("documentType", "Audit" + u, "minAmount", 10, "requiredRole", "Admin")));
        String ruleId = rule.get("id").asText();
        ok(put("/api/erp/approvals/rules/" + ruleId, map("minAmount", 20, "isActive", false)));

        JsonNode list = ok(get("/api/erp/audit-logs?entityType=APPROVALS&search=" + ruleId));
        assertEquals(2, list.get("total").asInt());
        assertEquals(1, list.get("page").asInt());
        assertEquals(50, list.get("limit").asInt());
        JsonNode newest = list.get("data").get(0);
        assertEquals("UPDATE", newest.get("action").asText());
        assertEquals("approvals", newest.get("entityType").asText());
        assertEquals(ruleId, newest.get("entityId").asText());
        assertEquals(adminId, newest.get("userId").asText());
        assertEquals(ADMIN_EMAIL, newest.get("user").get("email").asText());
        assertTrue(newest.get("user").has("fullName"));
        assertEquals(2, newest.get("user").size());
        assertFalse(newest.get("newValues").get("isActive").asBoolean());
        assertEquals("CREATE", list.get("data").get(1).get("action").asText());
        assertEquals("Audit" + u, list.get("data").get(1).get("newValues").get("documentType").asText());

        JsonNode paged = ok(get("/api/erp/audit-logs?search=" + ruleId + "&limit=1&page=2"));
        assertEquals(2, paged.get("total").asInt());
        assertEquals(1, paged.get("data").size());
        assertEquals("CREATE", paged.get("data").get(0).get("action").asText());
        assertEquals("CREATE", ok(get("/api/erp/audit-logs?search=" + ruleId + "&sort=action")).get("data").get(0).get("action").asText());
        assertEquals("UPDATE", ok(get("/api/erp/audit-logs?search=" + ruleId + "&sort=-action")).get("data").get(0).get("action").asText());
        assertEquals(1, ok(get("/api/erp/audit-logs?search=" + ruleId + "&action=CREATE")).get("total").asInt());
        assertEquals(2, ok(get("/api/erp/audit-logs?search=" + ruleId + "&userId=" + adminId)).get("total").asInt());
        assertEquals(0, ok(get("/api/erp/audit-logs?search=" + ruleId + "&userId=" + UUID.randomUUID())).get("total").asInt());
        // Hari kalender (WIB) dari createdAt yang terbaca lewat API — filter tanggal memakai nilai yang sama.
        java.time.LocalDate day = ErpDates.businessDate(java.time.Instant.parse(newest.get("createdAt").asText()));
        String today = day.toString();
        String yesterday = day.minusDays(1).toString();
        assertEquals(2, ok(get("/api/erp/audit-logs?search=" + ruleId + "&startDate=" + today + "&endDate=" + today)).get("total").asInt());
        assertEquals(0, ok(get("/api/erp/audit-logs?search=" + ruleId + "&endDate=" + yesterday)).get("total").asInt());
        assertEquals("Format tanggal harus YYYY-MM-DD.", call(get("/api/erp/audit-logs?startDate=20-10-2026")).error());

        JsonNode summary = ok(get("/api/erp/audit-logs/summary?search=" + ruleId));
        assertEquals(2, summary.get("total").asInt());
        assertEquals(1, summary.get("byAction").get("CREATE").asInt());
        assertEquals(1, summary.get("byAction").get("UPDATE").asInt());

        JsonNode facets = ok(get("/api/erp/audit-logs/facets"));
        assertTrue(facets.get("actions").toString().contains("\"CREATE\""));
        assertTrue(facets.get("entityTypes").toString().contains("\"approvals\""));
        boolean adminListed = false;
        for (JsonNode user : facets.get("users")) {
            adminListed |= adminId.equals(user.get("id").asText());
        }
        assertTrue(adminListed);

        JsonNode detail = ok(get("/api/erp/audit-logs/" + newest.get("id").asText()));
        assertEquals("UPDATE", detail.get("action").asText());
        assertEquals(ADMIN_EMAIL, detail.get("user").get("email").asText());
        assertTrue(detail.has("oldValues"));
        assertEquals(20, detail.get("newValues").get("minAmount").asInt());
        Result missing = call(get("/api/erp/audit-logs/" + UUID.randomUUID()));
        assertEquals(404, missing.status());
        assertEquals("Catatan audit tidak ditemukan.", missing.error());

        login("noaudit." + u + "@test.local", "ERP_ACCOUNTANT");
        assertEquals(403, call(get("/api/erp/audit-logs")).status());
    }
}
