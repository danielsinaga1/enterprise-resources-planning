package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.security.ErpActor;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Detail pengajuan: perbandingan sebelum/sesudah (tipe edit), cek Pending per dokumen, dan konteks dialog. */
class ApprovalViewTest extends AdminTestSupport {

    @Test
    void invoice_revise_request_shows_comparison_and_pending_lookup() throws Exception {
        loginAdmin();
        String u = uid();
        String email = "cmp." + u + "@test.local";
        String requesterId = login(email, "ERP_ACCOUNTANT");
        ErpActor requester = new ErpActor(requesterId, email, "Peminta", "ERP_ACCOUNTANT", Set.of("ERP_ACCOUNTANT"), null);
        String invoiceId = UUID.randomUUID().toString();
        String line = UUID.randomUUID().toString();
        Map<String, Object> before = map("kind", "PurchaseInvoiceRevisePrice", "invoiceNumber", "PI-OLD-" + u, "grandTotal", 12345000,
                "lines", List.of(map("itemId", line, "itemName", "Gabah", "quantity", 1234.5, "unit", "kg", "rate", 10000)));
        JsonNode body = json.valueToTree(map("lines", List.of(map("itemId", line, "newRate", 11000.5)), "reason", "Koreksi harga supplier"));
        Map<String, Object> queued = gate.queueEditApproval("PurchaseInvoiceRevisePrice", invoiceId, "PI-" + u,
                new BigDecimal("13580117.25"), body, before, "selisih", requester);
        String id = ((ApprovalRequest) queued.get("request")).getId();

        asUser(email, "ERP_ACCOUNTANT");
        JsonNode cmp = ok(get("/api/erp/approvals/requests/" + id)).get("comparison");
        assertEquals("Revisi Harga Invoice Pembelian", cmp.get("title").asText());
        assertEquals("PI-" + u, cmp.get("targetNumber").asText());
        assertEquals("PurchaseInvoice", cmp.get("targetType").asText());
        assertEquals(invoiceId, cmp.get("targetId").asText());
        assertEquals("Total invoice (baru)", cmp.get("amountLabel").asText());
        JsonNode summary = cmp.get("sections").get(0);
        assertEquals("Ringkasan", summary.get("title").asText());
        JsonNode total = summary.get("rows").get(0);
        assertEquals("Total invoice", total.get("label").asText());
        assertAmount("12345000", total.get("before").asText());
        assertAmount("13580117.25", total.get("after").asText());
        assertEquals("currency", total.get("kind").asText());
        assertTrue(total.get("changed").asBoolean());
        assertEquals("Estimasi dari selisih harga × qty; pajak/potongan dihitung sistem.", total.get("note").asText());
        JsonNode delta = summary.get("rows").get(1);
        assertEquals(0, delta.get("before").asInt());
        assertAmount("1235117.25", delta.get("after").asText());
        JsonNode reason = summary.get("rows").get(2);
        assertTrue(reason.get("before").isNull());
        assertEquals("Koreksi harga supplier", reason.get("after").asText());
        assertFalse(reason.get("changed").asBoolean());
        JsonNode rate = cmp.get("sections").get(1).get("rows").get(0);
        assertEquals("Gabah · harga per kg", rate.get("label").asText());
        assertAmount("10000", rate.get("before").asText());
        assertAmount("11000.5", rate.get("after").asText());
        JsonNode value = cmp.get("sections").get(2).get("rows").get(0);
        assertEquals("Gabah · nilai (1.234,5 kg)", value.get("label").asText());
        assertAmount("12345000", value.get("before").asText());
        assertAmount("13580117.25", value.get("after").asText());

        JsonNode pending = ok(get("/api/erp/approvals/pending?documentType=PurchaseInvoiceRevisePrice&documentId=" + invoiceId));
        assertEquals(id, pending.get("pending").get("id").asText());
        assertTrue(pending.get("pending").hasNonNull("createdAt"));
        assertTrue(ok(get("/api/erp/approvals/pending?documentType=PurchaseInvoiceRevisePrice&documentId=" + UUID.randomUUID()))
                .get("pending").isNull());
        assertEquals("documentType/documentId tidak valid.",
                call(get("/api/erp/approvals/pending?documentType=PurchaseInvoice&documentId=" + invoiceId)).error());
        assertEquals(400, call(get("/api/erp/approvals/pending?documentType=ProductionRunEdit")).status());

        loginAdmin();
        ok(post("/api/erp/approvals/requests/" + id + "/reject", map("notes", "uji selesai")));
        assertTrue(ok(get("/api/erp/approvals/pending?documentType=PurchaseInvoiceRevisePrice&documentId=" + invoiceId))
                .get("pending").isNull());
    }

    @Test
    void production_edit_request_shows_quantity_and_yield_comparison() throws Exception {
        loginAdmin();
        String u = uid();
        String email = "pcmp." + u + "@test.local";
        String requesterId = login(email, "ERP_ACCOUNTANT");
        ErpActor requester = new ErpActor(requesterId, email, "Peminta", "ERP_ACCOUNTANT", Set.of("ERP_ACCOUNTANT"), null);
        String runId = UUID.randomUUID().toString();
        String i1 = "in1-" + u;
        String i2 = "in2-" + u;
        String o1 = "out1-" + u;
        String o2 = "out2-" + u;
        Map<String, Object> before = map("kind", "ProductionRunEdit", "runNumber", "PR-OLD", "date", "2026-10-01",
                "inputs", List.of(map("itemId", i1, "itemName", "Gabah", "quantity", 100)),
                "outputs", List.of(map("itemId", o1, "itemName", "Beras", "quantity", 60, "byProduct", false),
                        map("itemId", o2, "itemName", "Dedak", "quantity", 10, "byProduct", true)),
                "computedHpp", 5000,
                "itemNames", map(i1, "Gabah", o1, "Beras", o2, "Dedak", i2, "Gabah Baru"));
        JsonNode body = json.valueToTree(map("date", "2026-10-02T00:00:00.000Z",
                "inputs", List.of(map("itemId", i1, "quantity", "90"), map("itemId", i2, "quantity", 20)),
                "outputs", List.of(map("itemId", o1, "quantity", 70), map("itemId", o2, "quantity", 10, "isByProduct", true))));
        Map<String, Object> queued = gate.queueEditApproval("ProductionRunEdit", runId, "PR-" + u, new BigDecimal("110"),
                body, before, "110 kg input baru", requester);
        String id = ((ApprovalRequest) queued.get("request")).getId();

        JsonNode cmp = ok(get("/api/erp/approvals/requests/" + id)).get("comparison");
        assertEquals("Edit Data Produksi", cmp.get("title").asText());
        assertEquals("PR-" + u, cmp.get("targetNumber").asText());
        assertEquals("ProductionRun", cmp.get("targetType").asText());
        assertEquals(runId, cmp.get("targetId").asText());
        JsonNode s = cmp.get("sections").get(0).get("rows");
        assertRow(s.get(0), "Tanggal produksi", "2026-10-01", "2026-10-02", true);
        assertRow(s.get(1), "Total input", "100", "110", true);
        assertRow(s.get(2), "Total output (termasuk sampingan)", "70", "80", true);
        assertRow(s.get(3), "Yield produk utama", "60", "63.64", true);
        assertEquals("percent", s.get(3).get("kind").asText());
        assertRow(s.get(4), "HPP terhitung (per kg produk utama)", "5000", "null", false);
        assertEquals("Dihitung ulang oleh sistem saat disetujui.", s.get(4).get("note").asText());
        JsonNode inputs = cmp.get("sections").get(1).get("rows");
        assertEquals("Bahan baku (input)", cmp.get("sections").get(1).get("title").asText());
        assertRow(inputs.get(0), "Gabah", "100", "90", true);
        assertRow(inputs.get(1), "Gabah Baru", "0", "20", true);
        JsonNode outputs = cmp.get("sections").get(2).get("rows");
        assertRow(outputs.get(0), "Beras", "60", "70", true);
        assertRow(outputs.get(1), "Dedak (produk sampingan)", "10", "10", false);

        loginAdmin();
        ok(post("/api/erp/approvals/requests/" + id + "/reject", map("notes", "uji selesai")));
    }

    @Test
    void context_resolves_names_supplier_position_and_attachments() throws Exception {
        String adminId = loginAdmin();
        String u = uid();
        String email = "ctx." + u + "@test.local";
        String requesterId = login(email, "ERP_ACCOUNTANT");
        String supplier = createParty("Supplier Ctx " + u, "Supplier");
        jdbc.update("UPDATE parties SET deposit_balance = 250000 WHERE id = ?", supplier);
        String openPi = createPurchaseInvoice(supplier, "PI-OPEN-" + u, "500000", "Submitted", adminId);
        createPurchaseInvoice(supplier, "PI-CXL-" + u, "70000", "Cancelled", adminId);
        String inv1 = createItem("CTX-" + u, "Gabah Ctx", "kg");
        String prodIn = createItem("CTXI-" + u, "Gabah Giling", "kg");
        String prodOut = createItem("CTXO-" + u, "Beras Premium", "kg");
        String account = jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = '1.1.3'", String.class);
        jdbc.update("INSERT INTO purchase_invoice_items (id, purchase_invoice_id, item_type, inventory_item_id, item_name, quantity, "
                        + "unit, rate, amount, account_id) VALUES (?, ?, 'product', ?, 'Gabah', 10, 'kg', 12000, 120000, ?)",
                UUID.randomUUID().toString(), openPi, inv1, account);
        Map<String, Object> payload = map("partyId", supplier,
                "items", List.of(map("inventoryItemId", inv1, "goodsReceiptItemId", UUID.randomUUID().toString())),
                "inputs", List.of(map("itemId", prodIn)), "outputs", List.of(map("itemId", prodOut)), "label", "CTX");
        String id = queue(ErpAdminTestApprovalHandler.TYPE, payload, "1000", requesterId);

        asUser(email, "ERP_ACCOUNTANT");
        MockMultipartFile pdf = new MockMultipartFile("files", "nota.pdf", "application/pdf",
                "%PDF-1.4\n%uji\n".getBytes(StandardCharsets.US_ASCII));
        assertEquals(201, call(MockMvcRequestBuilders.multipart("/api/erp/attachments/upload").file(pdf)
                .param("referenceType", "approval_request").param("referenceId", id)).status());

        JsonNode ctx = ok(get("/api/erp/approvals/requests/" + id + "/context"));
        assertEquals(0, ctx.get("sttLines").size());
        assertEquals(0, ctx.get("stts").size());
        JsonNode att = ctx.get("requestAttachments").get(0);
        assertEquals("nota.pdf", att.get("fileName").asText());
        assertEquals("approval_request", att.get("referenceType").asText());
        assertEquals("ctx." + u, att.get("user").get("fullName").asText());
        assertFalse(att.has("filePath"));
        JsonNode sup = ctx.get("supplier");
        assertEquals("Supplier Ctx " + u, sup.get("name").asText());
        assertAmount("250000", sup.get("depositBalance").asText());
        assertEquals(1, sup.get("openInvoiceCount").asInt());
        assertAmount("500000", sup.get("openOutstanding").asText());
        JsonNode hist = ctx.get("priceHistory").get(inv1);
        assertEquals(1, hist.size());
        assertAmount("12000", hist.get(0).get("rate").asText());
        assertEquals("PI-OPEN-" + u, hist.get(0).get("invoiceNumber").asText());
        assertEquals("Supplier Ctx " + u, hist.get(0).get("supplier").asText());
        assertTrue(hist.get(0).get("sameSupplier").asBoolean());
        assertEquals(supplier, ctx.get("party").get("id").asText());
        assertEquals("Gabah Giling", ctx.get("items").get(prodIn).get("name").asText());
        assertEquals("kg", ctx.get("items").get(prodOut).get("unit").asText());
        assertTrue(ctx.get("comparison").isNull());

        login("ctx2." + u + "@test.local", "ERP_ACCOUNTANT");
        assertEquals(404, call(get("/api/erp/approvals/requests/" + id + "/context")).status());

        loginAdmin();
        JsonNode all = ok(get("/api/erp/attachments/approval_request/" + id));
        ok(delete("/api/erp/attachments/" + all.get(0).get("id").asText()));
        ok(post("/api/erp/approvals/requests/" + id + "/reject", map("notes", "uji selesai")));
    }

    private static void assertRow(JsonNode row, String label, String before, String after, boolean changed) {
        assertEquals(label, row.get("label").asText());
        assertEquals(before, row.get("before").asText());
        assertEquals(after, row.get("after").asText());
        assertEquals(changed, row.get("changed").asBoolean(), label);
    }
}
