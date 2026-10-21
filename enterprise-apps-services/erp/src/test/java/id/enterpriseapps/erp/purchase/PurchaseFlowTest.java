package id.enterpriseapps.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Alur pembelian end-to-end: PO → STT (stok + WAC + accrual) → PI dari STT (balik accrual +
 * selisih landed cost) → batal PI (lepas STT) → batal STT (balik stok + jurnal pembalik) → batal PO.
 */
class PurchaseFlowTest extends PurchaseTestSupport {

    @Test
    void po_gr_pi_and_cancel_cascade() throws Exception {
        asAdmin();
        String u = uid();
        String sup = supplier("Supplier Flow " + u);
        String itemId = item("FLOW-" + u);
        String wh = warehouse();

        // ── PO (tanpa aturan approval → langsung Submitted) ──
        JsonNode po = ok(post("/api/erp/purchase-orders", map(
                "date", today(), "partyId", sup, "warehouseId", wh, "paymentTermDays", 30,
                "items", List.of(map("inventoryItemId", itemId, "itemName", "Gabah " + u, "quantity", 100,
                        "unit", "kg", "estimatedRate", 10000)))));
        assertEquals("Submitted", po.get("status").asText());
        assertTrue(po.get("approvalRequestId").isNull());
        assertMoney("1000000", po.get("grandTotal").asText());
        assertFalse(po.has("supplier"), "respons create hanya kolom skalar");
        String poId = po.get("id").asText();

        JsonNode poDetail = ok(get("/api/erp/purchase-orders/" + poId));
        assertEquals("Unreceived", poDetail.get("receiptStatus").asText());
        assertEquals("Submitted", poDetail.get("displayStatus").asText());
        assertMoney("1000000", poDetail.get("totals").get("grandTotal").asText());
        assertEquals("FLOW-" + u, poDetail.get("items").get(0).get("inventoryItem").get("code").asText());
        assertEquals("GDG-UTAMA", poDetail.get("warehouse").get("code").asText());
        assertFalse(poDetail.get("items").get(0).has("purchaseOrder"), "tanpa back-reference");

        JsonNode openList = ok(get("/api/erp/purchase-orders?status=Submitted&open=1&search=" + po.get("poNumber").asText()));
        assertEquals(1, openList.get("total").asInt());
        assertEquals(0, openList.get("data").get(0).get("_count").get("goodsReceipts").asInt());
        assertEquals(1, openList.get("data").get(0).get("_count").get("items").asInt());
        assertFalse(openList.get("data").get(0).has("items"));

        // ── STT dari PO (link baris PO otomatis per item) ──
        Result grRes = call(post("/api/erp/goods-receipts", map(
                "date", today(), "partyId", sup, "purchaseOrderId", poId, "nomorMobil", "B 1234 XY",
                "items", List.of(map("inventoryItemId", itemId, "warehouseId", wh, "itemName", "Gabah " + u,
                        "quantity", 100, "unit", "kg", "lockedUnitCost", 10000)))));
        assertEquals(201, grRes.status(), String.valueOf(grRes.body()));
        JsonNode gr = grRes.data();
        String stt = gr.get("sttNumber").asText();
        assertTrue(stt.startsWith("STT-"));
        assertMoney("100", stock(itemId));
        assertMoney("100", whStock(itemId, wh));
        assertMoney("10000", avg(itemId));
        BigDecimal[] sttJe = jeTotals("JV-" + stt);
        assertMoney("1000000", sttJe[0]);
        assertMoney("1000000", sttJe[1]);
        // Accrual: CR Hutang Belum Tagih (ACCRUED_PAYABLE → 2.2.3) dengan party supplier.
        assertMoney("1000000", jeSum("JV-" + stt, "2.2.3", "credit"));
        assertMoney("1000000", jeSum("JV-" + stt, "1.4.0", "debit"));
        assertEquals(sup, jdbc.queryForObject("SELECT ji.party_id FROM journal_entry_accounts ji JOIN journal_entries j "
                + "ON j.id = ji.journal_entry_id WHERE j.entry_number = ? AND ji.credit > 0", String.class, "JV-" + stt));

        JsonNode poAfterGr = ok(get("/api/erp/purchase-orders/" + poId));
        assertEquals("Full", poAfterGr.get("receiptStatus").asText());
        assertMoney("100", poAfterGr.get("items").get(0).get("qtyReceived").asText());
        assertEquals(stt, poAfterGr.get("goodsReceipts").get(0).get("sttNumber").asText());

        JsonNode grDetail = ok(get("/api/erp/goods-receipts/" + gr.get("id").asText()));
        JsonNode grItem = grDetail.get("items").get(0);
        assertMoney("10000", grItem.get("stockMovement").get("unitCost").asText());
        assertEquals(poAfterGr.get("items").get(0).get("id").asText(), grItem.get("purchaseOrderItemId").asText());
        assertEquals(po.get("poNumber").asText(), grDetail.get("purchaseOrder").get("poNumber").asText());
        assertFalse(grItem.has("goodsReceipt"));

        JsonNode pending = ok(get("/api/erp/goods-receipts/pending?partyId=" + sup));
        assertEquals(1, pending.get("data").size());
        assertEquals("Supplier Flow " + u, pending.get("data").get(0).get("supplier").get("name").asText());

        JsonNode grList = ok(get("/api/erp/goods-receipts?status=Uninvoiced&search=" + stt));
        assertEquals(1, grList.get("total").asInt());
        assertEquals(1, grList.get("data").get(0).get("_count").get("items").asInt());

        // ── PI dari STT: harga aktual 11.000 (selisih +100.000 ke Persediaan) ──
        Result piRes = call(post("/api/erp/purchase/invoices", map(
                "date", today(), "partyId", sup, "dueDate", today(), "terms", "Net 30",
                "items", List.of(map("itemName", "Gabah " + u, "inventoryItemId", itemId, "warehouseId", wh,
                        "quantity", 100, "unit", "kg", "rate", 11000, "goodsReceiptItemId", grItem.get("id").asText())))));
        assertEquals(201, piRes.status(), String.valueOf(piRes.body()));
        JsonNode pi = piRes.data();
        String piNo = pi.get("invoiceNumber").asText();
        assertEquals("Submitted", pi.get("status").asText());
        assertMoney("1100000", pi.get("grandTotal").asText());
        assertEquals(poId, pi.get("purchaseOrderId").asText());
        assertEquals("Supplier Flow " + u, pi.get("supplier").get("name").asText());
        assertEquals(grItem.get("id").asText(), pi.get("items").get(0).get("goodsReceiptItemId").asText());

        BigDecimal[] piJe = jeTotals("JV-" + piNo);
        assertMoney("1100000", piJe[0]);
        assertMoney("1100000", piJe[1]);
        assertMoney("1000000", jeSum("JV-" + piNo, "2.2.3", "debit"));
        assertMoney("100000", jeSum("JV-" + piNo, "1.4.0", "debit"));
        assertMoney("1100000", jeSum("JV-" + piNo, "2.1.1", "credit"));
        assertMoney("0", jeSum("JV-" + piNo, "5.11", "debit"));
        // Stok tidak bertambah lagi; landed cost menaikkan WAC & unit cost gerakan STT.
        assertMoney("100", stock(itemId));
        assertMoney("11000", avg(itemId));
        assertMoney("11000", jdbc.queryForObject("SELECT unit_cost FROM stock_movements WHERE reference_id = ?",
                java.math.BigDecimal.class, gr.get("id").asText()));
        assertEquals(pi.get("id").asText(), jdbc.queryForObject("SELECT invoice_id FROM goods_receipts WHERE id = ?",
                String.class, gr.get("id").asText()));
        assertMoney("1100000", outstanding(sup));

        JsonNode piDetail = ok(get("/api/erp/purchase/invoices/" + pi.get("id").asText()));
        assertEquals(stt, piDetail.get("items").get(0).get("goodsReceiptItem").get("goodsReceipt").get("sttNumber").asText());
        assertEquals("variance", piDetail.get("match").get("status").asText());
        assertEquals("price", piDetail.get("match").get("lines").get(0).get("status").asText());
        assertTrue(piDetail.get("paymentAllocations").isArray());
        assertTrue(piDetail.get("depositApplications").isArray());
        assertNotNull(piDetail.get("user").get("fullName"));

        JsonNode piList = ok(get("/api/erp/purchase/invoices?search=" + piNo + "&sort=-dueDate"));
        assertEquals(1, piList.get("total").asInt());
        assertEquals(1, piList.get("data").get(0).get("items").size());
        assertFalse(piList.get("data").get(0).get("items").get(0).has("purchaseInvoice"));
        JsonNode summary = ok(get("/api/erp/purchase/invoices/summary?partyId=" + sup));
        assertEquals(1, summary.get("count").asInt());
        assertMoney("1100000", summary.get("outstanding").asText());

        // STT tidak bisa dibatalkan selama PI Submitted.
        Result blocked = call(post("/api/erp/goods-receipts/" + gr.get("id").asText() + "/cancel", map()));
        assertEquals(400, blocked.status());
        assertTrue(blocked.error().endsWith("Batalkan invoice tersebut dulu."), blocked.error());

        // ── Batal PI: jurnal dibatalkan, STT dilepas, stok tetap ──
        JsonNode cancelled = ok(post("/api/erp/purchase/invoices/" + pi.get("id").asText() + "/cancel", map()));
        assertEquals("Cancelled", cancelled.get("status").asText());
        assertEquals("Cancelled", jeStatus("JV-" + piNo));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_ledger_entries le JOIN journal_entries j "
                + "ON j.id = le.reference_id WHERE j.entry_number = ? AND le.is_cancelled = false", Integer.class, "JV-" + piNo));
        assertEquals(null, jdbc.queryForObject("SELECT invoice_id FROM goods_receipts WHERE id = ?", String.class, gr.get("id").asText()));
        assertMoney("100", stock(itemId));
        assertMoney("0", outstanding(sup));
        Result again = call(post("/api/erp/purchase/invoices/" + pi.get("id").asText() + "/cancel", map()));
        assertEquals("Invoice sudah dibatalkan.", again.error());

        // ── Batal STT: stok dibalik, jurnal pembalik, qtyReceived PO kembali ──
        JsonNode grCancelled = ok(post("/api/erp/goods-receipts/" + gr.get("id").asText() + "/cancel", map()));
        assertEquals("Cancelled", grCancelled.get("status").asText());
        assertMoney("0", stock(itemId));
        assertMoney("0", whStock(itemId, wh));
        assertEquals("Cancelled", jeStatus("JV-" + stt));
        BigDecimal[] cxl = jeTotals("JV-" + stt + "-CXL");
        assertMoney("1000000", cxl[0]);
        assertMoney("1000000", cxl[1]);
        assertMoney("1000000", jeSum("JV-" + stt + "-CXL", "2.2.3", "debit"));
        JsonNode poAfterCancel = ok(get("/api/erp/purchase-orders/" + poId));
        assertMoney("0", poAfterCancel.get("items").get(0).get("qtyReceived").asText());
        assertEquals("Unreceived", poAfterCancel.get("receiptStatus").asText());
        assertEquals(0, poAfterCancel.get("goodsReceipts").size());

        // ── Batal PO ──
        JsonNode poCancelled = ok(post("/api/erp/purchase-orders/" + poId + "/cancel", map()));
        assertEquals("Cancelled", poCancelled.get("status").asText());
        assertEquals("PO sudah dibatalkan.", call(post("/api/erp/purchase-orders/" + poId + "/cancel", map())).error());
    }

    @Test
    void negative_landed_cost_posts_credit_to_inventory() throws Exception {
        asAdmin();
        String u = uid();
        String sup = supplier("Supplier Neg " + u);
        String itemId = item("NEG-" + u);
        String wh = warehouse();
        JsonNode gr = ok(post("/api/erp/goods-receipts", map("date", today(), "partyId", sup,
                "items", List.of(map("inventoryItemId", itemId, "warehouseId", wh, "itemName", "Beras", "quantity", 100,
                        "unit", "kg", "lockedUnitCost", 10000)))));
        String grItemId = ok(get("/api/erp/goods-receipts/" + gr.get("id").asText())).get("items").get(0).get("id").asText();
        JsonNode pi = ok(post("/api/erp/purchase/invoices", map("date", today(), "partyId", sup,
                "items", List.of(map("itemName", "Beras", "inventoryItemId", itemId, "quantity", 100, "unit", "kg",
                        "rate", 9500, "goodsReceiptItemId", grItemId)))));
        String jv = "JV-" + pi.get("invoiceNumber").asText();
        assertMoney("50000", jeSum(jv, "1.4.0", "credit"));
        assertMoney("1000000", jeSum(jv, "2.2.3", "debit"));
        assertMoney("950000", jeSum(jv, "2.1.1", "credit"));
        assertMoney("9500", avg(itemId));

        // Baris STT yang sudah di-invoice tidak bisa ditagih ulang.
        Result dup = call(post("/api/erp/purchase/invoices", map("date", today(), "partyId", sup,
                "items", List.of(map("itemName", "Beras", "inventoryItemId", itemId, "quantity", 100, "unit", "kg",
                        "rate", 9500, "goodsReceiptItemId", grItemId)))));
        assertEquals(400, dup.status());
        assertTrue(dup.error().contains("sudah di-invoice sebelumnya"), dup.error());
    }

    @Test
    void standalone_service_invoice_and_validation() throws Exception {
        asAdmin();
        String u = uid();
        String sup = supplier("Supplier Jasa " + u);
        // Baris jasa (tanpa item inventori) + PPN 11% + biaya lain → DR Persediaan umum / CR Hutang.
        JsonNode pi = ok(post("/api/erp/purchase/invoices", map("date", today(), "partyId", sup, "biayaLain", 1000,
                "notes", "", "items", List.of(map("itemName", "Jasa angkut", "itemType", "service", "quantity", 2,
                        "rate", 50000, "taxPct", 11)))));
        assertMoney("112000", pi.get("grandTotal").asText());
        assertEquals("service", pi.get("items").get(0).get("itemType").asText());
        assertEquals("pcs", pi.get("items").get(0).get("unit").asText());
        assertTrue(pi.get("notes").isNull());
        String jv = "JV-" + pi.get("invoiceNumber").asText();
        assertMoney("112000", jeSum(jv, "1.4.0", "debit"));
        assertMoney("112000", jeSum(jv, "2.1.1", "credit"));
        assertMoney("112000", outstanding(sup));

        // Edit metadata: terms di-trim, string kosong mengosongkan, field absen tetap.
        JsonNode upd = ok(put("/api/erp/purchase/invoices/" + pi.get("id").asText(), map("notes", "n", "terms", "  Net 30 ")));
        assertEquals("Net 30", upd.get("terms").asText());
        JsonNode upd2 = ok(put("/api/erp/purchase/invoices/" + pi.get("id").asText(), map("terms", "")));
        assertTrue(upd2.get("terms").isNull());
        assertEquals("n", upd2.get("notes").asText());
        assertEquals("Tanggal jatuh tempo tidak valid.",
                call(put("/api/erp/purchase/invoices/" + pi.get("id").asText(), map("dueDate", "bukan-tanggal"))).error());
        assertEquals(404, call(put("/api/erp/purchase/invoices/" + java.util.UUID.randomUUID(), map("notes", "x"))).status());

        // Item inventori tanpa STT ditolak (stok hanya masuk lewat STT).
        String itemId = item("ORPH-" + u);
        Result orphan = call(post("/api/erp/purchase/invoices", map("date", today(), "partyId", sup,
                "items", List.of(map("itemName", "Gabah", "inventoryItemId", itemId, "quantity", 1, "rate", 1)))));
        assertEquals(400, orphan.status());
        assertTrue(orphan.error().startsWith("Item inventori \"Item ORPH-" + u + "\" tidak bisa di-invoice langsung."), orphan.error());

        // Validasi body (pesan Zod).
        assertEquals("Minimal satu item.", call(post("/api/erp/purchase/invoices",
                map("date", today(), "partyId", sup, "items", List.of()))).error());
        assertEquals("Jumlah harus lebih dari 0.", call(post("/api/erp/purchase/invoices", map("date", today(), "partyId", sup,
                "items", List.of(map("itemName", "X", "quantity", 0, "rate", 1))))).error());
        assertEquals("Tanggal wajib diisi.", call(post("/api/erp/purchase/invoices", map("date", "", "partyId", "",
                "items", List.of()))).error());
        assertEquals("Data supplier tidak ditemukan.", call(post("/api/erp/purchase/invoices", map("date", today(),
                "partyId", "nope", "items", List.of(map("itemName", "X", "quantity", 1, "rate", 1))))).error());

        // Pratinjau match: baris tanpa link → unlinked.
        JsonNode preview = ok(post("/api/erp/purchase/invoices/match-preview",
                map("items", List.of(map("itemName", "X", "quantity", 1, "rate", 100)))));
        assertEquals("unlinked", preview.get("status").asText());
        assertEquals("0", preview.get("lines").get(0).get("key").asText());
    }

    @Test
    void goods_receipt_validation_and_insufficient_stock_cancel() throws Exception {
        asAdmin();
        String u = uid();
        String sup = supplier("Supplier GR " + u);
        String itemId = item("GRV-" + u);
        String wh = warehouse();
        // Harga 0 tanpa avg cost → ditolak.
        Result zero = call(post("/api/erp/goods-receipts", map("date", today(), "partyId", sup,
                "items", List.of(map("inventoryItemId", itemId, "warehouseId", wh, "itemName", "X", "quantity", 5, "unit", "kg")))));
        assertEquals(400, zero.status());
        assertTrue(zero.error().startsWith("Harga satuan untuk 'Item GRV-" + u + "' belum ada (nilai 0)."), zero.error());
        assertEquals("Supplier wajib dipilih.", call(post("/api/erp/goods-receipts", map("date", today(), "partyId", "x",
                "items", List.of(map("inventoryItemId", itemId, "warehouseId", wh, "itemName", "X", "quantity", 5, "unit", "kg"))))).error());

        // Barang titipan: stok masuk tanpa jurnal & tanpa WAC.
        JsonNode owned = ok(post("/api/erp/goods-receipts", map("date", today(), "partyId", sup, "isCustomerOwned", true,
                "items", List.of(map("inventoryItemId", itemId, "warehouseId", wh, "itemName", "X", "quantity", 5, "unit", "kg")))));
        assertMoney("5", stock(itemId));
        assertMoney("0", avg(itemId));
        assertEquals(null, jeStatus("JV-" + owned.get("sttNumber").asText()));

        // Stok keluar manual (simulasi penjualan) → pembatalan butuh bypass.
        jdbc.update("UPDATE inventory_items SET current_stock = current_stock - 3 WHERE id = ?", itemId);
        Result insufficient = call(post("/api/erp/goods-receipts/" + owned.get("id").asText() + "/cancel", map()));
        assertEquals(400, insufficient.status());
        assertEquals("GR_CANCEL_INSUFFICIENT_STOCK", insufficient.data().get("code").asText());
        assertTrue(insufficient.error().contains("(tersedia 2, perlu 5)"), insufficient.error());
        assertEquals("Alasan bypass stok minus wajib diisi (minimal 10 karakter).", call(post("/api/erp/goods-receipts/"
                + owned.get("id").asText() + "/cancel", map("allowNegativeStock", true, "negativeStockReason", "pendek"))).error());
        JsonNode forced = ok(post("/api/erp/goods-receipts/" + owned.get("id").asText() + "/cancel",
                map("allowNegativeStock", true, "negativeStockReason", "Koreksi data penerimaan")));
        assertEquals("Cancelled", forced.get("status").asText());
        assertMoney("-3", stock(itemId));
    }
}
