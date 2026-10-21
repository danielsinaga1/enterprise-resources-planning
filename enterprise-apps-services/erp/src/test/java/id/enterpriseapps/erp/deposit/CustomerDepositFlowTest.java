package id.enterpriseapps.erp.deposit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Alur uang muka pelanggan: aplikasi/batal aplikasi, refund+offset, batal uang muka, daftar & ringkasan. */
class CustomerDepositFlowTest extends DepositTestSupport {

    private static final String BASE = "/api/erp/customer-deposits";

    @Test
    void apply_posts_gl_and_cancel_application_reverses_it() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Cust Apply"), "Customer");
        BigDecimal dep0 = balance(CUSTOMER_DEPOSIT);
        BigDecimal ar0 = balance(AR);

        String deposit = receiveCustomerDeposit(party, "1000000", userId);
        String invoice = createSalesInvoice(party, "600000", 3, userId);
        assertMoney("1000000", partyColumn(party, "customer_deposit_balance"));

        Result r = call(post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", invoice, "amount", 400000)));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        JsonNode app = r.data();
        assertEquals(deposit, app.get("depositPaymentId").asText());
        assertEquals(invoice, app.get("salesInvoiceId").asText());
        assertMoney("400000", app.get("appliedAmount"));
        assertFalse(app.get("isCancelled").asBoolean());
        String journalId = app.get("journalEntryId").asText();
        assertLedger(journalId, false);
        String partyOnDepositLine = jdbc.queryForObject(
                "SELECT party_id FROM accounting_ledger_entries WHERE reference_id = ? AND account_id = ?",
                String.class, journalId, accountId(CUSTOMER_DEPOSIT));
        assertEquals(party, partyOnDepositLine);

        // DR 2.1.2 (liabilitas berkurang) / CR 1.2.1 (piutang berkurang)
        assertMoney(dep0.add(new BigDecimal("600000")).toPlainString(), balance(CUSTOMER_DEPOSIT));
        assertMoney(ar0.subtract(new BigDecimal("400000")).toPlainString(), balance(AR));
        assertMoney("200000", invoiceOutstanding("sales_invoices", invoice));
        assertEquals("PartiallyPaid", invoiceStatus("sales_invoices", invoice));
        assertMoney("600000", partyColumn(party, "customer_deposit_balance"));
        assertMoney("200000", partyColumn(party, "outstanding_amount"));

        JsonNode bal = ok(get(BASE + "/balance/" + party));
        assertEquals(1, bal.get("data").size());
        JsonNode item = bal.get("data").get(0);
        assertMoney("400000", item.get("totalApplied"));
        assertMoney("600000", item.get("remaining"));
        assertFalse(item.has("totalRefunded"));
        assertEquals(CASH, item.get("account").get("accountNumber").asText());
        assertMoney("600000", bal.get("totalBalance"));

        JsonNode cancelled = ok(post(BASE + "/apply/" + app.get("id").asText() + "/cancel", body()));
        assertEquals("Cancelled", cancelled.get("status").asText());
        assertEquals("Cancelled", journalStatus(journalId));
        assertLedger(journalId, true);
        assertMoney(dep0.add(new BigDecimal("1000000")).toPlainString(), balance(CUSTOMER_DEPOSIT));
        assertMoney(ar0.toPlainString(), balance(AR));
        assertMoney("600000", invoiceOutstanding("sales_invoices", invoice));
        assertEquals("Submitted", invoiceStatus("sales_invoices", invoice));
        assertMoney("1000000", partyColumn(party, "customer_deposit_balance"));
        assertMoney("600000", partyColumn(party, "outstanding_amount"));

        Result again = call(post(BASE + "/apply/" + app.get("id").asText() + "/cancel", body()));
        assertEquals(400, again.status());
        assertEquals("Aplikasi sudah dibatalkan.", again.error());
    }

    @Test
    void apply_full_outstanding_marks_invoice_paid() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Cust Full"), "Customer");
        String deposit = receiveCustomerDeposit(party, "500000", userId);
        String invoice = createSalesInvoice(party, "500000", 1, userId);

        ok(post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", invoice, "amount", "500000")));
        assertEquals("Paid", invoiceStatus("sales_invoices", invoice));
        assertMoney("0", invoiceOutstanding("sales_invoices", invoice));

        Result paid = call(post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", invoice, "amount", 1)));
        assertEquals(400, paid.status());
        assertEquals("Invoice sudah lunas.", paid.error());
    }

    @Test
    void apply_and_refund_guards_return_source_messages() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Cust Guard"), "Customer");
        String other = createParty(uniq("Cust Other"), "Customer");
        String deposit = receiveCustomerDeposit(party, "1000000", userId);
        String invoice = createSalesInvoice(party, "600000", 2, userId);
        String otherInvoice = createSalesInvoice(other, "100000", 2, userId);

        assertError("Sisa deposit hanya Rp 1000000.00. Tidak cukup.",
                post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", invoice, "amount", 1500000)));
        assertError("Sisa outstanding invoice hanya Rp 600000.00.",
                post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", invoice, "amount", 700000)));
        assertError("Deposit dan invoice harus dari customer yang sama.",
                post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", otherInvoice, "amount", 1000)));
        assertError("ID deposit harus UUID yang valid.",
                post(BASE + "/apply", body("depositPaymentId", "abc", "salesInvoiceId", invoice, "amount", 1000)));
        assertError("Invalid input: expected string, received undefined",
                post(BASE + "/apply", body("salesInvoiceId", invoice, "amount", 1000)));
        assertError("Invalid input: expected number, received NaN",
                post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", invoice)));
        assertError("Jumlah harus lebih dari 0.",
                post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", invoice, "amount", 0)));
        assertError("Deposit tidak ditemukan.",
                post(BASE + "/apply", body("depositPaymentId", UUID.randomUUID().toString(), "salesInvoiceId", invoice, "amount", 10)));
        assertError("Invoice penjualan tidak ditemukan.",
                post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", UUID.randomUUID().toString(), "amount", 10)));

        assertError("Masukkan jumlah offset piutang atau refund kas minimal salah satu.",
                post(BASE + "/" + deposit + "/refund", body("date", today(), "offsetAmount", 0, "cashAmount", 0)));
        assertError("Pilih akun kas/bank untuk refund kas.",
                post(BASE + "/" + deposit + "/refund", body("date", today(), "cashAmount", 1000)));
        assertError("Tanggal wajib diisi.",
                post(BASE + "/" + deposit + "/refund", body("date", "", "cashAmount", 1000)));
        assertError("Too small: expected number to be >=0",
                post(BASE + "/" + deposit + "/refund", body("date", today(), "offsetAmount", -1)));
        assertError("Total 2000000.00 melebihi sisa uang muka 1000000.00.",
                post(BASE + "/" + deposit + "/refund", body("date", today(), "cashAmount", 2000000, "cashAccountId", accountId(BANK))));
        assertError("Akun yang dipilih bukan kas/bank.",
                post(BASE + "/" + deposit + "/refund", body("date", today(), "cashAmount", 1000, "cashAccountId", accountId(AR))));
        assertError("Format tanggal tidak valid.",
                post(BASE + "/" + deposit + "/refund", body("date", "bukan-tanggal", "cashAmount", 1000, "cashAccountId", accountId(BANK))));
        assertError("Uang muka tidak ditemukan.",
                post(BASE + "/" + UUID.randomUUID() + "/refund", body("date", today(), "cashAmount", 1000, "cashAccountId", accountId(BANK))));
    }

    @Test
    void refund_offsets_receivables_fifo_and_refunds_cash() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Cust Refund"), "Customer");
        String deposit = receiveCustomerDeposit(party, "1000000", userId);
        String older = createSalesInvoice(party, "300000", 10, userId);
        String newer = createSalesInvoice(party, "500000", 2, userId);
        BigDecimal bank0 = balance(BANK);
        BigDecimal dep0 = balance(CUSTOMER_DEPOSIT);
        BigDecimal ar0 = balance(AR);

        JsonNode res = ok(post(BASE + "/" + deposit + "/refund", body("date", today(), "offsetAmount", 500000,
                "cashAmount", 200000, "cashAccountId", accountId(BANK), "notes", "selesai proyek")));
        assertEquals("Uang muka diselesaikan.", res.get("message").asText());
        assertMoney("500000", res.get("offsetApplied"));
        assertMoney("200000", res.get("cashRefunded"));
        assertEquals(2, res.get("applications").size());
        String cashJournal = res.get("cashJournalId").asText();
        assertLedger(cashJournal, false);

        // FIFO: faktur terlama lunas dulu, sisanya ke faktur berikutnya.
        String firstApp = res.get("applications").get(0).asText();
        assertEquals(older, jdbc.queryForObject(
                "SELECT sales_invoice_id FROM customer_deposit_applications WHERE id = ?", String.class, firstApp));
        assertMoney("0", invoiceOutstanding("sales_invoices", older));
        assertEquals("Paid", invoiceStatus("sales_invoices", older));
        assertMoney("300000", invoiceOutstanding("sales_invoices", newer));
        assertEquals("PartiallyPaid", invoiceStatus("sales_invoices", newer));

        assertMoney(bank0.subtract(new BigDecimal("200000")).toPlainString(), balance(BANK));
        assertMoney(dep0.subtract(new BigDecimal("700000")).toPlainString(), balance(CUSTOMER_DEPOSIT));
        assertMoney(ar0.subtract(new BigDecimal("500000")).toPlainString(), balance(AR));
        assertMoney("300000", partyColumn(party, "customer_deposit_balance"));
        assertMoney("300000", partyColumn(party, "outstanding_amount"));
        assertMoney("200000", jdbc.queryForObject("SELECT refunded_amount FROM payments WHERE id = ?", BigDecimal.class, deposit));

        JsonNode bal = ok(get(BASE + "/balance/" + party));
        assertMoney("300000", bal.get("totalBalance"));
    }

    @Test
    void refund_offset_exceeding_receivables_rolls_back() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Cust Short"), "Customer");
        String deposit = receiveCustomerDeposit(party, "500000", userId);
        String invoice = createSalesInvoice(party, "100000", 1, userId);

        assertError("Piutang customer tidak cukup untuk offset. Sisa offset yang tidak teralokasi: Rp 100000.00.",
                post(BASE + "/" + deposit + "/refund", body("date", today(), "offsetAmount", 200000)));
        assertMoney("100000", invoiceOutstanding("sales_invoices", invoice));
        assertEquals("Submitted", invoiceStatus("sales_invoices", invoice));
        assertMoney("500000", partyColumn(party, "customer_deposit_balance"));
        Integer apps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer_deposit_applications WHERE deposit_payment_id = ?", Integer.class, deposit);
        assertEquals(0, apps);
    }

    @Test
    void cancel_deposit_reverses_receipt_and_blocks_when_applied() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Cust Cancel"), "Customer");
        BigDecimal dep0 = balance(CUSTOMER_DEPOSIT);
        BigDecimal cash0 = balance(CASH);

        String used = receiveCustomerDeposit(party, "250000", userId);
        String invoice = createSalesInvoice(party, "100000", 1, userId);
        ok(post(BASE + "/apply", body("depositPaymentId", used, "salesInvoiceId", invoice, "amount", 50000)));
        assertError("Deposit sudah digunakan untuk invoice. Batalkan penggunaan terlebih dahulu.",
                MockMvcRequestBuilders.patch(BASE + "/" + used + "/cancel"));

        String deposit = receiveCustomerDeposit(party, "400000", userId);
        String journal = jdbc.queryForObject("SELECT journal_entry_id FROM payments WHERE id = ?", String.class, deposit);
        JsonNode res = ok(MockMvcRequestBuilders.patch(BASE + "/" + deposit + "/cancel"));
        assertEquals("Uang muka customer dibatalkan.", res.get("message").asText());
        assertEquals("Cancelled", jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, deposit));
        assertNotNull(jdbc.queryForObject("SELECT cancelled_at FROM payments WHERE id = ?", java.sql.Timestamp.class, deposit));
        assertEquals("Cancelled", journalStatus(journal));
        assertLedger(journal, true);
        // Tersisa uang muka pertama (250rb − 50rb terpakai).
        assertMoney(dep0.add(new BigDecimal("200000")).toPlainString(), balance(CUSTOMER_DEPOSIT));
        assertMoney(cash0.add(new BigDecimal("250000")).toPlainString(), balance(CASH));
        assertMoney("200000", partyColumn(party, "customer_deposit_balance"));

        assertError("Deposit sudah dibatalkan.", MockMvcRequestBuilders.patch(BASE + "/" + deposit + "/cancel"));
        assertError("Uang muka sudah dibatalkan.",
                post(BASE + "/" + deposit + "/refund", body("date", today(), "cashAmount", 1, "cashAccountId", accountId(BANK))));
        assertError("Deposit sudah dibatalkan.",
                post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", invoice, "amount", 1)));
    }

    /** Perbaikan atas sumber: sisa untuk aplikasi dikurangi refund kas (seperti rute supplier). */
    @Test
    void apply_remaining_subtracts_cash_refunds() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Cust Refunded"), "Customer");
        String deposit = receiveCustomerDeposit(party, "1000000", userId);
        String invoice = createSalesInvoice(party, "900000", 1, userId);
        ok(post(BASE + "/" + deposit + "/refund",
                body("date", today(), "cashAmount", 300000, "cashAccountId", accountId(BANK))));
        BigDecimal dep0 = balance(CUSTOMER_DEPOSIT);

        // Sebelum perbaikan sisa dihitung 1.000.000 sehingga 800rb lolos dan 2.1.2 mitra jadi minus.
        assertError("Sisa deposit hanya Rp 700000.00. Tidak cukup.",
                post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", invoice, "amount", 800000)));
        assertMoney("900000", invoiceOutstanding("sales_invoices", invoice));
        assertMoney("700000", partyColumn(party, "customer_deposit_balance"));
        assertMoney(dep0.toPlainString(), balance(CUSTOMER_DEPOSIT));

        ok(post(BASE + "/apply", body("depositPaymentId", deposit, "salesInvoiceId", invoice, "amount", 700000)));
        assertMoney("0", partyColumn(party, "customer_deposit_balance"));
        assertMoney("200000", invoiceOutstanding("sales_invoices", invoice));
        assertMoney(dep0.subtract(new BigDecimal("700000")).toPlainString(), balance(CUSTOMER_DEPOSIT));
        assertEquals(0, ok(get(BASE + "/balance/" + party)).get("data").size());
    }

    /** Perbaikan atas sumber: uang muka yang sudah direfund kas tidak boleh dibatalkan (tanpa pembalikan parsial). */
    @Test
    void cancel_rejects_partially_refunded_deposit() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Cust NoCancel"), "Customer");
        String deposit = receiveCustomerDeposit(party, "400000", userId);
        String journal = jdbc.queryForObject("SELECT journal_entry_id FROM payments WHERE id = ?", String.class, deposit);
        ok(post(BASE + "/" + deposit + "/refund",
                body("date", today(), "cashAmount", 100000, "cashAccountId", accountId(BANK))));
        BigDecimal dep0 = balance(CUSTOMER_DEPOSIT);
        BigDecimal cash0 = balance(CASH);

        assertError("Deposit sudah direfund sebagian/seluruhnya; tidak dapat dibatalkan.",
                MockMvcRequestBuilders.patch(BASE + "/" + deposit + "/cancel"));
        assertEquals("Submitted", jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, deposit));
        assertEquals("Submitted", journalStatus(journal));
        assertLedger(journal, false);
        assertMoney(dep0.toPlainString(), balance(CUSTOMER_DEPOSIT));
        assertMoney(cash0.toPlainString(), balance(CASH));
        assertMoney("300000", partyColumn(party, "customer_deposit_balance"));
    }

    @Test
    void cancel_legacy_deposit_without_journal_reverses_balances_directly() throws Exception {
        String userId = adminUserId();
        String party = createParty(uniq("Cust Legacy"), "Customer");
        String deposit = legacyDeposit(party, "150000", userId, true);
        String number = jdbc.queryForObject("SELECT payment_number FROM payments WHERE id = ?", String.class, deposit);
        String fy = fiscalYearId();
        String ledgerRef = UUID.randomUUID().toString();
        for (String[] line : new String[][]{{OPENING_EQUITY, "150000", "0"}, {CUSTOMER_DEPOSIT, "0", "150000"}}) {
            jdbc.update("INSERT INTO accounting_ledger_entries (id, date, account_id, party_id, debit, credit, reference_type, "
                            + "reference_id, description, fiscal_year_id, is_cancelled, created_at) "
                            + "VALUES (?, now(), ?, ?, ?, ?, 'Import', ?, ?, ?, false, now())",
                    UUID.randomUUID().toString(), accountId(line[0]), party, new BigDecimal(line[1]), new BigDecimal(line[2]),
                    ledgerRef, "Saldo awal UM " + number, fy);
        }
        BigDecimal dep0 = balance(CUSTOMER_DEPOSIT);
        BigDecimal eq0 = balance(OPENING_EQUITY);

        ok(MockMvcRequestBuilders.patch(BASE + "/" + deposit + "/cancel"));
        // DR Uang Muka Pelanggan (liabilitas turun) / CR Ekuitas Saldo Awal (ekuitas naik)
        assertMoney(dep0.subtract(new BigDecimal("150000")).toPlainString(), balance(CUSTOMER_DEPOSIT));
        assertMoney(eq0.add(new BigDecimal("150000")).toPlainString(), balance(OPENING_EQUITY));
        assertMoney("0", partyColumn(party, "customer_deposit_balance"));
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ? AND is_cancelled = false",
                Integer.class, ledgerRef);
        assertEquals(0, active);
    }

    @Test
    void list_paged_summary_and_legacy_modes() throws Exception {
        String userId = adminUserId();
        String name = uniq("Cust List");
        String party = createParty(name, "Customer");
        String active = receiveCustomerDeposit(party, "1000000", userId);
        String partial = receiveCustomerDeposit(party, "500000", userId);
        String full = receiveCustomerDeposit(party, "300000", userId);
        String cancelled = receiveCustomerDeposit(party, "100000", userId);
        String inv = createSalesInvoice(party, "900000", 1, userId);
        ok(post(BASE + "/apply", body("depositPaymentId", partial, "salesInvoiceId", inv, "amount", 200000)));
        ok(post(BASE + "/apply", body("depositPaymentId", full, "salesInvoiceId", inv, "amount", 300000)));
        ok(MockMvcRequestBuilders.patch(BASE + "/" + cancelled + "/cancel"));

        JsonNode page = ok(get(BASE + "?page=1&limit=10&partyId=" + party));
        assertEquals(4, page.get("total").asInt());
        assertEquals(1, page.get("page").asInt());
        assertEquals(10, page.get("limit").asInt());
        JsonNode rows = page.get("data");
        assertEquals(4, rows.size());
        for (JsonNode row : rows) {
            assertEquals(name, row.get("party").get("name").asText());
            assertEquals(CASH, row.get("account").get("accountNumber").asText());
            assertEquals(3, row.get("account").size());
            assertTrue(row.get("customerDepositApplications").isArray());
            String expectedState = row.get("id").asText().equals(active) ? "Active"
                    : row.get("id").asText().equals(partial) ? "PartiallyUsed"
                    : row.get("id").asText().equals(full) ? "FullyUsed" : "Cancelled";
            assertEquals(expectedState, row.get("state").asText());
            if (row.get("id").asText().equals(partial)) {
                assertMoney("200000", row.get("totalApplied"));
                assertMoney("300000", row.get("remaining"));
                assertMoney("0", row.get("totalRefunded"));
                JsonNode a = row.get("customerDepositApplications").get(0);
                assertEquals(inv, a.get("salesInvoiceId").asText());
                assertMoney("200000", a.get("appliedAmount"));
            }
        }

        JsonNode byAmount = ok(get(BASE + "?page=1&sort=-amount&partyId=" + party));
        assertEquals(active, byAmount.get("data").get(0).get("id").asText());

        JsonNode onlyPartial = ok(get(BASE + "?page=1&status=PartiallyUsed&partyId=" + party));
        assertEquals(1, onlyPartial.get("total").asInt());
        assertEquals(partial, onlyPartial.get("data").get(0).get("id").asText());

        JsonNode bySearch = ok(get(BASE + "?page=1&search=" + name.toUpperCase()));
        assertEquals(4, bySearch.get("total").asInt());

        JsonNode secondPage = ok(get(BASE + "?page=2&limit=3&partyId=" + party));
        assertEquals(4, secondPage.get("total").asInt());
        assertEquals(1, secondPage.get("data").size());

        JsonNode summary = ok(get(BASE + "/summary?partyId=" + party));
        assertEquals(4, summary.get("count").asInt());
        assertEquals(1, summary.get("cancelledCount").asInt());
        assertMoney("1800000", summary.get("totalDeposit"));
        assertMoney("500000", summary.get("totalApplied"));
        assertMoney("0", summary.get("totalRefunded"));
        assertMoney("1300000", summary.get("totalRemaining"));
        assertEquals(2, summary.get("activeCount").asInt());
        assertEquals(1, summary.get("byState").get("Active").asInt());
        assertEquals(1, summary.get("byState").get("PartiallyUsed").asInt());
        assertEquals(1, summary.get("byState").get("FullyUsed").asInt());
        assertEquals(1, summary.get("byState").get("Cancelled").asInt());

        JsonNode legacy = ok(get(BASE + "?partyId=" + party));
        assertEquals(4, legacy.get("data").size());
        assertFalse(legacy.has("total"));
        assertFalse(legacy.get("data").get(0).has("state"));
        assertTrue(legacy.get("data").get(0).has("remaining"));

        JsonNode legacySubmitted = ok(get(BASE + "?status=Submitted&partyId=" + party));
        assertEquals(3, legacySubmitted.get("data").size());

        Result badLegacy = call(get(BASE + "?status=Bogus"));
        assertEquals(500, badLegacy.status());
        assertEquals("Gagal mengambil data uang muka customer.", badLegacy.error());
        assertError("Status uang muka tidak dikenal.", get(BASE + "?page=1&status=Bogus"));
        assertError("Format tanggal harus YYYY-MM-DD.", get(BASE + "/summary?startDate=2026-13-01"));
    }

    private void assertError(String message, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        Result r = call(req);
        assertEquals(400, r.status(), String.valueOf(r.body()));
        assertEquals(message, r.error());
    }
}
