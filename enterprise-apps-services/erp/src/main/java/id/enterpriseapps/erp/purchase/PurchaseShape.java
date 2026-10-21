package id.enterpriseapps.erp.purchase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpJson;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bentuk JSON entity yang meniru {@code include}/{@code select} Prisma secara eksplisit.
 *
 * <p>Mengapa tidak cukup serialisasi entity apa adanya: relasi {@code @ManyToOne} lazy yang
 * targetnya sudah ada di persistence context di-resolve Hibernate ke instance aslinya (bukan proxy),
 * sehingga ikut tertulis (mis. {@code items[].purchaseInvoice}) dan bisa membentuk siklus.
 * Helper ini hanya menulis kolom skalar + relasi yang diminta, tidak bergantung pada apa yang
 * kebetulan sudah dimuat. Relasi yang diminta tetapi belum dimuat akan di-load lazy, jadi panggil
 * di dalam transaksi.
 */
final class PurchaseShape {

    private enum Kind { SCALAR, TO_ONE, TO_MANY, INVERSE_ONE }

    private record Meta(Field field, Kind kind) {
    }

    private static final Map<Class<?>, Map<String, Meta>> META = new ConcurrentHashMap<>();

    /** null = semua kolom skalar (include); selain itu hanya kolom ini (select). */
    private final Set<String> fields;
    private final Map<String, PurchaseShape> relations = new LinkedHashMap<>();

    private PurchaseShape(Set<String> fields) {
        this.fields = fields;
    }

    /** Semua kolom skalar — padanan baris Prisma tanpa include / {@code relasi: true}. */
    static PurchaseShape scalars() {
        return new PurchaseShape(null);
    }

    /** Hanya kolom tertentu — padanan {@code select: { a: true, b: true }}. */
    static PurchaseShape select(String... names) {
        return new PurchaseShape(new LinkedHashSet<>(Arrays.asList(names)));
    }

    /** Sertakan relasi dengan semua kolom skalarnya. */
    PurchaseShape with(String relation) {
        return with(relation, scalars());
    }

    PurchaseShape with(String relation, PurchaseShape nested) {
        relations.put(relation, nested);
        return this;
    }

    ObjectNode of(Object entity) {
        Object real = Hibernate.unproxy(entity);
        Map<String, Meta> metas = metas(real.getClass());
        validate(metas, real.getClass());
        ObjectNode out = ErpJson.object();
        for (Map.Entry<String, Meta> e : metas.entrySet()) {
            String name = e.getKey();
            Meta m = e.getValue();
            if (m.kind() == Kind.SCALAR) {
                if (fields == null || fields.contains(name)) {
                    out.set(name, ErpJson.tree(read(m.field(), real)));
                }
                continue;
            }
            PurchaseShape nested = relations.get(name);
            if (nested == null) {
                continue;
            }
            Object value = read(m.field(), real);
            switch (m.kind()) {
                case TO_ONE -> out.set(name, value == null ? NullNode.getInstance() : nested.of(value));
                case TO_MANY -> out.set(name, nested.list((Collection<?>) value));
                case INVERSE_ONE -> {
                    Collection<?> c = (Collection<?>) value;
                    Object first = c == null || c.isEmpty() ? null : c.iterator().next();
                    out.set(name, first == null ? NullNode.getInstance() : nested.of(first));
                }
                default -> {
                    // SCALAR sudah ditangani di atas.
                }
            }
        }
        return out;
    }

    ArrayNode list(Collection<?> entities) {
        ArrayNode arr = ErpJson.arrayNode();
        if (entities != null) {
            for (Object e : entities) {
                arr.add(e == null ? NullNode.getInstance() : of(e));
            }
        }
        return arr;
    }

    private void validate(Map<String, Meta> metas, Class<?> cls) {
        for (String rel : relations.keySet()) {
            Meta m = metas.get(rel);
            if (m == null || m.kind() == Kind.SCALAR) {
                throw new IllegalArgumentException("Relasi '" + rel + "' tidak ada di " + cls.getSimpleName());
            }
        }
        if (fields != null) {
            for (String f : fields) {
                Meta m = metas.get(f);
                if (m == null || m.kind() != Kind.SCALAR) {
                    throw new IllegalArgumentException("Kolom '" + f + "' tidak ada di " + cls.getSimpleName());
                }
            }
        }
    }

    private static Object read(Field f, Object target) {
        try {
            return f.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Metadata field per kelas entity, urut deklarasi (= urutan field model Prisma). */
    private static Map<String, Meta> metas(Class<?> cls) {
        return META.computeIfAbsent(cls, c -> {
            Map<String, Meta> out = new LinkedHashMap<>();
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) {
                    continue;
                }
                boolean toOne = f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToOne.class);
                boolean toMany = f.isAnnotationPresent(OneToMany.class) || f.isAnnotationPresent(ManyToMany.class);
                boolean ignored = f.isAnnotationPresent(JsonIgnore.class);
                f.setAccessible(true);
                if (toMany && ignored && f.getName().endsWith("Ref")) {
                    // Sisi balik relasi 1:1 (List<X> xRef) — JSON memakai nama tanpa "Ref".
                    String json = f.getName().substring(0, f.getName().length() - 3);
                    out.put(json, new Meta(f, Kind.INVERSE_ONE));
                } else if (toOne) {
                    out.put(f.getName(), new Meta(f, Kind.TO_ONE));
                } else if (toMany) {
                    out.put(f.getName(), new Meta(f, Kind.TO_MANY));
                } else if (!ignored) {
                    out.put(f.getName(), new Meta(f, Kind.SCALAR));
                }
            }
            return out;
        });
    }
}
