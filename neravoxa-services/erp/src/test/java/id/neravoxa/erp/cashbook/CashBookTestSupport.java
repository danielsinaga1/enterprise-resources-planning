package id.neravoxa.erp.cashbook;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.support.ErpIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixture bersama uji Draft Buku Kas &amp; impor (DB tidak di-rollback antar test → data unik). */
abstract class CashBookTestSupport extends ErpIntegrationTest {

    private static final Random RND = new Random();

    /** Token huruf acak (tanpa angka, agar ikut pola terpelajari dan tidak mirip antar test). */
    protected static String word() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append((char) ('a' + RND.nextInt(26)));
        }
        return sb.toString();
    }

    protected static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    protected static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    protected String accountId(String number) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", String.class, number);
    }

    protected BigDecimal balance(String number) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE account_number = ?", BigDecimal.class, number);
    }

    protected static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), "nilai " + actual + " ≠ " + expected);
    }

    protected static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "nilai " + actual + " ≠ " + expected);
    }

    protected static JsonNode rowByIdx(JsonNode detail, int idx) {
        for (JsonNode r : detail.get("rows")) {
            if (r.get("idx").asInt() == idx) {
                return r;
            }
        }
        throw new AssertionError("baris " + idx + " tidak ada");
    }

    protected static List<String> texts(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.asText()));
        return out;
    }

    /** Jurnal ber-status Submitted seimbang dan baris DR/CR sesuai harapan (nomor akun → debit, kredit). */
    protected void assertJournal(String journalId, String debitAccount, String creditAccount, String amount) {
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT a.account_number, i.debit, i.credit FROM journal_entry_accounts i JOIN accounts a ON a.id = i.account_id "
                        + "WHERE i.journal_entry_id = ?", journalId);
        assertEquals(2, items.size(), "jumlah baris jurnal");
        BigDecimal dr = BigDecimal.ZERO;
        BigDecimal cr = BigDecimal.ZERO;
        for (Map<String, Object> it : items) {
            BigDecimal d = (BigDecimal) it.get("debit");
            BigDecimal c = (BigDecimal) it.get("credit");
            dr = dr.add(d);
            cr = cr.add(c);
            if (d.signum() > 0) {
                assertEquals(debitAccount, it.get("account_number"), "akun debit");
                assertMoney(amount, d);
            } else {
                assertEquals(creditAccount, it.get("account_number"), "akun kredit");
                assertMoney(amount, c);
            }
        }
        assertEquals(0, dr.compareTo(cr), "jurnal seimbang");
        String status = jdbc.queryForObject("SELECT status::text FROM journal_entries WHERE id = ?", String.class, journalId);
        assertEquals("Submitted", status);
        BigDecimal ledger = jdbc.queryForObject("SELECT COALESCE(SUM(debit) - SUM(credit), 0) FROM accounting_ledger_entries "
                + "WHERE reference_id = ?", BigDecimal.class, journalId);
        assertEquals(0, ledger.signum(), "ledger seimbang");
        Integer ledgerRows = jdbc.queryForObject("SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ?",
                Integer.class, journalId);
        assertTrue(ledgerRows == 2, "ledger 2 baris");
    }
}
