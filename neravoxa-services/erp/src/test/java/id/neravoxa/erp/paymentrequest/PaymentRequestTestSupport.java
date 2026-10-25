package id.neravoxa.erp.paymentrequest;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.support.ErpIntegrationTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixture bersama uji Pengajuan Pembayaran (data unik per test; DB tidak di-rollback antar test). */
abstract class PaymentRequestTestSupport extends ErpIntegrationTest {

    protected static final String BASE = "/api/erp/payment-requests";

    protected static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    protected static String email(String who) {
        return who + "." + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    /** Login sebagai pengguna lalu pastikan snapshot users-nya ada; kembalikan id user ERP. */
    protected String provision(String email, String... roles) throws Exception {
        asUser(email, roles);
        ok(get(BASE + "/meta"));
        return jdbc.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?)", String.class, email);
    }

    protected void setSupervisor(String userId, String supervisorId) {
        jdbc.update("UPDATE users SET supervisor_id = ? WHERE id = ?", supervisorId, userId);
    }

    protected String typeId(String code) {
        return jdbc.queryForObject("SELECT id FROM payment_request_types WHERE code = ?", String.class, code);
    }

    protected String accountId(String number) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", String.class, number);
    }

    protected BigDecimal balance(String number) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE account_number = ?", BigDecimal.class, number);
    }

    protected String createParty(String name, String type) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO parties (id, name, party_type, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, true, now(), now())", id, name, type);
        return id;
    }

    protected static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    protected static Map<String, Object> line(String description, long amount, String expenseAccountId) {
        Map<String, Object> m = body("description", description, "amount", amount);
        if (expenseAccountId != null) {
            m.put("expenseAccountId", expenseAccountId);
        }
        return m;
    }

    /** Body {@code PaymentRequestInputSchema} penerima karyawan. */
    protected static Map<String, Object> draft(String typeId, String purpose, List<Map<String, Object>> lines) {
        return body("typeId", typeId, "payeeKind", "EMPLOYEE", "payeeName", "Budi Santoso", "bankName", "BCA",
                "bankAccountNo", "1234567890", "bankAccountHolder", "Budi Santoso", "department", "Produksi",
                "purpose", purpose, "lines", new ArrayList<>(lines));
    }

    protected JsonNode createDraft(String typeId, String purpose, List<Map<String, Object>> lines) throws Exception {
        Result r = call(post(BASE, draft(typeId, purpose, lines)));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.data();
    }

    /** Status + pesan error (400/403/404) sebuah panggilan. */
    protected void expectError(int status, String message, MockHttpServletRequestBuilder req) throws Exception {
        Result r = call(req);
        assertEquals(status, r.status(), String.valueOf(r.body()));
        if (message != null) {
            assertEquals(message, r.error());
        }
    }

    /** Respons mentah (unduhan berkas) dengan principal pengguna saat ini. */
    protected MvcResult raw(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(request -> {
            request.setUserPrincipal(SecurityContextHolder.getContext().getAuthentication());
            return request;
        })).andReturn();
    }

    /** Jurnal seimbang, ledger sebanyak baris jurnal; kembalikan baris (account_number, debit, credit). */
    protected List<Map<String, Object>> assertBalancedJournal(String journalId, String expectedStatus) {
        String status = jdbc.queryForObject("SELECT status FROM journal_entries WHERE id = ?", String.class, journalId);
        assertEquals(expectedStatus, status);
        BigDecimal dr = jdbc.queryForObject("SELECT COALESCE(SUM(debit),0) FROM journal_entry_accounts WHERE journal_entry_id = ?",
                BigDecimal.class, journalId);
        BigDecimal cr = jdbc.queryForObject("SELECT COALESCE(SUM(credit),0) FROM journal_entry_accounts WHERE journal_entry_id = ?",
                BigDecimal.class, journalId);
        assertEquals(0, dr.compareTo(cr), "jurnal tidak seimbang: " + dr + " vs " + cr);
        Integer items = jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry_accounts WHERE journal_entry_id = ?",
                Integer.class, journalId);
        Integer ledger = jdbc.queryForObject("SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ?",
                Integer.class, journalId);
        assertEquals(items, ledger);
        Integer cancelledLedger = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ? AND is_cancelled = true",
                Integer.class, journalId);
        assertEquals("Cancelled".equals(expectedStatus) ? items : Integer.valueOf(0), cancelledLedger);
        return jdbc.queryForList("SELECT a.account_number, j.debit, j.credit, j.party_id FROM journal_entry_accounts j "
                + "JOIN accounts a ON a.id = j.account_id WHERE j.journal_entry_id = ? ORDER BY j.debit DESC, a.account_number",
                journalId);
    }

    /** Jumlah debit−kredit baris jurnal untuk akun tertentu. */
    protected static BigDecimal net(List<Map<String, Object>> rows, String accountNumber) {
        BigDecimal n = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            if (accountNumber.equals(r.get("account_number"))) {
                n = n.add((BigDecimal) r.get("debit")).subtract((BigDecimal) r.get("credit"));
            }
        }
        return n;
    }

    protected static void assertAmount(String expected, JsonNode node) {
        assertTrue(node != null && node.isNumber(), "bukan angka: " + node);
        assertEquals(0, new BigDecimal(expected).compareTo(node.decimalValue()), "nilai " + node + " ≠ " + expected);
    }

    protected static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "nilai " + actual + " ≠ " + expected);
    }

    protected int notificationCount(String userId, String type, String requestId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = ? "
                + "AND metadata->>'paymentRequestId' = ?", Integer.class, userId, type, requestId);
        return n == null ? 0 : n;
    }
}
