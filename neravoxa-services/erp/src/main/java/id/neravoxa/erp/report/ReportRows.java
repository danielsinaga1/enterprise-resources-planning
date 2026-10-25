package id.neravoxa.erp.report;

import id.neravoxa.erp.core.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Konversi baris native SQL / JPQL proyeksi + aritmetika persen laporan.
 *
 * <p>Timestamp di native SQL dipilih sebagai epoch milidetik ({@link #epochMs(String)}) agar bebas
 * dari zona waktu JVM/JDBC; parameter timestamp dikirim sebagai teks UTC ({@link ReportDates#sqlTs}).
 */
final class ReportRows {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ReportRows() {
    }

    /** Ekspresi SQL epoch milidetik untuk kolom TIMESTAMP (berisi jam UTC). */
    static String epochMs(String column) {
        return "CAST(ROUND(EXTRACT(EPOCH FROM " + column + ") * 1000) AS bigint)";
    }

    /** {@code Number(x)} untuk nilai numerik dari DB (null → 0). */
    static BigDecimal dec(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Double d) {
            return BigDecimal.valueOf(d);
        }
        if (v instanceof Float f) {
            return BigDecimal.valueOf(f.doubleValue());
        }
        return Money.of(v);
    }

    static long lng(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    static Instant instantMs(Object v) {
        return v == null ? null : Instant.ofEpochMilli(((Number) v).longValue());
    }

    static String str(Object v) {
        return v == null ? null : v.toString();
    }

    /** Hasil native query sebagai daftar baris Object[] (kolom tunggal dibungkus). */
    @SuppressWarnings("unchecked")
    static List<Object[]> rows(Query q) {
        List<Object> raw = q.getResultList();
        List<Object[]> out = new ArrayList<>(raw.size());
        for (Object r : raw) {
            out.add(r instanceof Object[] arr ? arr : new Object[]{r});
        }
        return out;
    }

    /** Native query → daftar Map dengan nama kolom (padanan baris {@code $queryRaw}). */
    static List<Map<String, Object>> maps(EntityManager em, String sql, Map<String, Object> params, String... columns) {
        Query q = em.createNativeQuery(sql);
        if (params != null) {
            params.forEach(q::setParameter);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows(q)) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < columns.length; i++) {
                m.put(columns[i], i < r.length ? r[i] : null);
            }
            out.add(m);
        }
        return out;
    }

    /** {@code +((a / b) * 100).toFixed(scale)} — 0 bila b ≤ 0 tidak ditangani di sini (pemanggil). */
    static BigDecimal pct(BigDecimal part, BigDecimal whole, int scale) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return Money.nz(part).multiply(HUNDRED).divide(whole, scale, RoundingMode.HALF_UP);
    }

    /** {@code +(a / b).toFixed(scale)}. */
    static BigDecimal ratio(BigDecimal a, BigDecimal b, int scale) {
        if (b == null || b.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return Money.nz(a).divide(b, scale, RoundingMode.HALF_UP);
    }

    /** Pembagian tanpa pembulatan tampilan (JS Number) — skala 10. */
    static BigDecimal div(BigDecimal a, BigDecimal b) {
        return Money.div(a, b);
    }
}
