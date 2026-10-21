package id.enterpriseapps.erp.paymentrequest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Alur utama Pengajuan Pembayaran: draf → ubah → ajukan (atasan) → Keuangan → bayar (jurnal seimbang, guard akun
 * kas) → batal bayar; tolak / kembalikan / batal oleh pengaju; tahap Direktur; visibilitas & validasi.
 */
class PaymentRequestFlowTest extends PaymentRequestTestSupport {

    @Test
    void supervisor_finance_pay_and_cancel_payment_post_balanced_journal() throws Exception {
        String reqEmail = email("pr.req");
        String supEmail = email("pr.sup");
        String finEmail = email("pr.fin");
        String otherEmail = email("pr.other");
        String requester = provision(reqEmail, "EMPLOYEE");
        String supervisor = provision(supEmail, "EMPLOYEE");
        String finance = provision(finEmail, "ERP_ACCOUNTANT");
        provision(otherEmail, "EMPLOYEE");
        setSupervisor(requester, supervisor);

        String office = typeId("OFFICE");
        String atk = accountId("6.16");
        String pantry = accountId("6.20");

        // Draf oleh pengaju (tanpa izin modul apa pun).
        asUser(reqEmail, "EMPLOYEE");
        JsonNode created = createDraft(office, "Kebutuhan kantor Oktober",
                List.of(line("Kertas A4", 150000, atk), line("Snack rapat", 75000, null)));
        String id = created.get("id").asText();
        assertEquals("DRAFT", created.get("status").asText());
        assertTrue(created.get("number").asText().startsWith("PP-"));
        assertAmount("225000", created.get("totalAmount"));
        assertFalse(created.has("lines"), "respons create tanpa relasi (seperti Prisma tanpa include)");

        // Ubah draf: rincian diganti, total dihitung ulang.
        JsonNode updated = ok(put(BASE + "/" + id, draft(office, "Kebutuhan kantor Oktober (revisi)",
                List.of(line("Kertas A4", 150000, atk), line("Snack rapat", 100000, null)))));
        assertAmount("250000", updated.get("totalAmount"));
        assertEquals("Kebutuhan kantor Oktober (revisi)", updated.get("purpose").asText());

        // Pengguna lain tidak melihat pengajuan ini.
        asUser(otherEmail, "EMPLOYEE");
        expectError(404, "Pengajuan tidak ditemukan.", get(BASE + "/" + id));

        // Ajukan → atasan aktif → SUBMITTED + notifikasi ke atasan.
        asUser(reqEmail, "EMPLOYEE");
        JsonNode submitted = ok(post(BASE + "/" + id + "/submit", Map.of()));
        assertEquals("SUBMITTED", submitted.get("status").asText());
        assertEquals(supervisor, submitted.get("supervisorId").asText());
        assertEquals(1, notificationCount(supervisor, "payment_request_pending", id));
        String link = jdbc.queryForObject("SELECT metadata->>'link' FROM notifications WHERE user_id = ? AND "
                + "metadata->>'paymentRequestId' = ?", String.class, supervisor, id);
        assertEquals("/payment-requests/" + id, link);

        // Pengaju tidak bisa memutus pengajuannya sendiri; edit setelah diajukan ditolak.
        expectError(400, "Anda tidak dapat memproses pengajuan Anda sendiri.", post(BASE + "/" + id + "/approve", Map.of()));
        expectError(400, "Pengajuan hanya bisa diubah saat masih Draf atau Perlu Revisi.",
                put(BASE + "/" + id, draft(office, "Ubah setelah diajukan", List.of(line("X", 1000, atk)))));

        // Keuangan belum boleh memutus tahap atasan.
        asUser(finEmail, "ERP_ACCOUNTANT");
        expectError(400, "Hanya atasan langsung pengaju yang dapat memproses tahap ini.",
                post(BASE + "/" + id + "/approve", Map.of()));

        // Atasan menyetujui → PENDING_FINANCE.
        asUser(supEmail, "EMPLOYEE");
        JsonNode detail = ok(get(BASE + "/" + id));
        assertTrue(detail.get("actions").get("decide").asBoolean());
        assertEquals("SUPERVISOR", detail.get("actions").get("step").asText());
        assertEquals(2, detail.get("lines").size());
        assertEquals("Kertas A4", detail.get("lines").get(0).get("description").asText());
        assertEquals("6.16", detail.get("lines").get(0).get("expenseAccount").get("accountNumber").asText());
        assertTrue(detail.get("lines").get(1).get("expenseAccount").isNull());
        assertEquals(reqEmail, detail.get("requester").get("email").asText());
        JsonNode approved1 = ok(post(BASE + "/" + id + "/approve", body("note", "OK atasan")));
        assertEquals("PENDING_FINANCE", approved1.get("status").asText());
        assertNotNull(approved1.get("supervisorApprovedAt").asText());

        // Keuangan: approve sambil mengisi akun baris 2 → APPROVED (tanpa aturan direktur yang cocok).
        asUser(finEmail, "ERP_ACCOUNTANT");
        JsonNode beforeAccounts = ok(get(BASE + "/" + id));
        String line2 = beforeAccounts.get("lines").get(1).get("id").asText();
        JsonNode approved2 = ok(post(BASE + "/" + id + "/approve",
                body("note", "", "lineAccounts", List.of(body("id", line2, "expenseAccountId", pantry)))));
        assertEquals("APPROVED", approved2.get("status").asText());
        assertEquals(finance, approved2.get("financeApprovedBy").asText());
        assertEquals(1, notificationCount(requester, "payment_request_approved", id));

        // Guard akun kas: akun beban tidak boleh jadi sumber dana.
        expectError(400, "Akun sumber dana harus akun kas/bank (1.1.x).",
                post(BASE + "/" + id + "/pay", body("cashAccountId", atk, "paymentDate", today())));
        expectError(400, "Format tanggal harus YYYY-MM-DD.",
                post(BASE + "/" + id + "/pay", body("cashAccountId", accountId("1.1.2"), "paymentDate", "20-10-2026")));

        BigDecimal bankBefore = balance("1.1.2");
        BigDecimal atkBefore = balance("6.16");
        BigDecimal pantryBefore = balance("6.20");
        JsonNode paid = ok(post(BASE + "/" + id + "/pay",
                body("cashAccountId", accountId("1.1.2"), "paymentDate", today(), "reference", "TRF-001")));
        assertEquals("PAID", paid.get("status").asText());
        assertTrue(paid.get("settlementStatus").isNull(), "jenis beban tidak membuka pertanggungjawaban");
        String journalId = paid.get("journalEntryId").asText();
        List<Map<String, Object>> rows = assertBalancedJournal(journalId, "Submitted");
        assertAmount("150000", net(rows, "6.16"));
        assertAmount("100000", net(rows, "6.20"));
        assertAmount("-250000", net(rows, "1.1.2"));
        assertAmount("-250000", balance("1.1.2").subtract(bankBefore));
        assertAmount("150000", balance("6.16").subtract(atkBefore));
        assertAmount("100000", balance("6.20").subtract(pantryBefore));
        assertEquals(1, notificationCount(requester, "payment_request_paid", id));

        // Bayar dua kali ditolak.
        expectError(400, "Pengajuan ini sudah dibayar.",
                post(BASE + "/" + id + "/pay", body("cashAccountId", accountId("1.1.2"), "paymentDate", today())));

        // Detail: jurnal, riwayat dan aksi.
        JsonNode paidDetail = ok(get(BASE + "/" + id));
        assertEquals(journalId, paidDetail.get("journalEntry").get("id").asText());
        assertEquals("1.1.2", paidDetail.get("cashAccount").get("accountNumber").asText());
        assertTrue(paidDetail.get("actions").get("cancelPayment").asBoolean());
        JsonNode history = paidDetail.get("history");
        assertEquals("PAY", history.get(history.size() - 1).get("action").asText());
        assertTrue(history.get(history.size() - 1).get("note").asText().endsWith(" · Ref TRF-001"));
        assertEquals(finance, history.get(history.size() - 1).get("actor").get("id").asText());

        // Batal bayar → APPROVED, jurnal dibatalkan, saldo kembali.
        JsonNode reverted = ok(post(BASE + "/" + id + "/cancel-payment", body("reason", "Salah rekening")));
        assertEquals("APPROVED", reverted.get("status").asText());
        assertTrue(reverted.get("journalEntryId").isNull());
        assertBalancedJournal(journalId, "Cancelled");
        assertAmount("0", balance("1.1.2").subtract(bankBefore));
        assertAmount("0", balance("6.16").subtract(atkBefore));
        assertEquals(1, notificationCount(requester, "payment_request_revision", id));
    }

    @Test
    void return_reject_and_requester_cancel() throws Exception {
        String reqEmail = email("pr.req2");
        String finEmail = email("pr.fin2");
        String requester = provision(reqEmail, "EMPLOYEE");
        provision(finEmail, "ERP_ACCOUNTANT");
        String office = typeId("OFFICE");

        asUser(reqEmail, "EMPLOYEE");
        String id = createDraft(office, "Konsumsi rapat", List.of(line("Nasi kotak", 300000, null))).get("id").asText();
        // Tanpa atasan → langsung Keuangan.
        JsonNode submitted = ok(post(BASE + "/" + id + "/submit", Map.of()));
        assertEquals("PENDING_FINANCE", submitted.get("status").asText());
        assertTrue(submitted.get("supervisorId").isNull());

        asUser(finEmail, "ERP_ACCOUNTANT");
        JsonNode approvals = ok(get(BASE + "?scope=approvals&search=konsumsi rapat"));
        assertTrue(approvals.get("total").asInt() >= 1);
        expectError(400, "Alasan minimal 3 karakter.", post(BASE + "/" + id + "/return", body("reason", " a ")));
        JsonNode returned = ok(post(BASE + "/" + id + "/return", body("reason", "Lampirkan nota")));
        assertEquals("REVISION", returned.get("status").asText());
        assertEquals("Lampirkan nota", returned.get("lastNote").asText());
        assertEquals(1, notificationCount(requester, "payment_request_revision", id));

        // Pengaju merevisi lalu mengajukan ulang; Keuangan menolak (final).
        asUser(reqEmail, "EMPLOYEE");
        ok(put(BASE + "/" + id, draft(office, "Konsumsi rapat (revisi)", List.of(line("Nasi kotak", 250000, null)))));
        assertEquals("PENDING_FINANCE", ok(post(BASE + "/" + id + "/submit", Map.of())).get("status").asText());
        asUser(finEmail, "ERP_ACCOUNTANT");
        JsonNode rejected = ok(post(BASE + "/" + id + "/reject", body("reason", "Tidak sesuai anggaran")));
        assertEquals("REJECTED", rejected.get("status").asText());
        assertNotNull(rejected.get("rejectedAt").asText());
        assertEquals(1, notificationCount(requester, "payment_request_rejected", id));

        asUser(reqEmail, "EMPLOYEE");
        expectError(400, "Pengajuan yang sudah dibayar, ditolak, atau dibatalkan tidak bisa dibatalkan.",
                post(BASE + "/" + id + "/cancel", Map.of()));

        // Draf lain dibatalkan pengaju: tanpa notifikasi.
        String id2 = createDraft(office, "Batal saja", List.of(line("Lain", 10000, null))).get("id").asText();
        JsonNode cancelled = ok(post(BASE + "/" + id2 + "/cancel", body("reason", "  Tidak jadi  ")));
        assertEquals("CANCELLED", cancelled.get("status").asText());
        assertEquals("Tidak jadi", cancelled.get("lastNote").asText());
        Integer notes = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE metadata->>'paymentRequestId' = ?",
                Integer.class, id2);
        assertEquals(0, notes);

        // Ringkasan & daftar milik sendiri.
        JsonNode summary = ok(get(BASE + "/summary?scope=mine"));
        assertEquals(1, summary.get("byStatus").get("REJECTED").get("count").asInt());
        assertEquals(1, summary.get("byStatus").get("CANCELLED").get("count").asInt());
        assertAmount("0", summary.get("totalAmount"));
        JsonNode mine = ok(get(BASE + "?scope=mine&sort=-totalAmount"));
        assertEquals(2, mine.get("total").asInt());
        assertEquals("Konsumsi rapat (revisi)", mine.get("data").get(0).get("purpose").asText());
        assertEquals("OFFICE", jdbc.queryForObject("SELECT code FROM payment_request_types WHERE id = ?", String.class,
                mine.get("data").get(0).get("type").get("id").asText()));
        assertNotNull(mine.get("data").get(0).get("requester").get("fullName"));
    }

    @Test
    void director_rule_requires_director_role() throws Exception {
        String reqEmail = email("pr.req3");
        String finEmail = email("pr.fin3");
        String dirEmail = email("pr.dir3");
        String role = "PRDIR_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        provision(reqEmail, "EMPLOYEE");
        provision(finEmail, "ERP_ACCOUNTANT");
        String director = provision(dirEmail, role);
        String ruleId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO approval_rules (id, document_type, min_amount, required_role, is_active, created_at, updated_at) "
                + "VALUES (?, 'PaymentRequest', 900000000, ?, true, now(), now())", ruleId, role);
        try {
            asUser(reqEmail, "EMPLOYEE");
            String id = createDraft(typeId("OTHER"), "Pembelian besar", List.of(line("Mesin", 950000000L, accountId("6.25"))))
                    .get("id").asText();
            ok(post(BASE + "/" + id + "/submit", Map.of()));

            asUser(finEmail, "ERP_ACCOUNTANT");
            JsonNode pending = ok(post(BASE + "/" + id + "/approve", Map.of()));
            assertEquals("PENDING_DIRECTOR", pending.get("status").asText());
            assertEquals(role, pending.get("directorRole").asText());
            assertEquals(1, notificationCount(director, "payment_request_pending", id));
            expectError(400, "Tahap ini harus disetujui oleh peran " + role + ".", post(BASE + "/" + id + "/approve", Map.of()));

            asUser(dirEmail, role);
            JsonNode inbox = ok(get(BASE + "?scope=approvals"));
            assertTrue(inbox.get("total").asInt() >= 1);
            JsonNode approved = ok(post(BASE + "/" + id + "/approve", body("note", "Disetujui direksi")));
            assertEquals("APPROVED", approved.get("status").asText());
            assertEquals(director, approved.get("directorApprovedBy").asText());
        } finally {
            jdbc.update("DELETE FROM approval_rules WHERE id = ?", ruleId);
        }
    }

    @Test
    void validation_scopes_and_meta() throws Exception {
        String reqEmail = email("pr.req4");
        String supEmail = email("pr.sup4");
        String requester = provision(reqEmail, "EMPLOYEE");
        String supervisor = provision(supEmail, "EMPLOYEE");
        setSupervisor(requester, supervisor);
        String office = typeId("OFFICE");

        asUser(reqEmail, "EMPLOYEE");
        expectError(400, "Invalid input: expected string, received undefined",
                post(BASE, body("payeeKind", "EMPLOYEE", "payeeName", "A", "purpose", "abcd", "lines", List.of())));
        expectError(400, "Keperluan minimal 3 karakter.", post(BASE, draft(office, " ab ", List.of(line("x", 1, null)))));
        expectError(400, "Nominal harus lebih dari 0.", post(BASE, draft(office, "Keperluan", List.of(line("x", 0, null)))));
        Map<String, Object> vendor = draft(office, "Bayar vendor", List.of(line("x", 1000, null)));
        vendor.put("payeeKind", "VENDOR");
        expectError(400, "Pilih supplier penerima pembayaran.", post(BASE, vendor));
        vendor.put("payeeKind", "SUPPLIER");
        expectError(400, "Invalid option: expected one of \"EMPLOYEE\"|\"VENDOR\"", post(BASE, vendor));
        expectError(400, "Akun biaya baris 1 tidak valid (harus akun detail yang aktif).",
                post(BASE, draft(office, "Keperluan", List.of(line("x", 1000, accountId("6"))))));
        expectError(400, "Tambahkan minimal satu rincian biaya.",
                post(BASE + "/" + createDraft(office, "Tanpa baris", List.of()).get("id").asText() + "/submit", Map.of()));

        // Scope terlarang untuk non-Keuangan; filter tidak dikenal.
        expectError(403, "Akses ditolak: Tidak memiliki izin.", get(BASE + "?scope=all"));
        expectError(403, "Akses ditolak: Tidak memiliki izin.", get(BASE + "/summary?scope=payable"));
        expectError(400, "Status pengajuan tidak dikenal.", get(BASE + "?status=XYZ"));
        expectError(400, "Format tanggal harus YYYY-MM-DD.", get(BASE + "?startDate=2026-1-1"));
        expectError(400, "Tanggal awal tidak boleh setelah tanggal akhir.", get(BASE + "?startDate=2026-10-02&endDate=2026-10-01"));

        // Meta: atasan, badge, rekening terakhir.
        JsonNode meta = ok(get(BASE + "/meta"));
        assertEquals(supervisor, meta.get("me").get("supervisor").get("id").asText());
        assertFalse(meta.get("isFinance").asBoolean());
        assertFalse(meta.get("canViewAll").asBoolean());
        assertEquals("1234567890", meta.get("lastPayee").get("bankAccountNo").asText());
        assertEquals(14, meta.get("overdueDays").asInt());
        assertEquals(0, meta.get("openAdvances").get("count").asInt());
        assertEquals(0, meta.get("badges").get("payable").asInt());

        // Atasan melihat badge persetujuan setelah diajukan.
        String id = createDraft(office, "Untuk badge", List.of(line("x", 5000, null))).get("id").asText();
        ok(post(BASE + "/" + id + "/submit", Map.of()));
        asUser(supEmail, "EMPLOYEE");
        assertTrue(ok(get(BASE + "/meta")).get("badges").get("approvals").asInt() >= 1);
        JsonNode list = ok(get(BASE + "?scope=approvals"));
        assertEquals(id, list.get("data").get(0).get("id").asText());
    }
}
