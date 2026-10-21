package id.enterpriseapps.erp.accounting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * Tanggal jalan berikutnya template berulang — port {@code calculateNextRunDate}
 * (routes/recurring.ts & services/recurringRunner.ts).
 *
 * <p>Meniru aritmetika {@code Date} JavaScript pada server ber-TZ UTC, termasuk "luapan" tanggal:
 * {@code setMonth(+1)} dari 31 Januari menjadi 3 Maret (bukan 28 Februari), lalu bila
 * {@code dayOfMonth} diisi tanggal diset ke min(dayOfMonth, hari terakhir bulan hasil luapan).
 */
final class RecurringSchedule {

    private RecurringSchedule() {
    }

    static Instant next(String frequency, Instant from, Integer dayOfMonth) {
        LocalDateTime t = LocalDateTime.ofInstant(from, ZoneOffset.UTC);
        boolean clampDay = dayOfMonth != null && dayOfMonth != 0;
        switch (frequency == null ? "" : frequency) {
            case "daily" -> t = t.plusDays(1);
            case "weekly" -> t = t.plusDays(7);
            case "monthly" -> {
                t = addMonths(t, 1);
                if (clampDay) {
                    t = withDay(t, dayOfMonth);
                }
            }
            case "quarterly" -> {
                t = addMonths(t, 3);
                if (clampDay) {
                    t = withDay(t, dayOfMonth);
                }
            }
            case "yearly" -> t = addYears(t, 1);
            default -> t = addMonths(t, 1);
        }
        return t.toInstant(ZoneOffset.UTC);
    }

    /** {@code setMonth(getMonth() + n)}: tanggal dipertahankan dan meluap ke bulan berikutnya bila perlu. */
    static LocalDateTime addMonths(LocalDateTime t, int n) {
        LocalDate first = t.toLocalDate().withDayOfMonth(1).plusMonths(n);
        return first.plusDays(t.getDayOfMonth() - 1L).atTime(t.toLocalTime());
    }

    /** {@code setFullYear(getFullYear() + n)}: 29 Feb → 1 Mar pada tahun non-kabisat. */
    static LocalDateTime addYears(LocalDateTime t, int n) {
        LocalDate first = LocalDate.of(t.getYear() + n, t.getMonthValue(), 1);
        return first.plusDays(t.getDayOfMonth() - 1L).atTime(t.toLocalTime());
    }

    /** {@code setDate(Math.min(dayOfMonth, maxDay))} pada bulan berjalan. */
    static LocalDateTime withDay(LocalDateTime t, int dayOfMonth) {
        int max = YearMonth.from(t).lengthOfMonth();
        return t.withDayOfMonth(Math.max(1, Math.min(dayOfMonth, max)));
    }
}
