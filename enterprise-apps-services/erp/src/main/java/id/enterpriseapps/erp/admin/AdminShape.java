package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpJson;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON baris entity tanpa relasi — padanan hasil Prisma tanpa {@code include}.
 *
 * <p>Serialisasi entity apa adanya tidak cukup: relasi {@code @ManyToOne} yang bernilai null (entity
 * baru) tetap tertulis sebagai {@code null}, dan relasi lazy yang targetnya kebetulan sudah ada di
 * persistence context ikut tertulis lengkap. Helper ini hanya menulis kolom skalar; relasi yang
 * di-{@code select} sumber ditambahkan pemanggil secara eksplisit.
 */
final class AdminShape {

    private static final Map<Class<?>, List<Field>> SCALARS = new ConcurrentHashMap<>();

    private AdminShape() {
    }

    static ObjectNode scalars(Object entity) {
        Object real = Hibernate.unproxy(entity);
        ObjectNode out = ErpJson.object();
        for (Field f : scalarFields(real.getClass())) {
            try {
                out.set(f.getName(), ErpJson.tree(f.get(real)));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Tidak bisa membaca field " + f.getName(), e);
            }
        }
        return out;
    }

    static List<ObjectNode> scalars(Collection<?> entities) {
        List<ObjectNode> out = new ArrayList<>(entities.size());
        for (Object e : entities) {
            out.add(scalars(e));
        }
        return out;
    }

    private static List<Field> scalarFields(Class<?> type) {
        return SCALARS.computeIfAbsent(type, t -> {
            List<Field> out = new ArrayList<>();
            for (Field f : t.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) || Modifier.isTransient(m)
                        || f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToMany.class)
                        || f.isAnnotationPresent(OneToOne.class) || f.isAnnotationPresent(ManyToMany.class)
                        || f.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                f.setAccessible(true);
                out.add(f);
            }
            return List.copyOf(out);
        });
    }
}
