package id.neravoxa.erp.sales;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.support.ErpIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fixture bersama uji paket penjualan (data unik per test; DB tidak di-rollback antar test). */
abstract class SalesTestSupport extends ErpIntegrationTest {

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

    protected String createCustomer(String name) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO parties (id, name, party_type, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Customer', true, now(), now())", id, name);
        return id;
    }

    protected String accountId(String number) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", String.class, number);
    }

    protected BigDecimal balance(String number) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE account_number = ?", BigDecimal.class, number);
    }

    protected BigDecimal partyOutstanding(String partyId) {
        return jdbc.queryForObject("SELECT outstanding_amount FROM parties WHERE id = ?", BigDecimal.class, partyId);
    }

    protected String openFiscalYearId() {
        return jdbc.queryForObject(
                "SELECT id FROM fiscal_years WHERE is_closed = false AND start_date <= now() AND end_date >= now() - interval '1 day' "
                        + "ORDER BY start_date DESC LIMIT 1", String.class);
    }

    protected static Map<String, Object> serviceLine(String name, Object qty, Object rate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemName", name);
        m.put("itemType", "service");
        m.put("quantity", qty);
        m.put("unit", "jasa");
        m.put("rate", rate);
        return m;
    }

    protected static Map<String, Object> invoiceBody(String partyId, List<Map<String, Object>> items) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("date", today());
        b.put("partyId", partyId);
        b.put("items", items);
        return b;
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
        return dr;
    }

    protected static void assertAmount(String expected, JsonNode node) {
        assertEquals(0, new BigDecimal(expected).compareTo(node.decimalValue()), "nilai " + node + " ≠ " + expected);
    }

    protected static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "nilai " + actual + " ≠ " + expected);
    }
}
