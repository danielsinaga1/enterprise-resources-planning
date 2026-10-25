package id.neravoxa.erp.eam.ops;

import id.neravoxa.erp.core.ErpQuery;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kumpulan kondisi JPQL (AND) + parameternya — padanan objek {@code where} Prisma yang dipakai ulang
 * oleh daftar, hitungan KPI, dan subquery (mis. {@code parts where workOrder: filter}).
 */
final class GaWhere {

    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();

    GaWhere and(String condition, Object... nameValues) {
        conditions.add("(" + condition + ")");
        for (int i = 0; i + 1 < nameValues.length; i += 2) {
            params.put(String.valueOf(nameValues[i]), nameValues[i + 1]);
        }
        return this;
    }

    GaWhere andIf(boolean apply, String condition, Object... nameValues) {
        return apply ? and(condition, nameValues) : this;
    }

    /** Salinan dengan kondisi tambahan (filter dasar tidak berubah). */
    GaWhere plus(String condition, Object... nameValues) {
        GaWhere w = new GaWhere();
        w.conditions.addAll(conditions);
        w.params.putAll(params);
        return w.and(condition, nameValues);
    }

    /** {@code " where a and b"} atau string kosong. */
    String clause() {
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    /** Kondisi saja (tanpa kata "where"), {@code "1=1"} bila kosong. */
    String expr() {
        return conditions.isEmpty() ? "1=1" : String.join(" and ", conditions);
    }

    <Q extends Query> Q bind(Q q) {
        params.forEach(q::setParameter);
        return q;
    }

    <T> ErpQuery<T> applyTo(ErpQuery<T> q) {
        for (String c : conditions) {
            q.where(c.substring(1, c.length() - 1));
        }
        // Parameter diikat lewat kondisi pertama yang memakai nama tersebut (ErpQuery menyimpan per nama).
        if (!params.isEmpty() && !conditions.isEmpty()) {
            List<Object> kv = new ArrayList<>();
            params.forEach((k, v) -> {
                kv.add(k);
                kv.add(v);
            });
            q.where("1=1", kv.toArray());
        }
        return q;
    }
}
