package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/** Alur /api/erp/fiscal-years: buat (tumpang tindih), tutup buku ke Laba Ditahan, buka kembali. */
class FiscalYearFlowTest extends AccountingTestSupport {

    @Test
    void create_rejects_overlap_and_bad_range() throws Exception {
        asAdmin();
        String seedName = jdbc.queryForObject("SELECT name FROM fiscal_years WHERE start_date <= now() AND end_date >= now() "
                + "LIMIT 1", String.class);
        int y = java.time.LocalDate.now().getYear();
        Result overlap = call(post("/api/erp/fiscal-years",
                Map.of("name", "X-" + uniq(), "startDate", y + "-06-01", "endDate", y + "-07-01")));
        assertEquals(409, overlap.status());
        assertEquals("Tahun fiskal bertumpang tindih dengan \"" + seedName + "\". Periksa rentang tanggal.", overlap.error());

        assertEquals("Tanggal mulai harus sebelum tanggal akhir.", call(post("/api/erp/fiscal-years",
                Map.of("name", "X-" + uniq(), "startDate", "1800-02-01", "endDate", "1800-01-01"))).error());
        assertEquals("Nama tahun fiskal wajib diisi.", call(post("/api/erp/fiscal-years",
                Map.of("name", "", "startDate", "1800-01-01", "endDate", "1800-02-01"))).error());

        int free = freeYear();
        String name = "FY-T-" + uniq();
        Result created = call(post("/api/erp/fiscal-years",
                Map.of("name", name, "startDate", free + "-01-01", "endDate", free + "-01-31")));
        assertEquals(201, created.status());
        assertFalse(created.data().get("isClosed").asBoolean());
        assertEquals(free + "-01-01T00:00:00.000Z", created.data().get("startDate").asText());

        Result dupName = call(post("/api/erp/fiscal-years",
                Map.of("name", name, "startDate", free + "-03-01", "endDate", free + "-03-31")));
        assertEquals(409, dupName.status());
        assertEquals("Nama tahun fiskal sudah digunakan.", dupName.error());

        JsonNode list = ok(get("/api/erp/fiscal-years"));
        assertTrue(list.size() >= 2);
        assertFalse(list.get(0).has("user"));
    }

    @Test
    void close_moves_profit_to_retained_earnings_and_reopen_restores() throws Exception {
        String userId = adminUserId();
        int year = freeYear();
        String name = "FY-C-" + uniq();
        JsonNode fy = ok(post("/api/erp/fiscal-years",
                Map.of("name", name, "startDate", year + "-01-01", "endDate", year + "-12-31")));
        String fyId = fy.get("id").asText();

        String revenue = newAccount("4", "REVENUE").get("id").asText();
        String expense = newAccount("6", "EXPENSE").get("id").asText();
        String cash = accountId("1.1.1");
        ok(post("/api/erp/journals", journal(year + "-06-15", "Penjualan " + name,
                List.of(line(cash, 1000000, 0), line(revenue, 0, 1000000)))));
        ok(post("/api/erp/journals", journal(year + "-07-15", "Beban " + name,
                List.of(line(expense, 300000, 0), line(cash, 0, 300000)))));

        String currentProfit = accountId("3.4");
        String retained = accountId("3.2");
        BigDecimal cpBefore = balance(currentProfit);
        BigDecimal reBefore = balance(retained);

        // Tanpa capability doc.cancel → 403 (role dengan izin edit settings saja).
        asUser("settings." + uniq() + "@test.local", customRole(Map.of("settings", "edit")));
        assertEquals(403, call(post("/api/erp/fiscal-years/" + fyId + "/close", Map.of())).status());

        asAdmin();
        JsonNode closed = ok(post("/api/erp/fiscal-years/" + fyId + "/close", Map.of()));
        assertTrue(closed.get("isClosed").asBoolean());
        assertEquals(userId, closed.get("closedBy").asText());

        String closeId = jdbc.queryForObject("SELECT id FROM journal_entries WHERE entry_number = ?", String.class,
                "CLOSE-" + name);
        assertEquals(0, ledgerImbalance(closeId).signum());
        assertEquals("Tutup Buku Tahun " + name,
                jdbc.queryForObject("SELECT narration FROM journal_entries WHERE id = ?", String.class, closeId));
        assertEquals("Tutup buku " + name, jdbc.queryForObject("SELECT description FROM accounting_ledger_entries "
                + "WHERE reference_id = ? AND account_id = ?", String.class, closeId, currentProfit));
        assertEquals("Transfer ke laba ditahan " + name, jdbc.queryForObject("SELECT description FROM "
                + "journal_entry_accounts WHERE journal_entry_id = ? AND account_id = ?", String.class, closeId, retained));
        BigDecimal profit = new BigDecimal("700000");
        assertEquals(0, cpBefore.subtract(profit).compareTo(balance(currentProfit)));
        assertEquals(0, reBefore.add(profit).compareTo(balance(retained)));
        // Saldo pendapatan/beban dikurangi kontribusi tahun ini.
        assertEquals(0, balance(revenue).signum());
        assertEquals(0, balance(expense).signum());

        assertEquals("Tahun buku sudah ditutup.", call(post("/api/erp/fiscal-years/" + fyId + "/close", Map.of())).error());
        assertEquals("Tahun fiskal tidak ditemukan atau sudah ditutup untuk tanggal ini.", call(post("/api/erp/journals",
                journal(year + "-08-01", "x", List.of(line(cash, 1, 0), line(revenue, 0, 1))))).error());

        JsonNode reopened = ok(post("/api/erp/fiscal-years/" + fyId + "/reopen", Map.of()));
        assertFalse(reopened.get("isClosed").asBoolean());
        assertTrue(reopened.get("closedBy").isNull());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE entry_number = ?", Integer.class,
                "CLOSE-" + name));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry_accounts WHERE journal_entry_id = ?",
                Integer.class, closeId));
        assertEquals(0, new BigDecimal("1000000").compareTo(balance(revenue)));
        assertEquals(0, new BigDecimal("300000").compareTo(balance(expense)));
        assertEquals(0, cpBefore.compareTo(balance(currentProfit)));
        assertEquals(0, reBefore.compareTo(balance(retained)));
        assertEquals("Tahun buku belum ditutup.", call(post("/api/erp/fiscal-years/" + fyId + "/reopen", Map.of())).error());

        // Tutup ulang setelah dibuka kembali tetap bisa (jurnal CLOSE lama sudah dihapus).
        ok(post("/api/erp/fiscal-years/" + fyId + "/close", Map.of()));
        assertEquals(0, reBefore.add(profit).compareTo(balance(retained)));
        ok(post("/api/erp/fiscal-years/" + fyId + "/reopen", Map.of()));
        assertEquals("Tahun buku tidak ditemukan.", call(post("/api/erp/fiscal-years/nope/close", Map.of())).error());
    }

    /** Tahun yang belum dipakai tahun fiskal mana pun. */
    private int freeYear() {
        for (int i = 0; i < 200; i++) {
            int y = ThreadLocalRandom.current().nextInt(1900, 2000);
            Integer used = jdbc.queryForObject("SELECT COUNT(*) FROM fiscal_years WHERE start_date <= make_date(?, 12, 31) "
                    + "AND end_date >= make_date(?, 1, 1)", Integer.class, y, y);
            if (used == 0) {
                return y;
            }
        }
        throw new IllegalStateException("tidak ada tahun kosong");
    }
}
