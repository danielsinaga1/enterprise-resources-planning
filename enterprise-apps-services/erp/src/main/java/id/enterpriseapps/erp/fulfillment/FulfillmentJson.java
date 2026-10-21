package id.enterpriseapps.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpJson;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pembentuk JSON respons yang meniru {@code include}/{@code select} Prisma secara eksplisit, plus
 * format angka gaya JavaScript untuk pesan error.
 *
 * <p>{@link #scalars(Object)} hanya menulis kolom skalar entity; relasi ditambahkan pemanggil sesuai
 * {@code include} sumber. Dengan begitu bentuk JSON tidak bergantung pada relasi lazy yang kebetulan
 * sudah termuat di persistence context. Relasi {@code select} parsial dimuat lewat proyeksi
 * ({@link #briefs}).
 */
final class FulfillmentJson {

    private static final Map<Class<?>, List<Field>> COLUMNS = new ConcurrentHashMap<>();

    private FulfillmentJson() {
    }

    /** Semua kolom skalar entity (urutan deklarasi = urutan model Prisma); null → NullNode. */
    static JsonNode scalars(Object entity) {
        if (entity == null) {
            return ErpJson.tree(null);
        }
        Object target = Hibernate.unproxy(entity);
        ObjectNode node = ErpJson.object();
        for (Field f : columns(target.getClass())) {
            try {
                node.set(f.getName(), ErpJson.tree(f.get(target)));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Gagal membaca field " + f.getName(), e);
            }
        }
        return node;
    }

    static ObjectNode obj(Object entity) {
        return (ObjectNode) scalars(Objects.requireNonNull(entity));
    }

    static ArrayNode array(Collection<?> entities) {
        ArrayNode arr = ErpJson.arrayNode();
        if (entities != null) {
            entities.forEach(e -> arr.add(scalars(e)));
        }
        return arr;
    }

    private static List<Field> columns(Class<?> type) {
        return COLUMNS.computeIfAbsent(type, t -> {
            List<Field> out = new ArrayList<>();
            for (Field f : t.getDeclaredFields()) {
                if (f.isAnnotationPresent(Column.class) && !Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    out.add(f);
                }
            }
            return out;
        });
    }

    /**
     * Relasi {@code select} parsial untuk banyak id sekaligus. {@code jpql} memilih kolom sesuai urutan
     * {@code fields} (kolom pertama = kunci) dan memakai parameter {@code :ids}.
     */
    static Map<String, Map<String, Object>> briefs(EntityManager em, String jpql, Collection<String> ids,
                                                   String... fields) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        List<String> wanted = ids == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(Objects::nonNull).toList()));
        if (wanted.isEmpty()) {
            return out;
        }
        List<Object[]> rows = em.createQuery(jpql, Object[].class).setParameter("ids", wanted).getResultList();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < fields.length; i++) {
                m.put(fields[i], r[i]);
            }
            out.put(String.valueOf(r[0]), m);
        }
        return out;
    }

    /** Seperti {@link #briefs} tetapi kunci hasil = kolom ke-0 dan field yang ditulis mulai kolom ke-1. */
    static Map<String, Map<String, Object>> keyedBriefs(EntityManager em, String jpql, Collection<String> ids,
                                                        String... fields) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        List<String> wanted = ids == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(Objects::nonNull).toList()));
        if (wanted.isEmpty()) {
            return out;
        }
        List<Object[]> rows = em.createQuery(jpql, Object[].class).setParameter("ids", wanted).getResultList();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < fields.length; i++) {
                m.put(fields[i], r[i + 1]);
            }
            out.put(String.valueOf(r[0]), m);
        }
        return out;
    }

    /** Relasi opsional: objek bila id terisi & ditemukan, selain itu null (perilaku include Prisma). */
    static JsonNode relation(Map<String, Map<String, Object>> briefs, String id) {
        return ErpJson.tree(id == null ? null : briefs.get(id));
    }

    /** {@code user: { select: { id, fullName } }}. */
    static Map<String, Map<String, Object>> users(EntityManager em, Collection<String> ids) {
        return briefs(em, "select u.id, u.fullName from User u where u.id in :ids", ids, "id", "fullName");
    }

    /** {@code customer: { select: { id, name } }}. */
    static Map<String, Map<String, Object>> parties(EntityManager em, Collection<String> ids) {
        return briefs(em, "select p.id, p.name from Party p where p.id in :ids", ids, "id", "name");
    }

    /** Jumlah baris anak per induk ({@code _count}); {@code jpql} = select parentId, count(x) ... group by. */
    static Map<String, Long> counts(EntityManager em, String jpql, Collection<String> ids) {
        Map<String, Long> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (Object[] r : em.createQuery(jpql, Object[].class).setParameter("ids", ids).getResultList()) {
            out.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        }
        return out;
    }

    // ───────────────────────────── angka gaya JavaScript ─────────────────────────────

    /** {@code String(Number(x))}: tanpa nol di belakang, tanpa notasi eksponen. */
    static String js(BigDecimal v) {
        if (v == null) {
            return "null";
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.signum() == 0 ? "0" : s.toPlainString();
    }

    /** Angka JSON gaya JS (5 bukan 5.000) dari BigDecimal/Number apa pun; null tetap null. */
    static BigDecimal num(Object v) {
        if (v == null) {
            return null;
        }
        BigDecimal b = v instanceof BigDecimal bd ? bd : new BigDecimal(v.toString());
        return new BigDecimal(js(b));
    }

    /** {@code x.toLocaleString('id-ID', { maximumFractionDigits: 3 })}. */
    static String idNum(BigDecimal v) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"));
        nf.setMaximumFractionDigits(3);
        nf.setRoundingMode(RoundingMode.HALF_UP);
        return nf.format(v == null ? BigDecimal.ZERO : v);
    }
}
