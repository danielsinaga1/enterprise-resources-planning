package id.neravoxa.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pemeriksaan notifikasi terjadwal: invoice jatuh tempo, stok rendah, uang jaminan jatuh tempo (+ deduplikasi 24 jam). */
class CashBankNotificationCheckTest extends CashBankTestSupport {

    @Autowired
    ErpInvoiceOverdueCheck invoiceOverdue;
    @Autowired
    ErpLowStockCheck lowStock;
    @Autowired
    ErpGuaranteeDueCheck guaranteeDue;

    private List<Map<String, Object>> notifications(String type, String key, String value) {
        return jdbc.queryForList("SELECT title, message, user_id FROM notifications WHERE type = ? AND metadata ->> ? = ?",
                type, key, value);
    }

    @Test
    void overdue_sales_and_purchase_invoices_are_notified_once() throws Exception {
        String userId = adminUserId();
        String customerName = uniq("Pelanggan Telat");
        String customer = createParty(customerName, "Customer");
        String supplier = createParty(uniq("Supplier Telat"), "Supplier");
        String siNumber = uniq("SI-OD");
        String si = createInvoice("sales_invoices", siNumber, customer, daysFromToday(-40), daysFromToday(-10),
                "100000", "100000", "Submitted");
        String pi = createInvoice("purchase_invoices", uniq("PI-OD"), supplier, daysFromToday(-40), daysFromToday(-1),
                "50000", "20000", "PartiallyPaid");
        String paid = createInvoice("sales_invoices", uniq("SI-PAID"), customer, daysFromToday(-40), daysFromToday(-10),
                "100000", "0", "Paid");
        Instant now = Instant.now();

        assertTrue(invoiceOverdue.run(List.of(userId), now, now.minus(Duration.ofHours(24))) >= 2);
        List<Map<String, Object>> sales = notifications("invoice_overdue", "invoiceId", si);
        assertEquals(1, sales.size());
        assertEquals("Invoice Penjualan Jatuh Tempo", sales.get(0).get("title"));
        assertEquals("Invoice " + siNumber + " untuk " + customerName + " sudah melewati jatuh tempo.", sales.get(0).get("message"));
        assertEquals("Invoice Pembelian Jatuh Tempo", notifications("invoice_overdue", "invoiceId", pi).get(0).get("title"));
        assertEquals(0, notifications("invoice_overdue", "invoiceId", paid).size());

        invoiceOverdue.run(List.of(userId), now, now.minus(Duration.ofHours(24)));
        assertEquals(1, notifications("invoice_overdue", "invoiceId", si).size());
        assertEquals(1, notifications("invoice_overdue", "invoiceId", pi).size());
        assertEquals(0, invoiceOverdue.run(List.of(), now, now));
    }

    @Test
    void low_stock_items_are_notified_once() throws Exception {
        String userId = adminUserId();
        String itemId = UUID.randomUUID().toString();
        String code = uniq("BRG");
        jdbc.update("INSERT INTO inventory_items (id, code, name, unit, current_stock, minimum_stock, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Semen Uji', 'sak', 3.000, 10.000, true, now(), now())", itemId, code);
        Instant now = Instant.now();
        assertTrue(lowStock.run(List.of(userId), now, now.minus(Duration.ofHours(24))) >= 1);
        List<Map<String, Object>> n = notifications("low_stock", "itemId", itemId);
        assertEquals(1, n.size());
        assertEquals("Stok Rendah", n.get(0).get("title"));
        assertEquals("Stok Semen Uji (" + code + ") tersisa 3 sak, di bawah minimum 10 sak.", n.get(0).get("message"));
        lowStock.run(List.of(userId), now, now.minus(Duration.ofHours(24)));
        assertEquals(1, notifications("low_stock", "itemId", itemId).size());
    }

    @Test
    void guarantees_near_or_past_due_are_notified() throws Exception {
        String userId = adminUserId();
        String ownerName = uniq("Pemberi Proyek");
        String owner = createParty(ownerName, "Customer");
        asAdmin();
        JsonNode soon = ok(post("/api/erp/project-guarantees", body("date", today(), "partyId", owner,
                "guaranteeType", "Penawaran", "projectName", "Gedung A", "amount", 1500000,
                "accountId", accountId("1.1.3"), "expectedReturnDate", "2026-10-25")));
        JsonNode late = ok(post("/api/erp/project-guarantees", body("date", today(), "partyId", owner,
                "guaranteeType", "Penawaran", "projectName", "Gedung B", "amount", 750000,
                "accountId", accountId("1.1.3"), "expectedReturnDate", "2026-10-01")));
        JsonNode far = ok(post("/api/erp/project-guarantees", body("date", today(), "partyId", owner,
                "guaranteeType", "Penawaran", "projectName", "Gedung C", "amount", 500000,
                "accountId", accountId("1.1.3"), "expectedReturnDate", "2027-01-01")));
        // Sebagian sudah kembali → sisa yang disebut di pesan.
        ok(post("/api/erp/project-guarantees/" + soon.get("id").asText() + "/settle", body("date", today(),
                "returnAmount", 500000, "returnAccountId", accountId("1.1.3"))));

        Instant now = Instant.parse("2026-10-20T03:00:00Z");
        assertTrue(guaranteeDue.run(List.of(userId), now, now.minus(Duration.ofHours(24))) >= 2);

        List<Map<String, Object>> s = notifications("guarantee_due", "guaranteeId", soon.get("id").asText());
        assertEquals(1, s.size());
        assertEquals("Uang Jaminan Mendekati Jatuh Tempo", s.get(0).get("title"));
        assertEquals(soon.get("guaranteeNumber").asText() + " (Gedung A) ke " + ownerName
                        + ": sisa Rp 1.000.000, jatuh tempo pengembalian 25 Oktober 2026. Tagih pengembalian ke pemberi proyek.",
                s.get(0).get("message"));
        List<Map<String, Object>> l = notifications("guarantee_due", "guaranteeId", late.get("id").asText());
        assertEquals("Uang Jaminan Lewat Jatuh Tempo", l.get(0).get("title"));
        assertTrue(((String) l.get(0).get("message")).contains("jatuh tempo pengembalian sudah lewat 1 Oktober 2026"));
        assertEquals(0, notifications("guarantee_due", "guaranteeId", far.get("id").asText()).size());

        guaranteeDue.run(List.of(userId), now, now.minus(Duration.ofHours(24)));
        assertEquals(1, notifications("guarantee_due", "guaranteeId", soon.get("id").asText()).size());
    }
}
