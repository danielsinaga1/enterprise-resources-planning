package id.neravoxa.erp.report;

import id.neravoxa.erp.core.ErpDates;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantik {@code Date} JavaScript untuk laporan/dashboard ENTARIS.
 *
 * <p>Proses server ENTARIS berjalan dengan {@code TZ=Asia/Jakarta} ("batas periode laporan",
 * lihat docker-compose ENTARIS), jadi operasi waktu-lokal JS ({@code setHours(23,59,59,999)},
 * {@code new Date(y, m, 1)}, {@code getMonth()}) di sini memakai zona bisnis
 * {@link ErpDates#businessOffset()}. {@code new Date('YYYY-MM-DD')} tetap tengah malam UTC
 * (aturan ISO JS), sedangkan tanggal-waktu tanpa zona ditafsirkan waktu lokal (zona bisnis).
 */
final class ReportDates {

    static final long DAY_MS = 86_400_000L;

    private static final Pattern ISO_DAY = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern ISO_MONTH = Pattern.compile("^(\\d{4})-(\\d{2})$");
    private static final Pattern ISO_YEAR = Pattern.compile("^(\\d{4})$");
    private static final Pattern LOCAL_DATE_TIME = Pattern.compile(
            "^(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d{1,9}))?)?$");
    private static final Pattern LOOSE_DAY = Pattern.compile("^\\s*(\\d{4})-(\\d{1,2})-(\\d{1,2})\\s*$");
    private static final DateTimeFormatter SQL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC);
    /** Nama bulan pendek {@code toLocaleString('id-ID', { month: 'short' })}. */
    private static final String[] ID_MONTHS = {"Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt",
            "Nov", "Des"};

    private ReportDates() {
    }

    static ZoneOffset zone() {
        return ErpDates.businessOffset();
    }

    /** {@code new Date(raw)}; null bila kosong atau Invalid Date. */
    static Instant parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            Matcher m = ISO_DAY.matcher(raw);
            if (m.matches()) {
                return utcDay(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
            m = ISO_MONTH.matcher(raw);
            if (m.matches()) {
                return utcDay(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), 1);
            }
            m = ISO_YEAR.matcher(raw);
            if (m.matches()) {
                return utcDay(Integer.parseInt(m.group(1)), 1, 1);
            }
            m = LOCAL_DATE_TIME.matcher(raw);
            if (m.matches()) {
                LocalDate day = rollDay(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
                if (day == null) {
                    return null;
                }
                int h = Integer.parseInt(m.group(4));
                int min = Integer.parseInt(m.group(5));
                int s = m.group(6) == null ? 0 : Integer.parseInt(m.group(6));
                String frac = m.group(7) == null ? "0" : (m.group(7) + "000").substring(0, 3);
                if (h > 24 || min > 59 || s > 59 || (h == 24 && (min > 0 || s > 0))) {
                    return null;
                }
                return day.atStartOfDay().plusHours(h).plusMinutes(min).plusSeconds(s)
                        .plusNanos(Integer.parseInt(frac) * 1_000_000L).toInstant(zone());
            }
            Instant zoned = ErpDates.tryInstant(raw);
            if (zoned != null && (raw.endsWith("Z") || raw.matches(".*[+-]\\d{2}:?\\d{2}$"))) {
                return zoned;
            }
            m = LOOSE_DAY.matcher(raw);
            if (m.matches()) {
                // Format non-ISO (mis. "2026-1-5") diparse JS sebagai tengah malam waktu lokal.
                LocalDate day = rollDay(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
                return day == null ? null : day.atStartOfDay(zone()).toInstant();
            }
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
        return null;
    }

    /** Tanggal ISO JS: hari 1..31 diterima lalu digeser (2026-02-30 → 2 Maret), bulan wajib 1..12. */
    private static LocalDate rollDay(int y, int m, int d) {
        if (m < 1 || m > 12 || d < 1 || d > 31) {
            return null;
        }
        return LocalDate.of(y, m, 1).plusDays(d - 1L);
    }

    private static Instant utcDay(int y, int m, int d) {
        LocalDate day = rollDay(y, m, d);
        return day == null ? null : day.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** {@code d.setHours(23, 59, 59, 999)} pada proses ber-TZ zona bisnis. */
    static Instant endOfLocalDay(Instant instant) {
        LocalDate day = instant.atOffset(zone()).toLocalDate();
        return day.atTime(LocalTime.of(23, 59, 59, 999_000_000)).toInstant(zone());
    }

    /** {@code d.setHours(0, 0, 0, 0)} pada proses ber-TZ zona bisnis. */
    static Instant startOfLocalDay(Instant instant) {
        return instant.atOffset(zone()).toLocalDate().atStartOfDay(zone()).toInstant();
    }

    /** {@code new Date(y, monthIndex, day)} waktu lokal, dengan overflow bulan/hari seperti JS. */
    static Instant localDate(int year, int monthIndex, int day) {
        LocalDate d = LocalDate.of(year, 1, 1).plusMonths(monthIndex).plusDays(day - 1L);
        return d.atStartOfDay(zone()).toInstant();
    }

    /** {@code new Date(y, monthIndex, day, h, m, s)} waktu lokal. */
    static Instant localDateTime(int year, int monthIndex, int day, int h, int m, int s) {
        LocalDateTime t = LocalDate.of(year, 1, 1).plusMonths(monthIndex).plusDays(day - 1L).atStartOfDay()
                .plusHours(h).plusMinutes(m).plusSeconds(s);
        return t.toInstant(zone());
    }

    /** Tanggal lokal (zona bisnis) dari instant — padanan getFullYear/getMonth/getDate. */
    static LocalDate localDay(Instant instant) {
        return instant.atOffset(zone()).toLocalDate();
    }

    /** {@code new Date(now.getFullYear(), now.getMonth() + delta, 1)}. */
    static Instant startOfLocalMonth(Instant now, int monthsDelta) {
        LocalDate today = localDay(now);
        return localDate(today.getYear(), today.getMonthValue() - 1 + monthsDelta, 1);
    }

    /** {@code new Date(now.getFullYear(), 0, 1)}. */
    static Instant startOfLocalYear(Instant now) {
        return localDate(localDay(now).getYear(), 0, 1);
    }

    /** {@code Math.ceil(ms / 86400000)} dengan aritmetika bulat. */
    static long ceilDays(long ms) {
        return -Math.floorDiv(-ms, DAY_MS);
    }

    /** {@code Math.floor(ms / 86400000)}. */
    static long floorDays(long ms) {
        return Math.floorDiv(ms, DAY_MS);
    }

    /** Parameter timestamp native SQL: jam dinding UTC (kolom TIMESTAMP tanpa zona berisi UTC). */
    static String sqlTs(Instant instant) {
        return SQL_TS.format(instant);
    }

    /** Label bulan grafik — {@code toLocaleString('id-ID', { month: 'short', year: '2-digit' })}, mis. "Okt 26". */
    static String monthLabel(int year, int month) {
        return ID_MONTHS[month - 1] + " " + String.format("%02d", Math.floorMod(year, 100));
    }

    /** Kunci "YYYY-MM". */
    static String monthKey(int year, int month) {
        return String.format("%04d-%02d", year, month);
    }
}
