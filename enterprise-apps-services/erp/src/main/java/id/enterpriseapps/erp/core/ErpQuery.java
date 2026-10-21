package id.enterpriseapps.erp.core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pembangun JPQL kecil untuk menerjemahkan {@code prisma.x.findMany({ where, include, orderBy, skip, take })}
 * + {@code count({ where })} dengan benar.
 *
 * <pre>{@code
 * ErpQuery<SalesInvoice> q = ErpQuery.of(em, SalesInvoice.class, "i")
 *     .join("left join i.customer c")                      // dipakai filter (count & data)
 *     .fetch("left join fetch i.customer", "left join fetch i.items")   // = include (data saja)
 *     .where("i.status <> :cancelled", "cancelled", InvoiceStatus.Cancelled)
 *     .whereIf(search != null, "(lower(i.invoiceNumber) like :s or lower(c.name) like :s)", "s", ErpQuery.contains(search))
 *     .orderBy("i.date desc, i.id desc");
 * long total = q.count();
 * List<SalesInvoice> rows = q.list(paging.skip(), paging.limit());
 * }</pre>
 *
 * <p>Paginasi + JOIN FETCH koleksi dilakukan dua langkah (ambil id halaman dulu, lalu muat entitasnya
 * dengan fetch) sehingga Hibernate tidak memotong hasil di memori (HHH90003004) dan
 * {@code total} tetap benar. Alias root wajib dipakai di where/orderBy.
 */
public final class ErpQuery<T> {

    private final EntityManager em;
    private final Class<T> type;
    private final String alias;
    private final List<String> joins = new ArrayList<>();
    private final List<String> fetches = new ArrayList<>();
    private final List<String> wheres = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();
    private String orderBy;

    private ErpQuery(EntityManager em, Class<T> type, String alias) {
        this.em = em;
        this.type = type;
        this.alias = alias;
    }

    public static <T> ErpQuery<T> of(EntityManager em, Class<T> type, String alias) {
        return new ErpQuery<>(em, type, alias);
    }

    /** Join untuk filter/sort (dipakai di query count & data), mis. {@code "left join i.customer c"}. */
    public ErpQuery<T> join(String... joinClauses) {
        joins.addAll(List.of(joinClauses));
        return this;
    }

    /** Fetch join untuk memuat relasi (padanan include), hanya di query data. */
    public ErpQuery<T> fetch(String... fetchClauses) {
        fetches.addAll(List.of(fetchClauses));
        return this;
    }

    /** Kondisi AND; pasangan (nama, nilai) parameter mengikuti. */
    public ErpQuery<T> where(String condition, Object... nameValues) {
        wheres.add("(" + condition + ")");
        for (int i = 0; i + 1 < nameValues.length; i += 2) {
            params.put(String.valueOf(nameValues[i]), nameValues[i + 1]);
        }
        return this;
    }

    public ErpQuery<T> whereIf(boolean apply, String condition, Object... nameValues) {
        return apply ? where(condition, nameValues) : this;
    }

    /** {@code orderBy} JPQL tanpa kata "order by", mis. {@code "i.date desc, i.id desc"}. */
    public ErpQuery<T> orderBy(String orderBy) {
        this.orderBy = orderBy;
        return this;
    }

    private String fromWhere(boolean withJoins) {
        StringBuilder sb = new StringBuilder(" from ").append(type.getSimpleName()).append(' ').append(alias);
        if (withJoins) {
            joins.forEach(j -> sb.append(' ').append(j));
        }
        if (!wheres.isEmpty()) {
            sb.append(" where ").append(String.join(" and ", wheres));
        }
        return sb.toString();
    }

    private void bind(jakarta.persistence.Query q) {
        params.forEach(q::setParameter);
    }

    /** Jumlah baris (distinct root). */
    public long count() {
        TypedQuery<Long> q = em.createQuery("select count(distinct " + alias + ")" + fromWhere(true), Long.class);
        bind(q);
        return q.getSingleResult();
    }

    /** Semua baris (tanpa paginasi) dengan fetch. */
    public List<T> list() {
        // Hibernate 6 men-dedup root entity hasil fetch join koleksi secara otomatis; "distinct" di SQL
        // dihindari karena PostgreSQL menolak ORDER BY kolom join yang tidak ada di SELECT DISTINCT.
        String jpql = "select " + alias + " from " + type.getSimpleName() + ' ' + alias
                + joinsAndFetches() + (wheres.isEmpty() ? "" : " where " + String.join(" and ", wheres))
                + (orderBy == null ? "" : " order by " + orderBy);
        TypedQuery<T> q = em.createQuery(jpql, type);
        bind(q);
        return q.getResultList();
    }

    /** Satu halaman. Dua langkah bila ada fetch (id halaman → entitas ber-fetch, urutan dipertahankan). */
    public List<T> list(int skip, int limit) {
        if (fetches.isEmpty()) {
            String jpql = "select " + alias + fromWhere(true) + (orderBy == null ? "" : " order by " + orderBy);
            TypedQuery<T> q = em.createQuery(jpql, type);
            bind(q);
            return q.setFirstResult(skip).setMaxResults(limit).getResultList();
        }
        List<Object> ids = pageIds(skip, limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        String jpql = "select " + alias + " from " + type.getSimpleName() + ' ' + alias
                + joinsAndFetches() + " where " + alias + ".id in :__ids";
        TypedQuery<T> q = em.createQuery(jpql, type);
        // Hanya parameter yang dipakai klausa join/fetch (biasanya tidak ada) — where tidak diulang.
        q.setParameter("__ids", ids);
        List<T> rows = q.getResultList();
        Map<Object, T> byId = new LinkedHashMap<>();
        for (T r : rows) {
            byId.put(idOf(r), r);
        }
        List<T> ordered = new ArrayList<>(ids.size());
        for (Object id : ids) {
            T r = byId.get(id);
            if (r != null) {
                ordered.add(r);
            }
        }
        return ordered;
    }

    /**
     * id satu halaman sesuai filter & urutan. Syarat: {@link #join} hanya relasi to-one (filter atas
     * relasi to-many pakai subquery {@code exists (...)}) agar tidak ada baris ganda.
     */
    public List<Object> pageIds(int skip, int limit) {
        String jpql = "select " + alias + ".id" + fromWhere(true) + (orderBy == null ? "" : " order by " + orderBy);
        jakarta.persistence.Query q = em.createQuery(jpql);
        bind(q);
        List<?> raw = q.setFirstResult(skip).setMaxResults(limit).getResultList();
        List<Object> ids = new ArrayList<>();
        java.util.Set<Object> seen = new java.util.HashSet<>();
        for (Object id : raw) {
            if (seen.add(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String joinsAndFetches() {
        StringBuilder sb = new StringBuilder();
        fetches.forEach(f -> sb.append(' ').append(f));
        // join filter yang tidak tercakup fetch tetap dibutuhkan bila where/order memakai aliasnya
        joins.forEach(j -> sb.append(' ').append(j));
        return sb.toString();
    }

    private Object idOf(T entity) {
        try {
            var m = entity.getClass().getMethod("getId");
            return m.invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ─────────────────────────── helper parameter ───────────────────────────

    /** Pola LIKE "mengandung", huruf kecil, dengan escape % dan _ (padanan contains + mode insensitive). */
    public static String contains(String term) {
        return "%" + escapeLike(term.trim().toLowerCase(Locale.ROOT)) + "%";
    }

    public static String startsWith(String term) {
        return escapeLike(term) + "%";
    }

    public static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Untuk klausa IN dengan koleksi kosong (JPQL menolak IN ()). */
    public static <V> Collection<V> nonEmpty(Collection<V> values, V placeholder) {
        return values == null || values.isEmpty() ? List.of(placeholder) : values;
    }
}
