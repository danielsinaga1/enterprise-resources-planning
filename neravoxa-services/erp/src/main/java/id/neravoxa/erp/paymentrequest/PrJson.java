package id.neravoxa.erp.paymentrequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.User;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bentuk JSON respons Pengajuan Pembayaran: baris entity tanpa relasi (padanan hasil Prisma tanpa
 * {@code include}) dan objek {@code select} parsial.
 */
final class PrJson {

    private static final Map<Class<?>, List<String>> RELATION_KEYS = new ConcurrentHashMap<>();

    private PrJson() {
    }

    /**
     * Semua kolom skalar entity — padanan {@code findUnique/update} Prisma tanpa include. Relasi yang kebetulan
     * sudah dimuat di persistence context dibuang agar bentuknya tidak bergantung pada urutan query.
     */
    static ObjectNode scalars(Object entity) {
        Object e = Hibernate.unproxy(entity);
        ObjectNode o = ErpJson.obj(e);
        for (String k : relationKeys(e.getClass())) {
            o.remove(k);
        }
        return o;
    }

    private static List<String> relationKeys(Class<?> type) {
        return RELATION_KEYS.computeIfAbsent(type, cls -> {
            List<String> keys = new ArrayList<>();
            for (Field f : cls.getDeclaredFields()) {
                if (f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToMany.class)
                        || f.isAnnotationPresent(OneToOne.class) || f.isAnnotationPresent(ManyToMany.class)) {
                    JsonProperty jp = f.getAnnotation(JsonProperty.class);
                    keys.add(jp != null && !jp.value().isEmpty() ? jp.value() : f.getName());
                    if (f.getName().endsWith("Ref")) {
                        // Sisi balik relasi 1:1 ditulis dengan nama tanpa "Ref".
                        keys.add(f.getName().substring(0, f.getName().length() - 3));
                    }
                }
            }
            return keys;
        });
    }

    /** Angka gaya JS {@code Decimal#toNumber()}: tanpa nol di belakang koma, tanpa notasi eksponen. */
    static BigDecimal num(BigDecimal v) {
        BigDecimal s = Money.nz(v).stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    /** {@code select: { id, fullName }}. */
    static Map<String, Object> userRef(User u) {
        if (u == null) {
            return null;
        }
        return ErpJson.map("id", u.getId(), "fullName", u.getFullName());
    }

    /** {@code select: { id, fullName, email }}. */
    static Map<String, Object> userRefWithEmail(User u) {
        if (u == null) {
            return null;
        }
        return ErpJson.map("id", u.getId(), "fullName", u.getFullName(), "email", u.getEmail());
    }

    /** {@code select: { id, accountNumber, name }}. */
    static Map<String, Object> accountRef(Account a) {
        if (a == null) {
            return null;
        }
        return ErpJson.map("id", a.getId(), "accountNumber", a.getAccountNumber(), "name", a.getName());
    }

    /** {@code select: { id, entryNumber, status }}. */
    static Map<String, Object> journalRef(JournalEntry je) {
        if (je == null) {
            return null;
        }
        return ErpJson.map("id", je.getId(), "entryNumber", je.getEntryNumber(), "status",
                je.getStatus() == null ? null : je.getStatus().name());
    }

    static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
