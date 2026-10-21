package id.enterpriseapps.erp.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsing parameter daftar berpaginasi — padanan {@code utils/listQuery.ts}.
 *
 * <p>Kontrak semua endpoint daftar:
 * {@code GET /api/erp/<resource>?page=1&limit=25&search=…&sort=-date&<filter>}
 * → {@code { data: T[], total, page, limit }} (lihat {@link #result}).
 * Controller menerima {@code @RequestParam Map<String, String> query} (padanan {@code req.query}).
 */
public final class ListQuery {

    private ListQuery() {
    }

    public record Paging(int page, int limit, int skip) {
    }

    public record Sort(String field, boolean desc) {
        public String dir() {
            return desc ? "desc" : "asc";
        }
    }

    /** Nilai string ter-trim, null bila absen/kosong (padanan {@code firstString}). */
    public static String str(Map<String, String> query, String key) {
        if (query == null) {
            return null;
        }
        return firstString(query.get(key));
    }

    public static String firstString(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static Integer positiveInt(String raw) {
        String s = firstString(raw);
        if (s == null) {
            return null;
        }
        try {
            double d = Double.parseDouble(s);
            if (d == Math.rint(d) && d > 0 && d <= Integer.MAX_VALUE) {
                return (int) d;
            }
        } catch (NumberFormatException ignored) {
            // jatuh ke null
        }
        return null;
    }

    public static Paging paging(Map<String, String> query, int defaultLimit, int maxLimit) {
        Integer p = positiveInt(query == null ? null : query.get("page"));
        Integer l = positiveInt(query == null ? null : query.get("limit"));
        int page = p == null ? 1 : p;
        int limit = Math.min(l == null ? defaultLimit : l, maxLimit);
        return new Paging(page, limit, (page - 1) * limit);
    }

    /** {@code sort=-date} → (date, desc); field di luar whitelist → fallback. */
    public static Sort sort(String raw, Collection<String> allowed, Sort fallback) {
        String s = firstString(raw);
        if (s == null) {
            return fallback;
        }
        boolean desc = s.startsWith("-");
        String field = desc ? s.substring(1) : s;
        return allowed.contains(field) ? new Sort(field, desc) : fallback;
    }

    /** Body daftar standar {@code { data, total, page, limit }}. */
    public static Map<String, Object> result(List<?> data, long total, Paging paging) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data);
        m.put("total", total);
        m.put("page", paging.page());
        m.put("limit", paging.limit());
        return m;
    }

    /**
     * Parameter tanggal {@code YYYY-MM-DD}: null bila absen; melempar 400 dengan pesan
     * {@code message} bila formatnya salah (padanan {@code parseDateParam} === null).
     */
    public static String dateParam(Map<String, String> query, String key, String message) {
        String s = str(query, key);
        if (s == null) {
            return null;
        }
        if (!ErpDates.isIsoDate(s)) {
            throw ErpException.business(message);
        }
        return s;
    }

    /** {@code ?flag=true} → true; selain itu false. */
    public static boolean bool(Map<String, String> query, String key) {
        String s = str(query, key);
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    /** Daftar nilai dipisah koma ({@code ?status=Draft,Submitted}). Kosong bila absen. */
    public static List<String> csv(Map<String, String> query, String key) {
        String s = str(query, key);
        if (s == null) {
            return List.of();
        }
        return java.util.Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }
}
