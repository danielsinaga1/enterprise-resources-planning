package id.enterpriseapps.erp.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inquiry: buat, status, konversi (Party otomatis untuk prospek) → penawaran → link. */
class InquiryFlowTest extends SalesTestSupport {

    private static final String BASE = "/api/erp/inquiries";

    private Map<String, Object> inquiryBody(String prospect) {
        Map<String, Object> item1 = new HashMap<>();
        item1.put("itemName", "Beras Medium");
        item1.put("quantity", 500);
        item1.put("unit", "Kg");
        item1.put("estimatedPrice", 12000);
        Map<String, Object> item2 = new HashMap<>();
        item2.put("itemName", "Dedak");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("date", today());
        b.put("channel", "WhatsApp");
        b.put("prospectName", prospect);
        b.put("prospectPhone", "081234567890");
        b.put("prospectEmail", "");
        b.put("itemsMode", "structured");
        b.put("items", List.of(item1, item2));
        b.put("followUpDate", daysFromToday(2));
        return b;
    }

    @Test
    void create_status_convert_and_link() throws Exception {
        adminUserId();
        String prospect = uniq("Prospek");

        Map<String, Object> empty = inquiryBody(prospect);
        empty.put("items", List.of());
        assertEquals("Inquiry harus berisi item (structured) atau permintaan (free-text).", call(post(BASE, empty)).error());
        Map<String, Object> badEmail = inquiryBody(prospect);
        badEmail.put("prospectEmail", "bukan-email");
        assertEquals("Invalid email address", call(post(BASE, badEmail)).error());

        Result created = call(post(BASE, inquiryBody(prospect)));
        assertEquals(201, created.status(), created.body().toString());
        JsonNode inq = created.data();
        String id = inq.get("id").asText();
        assertTrue(inq.get("inquiryNumber").asText().startsWith("INQ-"));
        assertEquals("Baru", inq.get("status").asText());
        assertEquals(2, inq.get("items").size());
        assertTrue(inq.get("party").isNull());
        assertTrue(inq.get("prospectEmail").isNull());

        JsonNode list = ok(get(BASE + "?search=" + prospect));
        assertEquals(1, list.get("total").asInt());
        assertEquals("erp.admin", list.get("data").get(0).get("user").get("fullName").asText());
        assertTrue(list.get("data").get(0).get("quotation").isNull());
        JsonNode summary = ok(get(BASE + "/summary?search=" + prospect));
        assertEquals(1, summary.get("newCount").asInt());
        assertEquals("Channel inquiry tidak dikenal.", call(get(BASE + "?channel=Fax")).error());

        assertEquals("Alasan hilang minimal 5 karakter.",
                call(patch(BASE + "/" + id + "/status", Map.of("status", "Hilang", "lossReason", "x"))).error());
        assertEquals("Gunakan endpoint konversi untuk menjadikan Penawaran.",
                call(patch(BASE + "/" + id + "/status", Map.of("status", "Dikonversi"))).error());
        assertEquals("Diproses", ok(patch(BASE + "/" + id + "/status", Map.of("status", "Diproses"))).get("status").asText());

        JsonNode payload = ok(get(BASE + "/" + id + "/convert-payload"));
        String partyId = payload.get("partyId").asText();
        assertEquals(prospect, payload.get("partyName").asText());
        assertEquals("Customer", jdbc.queryForObject("SELECT party_type FROM parties WHERE id = ?", String.class, partyId));
        assertEquals(2, payload.get("items").size());
        JsonNode second = payload.get("items").get(1);
        assertAmount("1", second.get("quantity"));
        assertEquals("Kg", second.get("unit").asText());
        assertAmount("0", second.get("rate"));

        Map<String, Object> line = serviceLine("Beras Medium", 500, 12000);
        Map<String, Object> qBody = new LinkedHashMap<>();
        qBody.put("date", today());
        qBody.put("validUntil", daysFromToday(7));
        qBody.put("partyId", partyId);
        qBody.put("items", List.of(line));
        JsonNode quotation = ok(post("/api/erp/quotations", qBody));

        assertEquals("quotationId wajib diisi.", call(post(BASE + "/" + id + "/link", Map.of())).error());
        JsonNode linked = ok(post(BASE + "/" + id + "/link", Map.of("quotationId", quotation.get("id").asText())));
        assertEquals("Dikonversi", linked.get("status").asText());
        assertEquals(quotation.get("quotationNumber").asText(), linked.get("quotation").get("quotationNumber").asText());

        JsonNode detail = ok(get(BASE + "/" + id));
        assertEquals(prospect, detail.get("party").get("name").asText());
        assertEquals(quotation.get("id").asText(), detail.get("quotation").get("id").asText());

        assertEquals("Inquiry yang sudah dikonversi tidak dapat diedit.", call(put(BASE + "/" + id, inquiryBody(prospect))).error());
        assertEquals("Inquiry yang sudah dikonversi tidak dapat dihapus.", call(delete(BASE + "/" + id)).error());
        assertEquals("Inquiry sudah dikonversi, status terkunci.",
                call(patch(BASE + "/" + id + "/status", Map.of("status", "Baru"))).error());
        assertEquals("Inquiry sudah dikonversi.", call(get(BASE + "/" + id + "/convert-payload")).error());

        JsonNode stats = ok(get(BASE + "/stats"));
        assertTrue(stats.get("counts").get("Dikonversi").asInt() >= 1);
    }

    @Test
    void update_and_delete_inquiry() throws Exception {
        adminUserId();
        String prospect = uniq("Prospek-U");
        String id = ok(post(BASE, inquiryBody(prospect))).get("id").asText();
        Map<String, Object> edit = inquiryBody(prospect + " Baru");
        edit.put("itemsMode", "freetext");
        edit.put("items", List.of());
        edit.put("itemsText", "Butuh 1 ton beras");
        JsonNode updated = ok(put(BASE + "/" + id, edit));
        assertEquals(prospect + " Baru", updated.get("prospectName").asText());
        assertEquals(0, updated.get("items").size());
        assertEquals("freetext", updated.get("itemsMode").asText());

        JsonNode payload = ok(get(BASE + "/" + id + "/convert-payload"));
        assertEquals(0, payload.get("items").size());
        assertEquals("Butuh 1 ton beras", payload.get("itemsText").asText());

        assertTrue(ok(delete(BASE + "/" + id)).get("success").asBoolean());
        Result gone = call(get(BASE + "/" + id));
        assertEquals(404, gone.status());
        assertEquals("Inquiry tidak ditemukan.", gone.error());
    }
}
