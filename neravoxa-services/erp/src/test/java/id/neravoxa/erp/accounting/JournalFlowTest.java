package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Alur /api/erp/journals: posting manual (saldo akun + mitra), daftar/filter, detail, pembatalan. */
class JournalFlowTest extends AccountingTestSupport {

    private static final BigDecimal AMOUNT = new BigDecimal("250000");

    @Test
    void manual_journal_posts_and_cancel_reverses_everything() throws Exception {
        String revenue = newAccount("4", "REVENUE").get("id").asText();
        String ar = accountId("1.2.1");
        String party = newParty("Customer");
        BigDecimal arBefore = balance(ar);
        String narration = "Jurnal uji " + uniq();

        Map<String, Object> arLine = line(ar, "250000", 0);
        arLine.put("partyId", party);
        arLine.put("description", "Piutang manual");
        Result created = call(post("/api/erp/journals",
                journal(LocalDate.now().toString(), narration, List.of(arLine, line(revenue, 0, 250000)))));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        JsonNode je = created.data();
        String id = je.get("id").asText();
        assertTrue(je.get("entryNumber").asText().startsWith("JV-"));
        assertEquals("Submitted", je.get("status").asText());
        assertEquals(2, je.get("items").size());
        assertEquals("1.2.1", je.get("items").get(0).get("account").get("accountNumber").asText());
        assertEquals("ASSET", je.get("items").get(0).get("account").get("rootType").asText());
        assertEquals(narration, je.get("items").get(1).get("description").asText());

        // Jurnal seimbang di ledger, saldo akun & outstanding mitra bertambah.
        assertEquals(0, ledgerImbalance(id).signum());
        assertEquals(0, AMOUNT.compareTo(balance(revenue)));
        assertEquals(0, arBefore.add(AMOUNT).compareTo(balance(ar)));
        assertEquals(0, AMOUNT.compareTo(outstanding(party)));

        // Detail & daftar.
        JsonNode detail = ok(get("/api/erp/journals/" + id));
        assertEquals("erp.admin", detail.get("user").get("fullName").asText().substring(0, 9));
        assertTrue(detail.get("items").get(0).get("account").has("id"));
        JsonNode list = ok(get("/api/erp/journals?search=" + narration.substring(12).toUpperCase() + "&status=Submitted"));
        assertEquals(1, list.get("total").asInt());
        assertEquals(50, list.get("limit").asInt());
        assertEquals(2, list.get("data").get(0).get("items").get(0).get("account").size());
        JsonNode byAccount = ok(get("/api/erp/journals?accountId=" + revenue + "&startDate=" + LocalDate.now()
                + "&endDate=" + LocalDate.now() + "&sort=-entryNumber"));
        assertEquals(1, byAccount.get("total").asInt());

        // Batal → saldo & mitra kembali, ledger soft-cancel, status Cancelled.
        assertEquals("Jurnal dibatalkan.", ok(patch("/api/erp/journals/" + id + "/cancel", Map.of())).get("message").asText());
        assertEquals(0, balance(revenue).signum());
        assertEquals(0, arBefore.compareTo(balance(ar)));
        assertEquals(0, outstanding(party).signum());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ? "
                + "AND is_cancelled = false", Integer.class, id));
        assertEquals("Cancelled", ok(get("/api/erp/journals/" + id)).get("status").asText());
        assertEquals("Jurnal sudah dibatalkan.", call(patch("/api/erp/journals/" + id + "/cancel", Map.of())).error());
        JsonNode hidden = ok(get("/api/erp/journals?accountId=" + revenue + "&hideCancelled=true"));
        assertEquals(0, hidden.get("total").asInt());
    }

    @Test
    void cancel_of_customer_deposit_offset_restores_invoice_and_refund() throws Exception {
        String userId = adminUserId();
        String party = newParty("Customer");
        String fy = currentFiscalYearId();
        String invoiceId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sales_invoices (id, invoice_number, date, party_id, status, grand_total, outstanding, "
                        + "fiscal_year_id, created_by) VALUES (?, ?, now(), ?, 'Paid', 1000, 0, ?, ?)",
                invoiceId, "SI-T-" + uniq(), party, fy, userId);
        String paymentNumber = "PAY-T-" + uniq();
        String paymentId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO payments (id, payment_number, date, payment_type, party_id, account_id, amount, "
                        + "refunded_amount, status, fiscal_year_id, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, now(), 'Receive', ?, ?, 5000, 300, 'Submitted', ?, ?, now(), now())",
                paymentId, paymentNumber, party, accountId("1.1.1"), fy, userId);

        // Jurnal refund UM manual: DR 2.1.2 300 / CR kas 300, narasi "Refund UM Pelanggan: <no>".
        asAdmin();
        JsonNode je = ok(post("/api/erp/journals", journal(LocalDate.now().toString(),
                "Refund UM Pelanggan: " + paymentNumber + " - uji",
                List.of(line(accountId("2.1.2"), 300, 0), line(accountId("1.1.1"), 0, 300)))));
        String jeId = je.get("id").asText();
        jdbc.update("INSERT INTO customer_deposit_applications (id, deposit_payment_id, sales_invoice_id, applied_amount, "
                        + "journal_entry_id, is_cancelled, applied_at, created_by) VALUES (?, ?, ?, 1000, ?, false, now(), ?)",
                UUID.randomUUID().toString(), paymentId, invoiceId, jeId, userId);

        ok(patch("/api/erp/journals/" + jeId + "/cancel", Map.of()));
        Map<String, Object> inv = jdbc.queryForMap("SELECT status, outstanding FROM sales_invoices WHERE id = ?", invoiceId);
        assertEquals("Submitted", inv.get("status"));
        assertEquals(0, new BigDecimal("1000").compareTo((BigDecimal) inv.get("outstanding")));
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject(
                "SELECT refunded_amount FROM payments WHERE id = ?", BigDecimal.class, paymentId)));
        assertTrue(jdbc.queryForObject("SELECT is_cancelled FROM customer_deposit_applications WHERE journal_entry_id = ?",
                Boolean.class, jeId));
    }

    @Test
    void validation_and_guards() throws Exception {
        asAdmin();
        String cash = accountId("1.1.1");
        String revenue = accountId("4.1");
        String today = LocalDate.now().toString();

        Result unbalanced = call(post("/api/erp/journals", journal(today, "x", List.of(line(cash, 100, 0), line(revenue, 0, 50)))));
        assertEquals(400, unbalanced.status());
        assertEquals("Jurnal tidak seimbang. Total Debit (100) ≠ Total Kredit (50).", unbalanced.error());

        assertEquals("Satu baris jurnal tidak boleh memiliki debit dan kredit sekaligus.", call(post("/api/erp/journals",
                journal(today, "x", List.of(line(cash, 100, 100), line(revenue, 0, 0))))).error());
        assertEquals("Keterangan wajib diisi.", call(post("/api/erp/journals",
                journal(today, "", List.of(line(cash, 1, 0))))).error());
        assertEquals("Minimal satu baris jurnal.", call(post("/api/erp/journals", journal(today, "x", List.of()))).error());
        assertEquals("Akun wajib dipilih.", call(post("/api/erp/journals",
                journal(today, "x", List.of(line("", 1, 0))))).error());
        assertEquals(ZodLite.NAN, call(post("/api/erp/journals",
                journal(today, "x", List.of(line(cash, "abc", 0))))).error());
        assertEquals("Referensi data tidak valid. Periksa data yang dipilih.", call(post("/api/erp/journals",
                journal(today, "x", List.of(line("no-such-account", 1, 0), line(revenue, 0, 1))))).error());
        assertEquals("Tahun fiskal tidak ditemukan atau sudah ditutup untuk tanggal ini.", call(post("/api/erp/journals",
                journal("1890-01-01", "x", List.of(line(cash, 1, 0), line(revenue, 0, 1))))).error());

        assertEquals("Status jurnal tidak dikenal.", call(get("/api/erp/journals?status=Posted")).error());
        assertEquals("Format tanggal harus YYYY-MM-DD.", call(get("/api/erp/journals?startDate=01-10-2026")).error());
        assertEquals(404, call(get("/api/erp/journals/nope-" + uniq())).status());

        // Jurnal otomatis (JV-PAY-*) dibatalkan dari modul asalnya.
        String autoId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO journal_entries (id, entry_number, date, status, narration, fiscal_year_id, created_by) "
                        + "VALUES (?, ?, now(), 'Submitted', 'otomatis', ?, ?)",
                autoId, "JV-PAY-T" + uniq(), currentFiscalYearId(), adminUserId());
        assertEquals("Jurnal ini dibuat otomatis dari transaksi sumber (Pembayaran/Invoice/HPP). Batalkan dari modul "
                + "asalnya, bukan dari Buku Besar.", call(patch("/api/erp/journals/" + autoId + "/cancel", Map.of())).error());

        // Tanpa izin jurnal → 403 dari gerbang rute.
        asUser("produksi." + uniq() + "@test.local", "ERP_PRODUCTION");
        assertEquals(403, call(get("/api/erp/journals")).status());
    }

    private BigDecimal outstanding(String partyId) {
        return jdbc.queryForObject("SELECT outstanding_amount FROM parties WHERE id = ?", BigDecimal.class, partyId);
    }
}
