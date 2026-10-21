package id.enterpriseapps.erp.production;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Helper JSON & angka bergaya JavaScript untuk paket produksi.
 *
 * <p>Respons dibangun eksplisit sebagai {@code Map} agar bentuknya sama persis dengan
 * {@code include}/{@code select} Prisma: {@link #scalars(Object)} = semua kolom skalar model,
 * {@link #pick(Object, String...)} = {@code select: { a: true, b: true }}. Relasi yang tidak
 * di-include tidak pernah muncul.
 */
final class ProdJson {

    private static final ConcurrentMap<Class<?>, List<Field>> COLUMNS = new ConcurrentHashMap<>();
    private static final Locale ID = Locale.forLanguageTag("id-ID");

    private ProdJson() {
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

    /** Semua kolom skalar entity — padanan baris Prisma tanpa include. Null → null. */
    static Map<String, Object> scalars(Object entity) {
        return scalarsExcept(entity);
    }

    /** Kolom skalar kecuali yang disebut (mis. {@code payload} pada daftar draft). */
    static Map<String, Object> scalarsExcept(Object entity, String... excluded) {
        if (entity == null) {
            return null;
        }
        Object e = Hibernate.unproxy(entity);
        Set<String> skip = Set.of(excluded);
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            for (Field f : columns(e.getClass())) {
                if (!skip.contains(f.getName())) {
                    m.put(f.getName(), f.get(e));
                }
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

    /** Angka JSON tanpa nol di belakang & tanpa notasi eksponen (1000.000 → 1000). Null tetap null. */
    static BigDecimal plain(BigDecimal v) {
        if (v == null) {
            return null;
        }
        if (v.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0, RoundingMode.UNNECESSARY) : s;
    }

    /** {@code String(number)} JS: tanpa nol di belakang, tanpa notasi eksponen. */
    static String js(BigDecimal v) {
        BigDecimal p = plain(v == null ? BigDecimal.ZERO : v);
        return p.toPlainString();
    }

    /** {@code n.toLocaleString('id-ID', { maximumFractionDigits })} (pembulatan half-expand). */
    static String idNum(BigDecimal v, int maxFractionDigits) {
        NumberFormat nf = NumberFormat.getNumberInstance(ID);
        nf.setMaximumFractionDigits(maxFractionDigits);
        nf.setMinimumFractionDigits(0);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        return nf.format(v == null ? BigDecimal.ZERO : v);
    }

    /** {@code n.toLocaleString('id-ID')} — default JS: maksimum 3 digit pecahan. */
    static String idNum(BigDecimal v) {
        return idNum(v, 3);
    }

    /** {@code Math.round(x * 10^scale) / 10^scale} — floor(x·10^s + 0,5), seperti JS. */
    static BigDecimal jsRound(BigDecimal v, int scale) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v;
        return x.movePointRight(scale).add(new BigDecimal("0.5")).setScale(0, RoundingMode.FLOOR)
                .movePointLeft(scale).setScale(Math.max(scale, 0), RoundingMode.UNNECESSARY);
    }

    /** Angka JS ke nilai JSON: NaN/Infinity → null (seperti JSON.stringify), bulat → Long. */
    static Object jsonNumber(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return null;
        }
        if (v == Math.rint(v) && Math.abs(v) < 9.007199254740992E15) {
            return (long) v;
        }
        return v;
    }

    /** Truthiness JavaScript untuk nilai JSON (absen/null/false/0/"" = false). */
    static boolean truthy(com.fasterxml.jackson.databind.JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) {
            return false;
        }
        if (v.isBoolean()) {
            return v.booleanValue();
        }
        if (v.isNumber()) {
            return v.decimalValue().signum() != 0;
        }
        if (v.isTextual()) {
            return !v.asText().isEmpty();
        }
        return true;
    }

    /** Teks JS untuk nilai mentah body ({@code `${req.body?.code}`}): absen → "undefined". */
    static String jsText(com.fasterxml.jackson.databind.JsonNode raw, String field) {
        if (raw == null || !raw.has(field)) {
            return "undefined";
        }
        com.fasterxml.jackson.databind.JsonNode v = raw.get(field);
        if (v.isNull()) {
            return "null";
        }
        if (v.isNumber()) {
            return js(v.decimalValue());
        }
        return v.isTextual() ? v.asText() : v.toString();
    }
}
