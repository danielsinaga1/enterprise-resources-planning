package id.enterpriseapps.lims.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pengumpul selisih nilai sebelum/sesudah untuk satu operasi penyuntingan.
 *
 * <p>Dipakai agar jejak audit hanya memuat field yang benar-benar berubah —
 * menyimpan seluruh field setiap kali tombol Simpan ditekan membuat jejaknya
 * tidak terbaca dan menyamarkan perubahan yang penting.
 *
 * <pre>
 * var changes = LimsAuditChanges.create()
 *         .compare("location", existing.getLocation(), dto.getLocation())
 *         .compare("notes",    existing.getNotes(),    dto.getNotes());
 * </pre>
 */
public final class LimsAuditChanges {

    private final Map<String, String[]> fields = new LinkedHashMap<>();

    private LimsAuditChanges() {
    }

    public static LimsAuditChanges create() {
        return new LimsAuditChanges();
    }

    /** Mencatat field bila nilainya berubah; pemanggilan berantai. */
    public LimsAuditChanges compare(String field, Object before, Object after) {
        String oldValue = stringify(before);
        String newValue = stringify(after);
        if (!Objects.equals(oldValue, newValue)) {
            fields.put(field, new String[]{oldValue, newValue});
        }
        return this;
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /** field -> [nilai lama, nilai baru]. */
    public Map<String, String[]> asMap() {
        return Collections.unmodifiableMap(fields);
    }

    private static String stringify(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
