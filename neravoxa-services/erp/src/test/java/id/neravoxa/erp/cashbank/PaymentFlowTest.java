package id.neravoxa.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Alur pembayaran: alokasi FIFO, split multi-akun, saldo awal, uang muka, refund, pembatalan, ledger kas. */
class PaymentFlowTest extends CashBankTestSupport {

    @Test
    void receive_allocates_oldest_invoice_first_and_cancel_reverses_everything() throws Exception {
        String customer = createParty(uniq("Pelanggan"), "Customer");
        String inv1 = createInvoice("sales_invoices", uniq("SI-T"), customer, daysFromToday(-20), daysFromToday(-5),
                "1000000", "1000000", "Submitted");
        String inv2 = createInvoice("sales_invoices", uniq("SI-T"), customer, daysFromToday(-2), daysFromToday(28),
                "500000", "500000", "Submitted");
        BigDecimal bank0 = balance("1.1.2");
        BigDecimal ar0 = balance("1.2.1");
        asAdmin();

        JsonNode pay = ok(post("/api/erp/payments", body("date", today(), "partyId", customer, "amount", 1200000,
                "paymentType", "Receive", "accountId", accountId("1.1.2"), "referenceNo", "TRF-001")));
        assertEquals("Submitted", pay.get("status").asText());
        assertAmount("0", pay.get("unallocatedAmount"));
        assertEquals(customer, pay.get("party").get("id").asText());
        assertTrue(pay.get("journalEntryId").isNull(), "payment = baris saat dibuat (journalEntryId masih null)");
        assertFalse(pay.has("splits"));
        String number = pay.get("paymentNumber").asText();
        assertTrue(number.startsWith("PAY-"));

        assertBalancedJournal("JV-" + number, "Submitted");
        assertAmount("1200000", balance("1.1.2").subtract(bank0));
        assertAmount("-1200000", balance("1.2.1").subtract(ar0));
        Map<String, Object> r1 = invoiceRow("sales_invoices", inv1);
        Map<String, Object> r2 = invoiceRow("sales_invoices", inv2);
        assertEquals("Paid", r1.get("status"));
        assertAmount("0", r1.get("outstanding"));
        assertEquals("PartiallyPaid", r2.get("status"));
        assertAmount("300000", r2.get("outstanding"));
        assertAmount("300000", partyColumn(customer, "outstanding_amount"));

        JsonNode detail = ok(get("/api/erp/payments/" + pay.get("id").asText()));
        assertEquals(2, detail.get("allocations").size());
        assertTrue(detail.get("allocations").get(0).hasNonNull("invoiceNumber"));
        assertEquals("1.1.2", detail.get("account").get("accountNumber").asText());
        assertEquals(0, detail.get("splits").size());
        assertEquals(2, detail.get("journalEntry").get("items").size());
        assertTrue(detail.get("journalEntry").get("items").get(0).get("account").has("accountNumber"));

        JsonNode cancelled = ok(post("/api/erp/payments/" + pay.get("id").asText() + "/cancel", Map.of()));
        assertEquals("Cancelled", cancelled.get("status").asText());
        assertBalancedJournal("JV-" + number, "Cancelled");
        assertAmount("0", balance("1.1.2").subtract(bank0));
        assertAmount("0", balance("1.2.1").subtract(ar0));
        r1 = invoiceRow("sales_invoices", inv1);
        r2 = invoiceRow("sales_invoices", inv2);
        assertEquals("Submitted", r1.get("status"));
        assertAmount("1000000", r1.get("outstanding"));
        assertEquals("Submitted", r2.get("status"));
        assertAmount("500000", r2.get("outstanding"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM payment_allocations WHERE payment_id = ?", Integer.class,
                pay.get("id").asText()));
        assertAmount("1500000", partyColumn(customer, "outstanding_amount"));

        var again = call(post("/api/erp/payments/" + pay.get("id").asText() + "/cancel", Map.of()));
        assertEquals(400, again.status());
        assertEquals("Pembayaran sudah dibatalkan.", again.error());
    }

    @Test
    void pay_with_splits_posts_each_account_and_validates_total() throws Exception {
        String supplier = createParty(uniq("Supplier"), "Supplier");
        String pi = createInvoice("purchase_invoices", uniq("PI-T"), supplier, daysFromToday(-3), daysFromToday(10),
                "1000000", "1000000", "Submitted");
        asAdmin();
        Map<String, Object> bad = body("date", today(), "partyId", supplier, "amount", 1000000, "paymentType", "Pay",
                "accountId", accountId("1.1.2"),
                "splits", List.of(body("accountId", accountId("1.1.2"), "amount", 900000)));
        var mismatch = call(post("/api/erp/payments", bad));
        assertEquals(400, mismatch.status());
        assertEquals("Total split (900000.00) tidak sama dengan jumlah pembayaran (1000000.00).", mismatch.error());

        BigDecimal bank0 = balance("1.1.2");
        BigDecimal fee0 = balance("6.1");
        BigDecimal ap0 = balance("2.1.1");
        JsonNode pay = ok(post("/api/erp/payments", body("date", today(), "partyId", supplier, "amount", "1000000",
                "paymentType", "Pay", "accountId", accountId("1.1.2"),
                "splits", List.of(body("accountId", accountId("1.1.2"), "amount", 975000),
                        body("accountId", accountId("6.1"), "amount", "25000", "notes", "potongan admin")))));
        String number = pay.get("paymentNumber").asText();
        assertBalancedJournal("JV-" + number, "Submitted");
        assertAmount("-975000", balance("1.1.2").subtract(bank0));
        assertAmount("-25000", balance("6.1").subtract(fee0));
        assertAmount("-1000000", balance("2.1.1").subtract(ap0));
        assertEquals("Paid", invoiceRow("purchase_invoices", pi).get("status"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM payment_splits WHERE payment_id = ?", Integer.class,
                pay.get("id").asText()));
        // Baris split Pay membawa party (item & ledger); deskripsi split memuat catatan.
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry_accounts ji JOIN journal_entries je "
                + "ON je.id = ji.journal_entry_id WHERE je.entry_number = ? AND ji.party_id = ?", Integer.class, "JV-" + number, supplier));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry_accounts ji JOIN journal_entries je "
                        + "ON je.id = ji.journal_entry_id WHERE je.entry_number = ? AND ji.description = ?", Integer.class,
                "JV-" + number, "Pembayaran: " + number + " (potongan admin)"));

        JsonNode list = ok(get("/api/erp/payments?page=1&limit=5"));
        assertEquals(1, list.get("page").asInt());
        assertEquals(5, list.get("limit").asInt());
        assertTrue(list.get("data").size() <= 5);
        assertTrue(list.get("data").get(0).has("party"));
        assertTrue(list.get("data").get(0).has("splits"));

        JsonNode detail = ok(get("/api/erp/payments/" + pay.get("id").asText()));
        assertEquals(2, detail.get("splits").size());
        assertTrue(detail.get("splits").get(0).get("account").has("accountNumber"));

        // Pembatalan membalik saldo per split.
        ok(post("/api/erp/payments/" + pay.get("id").asText() + "/cancel", Map.of()));
        assertAmount("0", balance("1.1.2").subtract(bank0));
        assertAmount("0", balance("6.1").subtract(fee0));
        assertAmount("0", balance("2.1.1").subtract(ap0));
        assertEquals("Submitted", invoiceRow("purchase_invoices", pi).get("status"));
    }

    @Test
    void overpayment_reduces_opening_outstanding() throws Exception {
        String customer = createParty(uniq("Pelanggan"), "Customer");
        jdbc.update("UPDATE parties SET opening_outstanding = 300000, outstanding_amount = 300000 WHERE id = ?", customer);
        asAdmin();
        JsonNode pay = ok(post("/api/erp/payments", body("date", today(), "partyId", customer, "amount", 500000,
                "paymentType", "Receive", "accountId", accountId("1.1.1"))));
        assertAmount("500000", pay.get("unallocatedAmount"));
        assertAmount("300000", pay.get("party").get("outstandingAmount"));
        assertAmount("0", partyColumn(customer, "opening_outstanding"));
        assertAmount("0", partyColumn(customer, "outstanding_amount"));
    }

    @Test
    void validation_and_guards_use_source_messages() throws Exception {
        String customer = createParty(uniq("Pelanggan"), "Customer");
        asAdmin();
        var r = call(post("/api/erp/payments", body("date", today(), "partyId", customer, "amount", 0,
                "paymentType", "Receive", "accountId", accountId("1.1.1"))));
        assertEquals(400, r.status());
        assertEquals("Jumlah harus lebih dari 0.", r.error());

        r = call(post("/api/erp/payments", body("date", today(), "partyId", customer, "amount", 10,
                "paymentType", "Transfer", "accountId", accountId("1.1.1"))));
        assertEquals("Invalid option: expected one of \"Receive\"|\"Pay\"|\"VendorDeposit\"|\"CustomerDeposit\"|"
                + "\"VendorRefund\"|\"CustomerRefund\"", r.error());

        r = call(post("/api/erp/payments", body("partyId", customer, "amount", 10, "paymentType", "Receive",
                "accountId", accountId("1.1.1"))));
        assertEquals("Invalid input: expected string, received undefined", r.error());

        r = call(post("/api/erp/payments", body("date", today(), "partyId", customer, "amount", "abc",
                "paymentType", "Receive", "accountId", accountId("1.1.1"))));
        assertEquals("Invalid input: expected number, received NaN", r.error());

        r = call(post("/api/erp/payments", body("date", today(), "partyId", customer, "amount", 10,
                "paymentType", "Receive", "accountId", accountId("1.2.1"))));
        assertEquals("Akun yang dipilih bukan akun kas/bank.", r.error());

        r = call(post("/api/erp/payments", body("date", "bukan-tanggal", "partyId", customer, "amount", 10,
                "paymentType", "Receive", "accountId", accountId("1.1.1"))));
        assertEquals("Format tanggal tidak valid.", r.error());

        r = call(post("/api/erp/payments", body("date", today(), "partyId", customer, "amount", 10,
                "paymentType", "VendorDeposit", "accountId", accountId("1.1.1"))));
        assertEquals("Uang muka hanya dapat dibuat untuk Supplier.", r.error());

        r = call(get("/api/erp/payments/tidak-ada"));
        assertEquals(404, r.status());
        assertEquals("Pembayaran tidak ditemukan.", r.error());
    }

    @Test
    void deposits_post_to_deposit_accounts_and_cannot_be_cancelled_here() throws Exception {
        String supplier = createParty(uniq("Supplier"), "Supplier");
        String customer = createParty(uniq("Pelanggan"), "Both");
        BigDecimal vd0 = balance("1.3");
        BigDecimal bank0 = balance("1.1.3");
        BigDecimal cd0 = balance("2.1.2");
        BigDecimal eq0 = balance("3.1");
        asAdmin();

        JsonNode vd = ok(post("/api/erp/payments", body("date", today(), "partyId", supplier, "amount", 750000,
                "paymentType", "VendorDeposit", "accountId", accountId("1.1.3"))));
        assertBalancedJournal("JV-" + vd.get("paymentNumber").asText(), "Submitted");
        assertAmount("750000", balance("1.3").subtract(vd0));
        assertAmount("-750000", balance("1.1.3").subtract(bank0));
        assertAmount("750000", partyColumn(supplier, "deposit_balance"));
        var r = call(post("/api/erp/payments/" + vd.get("id").asText() + "/cancel", Map.of()));
        assertEquals("Gunakan endpoint Uang Muka Supplier untuk membatalkan deposit.", r.error());

        // Saldo awal uang muka pelanggan: DR Ekuitas Saldo Awal / CR Uang Muka Pelanggan; akun kas tidak divalidasi.
        JsonNode cd = ok(post("/api/erp/payments", body("date", today(), "partyId", customer, "amount", 400000,
                "paymentType", "CustomerDeposit", "accountId", accountId("1.2.1"), "isOpeningBalance", true)));
        assertEquals(accountId("3.1"), cd.get("accountId").asText());
        assertAmount("400000", balance("2.1.2").subtract(cd0));
        assertAmount("-400000", balance("3.1").subtract(eq0));
        assertAmount("400000", partyColumn(customer, "customer_deposit_balance"));
    }

    @Test
    void vendor_refund_reduces_source_allocation_and_restores_invoice() throws Exception {
        String supplier = createParty(uniq("Supplier"), "Supplier");
        String pi = createInvoice("purchase_invoices", uniq("PI-T"), supplier, daysFromToday(-3), null,
                "1000000", "1000000", "Submitted");
        asAdmin();
        JsonNode pay = ok(post("/api/erp/payments", body("date", today(), "partyId", supplier, "amount", 600000,
                "paymentType", "Pay", "accountId", accountId("1.1.2"))));
        assertAmount("400000", invoiceRow("purchase_invoices", pi).get("outstanding"));

        var tooMuch = call(post("/api/erp/payments", body("date", today(), "partyId", supplier, "amount", 700000,
                "paymentType", "VendorRefund", "accountId", accountId("1.1.2"),
                "refundSourcePaymentId", pay.get("id").asText())));
        assertEquals(400, tooMuch.status());
        assertEquals("Nilai refund (700000) melebihi total alokasi pembayaran asal yang tersedia.", tooMuch.error());

        BigDecimal bank0 = balance("1.1.2");
        BigDecimal ap0 = balance("2.1.1");
        JsonNode refund = ok(post("/api/erp/payments", body("date", today(), "partyId", supplier, "amount", 200000,
                "paymentType", "VendorRefund", "accountId", accountId("1.1.2"),
                "refundSourcePaymentId", pay.get("id").asText())));
        assertBalancedJournal("JV-" + refund.get("paymentNumber").asText(), "Submitted");
        assertAmount("200000", balance("1.1.2").subtract(bank0));
        assertAmount("200000", balance("2.1.1").subtract(ap0));
        Map<String, Object> inv = invoiceRow("purchase_invoices", pi);
        assertAmount("600000", inv.get("outstanding"));
        assertEquals("PartiallyPaid", inv.get("status"));
        assertAmount("400000", jdbc.queryForObject("SELECT allocated_amount FROM payment_allocations WHERE payment_id = ?",
                BigDecimal.class, pay.get("id").asText()));
        assertAmount("600000", partyColumn(supplier, "outstanding_amount"));

        var r = call(post("/api/erp/payments/" + refund.get("id").asText() + "/cancel", Map.of()));
        assertEquals("Refund belum mendukung cancel otomatis. Buat jurnal koreksi manual untuk membatalkan refund ini.", r.error());
    }

    @Test
    void cash_ledger_merges_payments_and_cash_journals() throws Exception {
        String customer = createParty(uniq("Pelanggan Ledger"), "Customer");
        asAdmin();
        JsonNode pay = ok(post("/api/erp/payments", body("date", today(), "partyId", customer, "amount", 125000,
                "paymentType", "Receive", "accountId", accountId("1.1.4"), "notes", "catatan ledger")));

        // Pinbuk antar akun kas lewat jurnal manual (bukan pembayaran) → jenis Transfer di kedua baris.
        String marker = uniq("PINBUK");
        jdbc.update("WITH je AS (INSERT INTO journal_entries (id, entry_number, date, status, narration, fiscal_year_id, "
                        + "created_by, submitted_at, created_at, updated_at) VALUES (gen_random_uuid()::text, ?, "
                        + "CAST(? AS timestamp), 'Submitted', ?, ?, ?, now(), now(), now()) RETURNING id) "
                        + "INSERT INTO journal_entry_accounts (id, journal_entry_id, account_id, debit, credit, description) "
                        + "SELECT gen_random_uuid()::text, je.id, ?, 250000, 0, 'masuk' FROM je "
                        + "UNION ALL SELECT gen_random_uuid()::text, je.id, ?, 0, 250000, 'keluar' FROM je",
                "JV-" + marker, today(), marker, openFiscalYearId(), adminUserId(), accountId("1.1.5"), accountId("1.1.4"));
        asAdmin();

        JsonNode rows = ok(get("/api/erp/payments/ledger?partyId=" + customer));
        assertEquals(1, rows.get("total").asInt());
        JsonNode row = rows.get("data").get(0);
        assertEquals("payment", row.get("source").asText());
        assertEquals("Receive", row.get("kind").asText());
        assertEquals("in", row.get("direction").asText());
        assertEquals(pay.get("paymentNumber").asText(), row.get("number").asText());
        assertEquals("Customer", row.get("partyType").asText());
        assertEquals("1.1.4", row.get("accountNumber").asText());
        assertEquals("catatan ledger", row.get("notes").asText());
        assertAmount("125000", row.get("amount"));
        assertTrue(row.get("date").asText().endsWith("Z"));

        JsonNode journal = ok(get("/api/erp/payments/ledger?search=" + marker + "&sort=amount"));
        assertEquals(2, journal.get("total").asInt());
        for (JsonNode j : journal.get("data")) {
            assertEquals("journal", j.get("source").asText());
            assertEquals("Transfer", j.get("kind").asText());
            assertFalse(j.get("journalEntryId").isNull());
        }
        JsonNode outOnly = ok(get("/api/erp/payments/ledger?search=" + marker + "&direction=out"));
        assertEquals(1, outOnly.get("total").asInt());
        assertEquals("1.1.4", outOnly.get("data").get(0).get("accountNumber").asText());

        JsonNode summary = ok(get("/api/erp/payments/ledger/summary?search=" + marker));
        assertEquals(2, summary.get("count").asInt());
        assertAmount("250000", summary.get("inTotal"));
        assertAmount("250000", summary.get("outTotal"));
        assertAmount("0", summary.get("net"));

        JsonNode byAccount = ok(get("/api/erp/payments/ledger?accountId=" + accountId("1.1.5") + "&search=" + marker));
        assertEquals(1, byAccount.get("total").asInt());
        assertEquals("in", byAccount.get("data").get(0).get("direction").asText());

        JsonNode cashJournals = ok(get("/api/erp/payments/cash-journals"));
        boolean found = false;
        for (JsonNode j : cashJournals.get("data")) {
            if (("JV-" + marker).equals(j.get("entryNumber").asText())) {
                found = true;
                assertNull(j.get("partyName").textValue());
            }
            assertFalse(j.get("entryNumber").asText().startsWith("JV-PAY"));
        }
        assertTrue(found || cashJournals.get("data").size() == 100);

        var bad = call(get("/api/erp/payments/ledger?type=Unknown"));
        assertEquals(400, bad.status());
        assertEquals("Tipe transaksi tidak dikenal.", bad.error());
        bad = call(get("/api/erp/payments/ledger?direction=up"));
        assertEquals("Arah transaksi harus \"in\" atau \"out\".", bad.error());
        bad = call(get("/api/erp/payments/ledger/summary?startDate=20-10-2026"));
        assertEquals("Format tanggal harus YYYY-MM-DD.", bad.error());
        JsonNode ranged = ok(get("/api/erp/payments/ledger?partyId=" + customer + "&startDate=" + daysFromToday(-1)
                + "&endDate=" + daysFromToday(1)));
        assertEquals(1, ranged.get("total").asInt());
    }
}
