package id.enterpriseapps.erp.core;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Utilitas tanggal yang meniru semantik JavaScript {@code Date} di sumber ENTARIS.
 *
 * <ul>
 *   <li>{@code new Date("2026-10-19")} = tengah malam UTC → {@link #toInstant(String)}.</li>
 *   <li>Tanggal-waktu tanpa zona ({@code 2026-10-19T08:00}) ditafsirkan UTC, sama dengan proses
 *       Node di container (TZ default UTC).</li>
 *   <li>Filter "hari kalender" memakai zona bisnis (default WIB +07:00, env {@code APP_UTC_OFFSET})
 *       lewat {@link #localDayRange(String, String)} — padanan {@code utils/listQuery.ts}.</li>
 * </ul>
 */
public final class ErpDates {

    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern UTC_OFFSET = Pattern.compile("^[+-]\\d{2}:\\d{2}$");
    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private ErpDates() {
    }

    /** {@code new Date(s)}; null/blank → null; tak valid → ErpException 400 "Tanggal tidak valid.". */
    public static Instant toInstant(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        Instant v = tryInstant(s);
        if (v == null) {
            throw ErpException.business("Tanggal tidak valid.");
        }
        return v;
    }

    /** Seperti {@link #toInstant(String)} tetapi null bila tidak bisa diparse. */
    public static Instant tryInstant(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            if (ISO_DATE.matcher(s).matches()) {
                return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            if (s.endsWith("Z") || s.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                return OffsetDateTime.parse(normalizeOffset(s)).toInstant();
            }
            return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(s);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static String normalizeOffset(String s) {
        // "+0700" → "+07:00" (JS menerima keduanya; java.time hanya yang bertitik dua).
        if (s.matches(".*[+-]\\d{4}$")) {
            return s.substring(0, s.length() - 2) + ":" + s.substring(s.length() - 2);
        }
        return s;
    }

    /** True bila string berformat {@code YYYY-MM-DD} dan tanggalnya valid. */
    public static boolean isIsoDate(String s) {
        if (s == null || !ISO_DATE.matcher(s.trim()).matches()) {
            return false;
        }
        try {
            LocalDate.parse(s.trim());
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /** Zona bisnis sebagai offset tetap (default +07:00 / WIB). */
    public static ZoneOffset businessOffset() {
        String raw = System.getenv("APP_UTC_OFFSET");
        if (raw != null && UTC_OFFSET.matcher(raw.trim()).matches()) {
            return ZoneOffset.of(raw.trim());
        }
        return ZoneOffset.ofHours(7);
    }

    public static ZoneId businessZone() {
        return businessOffset();
    }

    /** Hari ini menurut zona bisnis. */
    public static LocalDate businessToday() {
        return LocalDate.now(businessZone());
    }

    /** Tanggal kalender (zona bisnis) dari sebuah instant. */
    public static LocalDate businessDate(Instant instant) {
        return instant == null ? null : instant.atZone(businessZone()).toLocalDate();
    }

    /** Awal hari (zona bisnis) untuk tanggal {@code YYYY-MM-DD}. */
    public static Instant businessStartOfDay(String isoDate) {
        return LocalDate.parse(isoDate).atStartOfDay(businessZone()).toInstant();
    }

    /**
     * Rentang setengah terbuka [start 00:00, hari-setelah-end 00:00) di zona bisnis untuk
     * filter tanggal dokumen. Elemen null bila parameternya kosong.
     */
    public static Instant[] localDayRange(String startDate, String endDate) {
        Instant gte = startDate == null || startDate.isBlank() ? null : businessStartOfDay(startDate);
        Instant lt = endDate == null || endDate.isBlank() ? null
                : LocalDate.parse(endDate).plusDays(1).atStartOfDay(businessZone()).toInstant();
        return new Instant[]{gte, lt};
    }

    /** Tengah malam UTC untuk tanggal (padanan {@code new Date('YYYY-MM-DD')}). */
    public static Instant utcStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** Akhir hari UTC (23:59:59.999) — padanan {@code d.setHours(23,59,59,999)} pada server ber-TZ UTC. */
    public static Instant utcEndOfDay(String isoDate) {
        return LocalDate.parse(isoDate).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
    }

    /** Bagian tanggal ISO (UTC) — padanan {@code d.toISOString().slice(0, 10)}. */
    public static String isoDay(Instant instant) {
        return instant == null ? null : ISO_DAY.format(instant.atZone(ZoneOffset.UTC));
    }

    /** "Segera jatuh tempo": hari ini s.d. sekian hari ke depan (uang jaminan, pajak kendaraan, dst.). */
    public static final int DUE_SOON_DAYS = 30;

    /**
     * Batas jatuh tempo — port {@code dueBoundaries()} (projectGuaranteeList.ts):
     * [0] = awal hari ini (zona bisnis; sebelum ini = terlambat),
     * [1] = akhir jendela "segera" (eksklusif) = awal hari ini + (DUE_SOON_DAYS + 1) hari.
     */
    public static Instant[] dueBoundaries(Instant now) {
        LocalDate today = now.atZone(businessZone()).toLocalDate();
        Instant todayStart = today.atStartOfDay(businessZone()).toInstant();
        return new Instant[]{todayStart, todayStart.plus(java.time.Duration.ofDays(DUE_SOON_DAYS + 1L))};
    }

    /** "overdue" / "dueSoon" / null untuk tanggal jatuh tempo terhadap {@link #dueBoundaries(Instant)}. */
    public static String dueState(Instant dueDate, Instant[] bounds) {
        if (dueDate == null) {
            return null;
        }
        if (dueDate.isBefore(bounds[0])) {
            return "overdue";
        }
        if (dueDate.isBefore(bounds[1])) {
            return "dueSoon";
        }
        return null;
    }

    /** Tahun & bulan (UTC) untuk penomoran dokumen — padanan getFullYear()/getMonth() di TZ UTC. */
    public static int utcYear(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).getYear();
    }

    public static int utcMonth(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).getMonthValue();
    }
}
