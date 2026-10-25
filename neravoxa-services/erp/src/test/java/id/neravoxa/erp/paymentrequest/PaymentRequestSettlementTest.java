package id.neravoxa.erp.paymentrequest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uang muka (CASH_ADVANCE) → bayar DR Uang Muka Karyawan → pertanggungjawaban: draf, ajukan, atasan, pratinjau &amp;
 * posting Keuangan (lebih / kurang / pas), batal posting, guard batal bayar, pengingat harian.
 */
class PaymentRequestSettlementTest extends PaymentRequestTestSupport {

    @Autowired
    ErpPaymentRequestNotifier notifier;

    private record Actors(String reqEmail, String requester, String supEmail, String supervisor, String finEmail,
                          String finance) {
    }

    private Actors actors(String tag) throws Exception {
        String reqEmail = email("st.req." + tag);
        String supEmail = email("st.sup." + tag);
        String finEmail = email("st.fin." + tag);
        String requester = provision(reqEmail, "EMPLOYEE");
        String supervisor = provision(supEmail, "EMPLOYEE");
        String finance = provision(finEmail, "ERP_ACCOUNTANT");
        return new Actors(reqEmail, requester, supEmail, supervisor, finEmail, finance);
    }

    /** Uang muka dibayar Keuangan (tanpa atasan → langsung Keuangan). */
    private String paidAdvance(Actors a, long amount) throws Exception {
        asUser(a.reqEmail(), "EMPLOYEE");
        String id = createDraft(typeId("CASH_ADV"), "Uang muka kunjungan pelanggan",
                List.of(line("Transport & akomodasi", amount, null))).get("id").asText();
        assertEquals("PENDING_FINANCE", ok(post(BASE + "/" + id + "/submit", Map.of())).get("status").asText());
        asUser(a.finEmail(), "ERP_ACCOUNTANT");
        assertEquals("APPROVED", ok(post(BASE + "/" + id + "/approve", Map.of())).get("status").asText());
        JsonNode paid = ok(post(BASE + "/" + id + "/pay", body("cashAccountId", accountId("1.1.1"), "paymentDate", today())));
        assertEquals("PAID", paid.get("status").asText());
        assertEquals("OPEN", paid.get("settlementStatus").asText());
        List<Map<String, Object>> rows = assertBalancedJournal(paid.get("journalEntryId").asText(), "Submitted");
        assertAmount(String.valueOf(amount), net(rows, "1.5.6"));
        assertAmount(String.valueOf(-amount), net(rows, "1.1.1"));
        return id;
    }

    @Test
    void over_settlement_reimburses_requester_and_cancel_posting_reverses() throws Exception {
        Actors a = actors("over");
        BigDecimal advanceBefore = balance("1.5.6");
        String id = paidAdvance(a, 1_000_000);
        assertAmount("1000000", balance("1.5.6").subtract(advanceBefore));

        // Pengaju mengisi realisasi (lebih besar dari uang muka) lalu atasan ditetapkan.
        asUser(a.reqEmail(), "EMPLOYEE");
        JsonNode meta = ok(get(BASE + "/meta"));
        assertEquals(1, meta.get("openAdvances").get("count").asInt());
        assertAmount("1000000", meta.get("openAdvances").get("amount"));
        String plannedLine = ok(get(BASE + "/" + id)).get("lines").get(0).get("id").asText();
        expectError(400, "Baris realisasi 1: baris rencana tidak ditemukan.", put(BASE + "/" + id + "/settlement",
                body("lines", List.of(body("description", "Hotel", "amount", 700000, "plannedLineId", "tidak-ada")))));
        JsonNode draft = ok(put(BASE + "/" + id + "/settlement", body("note", " Kunjungan 3 hari ", "deadline", today(),
                "lines", List.of(
                        body("description", "Hotel", "amount", 700000, "plannedLineId", plannedLine,
                                "expenseAccountId", accountId("6.15"), "category", "LODGING"),
                        body("description", "Tiket pesawat", "amount", 500000, "expenseAccountId", accountId("6.6"))))));
        assertEquals("Kunjungan 3 hari", draft.get("settlementNote").asText());
        assertEquals("OPEN", draft.get("settlementStatus").asText());
        setSupervisor(a.requester(), a.supervisor());
        JsonNode submitted = ok(post(BASE + "/" + id + "/settlement/submit", Map.of()));
        assertEquals("SUBMITTED", submitted.get("settlementStatus").asText());
        assertEquals(a.supervisor(), submitted.get("settlementSupervisorId").asText());
        assertAmount("1200000", submitted.get("settlementRealized"));
        assertEquals(1, notificationCount(a.supervisor(), "payment_request_pending", id));

        // Batal bayar ditolak selama pertanggungjawaban berjalan.
        asUser(a.finEmail(), "ERP_ACCOUNTANT");
        expectError(400, "Pertanggungjawaban uang muka sedang diproses; selesaikan atau kembalikan dulu sebelum membatalkan pembayaran.",
                post(BASE + "/" + id + "/cancel-payment", body("reason", "Coba batal")));
        expectError(400, "Hanya atasan langsung pengaju yang dapat memproses tahap ini.",
                post(BASE + "/" + id + "/settlement/approve", Map.of()));

        // Atasan menyetujui → Keuangan.
        asUser(a.supEmail(), "EMPLOYEE");
        JsonNode supDetail = ok(get(BASE + "/" + id));
        assertEquals("SUPERVISOR", supDetail.get("actions").get("settlement").get("step").asText());
        JsonNode approved = ok(post(BASE + "/" + id + "/settlement/approve", body("note", "Sesuai")));
        assertEquals("PENDING_FINANCE", approved.get("settlementStatus").asText());

        // Pratinjau tanpa akun kas: masalah dilaporkan, pratinjau tetap ada.
        asUser(a.finEmail(), "ERP_ACCOUNTANT");
        JsonNode preview = ok(post(BASE + "/" + id + "/settlement/preview", Map.of()));
        assertEquals("REIMBURSE", preview.get("mode").asText());
        assertAmount("200000", preview.get("difference"));
        assertTrue(preview.get("cashRequired").asBoolean());
        assertEquals("Pilih akun kas/bank untuk membayar kekurangan ke pengaju.", preview.get("problems").get(0).asText());
        JsonNode preview2 = ok(post(BASE + "/" + id + "/settlement/preview", body("cashAccountId", accountId("1.1.2"))));
        assertEquals(0, preview2.get("problems").size());
        assertEquals(4, preview2.get("lines").size());
        assertEquals("1.5.6 · Uang Muka Karyawan", preview2.get("lines").get(2).get("accountLabel").asText());

        expectError(400, "Pilih akun kas/bank untuk membayar kekurangan ke pengaju.",
                post(BASE + "/" + id + "/settlement/post", body("settlementDate", today())));
        expectError(400, "Akun sumber dana harus akun kas/bank (1.1.x).",
                post(BASE + "/" + id + "/settlement/post", body("settlementDate", today(), "cashAccountId", accountId("6.6"))));

        BigDecimal bankBefore = balance("1.1.2");
        BigDecimal advBefore = balance("1.5.6");
        JsonNode settled = ok(post(BASE + "/" + id + "/settlement/post",
                body("settlementDate", today(), "cashAccountId", accountId("1.1.2"), "reference", "KAS-77")));
        assertEquals("SETTLED", settled.get("settlementStatus").asText());
        assertAmount("1200000", settled.get("settlementRealized"));
        String sj = settled.get("settlementJournalId").asText();
        List<Map<String, Object>> rows = assertBalancedJournal(sj, "Submitted");
        assertAmount("700000", net(rows, "6.15"));
        assertAmount("500000", net(rows, "6.6"));
        assertAmount("-1000000", net(rows, "1.5.6"));
        assertAmount("-200000", net(rows, "1.1.2"));
        assertAmount("-1000000", balance("1.5.6").subtract(advBefore));
        assertAmount("-200000", balance("1.1.2").subtract(bankBefore));
        // Notifikasi "paid": pembayaran uang muka + pertanggungjawaban selesai.
        assertEquals(2, notificationCount(a.requester(), "payment_request_paid", id));

        // Batal bayar setelah SETTLED ditolak; batal posting membalik jurnal.
        expectError(400, "Uang muka sudah dipertanggungjawabkan; batalkan posting pertanggungjawaban terlebih dahulu.",
                post(BASE + "/" + id + "/cancel-payment", body("reason", "Coba batal")));
        JsonNode reverted = ok(post(BASE + "/" + id + "/settlement/cancel-posting", body("reason", "Salah akun biaya")));
        assertEquals("PENDING_FINANCE", reverted.get("settlementStatus").asText());
        assertTrue(reverted.get("settlementJournalId").isNull());
        assertBalancedJournal(sj, "Cancelled");
        assertAmount("0", balance("1.5.6").subtract(advBefore));
        assertAmount("0", balance("1.1.2").subtract(bankBefore));

        // Keuangan mengembalikan ke pengaju untuk revisi.
        JsonNode back = ok(post(BASE + "/" + id + "/settlement/return", body("reason", "Lengkapi kuitansi")));
        assertEquals("REVISION", back.get("settlementStatus").asText());
        asUser(a.reqEmail(), "EMPLOYEE");
        assertTrue(ok(get(BASE + "/" + id)).get("actions").get("settlement").get("edit").asBoolean());
    }

    @Test
    void under_settlement_returns_cash_and_exact_needs_no_cash() throws Exception {
        Actors a = actors("under");
        String id = paidAdvance(a, 1_000_000);

        asUser(a.reqEmail(), "EMPLOYEE");
        ok(put(BASE + "/" + id + "/settlement", body("lines", List.of(
                body("description", "Bensin", "amount", 300000, "expenseAccountId", accountId("6.3")),
                body("description", "Makan", "amount", 500000)))));
        // Tanpa atasan → langsung Keuangan.
        assertEquals("PENDING_FINANCE", ok(post(BASE + "/" + id + "/settlement/submit", Map.of())).get("settlementStatus").asText());

        asUser(a.finEmail(), "ERP_ACCOUNTANT");
        String line2 = ok(get(BASE + "/" + id)).get("settlementLines").get(1).get("id").asText();
        expectError(400, "Akun biaya baris 2 belum diisi.",
                post(BASE + "/" + id + "/settlement/post", body("settlementDate", today(), "cashAccountId", accountId("1.1.1"))));
        BigDecimal cashBefore = balance("1.1.1");
        BigDecimal advBefore = balance("1.5.6");
        JsonNode settled = ok(post(BASE + "/" + id + "/settlement/post", body("settlementDate", today(),
                "cashAccountId", accountId("1.1.1"),
                "lineAccounts", List.of(body("id", line2, "expenseAccountId", accountId("6.20"))))));
        assertEquals("SETTLED", settled.get("settlementStatus").asText());
        List<Map<String, Object>> rows = assertBalancedJournal(settled.get("settlementJournalId").asText(), "Submitted");
        assertAmount("300000", net(rows, "6.3"));
        assertAmount("500000", net(rows, "6.20"));
        assertAmount("200000", net(rows, "1.1.1"));
        assertAmount("-1000000", net(rows, "1.5.6"));
        assertAmount("200000", balance("1.1.1").subtract(cashBefore));
        assertAmount("-1000000", balance("1.5.6").subtract(advBefore));
        // Akun pilihan Keuangan tersimpan di baris realisasi.
        assertEquals(accountId("6.20"), jdbc.queryForObject(
                "SELECT expense_account_id FROM payment_request_settlement_lines WHERE id = ?", String.class, line2));

        // Pas (realisasi = uang muka): tanpa baris kas.
        String id2 = paidAdvance(a, 400_000);
        asUser(a.reqEmail(), "EMPLOYEE");
        ok(put(BASE + "/" + id2 + "/settlement", body("lines", List.of(
                body("description", "Parkir & tol", "amount", 400000, "expenseAccountId", accountId("6.3"))))));
        ok(post(BASE + "/" + id2 + "/settlement/submit", Map.of()));
        asUser(a.finEmail(), "ERP_ACCOUNTANT");
        JsonNode exact = ok(post(BASE + "/" + id2 + "/settlement/preview", Map.of()));
        assertEquals("EXACT", exact.get("mode").asText());
        assertFalse(exact.get("cashRequired").asBoolean());
        JsonNode settled2 = ok(post(BASE + "/" + id2 + "/settlement/post",
                body("settlementDate", today(), "cashAccountId", accountId("1.1.1"))));
        assertTrue(settled2.get("settlementCashAccountId").isNull(), "kas tidak dipakai bila pas");
        List<Map<String, Object>> rows2 = assertBalancedJournal(settled2.get("settlementJournalId").asText(), "Submitted");
        assertEquals(2, rows2.size());

        // Ringkasan uang muka terbuka Keuangan tidak lagi memuat keduanya.
        JsonNode summary = ok(get(BASE + "/advances/summary"));
        for (JsonNode e : summary.get("byEmployee")) {
            assertFalse(a.requester().equals(e.get("employeeId").asText()), "uang muka sudah selesai");
        }
    }

    @Test
    void cancel_payment_while_open_and_daily_reminder() throws Exception {
        Actors a = actors("remind");
        String id = paidAdvance(a, 250_000);

        // Masih OPEN → Keuangan boleh membatalkan pembayaran.
        asUser(a.finEmail(), "ERP_ACCOUNTANT");
        JsonNode reverted = ok(post(BASE + "/" + id + "/cancel-payment", body("reason", "Dobel transfer")));
        assertEquals("APPROVED", reverted.get("status").asText());
        assertTrue(reverted.get("settlementStatus").isNull());

        // Uang muka lain yang sudah 20 hari belum dipertanggungjawabkan → pengingat (maks. 1 per 3 hari).
        String id2 = paidAdvance(a, 300_000);
        jdbc.update("UPDATE payment_requests SET payment_date = now() - interval '20 days' WHERE id = ?", id2);
        asUser(a.reqEmail(), "EMPLOYEE");
        JsonNode detail = ok(get(BASE + "/" + id2));
        assertTrue(detail.get("advanceAge").get("overdue").asBoolean());
        assertTrue(detail.get("advanceAge").get("days").asInt() >= 19);
        notifier.runSettlementReminders(Instant.now());
        assertEquals(1, notificationCount(a.requester(), "payment_request_settlement_due", id2));
        notifier.runSettlementReminders(Instant.now());
        assertEquals(1, notificationCount(a.requester(), "payment_request_settlement_due", id2));
        String msg = jdbc.queryForObject("SELECT message FROM notifications WHERE user_id = ? AND type = ? "
                + "AND metadata->>'paymentRequestId' = ?", String.class, a.requester(), "payment_request_settlement_due", id2);
        assertTrue(msg.contains("(Rp 300.000)") && msg.endsWith("hari sejak dibayar. Segera isi pertanggungjawaban."), msg);

        JsonNode summary = ok(get(BASE + "/advances/summary"));   // pengaju: hanya miliknya
        assertEquals(1, summary.get("count").asInt());
        assertEquals(1, summary.get("overdueCount").asInt());
        assertEquals(a.requester(), summary.get("byEmployee").get(0).get("employeeId").asText());
    }
}
