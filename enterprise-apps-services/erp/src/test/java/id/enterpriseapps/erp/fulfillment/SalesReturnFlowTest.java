package id.enterpriseapps.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.approval.ErpApprovalHandler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Retur penjualan (JV-RN + balik HPP pada biaya DO, stok masuk, porsi kredit → uang muka pelanggan,
 * pembatalan membalik semuanya, gerbang approval) dan Surat Jalan retroaktif dari invoice langsung.
 */
class SalesReturnFlowTest extends FulfillmentTestSupport {

    /** DO lewat API (unit cost terkunci = averageCost saat kirim) lalu invoice dari baris DO itu. */
    private JsonNode invoiceFromDeliveryOrder(String party, String item, String itemName, int qty, int rate) throws Exception {
        JsonNode d = ok(post(DO, map("date", today(), "partyId", party,
                "items", List.of(map("inventoryItemId", item, "itemName", itemName, "quantity", qty, "unit", "kg")))));
        String doItemId = d.get("items").get(0).get("id").asText();
        // Seperti modal invoice: baris dari Surat Jalan membawa gudang baris DO.
        return ok(post(INVOICES, map("date", today(), "partyId", party, "items", List.of(map(
                "itemName", itemName, "itemType", "product", "inventoryItemId", item,
                "warehouseId", d.get("items").get(0).get("warehouseId").asText(), "quantity", qty, "unit", "kg",
                "rate", rate, "taxPct", 11, "deliveryOrderItemId", doItemId)))));
    }

    @Test
    void restock_return_posts_reversals_at_delivery_cost_and_cancel_restores() throws Exception {
        adminUserId();
        String party = customer(uniq("Cust Retur"));
        String itemName = uniq("Beras Retur");
        String item = item(itemName, "7000", "100");
        JsonNode inv = invoiceFromDeliveryOrder(party, item, itemName, 10, 10000);
        String invId = inv.get("id").asText();
        String lineId = inv.get("items").get(0).get("id").asText();
        assertAmount("111000", inv.get("outstanding"));
        assertEquals(1, ok(get(DO + "?status=Invoiced&partyId=" + party)).get("total").asInt());
        // averageCost berubah setelah kirim: balik HPP tetap memakai biaya terkunci di gerakan DO (7.000).
        jdbc.update("UPDATE inventory_items SET average_cost = 6500 WHERE id = ?", item);
        assertAmount("90", itemStock(item));

        BigDecimal retur = balance("4.3");
        BigDecimal ppn = balance("2.2.1");
        BigDecimal ar = balance("1.2.1");
        BigDecimal persediaan = balance("1.4.0");
        BigDecimal hpp = balance("5.1");
        BigDecimal outstandingBefore = partyColumn(party, "outstanding_amount");

        JsonNode eligible = ok(get(RETURNS + "/eligible/" + invId));
        assertEquals(inv.get("invoiceNumber").asText(), eligible.get("invoice").get("invoiceNumber").asText());
        assertEquals(party, eligible.get("invoice").get("customer").get("id").asText());
        assertAmount("10", eligible.get("lines").get(0).get("remaining"));
        assertTrue(eligible.get("lines").get(0).get("isInventory").asBoolean());
        assertEquals("Gudang Utama", eligible.get("lines").get(0).get("warehouseName").asText());

        Result created = call(post(RETURNS, map("salesInvoiceId", invId, "date", today(), "reason", "Kemasan rusak",
                "items", List.of(map("salesInvoiceItemId", lineId, "quantity", 4)))));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        JsonNode ret = created.data();
        String retId = ret.get("id").asText();
        String rn = ret.get("returnNumber").asText();
        assertTrue(rn.startsWith("RN-"), rn);
        assertAmount("40000", ret.get("subtotal"));
        assertAmount("4400", ret.get("taxAmount"));
        assertAmount("44400", ret.get("totalReturn"));
        assertAmount("44400", ret.get("arReducedAmount"));
        assertAmount("0", ret.get("creditCreatedAmount"));
        assertTrue(ret.get("customerDepositId").isNull());
        assertAmount("7000", ret.get("items").get(0).get("unitCostLoaded"));
        assertEquals(inv.get("invoiceNumber").asText(), ret.get("salesInvoice").get("invoiceNumber").asText());
        assertEquals(party, ret.get("customer").get("id").asText());

        assertAmount("44400", assertBalancedJournal("JV-" + rn, "Submitted"));
        assertAmount("28000", assertBalancedJournal("JV-COGS-" + rn, "Submitted"));
        assertAmount("-40000", balance("4.3").subtract(retur));
        assertAmount("-4400", balance("2.2.1").subtract(ppn));
        assertAmount("-44400", balance("1.2.1").subtract(ar));
        assertAmount("28000", balance("1.4.0").subtract(persediaan));
        assertAmount("-28000", balance("5.1").subtract(hpp));
        assertAmount("66600", jdbc.queryForObject("SELECT outstanding FROM sales_invoices WHERE id = ?", BigDecimal.class, invId));
        assertAmount("-44400", partyColumn(party, "outstanding_amount").subtract(outstandingBefore));
        assertAmount("94", itemStock(item));
        assertAmount("94", whStock(item));
        Map<String, Object> sm = jdbc.queryForMap("SELECT * FROM stock_movements WHERE reference_type = 'SalesReturn' AND reference_id = ?", retId);
        assertEquals("In", sm.get("movement_type"));
        assertAmount("7000", sm.get("unit_cost"));
        assertEquals(accountId("5.1"), sm.get("offset_account_id"));

        JsonNode after = ok(get(RETURNS + "/eligible/" + invId));
        assertAmount("4", after.get("lines").get(0).get("alreadyReturned"));
        assertAmount("6", after.get("lines").get(0).get("remaining"));
        assertEquals("Qty retur \"" + itemName + "\" (7) melebihi sisa yang bisa diretur (6).",
                call(post(RETURNS, map("salesInvoiceId", invId, "date", today(),
                        "items", List.of(map("salesInvoiceItemId", lineId, "quantity", 7))))).error());

        JsonNode detail = ok(get(RETURNS + "/" + retId));
        assertEquals("JV-" + rn, detail.get("journal").get("entryNumber").asText());
        assertTrue(detail.get("journal").get("items").get(0).get("account").has("accountNumber"));
        assertAmount("66600", detail.get("salesInvoice").get("outstanding"));
        JsonNode list = ok(get(RETURNS + "?salesInvoiceId=" + invId));
        assertEquals(1, list.get("total").asInt());
        assertEquals(1, list.get("data").get(0).get("items").size());
        assertEquals(1, ok(get(RETURNS + "?search=" + inv.get("invoiceNumber").asText())).get("total").asInt());
        assertEquals("Status retur tidak dikenal.", call(get(RETURNS + "?status=Paid")).error());

        JsonNode cancelled = ok(post(RETURNS + "/" + retId + "/cancel", map()));
        assertEquals("Cancelled", cancelled.get("status").asText());
        assertBalancedJournal("JV-" + rn, "Cancelled");
        assertBalancedJournal("JV-COGS-" + rn, "Cancelled");
        assertAmount("0", balance("4.3").subtract(retur));
        assertAmount("0", balance("2.2.1").subtract(ppn));
        assertAmount("0", balance("1.2.1").subtract(ar));
        assertAmount("0", balance("1.4.0").subtract(persediaan));
        assertAmount("0", balance("5.1").subtract(hpp));
        assertAmount("111000", jdbc.queryForObject("SELECT outstanding FROM sales_invoices WHERE id = ?", BigDecimal.class, invId));
        assertAmount("0", partyColumn(party, "outstanding_amount").subtract(outstandingBefore));
        assertAmount("90", itemStock(item));
        assertAmount("90", whStock(item));
        assertEquals("Retur sudah dibatalkan.", call(post(RETURNS + "/" + retId + "/cancel", map())).error());
        assertEquals("Retur tidak ditemukan.", call(get(RETURNS + "/" + UUID.randomUUID())).error());
        assertEquals("Invoice tidak ditemukan.", call(get(RETURNS + "/eligible/" + UUID.randomUUID())).error());
    }

    @Test
    void credit_portion_becomes_customer_deposit_and_non_bypass_role_is_queued() throws Exception {
        String adminId = adminUserId();
        String party = customer(uniq("Cust Kredit"));
        JsonNode inv = ok(post(INVOICES, map("date", today(), "partyId", party, "items", List.of(
                map("itemName", "Jasa giling", "itemType", "service", "quantity", 2, "unit", "jasa", "rate", 50000)))));
        String invId = inv.get("id").asText();
        String lineId = inv.get("items").get(0).get("id").asText();
        // Invoice sudah dibayar sebagian: sisa piutang 30.000 < nilai retur 50.000.
        jdbc.update("UPDATE sales_invoices SET outstanding = 30000 WHERE id = ?", invId);
        BigDecimal deposit = balance("2.1.2");
        BigDecimal depositBalance = partyColumn(party, "customer_deposit_balance");

        Map<String, Object> body = map("salesInvoiceId", invId, "date", today(),
                "items", List.of(map("salesInvoiceItemId", lineId, "quantity", 1)));
        JsonNode ret = ok(post(RETURNS, body));
        String rn = ret.get("returnNumber").asText();
        assertAmount("30000", ret.get("arReducedAmount"));
        assertAmount("20000", ret.get("creditCreatedAmount"));
        assertAmount("50000", assertBalancedJournal("JV-" + rn, "Submitted"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE entry_number = ?", Integer.class, "JV-COGS-" + rn));
        assertAmount("20000", balance("2.1.2").subtract(deposit));
        assertAmount("20000", partyColumn(party, "customer_deposit_balance").subtract(depositBalance));
        String depositId = ret.get("customerDepositId").asText();
        Map<String, Object> pay = jdbc.queryForMap("SELECT * FROM payments WHERE id = ?", depositId);
        assertEquals("CustomerDeposit", pay.get("payment_type"));
        assertEquals("Submitted", pay.get("status"));
        assertEquals(rn, pay.get("reference_number"));
        assertAmount("20000", pay.get("amount"));
        assertAmount("0", jdbc.queryForObject("SELECT outstanding FROM sales_invoices WHERE id = ?", BigDecimal.class, invId));

        ok(post(RETURNS + "/" + ret.get("id").asText() + "/cancel", map()));
        assertEquals("Cancelled", jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, depositId));
        assertAmount("0", balance("2.1.2").subtract(deposit));
        assertAmount("0", partyColumn(party, "customer_deposit_balance").subtract(depositBalance));
        assertAmount("30000", jdbc.queryForObject("SELECT outstanding FROM sales_invoices WHERE id = ?", BigDecimal.class, invId));

        // Role tanpa approval.bypass: antre (202) dengan nilai taksiran; disetujui → dibuat atas nama pengaju.
        String email = "akuntan." + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        asUser(email, "ERP_ACCOUNTANT");
        Result queued = call(post(RETURNS, body));
        assertEquals(202, queued.status(), String.valueOf(queued.body()));
        assertEquals("Retur menunggu persetujuan manajer sebelum diproses.", queued.data().get("message").asText());
        JsonNode req = queued.data().get("request");
        assertEquals("SalesReturn", req.get("documentType").asText());
        assertEquals("Retur Penjualan (menunggu approval)", req.get("documentNumber").asText());
        assertAmount("50000", req.get("amount"));
        assertEquals(403, call(post(RETURNS + "/" + ret.get("id").asText() + "/cancel", map())).status());

        ErpApprovalHandler.ApprovedDocument doc = approve(req.get("id").asText(), adminId);
        assertTrue(doc.number().startsWith("RN-"), doc.number());
        assertEquals(userId(email), jdbc.queryForObject("SELECT created_by FROM sales_returns WHERE id = ?", String.class, doc.id()));
        assertBalancedJournal("JV-" + doc.number(), "Submitted");

        asAdmin();
        assertEquals("Baris invoice wajib dipilih.", call(post(RETURNS, map("salesInvoiceId", invId, "date", today(),
                "items", List.of(map("salesInvoiceItemId", "", "quantity", 1))))).error());
        assertEquals("Qty retur harus > 0.", call(post(RETURNS, map("salesInvoiceId", invId, "date", today(),
                "items", List.of(map("salesInvoiceItemId", lineId, "quantity", -1))))).error());
        assertEquals("Invoice penjualan tidak ditemukan.", call(post(RETURNS, map("salesInvoiceId", UUID.randomUUID().toString(),
                "date", today(), "items", List.of(map("salesInvoiceItemId", lineId, "quantity", 1))))).error());
    }

    /**
     * Invoice penjualan langsung lama (sebelum direct-sale ditutup): stok sudah keluar oleh invoice
     * (gerakan Out referenceType SalesInvoice) dan belum punya Surat Jalan. Dibuat lewat SQL karena
     * submit invoice sekarang menolak baris persediaan tanpa DO.
     */
    private String[] legacyDirectSaleInvoice(String party, String item, String itemName, String userId) {
        String fy = jdbc.queryForObject("SELECT id FROM fiscal_years WHERE is_closed = false AND start_date <= now() "
                + "AND end_date >= now() ORDER BY start_date DESC LIMIT 1", String.class);
        String invId = UUID.randomUUID().toString();
        String invNo = uniq("SI-T");
        jdbc.update("INSERT INTO sales_invoices (id, invoice_number, date, party_id, status, grand_total, outstanding, "
                + "fiscal_year_id, created_by, submitted_at, created_at, updated_at) "
                + "VALUES (?, ?, now(), ?, 'Submitted', 30000, 30000, ?, ?, now(), now(), now())", invId, invNo, party, fy, userId);
        jdbc.update("INSERT INTO sales_invoice_items (id, sales_invoice_id, item_type, inventory_item_id, warehouse_id, item_name, "
                        + "quantity, unit, rate, amount, account_id) VALUES (?, ?, 'service', NULL, NULL, 'Jasa antar', 1, 'jasa', 10000, 10000, ?)",
                UUID.randomUUID().toString(), invId, accountId("4.1"));
        jdbc.update("INSERT INTO sales_invoice_items (id, sales_invoice_id, item_type, inventory_item_id, warehouse_id, item_name, "
                        + "quantity, unit, rate, amount, account_id) VALUES (?, ?, 'product', ?, ?, ?, 5, 'kg', 4000, 20000, ?)",
                UUID.randomUUID().toString(), invId, item, defaultWarehouseId(), itemName, accountId("4.1"));
        String smId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO stock_movements (id, movement_number, date, item_id, warehouse_id, movement_type, quantity, unit_cost, "
                        + "total_value, reference_type, reference_id, reference_number, fiscal_year_id, created_by_id, created_at, updated_at) "
                        + "VALUES (?, ?, now(), ?, ?, 'Out', 5, 3000, 15000, 'SalesInvoice', ?, ?, ?, ?, now(), now())",
                smId, uniq("SM-T"), item, defaultWarehouseId(), invId, invNo, fy, userId);
        jdbc.update("UPDATE inventory_items SET current_stock = current_stock - 5 WHERE id = ?", item);
        jdbc.update("UPDATE inventory_stocks SET current_stock = current_stock - 5 WHERE item_id = ?", item);
        return new String[]{invId, invNo, smId};
    }

    @Test
    void delivery_order_from_direct_sale_invoice_reuses_invoice_movements() throws Exception {
        String adminId = adminUserId();
        String party = customer(uniq("Cust Langsung"));
        String itemName = uniq("Dedak");
        String item = item(itemName, "3000", "20");
        String[] legacy = legacyDirectSaleInvoice(party, item, itemName, adminId);
        String invId = legacy[0];
        String invNo = legacy[1];
        assertAmount("15", itemStock(item));

        Result r = call(post(DO + "/from-invoice/" + invId, map("date", today(), "driverName", "Andi")));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        JsonNode d = r.data();
        String doId = d.get("id").asText();
        assertEquals(invId, d.get("invoiceId").asText());
        assertEquals("Andi", d.get("driverName").asText());
        assertTrue(!d.has("items"));
        assertAmount("15", itemStock(item));
        String invoiceSm = legacy[2];
        Map<String, Object> doItem = jdbc.queryForMap("SELECT * FROM delivery_order_items WHERE delivery_order_id = ?", doId);
        assertEquals(invoiceSm, doItem.get("stock_movement_id"));
        assertEquals(doItem.get("id"), jdbc.queryForObject("SELECT delivery_order_item_id FROM sales_invoice_items "
                + "WHERE sales_invoice_id = ? AND inventory_item_id = ?", String.class, invId, item));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE reference_id = ?", Integer.class, doId));

        assertEquals("Invoice " + invNo + " sudah punya surat jalan: " + d.get("doNumber").asText() + ".",
                call(post(DO + "/from-invoice/" + invId, map("date", today()))).error());
        assertEquals("Batalkan invoice terkait dulu sebelum batalkan surat jalan.",
                call(post(DO + "/" + doId + "/cancel", map())).error());
        assertEquals("Invoice tidak ditemukan.", call(post(DO + "/from-invoice/" + UUID.randomUUID(), map("date", today()))).error());
        assertEquals("Tanggal wajib diisi.", call(post(DO + "/from-invoice/" + invId, map("date", ""))).error());

        JsonNode service = ok(post(INVOICES, map("date", today(), "partyId", party, "items", List.of(
                map("itemName", "Jasa", "itemType", "service", "quantity", 1, "unit", "jasa", "rate", 1000)))));
        assertEquals("Invoice " + service.get("invoiceNumber").asText() + " tidak punya baris barang fisik untuk dikirim.",
                call(post(DO + "/from-invoice/" + service.get("id").asText(), map("date", today()))).error());
    }
}
