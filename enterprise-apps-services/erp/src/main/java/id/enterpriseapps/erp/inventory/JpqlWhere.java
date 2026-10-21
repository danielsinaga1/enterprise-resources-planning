package id.enterpriseapps.erp.inventory;

import id.enterpriseapps.erp.core.ErpQuery;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kumpulan kondisi JPQL + parameter — padanan objek {@code where} Prisma yang dipakai bersama oleh
 * daftar, hitungan, dan agregat (KPI) sehingga semuanya menggambarkan baris yang sama.
 */
final class JpqlWhere {

    private final List<String> conds = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();

    JpqlWhere and(String condition, Object... nameValues) {
        conds.add("(" + condition + ")");
        for (int i = 0; i + 1 < nameValues.length; i += 2) {
            params.put(String.valueOf(nameValues[i]), nameValues[i + 1]);
        }
        return this;
    }

    boolean isEmpty() {
        return conds.isEmpty();
    }

    /** " where a and b" atau string kosong. */
    String clause() {
        return conds.isEmpty() ? "" : " where " + String.join(" and ", conds);
    }

    void bind(Query q) {
        params.forEach(q::setParameter);
    }

    <T> ErpQuery<T> applyTo(ErpQuery<T> q) {
        if (conds.isEmpty()) {
            return q;
        }
        Object[] nv = new Object[params.size() * 2];
        int i = 0;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            nv[i++] = e.getKey();
            nv[i++] = e.getValue();
        }
        return q.where(String.join(" and ", conds), nv);
    }

    /**
     * Pola LIKE {@code contains + mode: 'insensitive'} Prisma: huruf kecil, % dan _ di-escape,
     * TANPA trim (Prisma memakai nilai apa adanya).
     */
    static String contains(String term) {
        return "%" + ErpQuery.escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
    }
}
