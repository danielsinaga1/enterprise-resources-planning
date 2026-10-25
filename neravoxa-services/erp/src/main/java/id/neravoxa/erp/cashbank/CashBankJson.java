package id.neravoxa.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpJson;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pembentuk JSON yang meniru bentuk {@code include}/{@code select} Prisma secara eksplisit.
 *
 * <p>Entity yang dibuat di transaksi yang sama menyimpan relasi bernilai {@code null} (bukan proxy),
 * sehingga serialisasi entity apa adanya akan menulis {@code "party": null} dsb. yang tidak ada di
 * respons ENTARIS. {@link #scalars(Object)} hanya mengambil kolom skalar; relasi ditambahkan
 * pemanggil sesuai {@code include} sumber.
 */
final class CashBankJson {

    private static final Map<Class<?>, List<String>> RELATIONS = new ConcurrentHashMap<>();

    private CashBankJson() {
    }

    /** Kolom skalar entity (padanan baris Prisma tanpa include). Null → null. */
    static ObjectNode scalars(Object entity) {
        if (entity == null) {
            return null;
        }
        Object real = Hibernate.unproxy(entity);
        ObjectNode o = ErpJson.obj(real);
        relationFields(real.getClass()).forEach(o::remove);
        return o;
    }

    /** Daftar kolom skalar untuk setiap entity. */
    static ArrayNode scalarsList(Collection<?> entities) {
        ArrayNode arr = ErpJson.arrayNode();
        if (entities != null) {
            entities.forEach(e -> arr.add(scalars(e)));
        }
        return arr;
    }

    /** Padanan {@code select: { a: true, b: true }} — hanya field tertentu, urut sesuai argumen. */
    static JsonNode pick(Object entity, String... fields) {
        if (entity == null) {
            return NullNode.getInstance();
        }
        ObjectNode all = scalars(entity);
        ObjectNode o = ErpJson.object();
        for (String f : fields) {
            JsonNode v = all.get(f);
            o.set(f, v == null ? NullNode.getInstance() : v);
        }
        return o;
    }

    private static List<String> relationFields(Class<?> type) {
        return RELATIONS.computeIfAbsent(type, t -> {
            List<String> names = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToMany.class)
                            || f.isAnnotationPresent(OneToOne.class) || f.isAnnotationPresent(ManyToMany.class)) {
                        names.add(f.getName());
                    }
                }
            }
            return List.copyOf(names);
        });
    }
}
