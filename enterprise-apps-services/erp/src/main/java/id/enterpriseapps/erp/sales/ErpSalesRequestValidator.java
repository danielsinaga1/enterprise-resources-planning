package id.enterpriseapps.erp.sales;

import id.enterpriseapps.erp.core.ErpException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validasi body request penjualan dengan semantik {@code validateBody} ENTARIS:
 * anotasi Jakarta Validation pada DTO, tetapi diperiksa BERURUTAN mengikuti urutan field schema Zod
 * (urutan komponen record / deklarasi field) sehingga pesan yang dikembalikan selalu issue pertama —
 * {@code res.status(400).json({ error: issues[0].message })}. Set violation Hibernate Validator
 * tidak berurutan, jadi {@code @Valid} di controller bisa memilih pesan yang berbeda dari sumber.
 *
 * <p>Daftar elemen DTO divalidasi per elemen (padanan path {@code items[i].field}); refinement
 * tingkat objek ({@link Refinable}) dijalankan setelah semua field lolos, seperti {@code .refine()}.
 */
@Component
public class ErpSalesRequestValidator {

    /** Refinement objek ({@code .refine(...)} Zod): kembalikan pesan bila gagal, null bila lolos. */
    interface Refinable {
        String refineError();
    }

    private record Prop(String name, java.util.function.Function<Object, Object> reader) {
    }

    private final Validator validator;
    private final Map<Class<?>, List<Prop>> props = new ConcurrentHashMap<>();

    public ErpSalesRequestValidator(ObjectProvider<Validator> validators) {
        // Bean validator Spring bila tunggal/primary; selain itu validator default Hibernate Validator.
        this.validator = validators.getIfUnique(() -> Validation.buildDefaultValidatorFactory().getValidator());
    }

    /** 400 dengan pesan issue pertama bila body tidak valid. */
    public void check(Object body) {
        String msg = firstError(body);
        if (msg != null) {
            throw ErpException.business(msg);
        }
    }

    String firstError(Object bean) {
        if (bean == null) {
            return null;
        }
        for (Prop p : propsOf(bean.getClass())) {
            Set<ConstraintViolation<Object>> violations = validator.validateProperty(bean, p.name());
            if (!violations.isEmpty()) {
                // Lebih dari satu aturan gagal pada field yang sama jarang terjadi; pilih deterministik.
                return violations.stream().map(ConstraintViolation::getMessage)
                        .min(Comparator.naturalOrder()).orElse(null);
            }
            Object value = p.reader().apply(bean);
            if (value instanceof Collection<?> c) {
                for (Object el : c) {
                    if (el == null) {
                        return ZodText.EXPECTED_OBJECT_NULL;
                    }
                    if (isDto(el)) {
                        String m = firstError(el);
                        if (m != null) {
                            return m;
                        }
                    }
                }
            }
        }
        if (bean instanceof Refinable r) {
            return r.refineError();
        }
        return null;
    }

    private static boolean isDto(Object o) {
        return o.getClass().getPackageName().equals(ErpSalesRequestValidator.class.getPackageName());
    }

    private List<Prop> propsOf(Class<?> type) {
        return props.computeIfAbsent(type, t -> {
            List<Prop> out = new ArrayList<>();
            if (t.isRecord()) {
                for (RecordComponent rc : t.getRecordComponents()) {
                    var accessor = rc.getAccessor();
                    accessor.setAccessible(true);
                    out.add(new Prop(rc.getName(), o -> invoke(accessor, o)));
                }
            } else {
                for (Field f : t.getDeclaredFields()) {
                    int mod = f.getModifiers();
                    if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) {
                        continue;
                    }
                    f.setAccessible(true);
                    out.add(new Prop(f.getName(), o -> read(f, o)));
                }
            }
            return out;
        });
    }

    private static Object invoke(java.lang.reflect.Method m, Object target) {
        try {
            return m.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object read(Field f, Object target) {
        try {
            return f.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
