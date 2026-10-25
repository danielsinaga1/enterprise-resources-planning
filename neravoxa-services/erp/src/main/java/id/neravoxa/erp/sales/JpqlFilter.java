package id.neravoxa.erp.sales;

import id.neravoxa.erp.core.ErpQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * Kumpulan kondisi {@code where} (+ join to-one untuk filter) yang dipakai bersama oleh endpoint
 * daftar dan ringkasan — padanan objek {@code where} Prisma yang dibangun sekali lalu dipakai untuk
 * {@code findMany}, {@code count}, {@code groupBy} dan {@code aggregate}, sehingga kartu KPI selalu
 * menggambarkan baris yang sama dengan daftar.
 */
final class JpqlFilter {

    private record Cond(String jpql, Object[] params) {
    }

    private final String entity;
    private final String alias;
    private final List<String> joins = new ArrayList<>();
    private final List<Cond> conds = new ArrayList<>();

    JpqlFilter(String entity, String alias) {
        this.entity = entity;
        this.alias = alias;
    }

    /** Join to-one untuk filter/sort (mis. {@code left join i.customer c}). */
    JpqlFilter join(String clause) {
        if (!joins.contains(clause)) {
            joins.add(clause);
        }
        return this;
    }

    /** Kondisi AND dengan pasangan (nama, nilai) parameter. */
    JpqlFilter and(String condition, Object... nameValues) {
        conds.add(new Cond(condition, nameValues));
        return this;
    }

    /** Pasang join + kondisi ke {@link ErpQuery}. */
    <T> ErpQuery<T> applyTo(ErpQuery<T> q) {
        joins.forEach(q::join);
        for (Cond c : conds) {
            q.where(c.jpql(), c.params());
        }
        return q;
    }

    /**
     * Query agregat {@code select <select> from <Entity> <alias> <joins> where <kondisi + extra> <tail>}.
     * {@code extra} = kondisi tambahan (mis. {@code AND: [where, overdueWhere]}) dengan parameternya.
     */
    <R> TypedQuery<R> aggregate(EntityManager em, String select, String tail, Class<R> type,
                                String extra, Object... extraParams) {
        StringBuilder sb = new StringBuilder("select ").append(select)
                .append(" from ").append(entity).append(' ').append(alias);
        joins.forEach(j -> sb.append(' ').append(j));
        List<String> all = new ArrayList<>();
        conds.forEach(c -> all.add("(" + c.jpql() + ")"));
        if (extra != null) {
            all.add("(" + extra + ")");
        }
        if (!all.isEmpty()) {
            sb.append(" where ").append(String.join(" and ", all));
        }
        if (tail != null) {
            sb.append(' ').append(tail);
        }
        TypedQuery<R> q = em.createQuery(sb.toString(), type);
        bind(q);
        for (int i = 0; i + 1 < extraParams.length; i += 2) {
            q.setParameter(String.valueOf(extraParams[i]), extraParams[i + 1]);
        }
        return q;
    }

    private void bind(Query q) {
        for (Cond c : conds) {
            for (int i = 0; i + 1 < c.params().length; i += 2) {
                q.setParameter(String.valueOf(c.params()[i]), c.params()[i + 1]);
            }
        }
    }
}
