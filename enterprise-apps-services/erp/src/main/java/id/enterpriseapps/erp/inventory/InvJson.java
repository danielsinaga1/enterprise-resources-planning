package id.enterpriseapps.erp.inventory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Helper JSON & angka bergaya JavaScript untuk paket inventori.
 *
 * <p>Respons dibangun eksplisit sebagai {@code Map} agar bentuknya sama persis dengan
 * {@code include}/{@code select} Prisma: {@link #scalars(Object)} = semua kolom skalar model
 * (tanpa relasi), {@link #pick(Object, String...)} = {@code select: { a: true, b: true }}.
 * Relasi yang tidak di-include tidak pernah muncul (bahkan sebagai null).
 */
final class InvJson {

    private static final ConcurrentMap<Class<?>, List<Field>> COLUMNS = new ConcurrentHashMap<>();
    private static final Locale ID = Locale.forLanguageTag("id-ID");

    private InvJson() {
    }

    private static List<Field> columns(Class<?> type) {
        return COLUMNS.computeIfAbsent(type, t -> {
            List<Field> out = new ArrayList<>();
            for (Field f : t.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !f.isAnnotationPresent(Column.class)
                        || f.isAnnotationPresent(JsonIgnore.class)) {
                    continue;
                }
                f.setAccessible(true);
                out.add(f);
            }
            return List.copyOf(out);
        });
    }

    /** Semua kolom skalar entity (urutan deklarasi) — padanan baris Prisma tanpa include. Null → null. */
    static Map<String, Object> scalars(Object entity) {
        if (entity == null) {
            return null;
        }
        Object e = Hibernate.unproxy(entity);
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            for (Field f : columns(e.getClass())) {
                m.put(f.getName(), f.get(e));
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
        return m;
    }

    /** Subset kolom — padanan {@code select} Prisma pada relasi. Relasi null → null. */
    static Map<String, Object> pick(Object entity, String... fields) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> all = scalars(entity);
        Map<String, Object> m = new LinkedHashMap<>();
        for (String f : fields) {
            m.put(f, all.get(f));
        }
        return m;
    }

    // ───────────────────────────── angka ala JS ─────────────────────────────

    /** {@code String(number)} JS: tanpa nol di belakang, tanpa notasi eksponen. */
    static String js(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        if (v.signum() == 0) {
            return "0";
        }
        return v.stripTrailingZeros().toPlainString();
    }

    /** {@code n.toLocaleString('id-ID', { maximumFractionDigits })} (pembulatan half-expand). */
    static String idNum(BigDecimal v, int maxFractionDigits) {
        NumberFormat nf = NumberFormat.getNumberInstance(ID);
        nf.setMaximumFractionDigits(maxFractionDigits);
        nf.setMinimumFractionDigits(0);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        return nf.format(v == null ? BigDecimal.ZERO : v);
    }

    /** {@code Math.round(x * 10^scale) / 10^scale} — floor(x·10^s + 0,5), seperti JS. */
    static BigDecimal jsRound(BigDecimal v, int scale) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v;
        return x.movePointRight(scale).add(new BigDecimal("0.5")).setScale(0, RoundingMode.FLOOR)
                .movePointLeft(scale).setScale(Math.max(scale, 0), RoundingMode.UNNECESSARY);
    }

    /** {@code +x.toFixed(scale)}. */
    static BigDecimal toFixed(BigDecimal v, int scale) {
        return (v == null ? BigDecimal.ZERO : v).setScale(scale, RoundingMode.HALF_UP);
    }

    /** Rata-rata (skala 10); kosong → 0. */
    static BigDecimal avg(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), 10, RoundingMode.HALF_UP);
    }

    /** {@code Number(x)} JS: null/kosong → 0, tidak valid → NaN. */
    static double jsNumber(String raw) {
        if (raw == null) {
            return Double.NaN;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return 0;
        }
        if (!s.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")) {
            return Double.NaN;
        }
        return Double.parseDouble(s);
    }

    /** Angka JS ke nilai JSON: bulat → Long, NaN/Infinity → null (seperti JSON.stringify). */
    static Object jsonNumber(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return null;
        }
        if (v == Math.rint(v) && Math.abs(v) < 9.007199254740992E15) {
            return (long) v;
        }
        return v;
    }

    /** {@code Array.prototype.slice(start, end)} dengan argumen angka JS (NaN → 0, negatif dari belakang). */
    static <T> List<T> jsSlice(List<T> list, double start, double end) {
        int len = list.size();
        int from = sliceIndex(start, len);
        int to = sliceIndex(end, len);
        if (from >= to) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list.subList(from, to));
    }

    private static int sliceIndex(double v, int len) {
        if (Double.isNaN(v)) {
            return 0;
        }
        double t = v < 0 ? Math.ceil(v) : Math.floor(v);
        if (t < 0) {
            return (int) Math.max(len + t, 0);
        }
        return (int) Math.min(t, len);
    }

    /** {@code a.localeCompare(b, 'id-ID')}. */
    static Collator idCollator() {
        return Collator.getInstance(ID);
    }
}
