package id.neravoxa.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.support.ErpIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fixture bersama uji paket kas &amp; bank (data unik per test; DB tidak di-rollback antar test). */
abstract class CashBankTestSupport extends ErpIntegrationTest {

    protected static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    protected static String daysFromToday(int days) {
        return LocalDate.now(ZoneOffset.UTC).plusDays(days).toString();
    }

    /** Pastikan user admin ERP terprovisi; kembalikan id-nya. */
    protected String adminUserId() throws Exception {
        asAdmin();
        ok(get("/api/erp/me"));
        return jdbc.queryForObject("SELECT id FROM users WHERE email = 'erp.admin@test.local'", String.class);
    }

    protected String createParty(String name, String type) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO parties (id, name, party_type, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, true, now(), now())", id, name, type);
        return id;
    }

    protected String accountId(String number) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", String.class, number);
    }

    protected BigDecimal balance(String number) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE account_number = ?", BigDecimal.class, number);
    }

    protected BigDecimal partyColumn(String partyId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM parties WHERE id = ?", BigDecimal.class, partyId);
    }

    protected String openFiscalYearId() {
        return jdbc.queryForObject(
                "SELECT id FROM fiscal_years WHERE is_closed = false AND start_date <= now() AND end_date >= now() - interval '1 day' "
                        + "ORDER BY start_date DESC LIMIT 1", String.class);
    }

    /** Invoice penjualan/pembelian langsung via SQL (status Submitted, outstanding = sisa). */
    protected String createInvoice(String table, String number, String partyId, String date, String dueDate,
                                   String grandTotal, String outstanding, String status) throws Exception {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO " + table + " (id, invoice_number, date, party_id, status, grand_total, outstanding, due_date, "
                        + "fiscal_year_id, created_by, submitted_at, created_at, updated_at) "
                        + "VALUES (?, ?, CAST(? AS timestamp), ?, ?, ?, ?, CAST(? AS timestamp), ?, ?, now(), now(), now())",
                id, number, date, partyId, status, new BigDecimal(grandTotal), new BigDecimal(outstanding), dueDate,
                openFiscalYearId(), adminUserId());
        return id;
    }

    protected Map<String, Object> invoiceRow(String table, String id) {
        return jdbc.queryForMap("SELECT status, outstanding FROM " + table + " WHERE id = ?", id);
    }

    protected static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Jurnal seimbang & ledger sejumlah baris jurnal; kembalikan total debit. */
    protected BigDecimal assertBalancedJournal(String entryNumber, String expectedStatus) {
        Map<String, Object> je = jdbc.queryForMap("SELECT id, status FROM journal_entries WHERE entry_number = ?", entryNumber);
        assertEquals(expectedStatus, je.get("status"));
        String jeId = (String) je.get("id");
        BigDecimal dr = jdbc.queryForObject("SELECT COALESCE(SUM(debit),0) FROM journal_entry_accounts WHERE journal_entry_id = ?",
                BigDecimal.class, jeId);
        BigDecimal cr = jdbc.queryForObject("SELECT COALESCE(SUM(credit),0) FROM journal_entry_accounts WHERE journal_entry_id = ?",
                BigDecimal.class, jeId);
        assertEquals(0, dr.compareTo(cr), "jurnal " + entryNumber + " tidak seimbang");
        Integer items = jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry_accounts WHERE journal_entry_id = ?", Integer.class, jeId);
        Integer ledger = jdbc.queryForObject("SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ?", Integer.class, jeId);
        assertEquals(items, ledger);
        Integer cancelledLedger = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ? AND is_cancelled = true", Integer.class, jeId);
        assertEquals("Cancelled".equals(expectedStatus) ? items : Integer.valueOf(0), cancelledLedger);
        return dr;
    }

    protected static void assertAmount(String expected, JsonNode node) {
        assertEquals(0, new BigDecimal(expected).compareTo(node.decimalValue()), "nilai " + node + " ≠ " + expected);
    }

    protected static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "nilai " + actual + " ≠ " + expected);
    }

    protected static void assertAmount(String expected, Object actual) {
        assertAmount(expected, new BigDecimal(actual.toString()));
    }
}
