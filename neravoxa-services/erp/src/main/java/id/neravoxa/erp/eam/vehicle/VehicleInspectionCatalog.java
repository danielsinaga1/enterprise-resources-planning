package id.neravoxa.erp.eam.vehicle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Struktur checklist inspeksi berkala. Struktur baru = kategori + sub-item; kunci sub-item tetap datar di
 * JSON {@code checklist} supaya data inspeksi LAMA (12 checkpoint datar) tetap terbaca tanpa migrasi.
 */
final class VehicleInspectionCatalog {

    private VehicleInspectionCatalog() {
    }

    /** Interval default jadwal inspeksi berikutnya (hari). */
    static final int DEFAULT_INTERVAL_DAYS = 30;

    /** Checkpoint format LAMA (12 item datar) — dipertahankan agar riwayat lama punya label benar di FE. */
    static final List<String> CHECKPOINTS = List.of(
            "ban", "rem", "oli", "lampu", "klakson", "wiper", "kaca_spion", "sabuk_pengaman",
            "ac", "aki", "radiator", "kelengkapan_p3k_apar");

    static final List<Map<String, Object>> CATEGORIES = List.of(
            category("mesin", "Mesin & Cairan",
                    "oli_mesin", "Oli Mesin",
                    "oli_rem", "Oli Rem",
                    "air_radiator", "Air Radiator / Coolant",
                    "air_aki", "Air Aki",
                    "air_wiper", "Air Wiper"),
            category("kelistrikan", "Kelistrikan",
                    "lampu_depan", "Lampu Depan",
                    "lampu_belakang", "Lampu Belakang",
                    "lampu_sein", "Lampu Sein",
                    "lampu_rem", "Lampu Rem",
                    "lampu_mundur", "Lampu Mundur",
                    "klakson", "Klakson"),
            category("rem_kemudi", "Rem & Kemudi",
                    "rem_depan", "Rem Depan",
                    "rem_belakang", "Rem Belakang",
                    "rem_tangan", "Rem Tangan",
                    "kemudi_suspensi", "Kemudi / Suspensi"),
            category("ban_roda", "Ban & Roda",
                    "ban_depan", "Ban Depan",
                    "ban_belakang", "Ban Belakang",
                    "ban_serep", "Ban Serep",
                    "tekanan_ban", "Tekanan Ban",
                    "velg", "Velg"),
            category("keselamatan", "Keselamatan",
                    "sabuk_pengaman", "Sabuk Pengaman",
                    "p3k", "P3K",
                    "apar", "APAR",
                    "segitiga_pengaman", "Segitiga Pengaman",
                    "dongkrak", "Dongkrak",
                    "kunci_roda", "Kunci Roda"),
            category("lain", "Lain-lain",
                    "kaca_spion", "Kaca Spion",
                    "kaca_depan", "Kaca Depan",
                    "kaca_belakang", "Kaca Belakang",
                    "wiper", "Wiper",
                    "ac", "AC",
                    "body_cat", "Body / Cat",
                    "dashboard_indikator", "Dashboard / Indikator"));

    static final List<String> CATEGORY_KEYS = CATEGORIES.stream().map(c -> (String) c.get("key")).toList();

    private static Map<String, Object> category(String key, String label, String... items) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < items.length; i += 2) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("key", items[i]);
            it.put("label", items[i + 1]);
            list.add(java.util.Collections.unmodifiableMap(it));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("items", List.copyOf(list));
        return java.util.Collections.unmodifiableMap(m);
    }
}
