package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pembantu kecil paket EAM aset: bentuk JSON gaya Prisma dan semantik JavaScript yang dipakai sumber.
 *
 * <p>{@link #scalars(Object)} membangun objek berisi kolom skalar entity saja (tanpa relasi) agar
 * respons sama persis dengan baris Prisma; relasi yang di-{@code include}/{@code select} ditambahkan
 * eksplisit oleh pemanggil. Ini juga mencegah back-reference yang kebetulan sudah ada di persistence
 * context ikut terserialisasi.
 */
final class GaJs {

    /** Pesan P2025 Prisma (update/delete baris yang tidak ada) dari {@code handleRouteError}. */
    static final String NOT_FOUND_P2025 = "Data terkait tidak ditemukan. Periksa referensi yang dipilih.";

    private static final Map<Class<?>, List<Field>> SCALAR_FIELDS = new ConcurrentHashMap<>();

    private GaJs() {
    }

    /** 400 gaya P2025 — dipakai saat {@code prisma.x.update/delete} menyasar id yang tidak ada. */
    static ErpException p2025() {
        return ErpException.business(NOT_FOUND_P2025);
    }

    private static List<Field> scalarFields(Class<?> type) {
        return SCALAR_FIELDS.computeIfAbsent(type, t -> {
            List<Field> out = new ArrayList<>();
            for (Field f : t.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isAnnotationPresent(Transient.class)
                        || f.isAnnotationPresent(ManyToOne.class) || f.isAnnotationPresent(OneToMany.class)
                        || f.isAnnotationPresent(OneToOne.class) || f.isAnnotationPresent(ManyToMany.class)) {
                    continue;
                }
                f.setAccessible(true);
                out.add(f);
            }
            return out;
        });
    }

    /** Kolom skalar entity (urutan deklarasi = urutan model Prisma); null → NullNode. */
    static ObjectNode scalars(Object entity) {
        if (entity == null) {
            return null;
        }
        Object real = Hibernate.unproxy(entity);
        Map<String, Object> m = new LinkedHashMap<>();
        for (Field f : scalarFields(real.getClass())) {
            try {
                m.put(f.getName(), f.get(real));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return (ObjectNode) ErpJson.tree(m);
    }

    /** {@code select: { a: true, b: true }} atas kolom skalar; null bila entity null. */
    static JsonNode pick(Object entity, String... fields) {
        if (entity == null) {
            return NullNode.getInstance();
        }
        ObjectNode all = scalars(entity);
        ObjectNode out = ErpJson.object();
        for (String f : fields) {
            JsonNode v = all.get(f);
            out.set(f, v == null ? NullNode.getInstance() : v);
        }
        return out;
    }

    /** {@code value ?? NullNode} untuk relasi opsional. */
    static JsonNode orNull(JsonNode v) {
        return v == null ? NullNode.getInstance() : v;
    }

    /** Truthy JavaScript untuk string query ({@code if (req.query.x)}). */
    static boolean truthy(String s) {
        return s != null && !s.isEmpty();
    }

    /**
     * {@code parseInt(s, 10)} JavaScript: spasi awal diabaikan, tanda opsional, ambil digit awal.
     * Null bila NaN.
     */
    static Integer parseInt(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.stripLeading();
        int i = 0;
        boolean neg = false;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            neg = s.charAt(i) == '-';
            i++;
        }
        int start = i;
        while (i < s.length() && Character.isDigit(s.charAt(i)) && s.charAt(i) < 128) {
            i++;
        }
        if (i == start) {
            return null;
        }
        String digits = s.substring(start, Math.min(i, start + 15));
        long v = Long.parseLong(digits);
        if (v > Integer.MAX_VALUE) {
            v = Integer.MAX_VALUE;
        }
        return (int) (neg ? -v : v);
    }

    /**
     * {@code d.setMonth(d.getMonth() + n)} JavaScript (zona server UTC): tanggal yang melampaui akhir
     * bulan target bergulir ke bulan berikutnya (31 Jan + 1 → 3 Mar), jam dipertahankan.
     */
    static Instant addMonthsJs(Instant instant, int months) {
        ZonedDateTime z = instant.atZone(ZoneOffset.UTC);
        YearMonth target = YearMonth.from(z).plusMonths(months);
        LocalDate date = target.atDay(1).plusDays(z.getDayOfMonth() - 1L);
        return z.with(date).toInstant();
    }

    /** Tanggal jadwal penyusutan ke-i: {@code setMonth(getMonth() + i)} lalu {@code setDate(1)}. */
    static Instant schedulePeriod(Instant acquisition, int i) {
        ZonedDateTime z = addMonthsJs(acquisition, i).atZone(ZoneOffset.UTC);
        return z.withDayOfMonth(1).toInstant();
    }

    /** Hash prefix → kunci advisory lock persis seperti sumber ({@code (h << 5) - h + c | 0}). */
    static long advisoryKey(String prefix) {
        int h = 0;
        for (int i = 0; i < prefix.length(); i++) {
            h = (h << 5) - h + prefix.charAt(i);
        }
        int key = h & 0x7fffffff;
        return key == 0 ? 1 : key;
    }

    /** {@code parseInt(code.slice(prefix.length), 10)} lalu +1 (NaN → 1), dipad 4 digit. */
    static String nextSequence(String prefix, String latestCode) {
        Integer last = latestCode == null ? Integer.valueOf(0) : parseInt(latestCode.substring(prefix.length()));
        int next = last == null ? 1 : last + 1;
        String n = String.valueOf(next);
        return prefix + (n.length() >= 4 ? n : "0".repeat(4 - n.length()) + n);
    }
}
