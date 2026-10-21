package id.enterpriseapps.erp.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpJson;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pembentuk JSON respons yang meniru {@code include}/{@code select} Prisma secara eksplisit.
 *
 * <p>{@link #scalars(Object)} hanya menulis kolom skalar entity (tanpa relasi). Relasi ditambahkan
 * pemanggil sesuai {@code include} sumber. Alasannya: relasi lazy yang targetnya kebetulan sudah
 * ada di persistence context ter-resolve menjadi entity terinisialisasi dan akan ikut tertulis oleh
 * serialisasi entity biasa — bentuk JSON lalu bergantung pada apa yang pernah dimuat transaksi.
 * Relasi {@code select} parsial dimuat lewat proyeksi ({@link #briefs}) sehingga tidak menambah
 * entity ke persistence context.
 */
final class SalesJson {

    private static final Map<Class<?>, List<Field>> COLUMNS = new ConcurrentHashMap<>();

    private SalesJson() {
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

    /** Seperti {@link #scalars(Object)} tetapi dijamin objek (entity tidak boleh null). */
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
     * Relasi {@code select} parsial untuk banyak id sekaligus. {@code jpql} memilih kolom sesuai
     * urutan {@code fields} (kolom pertama = id) dan memakai parameter {@code :ids}.
     * Hasil: id → objek {@code { field: nilai }} berurutan.
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

    /** Nilai relasi opsional: objek bila id terisi & ditemukan, selain itu null (perilaku include Prisma). */
    static JsonNode relation(Map<String, Map<String, Object>> briefs, String id) {
        return ErpJson.tree(id == null ? null : briefs.get(id));
    }

    /** {@code user: { select: { id, fullName } }} untuk sekumpulan id pengguna. */
    static Map<String, Map<String, Object>> users(EntityManager em, Collection<String> ids) {
        return briefs(em, "select u.id, u.fullName from User u where u.id in :ids", ids, "id", "fullName");
    }
}
