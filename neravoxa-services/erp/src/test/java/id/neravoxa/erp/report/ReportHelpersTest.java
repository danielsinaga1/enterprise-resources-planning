package id.neravoxa.erp.report;

import id.neravoxa.erp.entity.AccountType;
import id.neravoxa.erp.entity.RootType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Semantik Date JS (proses ber-TZ WIB), urutan nomor akun, dan neraca saldo — tanpa Spring/DB. */
class ReportHelpersTest {

    @Test
    void js_date_semantics_in_business_zone() {
        assertEquals(Instant.parse("2026-10-19T00:00:00Z"), ReportDates.parse("2026-10-19"));
        assertEquals(Instant.parse("2026-03-02T00:00:00Z"), ReportDates.parse("2026-02-30"), "hari digeser seperti V8");
        assertNull(ReportDates.parse("2026-13-01"));
        assertNull(ReportDates.parse("bukan"));
        assertEquals(Instant.parse("2026-10-19T01:00:00Z"), ReportDates.parse("2026-10-19T08:00"), "tanpa zona = waktu lokal");
        assertEquals(Instant.parse("2026-10-19T08:00:00Z"), ReportDates.parse("2026-10-19T08:00:00.000Z"));
        assertEquals(Instant.parse("2026-10-19T16:59:59.999Z"),
                ReportDates.endOfLocalDay(Instant.parse("2026-10-19T00:00:00Z")), "setHours(23,59,59,999) WIB");
        assertEquals(Instant.parse("2026-04-30T17:00:00Z"), ReportDates.localDate(2026, 9 - 5, 1));
        assertEquals("Okt 26", ReportDates.monthLabel(2026, 10));
        assertEquals(2, ReportDates.ceilDays(86_400_001L));
        assertEquals(-1, ReportDates.ceilDays(-86_400_000L));
    }

    @Test
    void account_numbers_sort_numerically_and_trial_balance_skips_empty() {
        assertTrue(ReportAccounts.compare("1.4.2", "1.4.10") < 0);
        assertTrue(ReportAccounts.compare("1", "1.1") < 0, "induk sebelum anak");
        List<TrialBalance.Account> accounts = List.of(
                new TrialBalance.Account("s", "Penjualan", "4.1", AccountType.REVENUE, RootType.REVENUE),
                new TrialBalance.Account("g", "HPP grup", "5", AccountType.EXPENSE, RootType.EXPENSE),
                new TrialBalance.Account("c", "Kas", "1.1.10", AccountType.ASSET, RootType.ASSET),
                new TrialBalance.Account("u", "Tanpa posting", "1.1.2", AccountType.ASSET, RootType.ASSET));
        List<Map<String, Object>> rows = TrialBalance.build(accounts, List.of(
                new TrialBalance.Sums("c", new BigDecimal("1000"), BigDecimal.ZERO),
                new TrialBalance.Sums("s", BigDecimal.ZERO, new BigDecimal("1000")),
                new TrialBalance.Sums("g", new BigDecimal("600"), new BigDecimal("600"))));
        assertEquals(List.of("1.1.10", "4.1", "5"), rows.stream().map(r -> r.get("accountNumber")).toList());
        assertEquals(new BigDecimal("1000"), rows.get(1).get("balance"));
        assertEquals(List.of("id", "name", "accountNumber", "accountType", "rootType", "debit", "credit", "balance"),
                List.copyOf(rows.get(0).keySet()));
    }

    @Test
    void search_limit_follows_number_or_default() {
        assertEquals(10, ErpGlobalSearchService.limit(null));
        assertEquals(10, ErpGlobalSearchService.limit("abc"));
        assertEquals(10, ErpGlobalSearchService.limit("0"));
        assertEquals(5, ErpGlobalSearchService.limit("5"));
        assertEquals(20, ErpGlobalSearchService.limit("50"));
    }
}
