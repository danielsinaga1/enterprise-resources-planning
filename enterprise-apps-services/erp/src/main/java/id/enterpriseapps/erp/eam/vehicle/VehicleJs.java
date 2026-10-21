package id.enterpriseapps.erp.eam.vehicle;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Semantik angka/tanggal JavaScript untuk laporan armada. Agregat laporan di ENTARIS dihitung dengan
 * {@code Number(decimal)} (double) dan tanggal "lokal" server (TZ UTC), jadi port ini memakai double
 * dan UTC agar hasil serta bucket bulan identik.
 */
final class VehicleJs {

    private static final DateTimeFormatter JS_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final double MAX_SAFE = 9007199254740991d;

    private VehicleJs() {
    }

    /** {@code Number(v ?? 0)}. */
    static double dec(BigDecimal v) {
        return v == null ? 0d : v.doubleValue();
    }

    /** {@code Number(v ?? 0)} untuk Integer. */
    static double dec(Integer v) {
        return v == null ? 0d : v.doubleValue();
    }

    /**
     * Angka JSON seperti {@code JSON.stringify}: bilangan bulat ditulis tanpa ".0", NaN/Infinity → null,
     * pecahan ditulis representasi terpendek (tanpa notasi eksponen).
     */
    static Object num(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return null;
        }
        if (d == Math.rint(d) && Math.abs(d) <= MAX_SAFE) {
            return (long) d;
        }
        BigDecimal b = BigDecimal.valueOf(d);
        return b.scale() < 0 ? b.setScale(0) : b;
    }

    /** {@code date.toISOString()}. */
    static String iso(Instant d) {
        return d == null ? null : JS_ISO.format(d);
    }

    static ZonedDateTime utc(Instant d) {
        return d.atZone(ZoneOffset.UTC);
    }

    /** {@code YYYY-MM} (UTC). */
    static String monthKey(Instant d) {
        ZonedDateTime z = utc(d);
        return z.getYear() + "-" + String.format("%02d", z.getMonthValue());
    }

    /** {@code Math.floor((d - now) / hari)}. */
    static long daysUntil(Instant d, Instant now) {
        return Math.floorDiv(d.toEpochMilli() - now.toEpochMilli(), 24L * 3600 * 1000);
    }
}
