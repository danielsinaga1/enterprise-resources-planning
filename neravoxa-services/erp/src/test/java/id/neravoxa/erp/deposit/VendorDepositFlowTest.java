package id.neravoxa.erp.deposit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Alur uang muka supplier: aplikasi/batal aplikasi, refund+offset, batal uang muka, daftar & ringkasan. */
class VendorDepositFlowTest extends DepositTestSupport {

    private static final String BASE = "/api/erp/vendor-deposits";

    @Test
    void apply_posts_gl_and_cancel_application_reverses_it() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Vend Apply"), "Supplier");
        BigDecimal dep0 = balance(VENDOR_DEPOSIT);
        BigDecimal ap0 = balance(AP);

        String deposit = payVendorDeposit(party, "800000", userId);
        String invoice = createPurchaseInvoice(party, "500000", 4, userId);

        Result r = call(post(BASE + "/apply", body("depositPaymentId", deposit, "purchaseInvoiceId", invoice, "amount", 300000)));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        JsonNode app = r.data();
        assertEquals(invoice, app.get("purchaseInvoiceId").asText());
        assertMoney("300000", app.get("appliedAmount"));
        String journalId = app.get("journalEntryId").asText();
        assertLedger(journalId, false);

        // DR 2.1.1 (hutang berkurang) / CR 1.3 (uang muka berkurang)
        assertMoney(dep0.add(new BigDecimal("500000")).toPlainString(), balance(VENDOR_DEPOSIT));
        assertMoney(ap0.subtract(new BigDecimal("300000")).toPlainString(), balance(AP));
        assertMoney("200000", invoiceOutstanding("purchase_invoices", invoice));
        assertEquals("PartiallyPaid", invoiceStatus("purchase_invoices", invoice));
        assertMoney("500000", partyColumn(party, "deposit_balance"));
        assertMoney("200000", partyColumn(party, "outstanding_amount"));

        JsonNode bal = ok(get(BASE + "/balance/" + party));
        JsonNode item = bal.get("data").get(0);
        assertMoney("300000", item.get("totalApplied"));
        assertMoney("0", item.get("totalRefunded"));
        assertMoney("500000", item.get("remaining"));
        assertMoney("500000", bal.get("totalBalance"));

        ok(post(BASE + "/apply/" + app.get("id").asText() + "/cancel", body()));
        assertEquals("Cancelled", journalStatus(journalId));
        assertLedger(journalId, true);
        assertMoney(dep0.add(new BigDecimal("800000")).toPlainString(), balance(VENDOR_DEPOSIT));
        assertMoney(ap0.toPlainString(), balance(AP));
        assertMoney("500000", invoiceOutstanding("purchase_invoices", invoice));
        assertEquals("Submitted", invoiceStatus("purchase_invoices", invoice));
        assertMoney("800000", partyColumn(party, "deposit_balance"));
        assertMoney("500000", partyColumn(party, "outstanding_amount"));
    }

    @Test
    void refund_offsets_payables_fifo_and_receives_cash_back() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Vend Refund"), "Supplier");
        String deposit = payVendorDeposit(party, "1000000", userId);
        String older = createPurchaseInvoice(party, "400000", 9, userId);
        String newer = createPurchaseInvoice(party, "300000", 1, userId);
        BigDecimal bank0 = balance(BANK);
        BigDecimal dep0 = balance(VENDOR_DEPOSIT);
        BigDecimal ap0 = balance(AP);

        JsonNode res = ok(post(BASE + "/" + deposit + "/refund", body("date", today(), "offsetAmount", 500000,
                "cashAmount", 250000, "cashAccountId", accountId(BANK))));
        assertEquals("Uang muka supplier diselesaikan.", res.get("message").asText());
        assertEquals(2, res.get("applications").size());
        assertLedger(res.get("cashJournalId").asText(), false);

        assertMoney("0", invoiceOutstanding("purchase_invoices", older));
        assertEquals("Paid", invoiceStatus("purchase_invoices", older));
        assertMoney("200000", invoiceOutstanding("purchase_invoices", newer));
        assertEquals("PartiallyPaid", invoiceStatus("purchase_invoices", newer));

        // Kas kembali dari vendor: DR Bank / CR 1.3.
        assertMoney(bank0.add(new BigDecimal("250000")).toPlainString(), balance(BANK));
        assertMoney(dep0.subtract(new BigDecimal("750000")).toPlainString(), balance(VENDOR_DEPOSIT));
        assertMoney(ap0.subtract(new BigDecimal("500000")).toPlainString(), balance(AP));
        assertMoney("250000", partyColumn(party, "deposit_balance"));
        assertMoney("200000", partyColumn(party, "outstanding_amount"));

        // Sisa 250rb: aplikasi supplier ikut mengurangkan refund.
        String third = createPurchaseInvoice(party, "900000", 0, userId);
        assertError("Sisa deposit hanya Rp 250000.00. Tidak cukup.",
                post(BASE + "/apply", body("depositPaymentId", deposit, "purchaseInvoiceId", third, "amount", 300000)));
        assertError("Hutang supplier tidak cukup untuk offset. Sisa offset yang tidak teralokasi: Rp 50000.00.",
                post(BASE + "/" + createShortVendor(userId) + "/refund", body("date", today(), "offsetAmount", 150000)));
        assertError("Masukkan jumlah offset hutang atau refund kas minimal salah satu.",
                post(BASE + "/" + deposit + "/refund", body("date", today())));
    }

    /** Supplier dengan uang muka 200rb dan hutang 100rb. */
    private String createShortVendor(String userId) {
        String party = createParty(uniq("Vend Short"), "Supplier");
        String deposit = payVendorDeposit(party, "200000", userId);
        createPurchaseInvoice(party, "100000", 1, userId);
        return deposit;
    }

    @Test
    void cancel_deposit_reverses_payment_journal() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Vend Cancel"), "Supplier");
        BigDecimal dep0 = balance(VENDOR_DEPOSIT);
        BigDecimal cash0 = balance(CASH);
        String deposit = payVendorDeposit(party, "350000", userId);
        String journal = jdbc.queryForObject("SELECT journal_entry_id FROM payments WHERE id = ?", String.class, deposit);

        JsonNode res = ok(MockMvcRequestBuilders.patch(BASE + "/" + deposit + "/cancel"));
        assertEquals("Uang muka supplier dibatalkan.", res.get("message").asText());
        assertEquals("Cancelled", journalStatus(journal));
        assertLedger(journal, true);
        assertMoney(dep0.toPlainString(), balance(VENDOR_DEPOSIT));
        assertMoney(cash0.toPlainString(), balance(CASH));
        assertMoney("0", partyColumn(party, "deposit_balance"));

        assertError("Deposit sudah dibatalkan.", MockMvcRequestBuilders.patch(BASE + "/" + deposit + "/cancel"));
        String customerDeposit = receiveCustomerDeposit(createParty(uniq("Cust X"), "Customer"), "1000", userId);
        assertError("Bukan tipe uang muka supplier.", MockMvcRequestBuilders.patch(BASE + "/" + customerDeposit + "/cancel"));
    }

    /** Perbaikan atas sumber: uang muka supplier yang sudah direfund kas (di sini penuh) tidak boleh dibatalkan. */
    @Test
    void cancel_rejects_fully_refunded_vendor_deposit() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Vend NoCancel"), "Supplier");
        String deposit = payVendorDeposit(party, "200000", userId);
        String journal = jdbc.queryForObject("SELECT journal_entry_id FROM payments WHERE id = ?", String.class, deposit);
        ok(post(BASE + "/" + deposit + "/refund",
                body("date", today(), "cashAmount", 200000, "cashAccountId", accountId(BANK))));
        BigDecimal dep0 = balance(VENDOR_DEPOSIT);
        BigDecimal cash0 = balance(CASH);

        assertError("Deposit sudah direfund sebagian/seluruhnya; tidak dapat dibatalkan.",
                MockMvcRequestBuilders.patch(BASE + "/" + deposit + "/cancel"));
        assertEquals("Submitted", jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, deposit));
        assertEquals("Submitted", journalStatus(journal));
        assertMoney(dep0.toPlainString(), balance(VENDOR_DEPOSIT));
        assertMoney(cash0.toPlainString(), balance(CASH));
        assertMoney("0", partyColumn(party, "deposit_balance"));
    }

    @Test
    void cancel_legacy_vendor_deposit_reverses_balances_directly() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Vend Legacy"), "Supplier");
        String deposit = legacyDeposit(party, "120000", userId, false);
        BigDecimal dep0 = balance(VENDOR_DEPOSIT);
        BigDecimal eq0 = balance(OPENING_EQUITY);

        ok(MockMvcRequestBuilders.patch(BASE + "/" + deposit + "/cancel"));
        // CR Uang Muka Pembelian (aset turun) / DR Ekuitas Saldo Awal (ekuitas turun)
        assertMoney(dep0.subtract(new BigDecimal("120000")).toPlainString(), balance(VENDOR_DEPOSIT));
        assertMoney(eq0.subtract(new BigDecimal("120000")).toPlainString(), balance(OPENING_EQUITY));
        assertMoney("0", partyColumn(party, "deposit_balance"));
    }

    @Test
    void list_paged_with_supplier_sort_and_summary() throws Exception {
        String userId = adminUserId();
        String name = uniq("Vend List");
        String party = createParty(name, "Supplier");
        String a = payVendorDeposit(party, "700000", userId);
        String b = payVendorDeposit(party, "200000", userId);
        String inv = createPurchaseInvoice(party, "200000", 1, userId);
        ok(post(BASE + "/apply", body("depositPaymentId", b, "purchaseInvoiceId", inv, "amount", 200000)));

        JsonNode page = ok(get(BASE + "?page=1&sort=supplier&partyId=" + party));
        assertEquals(2, page.get("total").asInt());
        for (JsonNode row : page.get("data")) {
            assertTrue(row.get("depositApplications").isArray());
            assertEquals(row.get("id").asText().equals(a) ? "Active" : "FullyUsed", row.get("state").asText());
        }
        JsonNode fullyUsed = ok(get(BASE + "?page=1&status=FullyUsed&partyId=" + party));
        assertEquals(1, fullyUsed.get("total").asInt());
        assertEquals(b, fullyUsed.get("data").get(0).get("id").asText());
        assertEquals(inv, fullyUsed.get("data").get(0).get("depositApplications").get(0).get("purchaseInvoiceId").asText());

        JsonNode summary = ok(get(BASE + "/summary?partyId=" + party));
        assertEquals(2, summary.get("count").asInt());
        assertMoney("900000", summary.get("totalDeposit"));
        assertMoney("700000", summary.get("totalRemaining"));
        assertEquals(1, summary.get("activeCount").asInt());

        JsonNode balance = ok(get(BASE + "/balance/" + party));
        assertEquals(1, balance.get("data").size());
        assertEquals(a, balance.get("data").get(0).get("id").asText());

        Result badLegacy = call(get(BASE + "?status=Active"));
        assertEquals(500, badLegacy.status());
        assertEquals("Gagal mengambil data uang muka supplier.", badLegacy.error());
    }

    private void assertError(String message, MockHttpServletRequestBuilder req) throws Exception {
        Result r = call(req);
        assertEquals(400, r.status(), String.valueOf(r.body()));
        assertEquals(message, r.error());
    }
}
