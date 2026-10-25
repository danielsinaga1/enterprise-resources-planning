package id.neravoxa.erp.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Siklus penawaran: Draft → Sent → Accepted → konversi ke invoice (GL) → link; reject/cancel/delete; sweeper. */
class QuotationFlowTest extends SalesTestSupport {

    private static final String BASE = "/api/erp/quotations";

    @Autowired
    ErpQuotationSweeperJob sweeperJob;

    private Map<String, Object> quotationBody(String partyId, String validUntil, Object qty) {
        Map<String, Object> line = serviceLine("Jasa Pengeringan", qty, 1_000_000);
        line.put("discount", 10);
        line.put("taxPct", 11);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("date", today());
        b.put("validUntil", validUntil);
        b.put("partyId", partyId);
        b.put("items", List.of(line));
        b.put("potongan", 8000);
        b.put("terms", "Net 30");
        return b;
    }

    @Test
    void full_lifecycle_converts_to_invoice_and_links() throws Exception {
        adminUserId();
        String partyName = uniq("CUST-QO");
        String partyId = createCustomer(partyName);

        Result bad = call(post(BASE, quotationBody(partyId, daysFromToday(-1), 2)));
        assertEquals(400, bad.status());
        assertEquals("Tanggal berlaku-sampai harus sama dengan atau setelah tanggal penawaran.", bad.error());

        Result created = call(post(BASE, quotationBody(partyId, daysFromToday(10), 2)));
        assertEquals(201, created.status(), created.body().toString());
        JsonNode q = created.data();
        String id = q.get("id").asText();
        assertTrue(q.get("quotationNumber").asText().startsWith("QO-"));
        assertEquals("Draft", q.get("status").asText());
        // 2 × 1.000.000 − 10% = 1.800.000; PPN 11% = 198.000; potongan 8.000 → 1.990.000.
        assertAmount("1800000", q.get("subtotal"));
        assertAmount("1990000", q.get("grandTotal"));
        assertEquals(partyName, q.get("customer").get("name").asText());
        assertEquals(1, q.get("items").size());

        JsonNode updated = ok(put(BASE + "/" + id, quotationBody(partyId, daysFromToday(10), 3)));
        assertAmount("2700000", updated.get("subtotal"));
        assertEquals(1, updated.get("items").size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM quotation_items WHERE quotation_id = ?", Integer.class, id));

        Result acceptDraft = call(patch(BASE + "/" + id + "/accept", Map.of()));
        assertEquals("Hanya status Sent yang bisa di-Accept. Status saat ini: Draft.", acceptDraft.error());

        assertEquals("Sent", ok(patch(BASE + "/" + id + "/send", Map.of())).get("status").asText());
        Result editSent = call(put(BASE + "/" + id, quotationBody(partyId, daysFromToday(10), 1)));
        assertEquals("Hanya penawaran berstatus Draft yang dapat diedit. Status saat ini: Sent.", editSent.error());
        Result deleteSent = call(delete(BASE + "/" + id));
        assertEquals("Hanya penawaran Draft yang bisa dihapus. Gunakan tombol Cancel untuk yang sudah diterbitkan.", deleteSent.error());

        JsonNode accepted = ok(patch(BASE + "/" + id + "/accept", Map.of()));
        assertEquals("Accepted", accepted.get("status").asText());
        assertTrue(accepted.get("acceptedAt").isTextual());

        // Konversi seperti QuotationDetailDrawer: payload → POST invoice → link.
        JsonNode payload = ok(get(BASE + "/" + id + "/convert-payload"));
        assertEquals(partyName, payload.get("partyName").asText());
        assertAmount("8000", payload.get("potongan"));
        JsonNode pItem = payload.get("items").get(0);
        assertAmount("3", pItem.get("quantity"));
        List<Map<String, Object>> invItems = new ArrayList<>();
        for (JsonNode it : payload.get("items")) {
            invItems.add(json.convertValue(it, Map.class));
        }
        Map<String, Object> invBody = invoiceBody(payload.get("partyId").asText(), invItems);
        invBody.put("potongan", payload.get("potongan").decimalValue());
        invBody.put("biayaLain", payload.get("biayaLain").decimalValue());
        invBody.put("terms", payload.get("terms").asText());
        JsonNode invoice = ok(post("/api/erp/sales/invoices", invBody));
        // 2.700.000 + PPN 297.000 − 8.000 = 2.989.000.
        assertAmount("2989000", invoice.get("grandTotal"));
        assertBalancedJournal("JV-" + invoice.get("invoiceNumber").asText(), "Submitted");

        Result noId = call(post(BASE + "/" + id + "/link", Map.of()));
        assertEquals("salesInvoiceId wajib diisi.", noId.error());
        JsonNode linked = ok(post(BASE + "/" + id + "/link", Map.of("salesInvoiceId", invoice.get("id").asText())));
        assertEquals(invoice.get("invoiceNumber").asText(), linked.get("salesInvoice").get("invoiceNumber").asText());
        assertTrue(linked.get("convertedAt").isTextual());

        JsonNode detail = ok(get(BASE + "/" + id));
        assertEquals(invoice.get("id").asText(), detail.get("salesInvoice").get("id").asText());
        assertEquals(0, detail.get("salesOrders").size());
        assertEquals("erp.admin", detail.get("user").get("fullName").asText());
        assertTrue(detail.get("items").get(0).has("serviceItem"));

        Result relink = call(post(BASE + "/" + id + "/link", Map.of("salesInvoiceId", invoice.get("id").asText())));
        assertEquals("Penawaran sudah ter-link ke invoice lain.", relink.error());
        Result convertAgain = call(get(BASE + "/" + id + "/convert-payload"));
        assertEquals("Penawaran sudah dikonversi.", convertAgain.error());
        Result cancelConverted = call(patch(BASE + "/" + id + "/cancel", Map.of()));
        assertEquals("Penawaran sudah dikonversi ke Sales Invoice. Batalkan invoice-nya terlebih dulu.", cancelConverted.error());

        JsonNode list = ok(get(BASE + "?search=" + q.get("quotationNumber").asText()));
        assertEquals(1, list.get("total").asInt());
        assertEquals(invoice.get("invoiceNumber").asText(), list.get("data").get(0).get("salesInvoice").get("invoiceNumber").asText());
        assertEquals(partyName, list.get("data").get(0).get("customer").get("name").asText());
        assertTrue(!list.get("data").get(0).get("customer").has("outstandingAmount"), "customer = select parsial");

        JsonNode summary = ok(get(BASE + "/summary?partyId=" + partyId));
        assertEquals(1, summary.get("acceptedCount").asInt());
        assertEquals(1, summary.get("issuedCount").asInt());
        assertAmount("1", summary.get("conversionRate"));
        assertEquals(0, summary.get("awaitingConversionCount").asInt());

        JsonNode stats = ok(get(BASE + "/stats?from=" + today() + "&to=" + today()));
        assertTrue(stats.get("counts").get("Accepted").asInt() >= 1);
        assertTrue(stats.has("conversionRate"));
    }

    @Test
    void reject_cancel_and_delete_rules() throws Exception {
        adminUserId();
        String partyId = createCustomer(uniq("CUST-QR"));
        String id = ok(post(BASE, quotationBody(partyId, daysFromToday(5), 1))).get("id").asText();
        ok(patch(BASE + "/" + id + "/send", Map.of()));
        Result shortReason = call(patch(BASE + "/" + id + "/reject", Map.of("reason", "x")));
        assertEquals("Alasan minimal 5 karakter.", shortReason.error());
        JsonNode rejected = ok(patch(BASE + "/" + id + "/reject", Map.of("reason", "Harga terlalu tinggi")));
        assertEquals("Rejected", rejected.get("status").asText());
        assertEquals("Harga terlalu tinggi", rejected.get("rejectReason").asText());

        asUser(uniq("acc") + "@test.local", "ERP_ACCOUNTANT");
        assertEquals(403, call(patch(BASE + "/" + id + "/cancel", Map.of())).status());
        asAdmin();
        assertEquals("Cancelled", ok(patch(BASE + "/" + id + "/cancel", Map.of())).get("status").asText());
        assertEquals("Penawaran sudah dibatalkan.", call(patch(BASE + "/" + id + "/cancel", Map.of())).error());

        String draftId = ok(post(BASE, quotationBody(partyId, daysFromToday(5), 1))).get("id").asText();
        assertTrue(ok(delete(BASE + "/" + draftId)).get("success").asBoolean());
        Result gone = call(get(BASE + "/" + draftId));
        assertEquals(404, gone.status());
        assertEquals("Penawaran tidak ditemukan.", gone.error());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM quotation_items WHERE quotation_id = ?", Integer.class, draftId));

        Result badStatus = call(get(BASE + "?status=Terkirim"));
        assertEquals("Status penawaran tidak dikenal.", badStatus.error());
    }

    @Test
    void sweeper_expires_overdue_and_notifies_expiring_once() throws Exception {
        String userId = adminUserId();
        String partyId = createCustomer(uniq("CUST-SW"));
        String overdue = ok(post(BASE, quotationBody(partyId, daysFromToday(10), 1))).get("id").asText();
        String expiring = ok(post(BASE, quotationBody(partyId, daysFromToday(10), 1))).get("id").asText();
        ok(patch(BASE + "/" + overdue + "/send", Map.of()));
        ok(patch(BASE + "/" + expiring + "/send", Map.of()));
        jdbc.update("UPDATE quotations SET valid_until = now() - interval '1 hour' WHERE id = ?", overdue);
        jdbc.update("UPDATE quotations SET valid_until = now() + interval '1 day' WHERE id = ?", expiring);

        sweeperJob.scheduled();

        Map<String, Object> o = jdbc.queryForMap("SELECT status, expired_at FROM quotations WHERE id = ?", overdue);
        assertEquals("Expired", o.get("status"));
        assertTrue(o.get("expired_at") != null);
        Map<String, Object> e = jdbc.queryForMap("SELECT status, expiry_notified_at FROM quotations WHERE id = ?", expiring);
        assertEquals("Sent", e.get("status"));
        assertTrue(e.get("expiry_notified_at") != null);
        String countSql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'quotation_expiring' "
                + "AND metadata->>'quotationId' = ?";
        assertEquals(1, jdbc.queryForObject(countSql, Integer.class, userId, expiring));
        String message = jdbc.queryForObject("SELECT message FROM notifications WHERE metadata->>'quotationId' = ?", String.class, expiring);
        assertTrue(message.startsWith("Penawaran QO-") && message.contains("akan kadaluarsa pada"), message);

        sweeperJob.scheduled();
        assertEquals(1, jdbc.queryForObject(countSql, Integer.class, userId, expiring));

        // Penawaran kedaluwarsa tidak bisa di-accept.
        Result accept = call(patch(BASE + "/" + overdue + "/accept", Map.of()));
        assertEquals("Hanya status Sent yang bisa di-Accept. Status saat ini: Expired.", accept.error());
    }
}
