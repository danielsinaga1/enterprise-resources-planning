package id.enterpriseapps.erp.purchase;

import id.enterpriseapps.erp.support.ErpIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Helper data uji modul pembelian (data unik per test; DB dipakai bersama satu JVM). */
abstract class PurchaseTestSupport extends ErpIntegrationTest {

    static String uid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    String supplier(String name) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO parties (id, name, party_type) VALUES (?, ?, 'Supplier')", id, name);
        return id;
    }

    String item(String code) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO inventory_items (id, code, name, unit) VALUES (?, ?, ?, 'kg')", id, code, "Item " + code);
        return id;
    }

    String warehouse() {
        return jdbc.queryForObject("SELECT id FROM warehouses WHERE code = 'GDG-UTAMA'", String.class);
    }

    String userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?)", String.class, email);
    }

    BigDecimal stock(String itemId) {
        return jdbc.queryForObject("SELECT current_stock FROM inventory_items WHERE id = ?", BigDecimal.class, itemId);
    }

    BigDecimal whStock(String itemId, String whId) {
        List<BigDecimal> r = jdbc.queryForList("SELECT current_stock FROM inventory_stocks WHERE item_id = ? AND warehouse_id = ?",
                BigDecimal.class, itemId, whId);
        return r.isEmpty() ? BigDecimal.ZERO : r.get(0);
    }

    BigDecimal avg(String itemId) {
        return jdbc.queryForObject("SELECT average_cost FROM inventory_items WHERE id = ?", BigDecimal.class, itemId);
    }

    BigDecimal outstanding(String partyId) {
        return jdbc.queryForObject("SELECT outstanding_amount FROM parties WHERE id = ?", BigDecimal.class, partyId);
    }

    /** Σ debit / Σ kredit baris jurnal per nomor akun untuk satu nomor jurnal. */
    BigDecimal jeSum(String entryNumber, String accountNumber, String side) {
        BigDecimal v = jdbc.queryForObject("SELECT COALESCE(SUM(ji." + side + "), 0) FROM journal_entry_accounts ji "
                        + "JOIN journal_entries j ON j.id = ji.journal_entry_id JOIN accounts a ON a.id = ji.account_id "
                        + "WHERE j.entry_number = ? AND a.account_number = ?",
                BigDecimal.class, entryNumber, accountNumber);
        return v;
    }

    /** {debit, credit} total satu jurnal + ledger aktif-nya. */
    BigDecimal[] jeTotals(String entryNumber) {
        Map<String, Object> r = jdbc.queryForMap("SELECT COALESCE(SUM(ji.debit),0) d, COALESCE(SUM(ji.credit),0) c "
                + "FROM journal_entry_accounts ji JOIN journal_entries j ON j.id = ji.journal_entry_id WHERE j.entry_number = ?",
                entryNumber);
        return new BigDecimal[]{(BigDecimal) r.get("d"), (BigDecimal) r.get("c")};
    }

    String jeStatus(String entryNumber) {
        List<String> r = jdbc.queryForList("SELECT status FROM journal_entries WHERE entry_number = ?", String.class, entryNumber);
        return r.isEmpty() ? null : r.get(0);
    }

    static void assertMoney(String expected, Object actual) {
        BigDecimal a = actual instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(actual));
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal(expected).compareTo(a),
                "expected " + expected + " but was " + a);
    }
}
