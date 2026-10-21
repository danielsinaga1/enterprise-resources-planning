package id.enterpriseapps.erp.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Alur invoice penjualan: submit (GL), daftar/ringkasan/detail, edit meta, batal (membalik GL & DO). */
class SalesInvoiceFlowTest extends SalesTestSupport {

    private static final String BASE = "/api/erp/sales/invoices";

    @Test
    void submit_service_invoice_posts_balanced_journal_and_cancel_reverses() throws Exception {
        adminUserId();
        String partyName = uniq("CUST-SI");
        String partyId = createCustomer(partyName);
        BigDecimal arBefore = balance("1.2.1");
        BigDecimal svcBefore = balance("4.2");
        BigDecimal ppnBefore = balance("2.2.1");
        BigDecimal pphBefore = balance("1.5.4");

        Map<String, Object> line = serviceLine("Jasa Konsultansi", 1, 5_000_000);
        line.put("taxPct", 11);
        line.put("pphPct", 2);
        Map<String, Object> body = invoiceBody(partyId, List.of(line));
        body.put("dueDate", daysFromToday(30));
        body.put("notes", "uji");
        Result created = call(post(BASE, body));
        assertEquals(201, created.status(), created.body().toString());
        JsonNode inv = created.data();
        String no = inv.get("invoiceNumber").asText();
        assertTrue(no.startsWith("SI-"));
        assertEquals("Submitted", inv.get("status").asText());
        assertAmount("5450000", inv.get("grandTotal"));
        assertAmount("5450000", inv.get("outstanding"));
        assertEquals(partyName, inv.get("customer").get("name").asText());
        assertEquals(1, inv.get("items").size());
        assertAmount("5000000", inv.get("items").get(0).get("amount"));
        assertEquals(accountId("4.2"), inv.get("items").get(0).get("accountId").asText());

        // Jurnal pendapatan seimbang: DR AR 5.450.000 + PPh 100.000 = CR Jasa 5.000.000 + PPN 550.000.
        assertAmount("5550000", assertBalancedJournal("JV-" + no, "Submitted"));
        assertAmount("5450000", balance("1.2.1").subtract(arBefore));
        assertAmount("5000000", balance("4.2").subtract(svcBefore));
        assertAmount("550000", balance("2.2.1").subtract(ppnBefore));
        assertAmount("100000", balance("1.5.4").subtract(pphBefore));
        String arParty = jdbc.queryForObject("SELECT ji.party_id FROM journal_entry_accounts ji JOIN journal_entries j "
                + "ON j.id = ji.journal_entry_id WHERE j.entry_number = ? AND ji.debit = 5450000", String.class, "JV-" + no);
        assertEquals(partyId, arParty);
        assertAmount("5450000", partyOutstanding(partyId));

        // Daftar + ringkasan + detail.
        JsonNode list = ok(get(BASE + "?search=" + no));
        assertEquals(1, list.get("total").asInt());
        JsonNode row = list.get("data").get(0);
        assertAmount("0", row.get("deductionTotal"));
        assertEquals(partyName, row.get("customer").get("name").asText());
        assertEquals(1, row.get("items").size());

        JsonNode summary = ok(get(BASE + "/summary?partyId=" + partyId));
        assertEquals(1, summary.get("count").asInt());
        assertAmount("5450000", summary.get("grandTotal"));
        assertEquals(1, summary.get("byStatus").get("Submitted").get("count").asInt());

        JsonNode detail = ok(get(BASE + "/" + inv.get("id").asText()));
        assertEquals("erp.admin", detail.get("user").get("fullName").asText());
        assertEquals(0, detail.get("paymentAllocations").size());
        assertEquals(0, detail.get("depositApplications").size());
        assertTrue(detail.get("items").get(0).get("serviceItem").isNull());
        assertTrue(detail.get("items").get(0).has("deliveryOrderItem"));

        // Edit metadata: trim, kosongkan, validasi tanggal.
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("notes", "  catatan baru  ");
        meta.put("terms", null);
        JsonNode updated = ok(put(BASE + "/" + inv.get("id").asText(), meta));
        assertEquals("catatan baru", updated.get("notes").asText());
        assertTrue(updated.get("terms").isNull());
        assertTrue(updated.get("dueDate").isTextual(), "dueDate tidak dikirim → tetap");
        Result badDue = call(put(BASE + "/" + inv.get("id").asText(), Map.of("dueDate", "bukan-tanggal")));
        assertEquals(400, badDue.status());
        assertEquals("Tanggal jatuh tempo tidak valid.", badDue.error());

        // Batal: tanpa capability doc.cancel → 403.
        asUser(uniq("acc") + "@test.local", "ERP_ACCOUNTANT");
        Result denied = call(post(BASE + "/" + inv.get("id").asText() + "/cancel", Map.of()));
        assertEquals(403, denied.status());

        asAdmin();
        JsonNode cancelled = ok(post(BASE + "/" + inv.get("id").asText() + "/cancel", Map.of()));
        assertEquals("Cancelled", cancelled.get("status").asText());
        assertEquals(inv.get("id").asText(), cancelled.get("id").asText());
        assertBalancedJournal("JV-" + no, "Cancelled");
        Integer activeLedger = jdbc.queryForObject("SELECT COUNT(*) FROM accounting_ledger_entries le JOIN journal_entries j "
                + "ON j.id = le.reference_id WHERE j.entry_number = ? AND le.is_cancelled = false", Integer.class, "JV-" + no);
        assertEquals(0, activeLedger);
        assertAmount("0", balance("1.2.1").subtract(arBefore));
        assertAmount("0", balance("4.2").subtract(svcBefore));
        assertAmount("0", balance("2.2.1").subtract(ppnBefore));
        assertAmount("0", partyOutstanding(partyId));
        Map<String, Object> invRow = jdbc.queryForMap("SELECT status, outstanding, cancelled_at FROM sales_invoices WHERE id = ?",
                inv.get("id").asText());
        assertEquals("Cancelled", invRow.get("status"));
        assertAmount("0", (BigDecimal) invRow.get("outstanding"));

        Result again = call(post(BASE + "/" + inv.get("id").asText() + "/cancel", Map.of()));
        assertEquals(400, again.status());
        assertEquals("Invoice sudah dibatalkan.", again.error());

        Result editCancelled = call(put(BASE + "/" + inv.get("id").asText(), Map.of("notes", "x")));
        assertEquals(400, editCancelled.status());
        assertEquals("Invoice sudah dibatalkan, tidak bisa diedit.", editCancelled.error());
    }

    @Test
    void validation_and_business_errors_match_source_messages() throws Exception {
        adminUserId();
        String partyId = createCustomer(uniq("CUST-VAL"));

        Result noItems = call(post(BASE, invoiceBody(partyId, List.of())));
        assertEquals(400, noItems.status());
        assertEquals("Minimal satu item.", noItems.error());

        Result zeroQty = call(post(BASE, invoiceBody(partyId, List.of(serviceLine("X", 0, 100)))));
        assertEquals("Jumlah harus lebih dari 0.", zeroQty.error());

        Map<String, Object> noDate = invoiceBody(partyId, List.of(serviceLine("X", 1, 100)));
        noDate.put("date", "");
        assertEquals("Tanggal wajib diisi.", call(post(BASE, noDate)).error());

        Result unknownParty = call(post(BASE, invoiceBody(UUID.randomUUID().toString(), List.of(serviceLine("X", 1, 100)))));
        assertEquals(400, unknownParty.status());
        assertEquals("Data customer tidak ditemukan.", unknownParty.error());

        // Barang inventori tanpa Surat Jalan ditolak dengan nama item.
        String itemName = uniq("Beras Premium");
        String itemId = createInventoryItem(itemName, null);
        Map<String, Object> invLine = new LinkedHashMap<>();
        invLine.put("itemName", "baris");
        invLine.put("inventoryItemId", itemId);
        invLine.put("quantity", 2);
        invLine.put("rate", 1000);
        Result orphan = call(post(BASE, invoiceBody(partyId, List.of(invLine))));
        assertEquals(400, orphan.status());
        assertEquals("Item inventori \"" + itemName + "\" tidak bisa di-invoice langsung. Buat Surat Jalan (Delivery Order) "
                + "terlebih dahulu. Invoice hanya bisa di-generate dari DO yang sudah submit. Stok keluar lewat DO, bukan invoice.",
                orphan.error());

        Result notFound = call(get(BASE + "/" + UUID.randomUUID()));
        assertEquals(404, notFound.status());
        assertEquals("Invoice tidak ditemukan.", notFound.error());

        Result badStatus = call(get(BASE + "?status=Unpaid"));
        assertEquals("Status invoice tidak dikenal.", badStatus.error());
        Result badDate = call(get(BASE + "/summary?startDate=01/02/2026"));
        assertEquals("Format tanggal harus YYYY-MM-DD.", badDate.error());

        // Sort whitelist: dueDate (nulls last), customer (join), rentang tanggal hari lokal.
        for (String sort : List.of("dueDate", "-dueDate", "customer", "-outstanding", "bogus")) {
            JsonNode page = ok(get(BASE + "?limit=3&page=1&sort=" + sort + "&startDate=" + today() + "&endDate=" + today()));
            assertEquals(3, page.get("limit").asInt());
        }
        assertEquals(200, call(get("/api/erp/quotations?sort=customer&limit=2")).status());
        assertEquals(200, call(get("/api/erp/inquiries?sort=prospectName&limit=2")).status());
    }

    @Test
    void invoice_from_delivery_order_posts_cogs_at_locked_cost_and_cancel_releases_do() throws Exception {
        String userId = adminUserId();
        String partyId = createCustomer(uniq("CUST-DO"));
        String itemName = uniq("Gula");
        String itemId = createInventoryItem(itemName, null);
        String[] dox = createDeliveryOrder(partyId, itemId, itemName, userId, "10", "7000.00");
        String doId = dox[0];
        String doItemId = dox[1];
        BigDecimal cogsBefore = balance("5.1");
        BigDecimal invBefore = balance("1.4.0");
        BigDecimal salesBefore = balance("4.1");

        Map<String, Object> line = new LinkedHashMap<>();
        line.put("itemName", itemName);
        line.put("itemType", "product");
        line.put("inventoryItemId", itemId);
        line.put("quantity", 10);
        line.put("unit", "Kg");
        line.put("rate", 10_000);
        line.put("deliveryOrderItemId", doItemId);
        JsonNode inv = ok(post(BASE, invoiceBody(partyId, List.of(line))));
        String no = inv.get("invoiceNumber").asText();
        String invId = inv.get("id").asText();

        assertAmount("100000", assertBalancedJournal("JV-" + no, "Submitted"));
        // HPP memakai unit cost terkunci di gerakan stok DO (7.000), bukan averageCost item (6.500).
        assertAmount("70000", assertBalancedJournal("JV-COGS-" + no, "Submitted"));
        assertAmount("70000", balance("5.1").subtract(cogsBefore));
        assertAmount("-70000", balance("1.4.0").subtract(invBefore));
        assertAmount("100000", balance("4.1").subtract(salesBefore));
        assertEquals(invId, jdbc.queryForObject("SELECT invoice_id FROM delivery_orders WHERE id = ?", String.class, doId));

        JsonNode detail = ok(get(BASE + "/" + invId));
        JsonNode dItem = detail.get("items").get(0);
        assertEquals(doItemId, dItem.get("deliveryOrderItem").get("id").asText());
        assertEquals(dox[2], dItem.get("deliveryOrderItem").get("deliveryOrder").get("doNumber").asText());
        assertAmount("6500", dItem.get("inventoryItem").get("averageCost"));

        // Baris DO yang sama tidak bisa ditautkan ke invoice kedua (unik per baris DO → P2002).
        Result dup = call(post(BASE, invoiceBody(partyId, List.of(line))));
        assertEquals(400, dup.status());
        assertEquals("Data duplikat terdeteksi. Silakan coba lagi.", dup.error());

        ok(post(BASE + "/" + invId + "/cancel", Map.of()));
        assertBalancedJournal("JV-COGS-" + no, "Cancelled");
        assertAmount("0", balance("5.1").subtract(cogsBefore));
        assertAmount("0", balance("1.4.0").subtract(invBefore));
        assertNull(jdbc.queryForObject("SELECT invoice_id FROM delivery_orders WHERE id = ?", String.class, doId));
        assertNull(jdbc.queryForObject("SELECT delivery_order_item_id FROM sales_invoice_items WHERE sales_invoice_id = ?",
                String.class, invId));
        // Gerakan stok DO tetap sah.
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT is_cancelled FROM stock_movements WHERE id = ?", Boolean.class, dox[3]));

        // DO yang dilepas bisa ditagih invoice baru.
        JsonNode again = ok(post(BASE, invoiceBody(partyId, List.of(line))));
        assertEquals(again.get("id").asText(), jdbc.queryForObject("SELECT invoice_id FROM delivery_orders WHERE id = ?", String.class, doId));

        // DO yang dibatalkan tidak bisa ditagih.
        String[] cancelledDo = createDeliveryOrder(partyId, itemId, itemName, userId, "1", "7000.00");
        jdbc.update("UPDATE delivery_orders SET status = 'Cancelled' WHERE id = ?", cancelledDo[0]);
        line.put("deliveryOrderItemId", cancelledDo[1]);
        line.put("quantity", 1);
        Result rejected = call(post(BASE, invoiceBody(partyId, List.of(line))));
        assertEquals(400, rejected.status());
        assertEquals("Surat Jalan " + cancelledDo[2] + " sudah dibatalkan dan tidak bisa di-invoice.", rejected.error());
    }

    @Test
    void zero_value_invoice_is_paid_and_still_cancellable() throws Exception {
        adminUserId();
        String partyId = createCustomer(uniq("CUST-ZERO"));
        JsonNode inv = ok(post(BASE, invoiceBody(partyId, List.of(serviceLine("Sampel gratis", 1, 0)))));
        assertEquals("Paid", inv.get("status").asText());
        String no = inv.get("invoiceNumber").asText();
        assertAmount("0", assertBalancedJournal("JV-" + no, "Submitted"));
        JsonNode cancelled = ok(post(BASE + "/" + inv.get("id").asText() + "/cancel", Map.of()));
        assertEquals("Cancelled", cancelled.get("status").asText());
    }

    @Test
    void allocated_payment_shows_in_detail_feeds_deduction_and_blocks_cancel() throws Exception {
        String userId = adminUserId();
        String partyId = createCustomer(uniq("CUST-PAY"));
        Map<String, Object> body = invoiceBody(partyId, List.of(serviceLine("Jasa", 1, 1000)));
        body.put("potongan", 0);
        body.put("biayaLain", 0);
        JsonNode inv = ok(post(BASE, body));
        String invId = inv.get("id").asText();

        String paymentId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO payments (id, payment_number, date, payment_type, party_id, account_id, amount, status, "
                        + "created_by, fiscal_year_id, created_at, updated_at) VALUES (?, ?, now(), 'Receive', ?, ?, 1000, 'Submitted', ?, ?, now(), now())",
                paymentId, uniq("PAY-T"), partyId, accountId("1.1.2"), userId, openFiscalYearId());
        jdbc.update("INSERT INTO payment_splits (id, payment_id, account_id, amount, notes) VALUES (?, ?, ?, 900, 'kas')",
                UUID.randomUUID().toString(), paymentId, accountId("1.1.2"));
        jdbc.update("INSERT INTO payment_splits (id, payment_id, account_id, amount, notes) VALUES (?, ?, ?, 100, 'komisi')",
                UUID.randomUUID().toString(), paymentId, accountId("5.1"));
        jdbc.update("INSERT INTO payment_allocations (id, payment_id, invoice_type, invoice_id, allocated_amount) "
                + "VALUES (?, ?, 'SalesInvoice', ?, 1000)", UUID.randomUUID().toString(), paymentId, invId);

        JsonNode detail = ok(get(BASE + "/" + invId));
        JsonNode alloc = detail.get("paymentAllocations").get(0);
        assertAmount("1000", alloc.get("allocatedAmount"));
        assertEquals(paymentId, alloc.get("payment").get("id").asText());
        assertEquals("1.1.2", alloc.get("payment").get("account").get("accountNumber").asText());
        assertEquals(2, alloc.get("payment").get("splits").size());

        JsonNode list = ok(get(BASE + "?partyId=" + partyId));
        assertAmount("100", list.get("data").get(0).get("deductionTotal"));

        Result blocked = call(post(BASE + "/" + invId + "/cancel", Map.of()));
        assertEquals(400, blocked.status());
        assertEquals("Invoice memiliki pembayaran teralokasi. Batalkan pembayaran terlebih dahulu.", blocked.error());
    }

    // ───────────────────────────── fixture ─────────────────────────────

    private String createInventoryItem(String name, String accountId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO inventory_items (id, code, name, unit, current_stock, average_cost, is_active, account_id, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'Kg', 0, 6500, true, ?, now(), now())", id, uniq("ITM"), name, accountId);
        return id;
    }

    /** DO Submitted + baris tertaut gerakan stok Out (unit cost terkunci). [doId, doItemId, doNumber, movementId]. */
    private String[] createDeliveryOrder(String partyId, String itemId, String itemName, String userId, String qty, String unitCost) {
        String fy = openFiscalYearId();
        String wh = jdbc.queryForObject("SELECT id FROM warehouses WHERE code = 'GDG-UTAMA'", String.class);
        String doId = UUID.randomUUID().toString();
        String doNumber = uniq("DO-T");
        jdbc.update("INSERT INTO delivery_orders (id, do_number, date, party_id, status, fiscal_year_id, created_by, submitted_at, "
                + "created_at, updated_at) VALUES (?, ?, now(), ?, 'Submitted', ?, ?, now(), now(), now())", doId, doNumber, partyId, fy, userId);
        String smId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO stock_movements (id, movement_number, date, item_id, warehouse_id, movement_type, quantity, unit_cost, "
                        + "total_value, reference_type, reference_id, reference_number, fiscal_year_id, created_by_id, created_at, updated_at) "
                        + "VALUES (?, ?, now(), ?, ?, 'Out', CAST(? AS numeric), CAST(? AS numeric), 0, 'DeliveryOrder', ?, ?, ?, ?, now(), now())",
                smId, uniq("SM-T"), itemId, wh, qty, unitCost, doId, doNumber, fy, userId);
        String doItemId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO delivery_order_items (id, delivery_order_id, inventory_item_id, warehouse_id, item_name, quantity, unit, "
                + "stock_movement_id, created_at) VALUES (?, ?, ?, ?, ?, CAST(? AS numeric), 'Kg', ?, now())",
                doItemId, doId, itemId, wh, itemName, qty, smId);
        return new String[]{doId, doItemId, doNumber, smId};
    }
}
