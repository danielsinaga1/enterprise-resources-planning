package id.neravoxa.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.approval.ErpApprovalGateService;
import id.neravoxa.erp.core.approval.ErpApprovalHandler;
import id.neravoxa.erp.core.approval.ErpApprovalHandlerRegistry;
import id.neravoxa.erp.entity.ApprovalRequest;
import id.neravoxa.erp.repository.ApprovalRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gerbang approval (role non-bypass → 202 + eksekusi via handler sebagai pengaju), revisi harga,
 * siklus PO dengan aturan approval, laporan pembelian, dan cek notifikasi PO terlambat.
 */
class PurchaseApprovalTest extends PurchaseTestSupport {

    @Autowired
    ErpApprovalHandlerRegistry handlers;
    @Autowired
    ApprovalRequestRepository requests;
    @Autowired
    ErpPurchaseOrderOverdueCheck overdueCheck;
    @Autowired
    @Qualifier(ErpTx.MANAGER)
    PlatformTransactionManager tm;

    private ErpApprovalHandler.ApprovedDocument approve(String requestId, String approverId) {
        return new TransactionTemplate(tm).execute(s -> {
            ApprovalRequest r = requests.findById(requestId).orElseThrow();
            ErpApprovalHandler h = handlers.find(r.getDocumentType()).orElseThrow();
            JsonNode[] split = ErpApprovalGateService.splitStoredPayload(r.getPayload());
            return h.approve(r, split[0], split[1], approverId, "");
        });
    }

    @Test
    void non_bypass_role_queues_invoice_and_handler_creates_it_as_requester() throws Exception {
        asAdmin();
        ok(get("/api/erp/purchase-orders?limit=1"));
        String adminId = userId("erp.admin@test.local");
        String u = uid();
        String sup = supplier("Supplier Appr " + u);
        String email = "acc." + u.toLowerCase() + "@test.local";

        asUser(email, "ERP_ACCOUNTANT");
        Result r = call(post("/api/erp/purchase/invoices", map("date", today(), "partyId", sup,
                "items", List.of(map("itemName", "Jasa giling", "itemType", "service", "quantity", 2, "rate", 50000, "taxPct", 11)),
                "biayaLain", 1000)));
        assertEquals(202, r.status(), String.valueOf(r.body()));
        assertTrue(r.data().get("pendingApproval").asBoolean());
        assertEquals("Invoice menunggu persetujuan manajer sebelum diproses.", r.data().get("message").asText());
        JsonNode req = r.data().get("request");
        assertEquals("PurchaseInvoice", req.get("documentType").asText());
        assertEquals("Invoice Pembelian (menunggu approval)", req.get("documentNumber").asText());
        assertMoney("112000", req.get("amount").asText());
        assertFalse(req.has("requester"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM purchase_invoices WHERE party_id = ?", Integer.class, sup));
        String requesterId = userId(email);

        ErpApprovalHandler.ApprovedDocument doc = approve(req.get("id").asText(), adminId);
        assertNotNull(doc);
        assertTrue(doc.number().startsWith("PI-"));
        assertEquals(requesterId, jdbc.queryForObject("SELECT created_by FROM purchase_invoices WHERE id = ?", String.class, doc.id()));
        assertMoney("112000", jdbc.queryForObject("SELECT grand_total FROM purchase_invoices WHERE id = ?",
                java.math.BigDecimal.class, doc.id()));
        assertMoney("112000", jeSum("JV-" + doc.number(), "2.1.1", "credit"));

        // Non-bypass tanpa doc.cancel tidak bisa membatalkan.
        Result denied = call(post("/api/erp/purchase/invoices/" + doc.id() + "/cancel", map()));
        assertEquals(403, denied.status());
    }

    @Test
    void revise_price_direct_and_via_approval() throws Exception {
        asAdmin();
        ok(get("/api/erp/purchase-orders?limit=1"));
        String adminId = userId("erp.admin@test.local");
        String u = uid();
        String sup = supplier("Supplier Rev " + u);
        String itemId = item("REV-" + u);
        String wh = warehouse();
        JsonNode gr = ok(post("/api/erp/goods-receipts", map("date", today(), "partyId", sup,
                "items", List.of(map("inventoryItemId", itemId, "warehouseId", wh, "itemName", "Gabah", "quantity", 100,
                        "unit", "kg", "lockedUnitCost", 10000)))));
        String grItemId = ok(get("/api/erp/goods-receipts/" + gr.get("id").asText())).get("items").get(0).get("id").asText();
        JsonNode pi = ok(post("/api/erp/purchase/invoices", map("date", today(), "partyId", sup,
                "items", List.of(map("itemName", "Gabah", "inventoryItemId", itemId, "quantity", 100, "unit", "kg",
                        "rate", 10000, "goodsReceiptItemId", grItemId)))));
        String piId = pi.get("id").asText();
        String lineId = pi.get("items").get(0).get("id").asText();

        // Langsung (admin): semua masih di stok → DR Persediaan + WAC naik, CR Hutang.
        JsonNode revised = ok(post("/api/erp/purchase/invoices/" + piId + "/revise-price",
                map("lines", List.of(map("itemId", lineId, "newRate", 10500)), "reason", "Koreksi harga supplier")));
        assertMoney("1050000", revised.get("grandTotal").asText());
        assertMoney("10500", revised.get("items").get(0).get("rate").asText());
        assertEquals("Supplier Rev " + u, revised.get("supplier").get("name").asText());
        assertEquals(1, revised.get("supplier").size());
        assertTrue(revised.get("notes").asText().contains("[Revisi harga "));
        assertMoney("10500", avg(itemId));
        assertMoney("1050000", outstanding(sup));
        String reviseJv = jdbc.queryForObject("SELECT entry_number FROM journal_entries WHERE narration LIKE ?",
                String.class, "Revisi harga " + pi.get("invoiceNumber").asText() + "%");
        assertMoney("50000", jeSum(reviseJv, "1.4.0", "debit"));
        assertMoney("50000", jeSum(reviseJv, "2.1.1", "credit"));
        assertEquals("Alasan revisi minimal 10 karakter.", call(post("/api/erp/purchase/invoices/" + piId + "/revise-price",
                map("lines", List.of(map("itemId", lineId, "newRate", 1)), "reason", "pendek"))).error());

        // Non-bypass: antre (202), Pending ganda → 409, target tidak ada → 404; approve via handler.
        String email = "rev." + u.toLowerCase() + "@test.local";
        asUser(email, "ERP_ACCOUNTANT");
        Result q = call(post("/api/erp/purchase/invoices/" + piId + "/revise-price",
                map("lines", List.of(map("itemId", lineId, "newRate", 11000)), "reason", "Supplier koreksi lagi")));
        assertEquals(202, q.status(), String.valueOf(q.body()));
        assertEquals("Revisi harga menunggu persetujuan manajer sebelum diproses.", q.data().get("message").asText());
        JsonNode request = q.data().get("request");
        assertEquals(piId, request.get("documentId").asText());
        assertMoney("1100000", request.get("amount").asText());
        assertEquals("PurchaseInvoiceRevisePrice", request.get("payload").get("_before").get("kind").asText());
        Result dup = call(post("/api/erp/purchase/invoices/" + piId + "/revise-price",
                map("lines", List.of(map("itemId", lineId, "newRate", 11000)), "reason", "Supplier koreksi lagi")));
        assertEquals(409, dup.status());
        Result missing = call(post("/api/erp/purchase/invoices/" + UUID.randomUUID() + "/revise-price",
                map("lines", List.of(map("itemId", lineId, "newRate", 11000)), "reason", "Supplier koreksi lagi")));
        assertEquals(404, missing.status());
        assertEquals("Invoice pembelian tidak ditemukan.", missing.error());

        ErpApprovalHandler.ApprovedDocument doc = approve(request.get("id").asText(), adminId);
        assertEquals(piId, doc.id());
        assertMoney("1100000", jdbc.queryForObject("SELECT grand_total FROM purchase_invoices WHERE id = ?",
                java.math.BigDecimal.class, piId));
        assertMoney("11000", avg(itemId));
    }

    @Test
    void po_approval_rule_draft_submit_delete_close_and_reports() throws Exception {
        asAdmin();
        ok(get("/api/erp/purchase-orders?limit=1"));
        String adminId = userId("erp.admin@test.local");
        String u = uid();
        String sup = supplier("Supplier PO " + u);
        String itemId = item("PO-" + u);
        String wh = warehouse();
        String ruleId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO approval_rules (id, document_type, min_amount, required_role) VALUES (?, 'PurchaseOrder', 900000000, 'Admin')", ruleId);
        try {
            // Nilai ≥ minAmount → tetap Draf + Pending.
            JsonNode big = ok(post("/api/erp/purchase-orders", map("date", today(), "partyId", sup,
                    "items", List.of(map("itemName", "Mesin", "quantity", 1000, "unit", "unit", "estimatedRate", 1000000)))));
            assertEquals("Draft", big.get("status").asText());
            assertEquals("Pending", big.get("approvalStatus").asText());
            assertNotNull(big.get("approvalRequestId").asText());
            JsonNode bigDetail = ok(get("/api/erp/purchase-orders/" + big.get("id").asText()));
            assertEquals("PendingApproval", bigDetail.get("displayStatus").asText());
            assertEquals("Pending", bigDetail.get("approvals").get(0).get("status").asText());
            assertNotNull(bigDetail.get("approvals").get(0).get("requester").get("fullName"));
            assertEquals("PO sedang menunggu persetujuan, tidak bisa diubah.", call(put("/api/erp/purchase-orders/"
                    + big.get("id").asText(), map("date", today(), "items", List.of(map("itemName", "X", "quantity", 1,
                    "unit", "u"))))).error());
            JsonNode pendingList = ok(get("/api/erp/purchase-orders?status=PendingApproval&partyId=" + sup));
            assertEquals(1, pendingList.get("total").asInt());

            ErpApprovalHandler.ApprovedDocument doc = approve(big.get("approvalRequestId").asText(), adminId);
            assertEquals(big.get("poNumber").asText(), doc.number());
            assertEquals("Submitted", jdbc.queryForObject("SELECT status FROM purchase_orders WHERE id = ?", String.class, doc.id()));
        } finally {
            jdbc.update("UPDATE approval_rules SET is_active = false WHERE id = ?", ruleId);
        }

        // Draf → edit → ajukan → terima sebagian → tutup.
        JsonNode draft = ok(post("/api/erp/purchase-orders", map("date", today(), "partyId", sup, "saveAs", "draft",
                "items", List.of(map("inventoryItemId", itemId, "itemName", "Beras", "quantity", 10, "unit", "kg",
                        "estimatedRate", 1000, "discount", 10, "taxPct", 11)))));
        assertEquals("Draft", draft.get("status").asText());
        assertMoney("9990", draft.get("grandTotal").asText());
        String draftId = draft.get("id").asText();
        JsonNode edited = ok(put("/api/erp/purchase-orders/" + draftId, map("date", today(), "partyId", sup,
                "expectedDeliveryDate", LocalDate.now(ZoneOffset.UTC).minusDays(3).toString(),
                "items", List.of(map("inventoryItemId", itemId, "itemName", "Beras", "quantity", 20, "unit", "kg",
                        "estimatedRate", 1000)))));
        assertMoney("20000", edited.get("grandTotal").asText());
        JsonNode submitted = ok(post("/api/erp/purchase-orders/" + draftId + "/submit", map()));
        assertEquals("Submitted", submitted.get("status").asText());
        assertTrue(submitted.get("approvalRequestId").isNull());
        ok(post("/api/erp/goods-receipts", map("date", today(), "partyId", sup, "purchaseOrderId", draftId,
                "items", List.of(map("inventoryItemId", itemId, "warehouseId", wh, "itemName", "Beras", "quantity", 5,
                        "unit", "kg", "lockedUnitCost", 1000)))));
        JsonNode partial = ok(get("/api/erp/purchase-orders?receipt=partial&partyId=" + sup));
        assertEquals(1, partial.get("total").asInt());
        assertTrue(partial.get("data").get(0).get("isOverdue").asBoolean());
        assertEquals("PO sudah memiliki STT/penerimaan barang, tidak bisa diubah. Buat PO baru atau tutup PO ini.",
                call(put("/api/erp/purchase-orders/" + draftId, map("date", today(), "items", List.of(map("itemName", "X",
                        "quantity", 1, "unit", "u"))))).error());
        JsonNode overdueList = ok(get("/api/erp/purchase-orders?due=overdue&partyId=" + sup));
        assertEquals(1, overdueList.get("total").asInt());

        // Laporan.
        // Dua PO terbuka (PO besar yang disetujui + PO ini); filter overdue hanya PO ini.
        assertEquals(2, ok(get("/api/erp/reports/po-outstanding?partyId=" + sup)).get("summary").get("poCount").asInt());
        JsonNode outstanding = ok(get("/api/erp/reports/po-outstanding?overdue=1&partyId=" + sup));
        assertEquals(1, outstanding.get("summary").get("poCount").asInt());
        assertEquals(1, outstanding.get("summary").get("overduePoCount").asInt());
        JsonNode row = outstanding.get("rows").get(0);
        assertEquals(15, row.get("remaining").asInt());
        assertTrue(row.get("daysOverdue").asInt() >= 3);
        assertEquals(15000, row.get("remainingValue").asInt());
        JsonNode perf = ok(get("/api/erp/reports/supplier-performance?partyId=" + sup));
        assertEquals("Supplier PO " + u, perf.get("rows").get(0).get("supplierName").asText());
        assertEquals(2, perf.get("rows").get(0).get("poCount").asInt());
        assertEquals("Format tanggal harus YYYY-MM-DD.", call(get("/api/erp/reports/po-outstanding?startDate=2026-1-1")).error());

        // Notifikasi PO terlambat: dibuat sekali (deduplikasi 24 jam).
        Instant now = Instant.now();
        overdueCheck.run(List.of(adminId), now, now.minusSeconds(86400));
        overdueCheck.run(List.of(adminId), now, now.minusSeconds(86400));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type = 'po_overdue' "
                + "AND metadata->>'purchaseOrderId' = ?", Integer.class, draftId));

        // Tutup PO (short-close).
        assertEquals("Alasan penutupan minimal 5 karakter.", call(post("/api/erp/purchase-orders/" + draftId + "/close",
                map("reason", "  ab  "))).error());
        JsonNode closed = ok(post("/api/erp/purchase-orders/" + draftId + "/close", map("reason", "  Supplier stop kirim ")));
        assertEquals("Supplier stop kirim", closed.get("closeReason").asText());
        assertNotNull(closed.get("closedAt").asText());
        assertEquals("Closed", ok(get("/api/erp/purchase-orders/" + draftId)).get("receiptStatus").asText());

        // Hapus draf.
        JsonNode another = ok(post("/api/erp/purchase-orders", map("date", today(), "saveAs", "draft",
                "items", List.of(map("itemName", "ATK", "quantity", 1, "unit", "pak")))));
        JsonNode del = ok(delete("/api/erp/purchase-orders/" + another.get("id").asText()));
        assertTrue(del.get("success").asBoolean());
        assertEquals(another.get("poNumber").asText(), del.get("poNumber").asText());
        assertNull(jdbc.queryForList("SELECT id FROM purchase_orders WHERE id = ?", String.class,
                another.get("id").asText()).stream().findFirst().orElse(null));
        assertEquals("PO Draf tidak dibatalkan. Hapus draf.", call(post("/api/erp/purchase-orders/" + ok(post(
                "/api/erp/purchase-orders", map("date", today(), "saveAs", "draft", "items", List.of(map("itemName", "A",
                        "quantity", 1, "unit", "u"))))).get("id").asText() + "/cancel", map())).error());
    }
}
