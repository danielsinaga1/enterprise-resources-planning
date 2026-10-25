package id.neravoxa.erp.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.function.Function;

/**
 * Helper BigDecimal untuk port aritmetika ENTARIS (decimal.js / Number).
 *
 * <p>decimal.js memakai pembulatan default ROUND_HALF_UP, jadi {@link #round2(BigDecimal)}
 * = {@code new Decimal(x).toDecimalPlaces(2)}. Simpan nilai uang sebagai BigDecimal skala 2,
 * qty skala 3 (sesuai kolom NUMERIC di db_erp). Jangan pakai double untuk nilai yang ditulis ke DB.
 */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static final BigDecimal ONE = BigDecimal.ONE;
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Toleransi selisih sen yang dianggap seimbang (trigger DB juga 0.01). */
    public static final BigDecimal CENT = new BigDecimal("0.01");

    private Money() {
    }

    /** null → 0. */
    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** {@code Number(x)} untuk Number/String/BigDecimal; null/kosong → 0. */
    public static BigDecimal of(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Integer || v instanceof Long || v instanceof Short) {
            return BigDecimal.valueOf(((Number) v).longValue());
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(s);
    }

    /** Bulatkan 2 desimal HALF_UP. */
    public static BigDecimal round2(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal round(BigDecimal v, int scale) {
        return nz(v).setScale(scale, RoundingMode.HALF_UP);
    }

    /** Pembagian aman (skala 10, HALF_UP); pembagi 0 → 0. */
    public static BigDecimal div(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return nz(a).divide(b, 10, RoundingMode.HALF_UP);
    }

    public static BigDecimal div(BigDecimal a, BigDecimal b, int scale) {
        if (b == null || b.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return nz(a).divide(b, scale, RoundingMode.HALF_UP);
    }

    /** a × b / 100 (persen), dibulatkan 2 desimal. */
    public static BigDecimal pct(BigDecimal amount, BigDecimal percent) {
        return round2(nz(amount).multiply(nz(percent)).divide(HUNDRED, 10, RoundingMode.HALF_UP));
    }

    public static BigDecimal sum(Collection<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        if (values != null) {
            for (BigDecimal v : values) {
                total = total.add(nz(v));
            }
        }
        return total;
    }

    public static <T> BigDecimal sum(Collection<T> items, Function<T, BigDecimal> f) {
        BigDecimal total = BigDecimal.ZERO;
        if (items != null) {
            for (T t : items) {
                total = total.add(nz(f.apply(t)));
            }
        }
        return total;
    }

    public static boolean isZero(BigDecimal v) {
        return v == null || v.signum() == 0;
    }

    public static boolean isPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    public static boolean isNegative(BigDecimal v) {
        return v != null && v.signum() < 0;
    }

    /** |a - b| ≤ toleransi. */
    public static boolean near(BigDecimal a, BigDecimal b, BigDecimal tolerance) {
        return nz(a).subtract(nz(b)).abs().compareTo(tolerance) <= 0;
    }

    /** Sama sampai sen (toleransi 0.01). */
    public static boolean eqCents(BigDecimal a, BigDecimal b) {
        return near(a, b, CENT);
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) >= 0 ? nz(a) : nz(b);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) <= 0 ? nz(a) : nz(b);
    }

    /** Untuk JSON/log: {@code Number(x)} di JS. */
    public static double toDouble(BigDecimal v) {
        return nz(v).doubleValue();
    }

    /** Format Rupiah sederhana gaya {@code toLocaleString('id-ID')} untuk teks notifikasi/narasi. */
    public static String idr(BigDecimal v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("id-ID"));
        nf.setMaximumFractionDigits(2);
        return nf.format(nz(v));
    }
}
