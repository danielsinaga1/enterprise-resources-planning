package id.enterpriseapps.erp.eam.vehicle;

import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolusi kategori aset GA untuk halaman Kendaraan/Gedung/Peralatan — salinan
 * {@code services/gaCategoryKind.ts} (paket eam.asset ditulis paralel, jadi logikanya disalin, bukan
 * dirujuk). Bila ada id legacy yang di-hardcode, hanya itu yang dipakai; selain itu cocokkan kode,
 * lalu kata kunci nama.
 */
final class VehicleCategoryKind {

    static final List<String> KINDS = List.of("vehicle", "building", "equipment");

    private static final Map<String, List<String>> LEGACY_IDS = Map.of(
            "vehicle", List.of("cmq7ja1dc000001lpil7negq7"),
            "building", List.of("cmq6bx8z30003vvqjbog7voys", "cmq7ja1kg000401lp4k5obtk8"),
            "equipment", List.of("cmq7ja1lo000501lpk8lwwi2q"));

    private static final Map<String, List<String>> CODES = Map.of(
            "vehicle", List.of("KENDARAAN", "VEHICLE", "KND"),
            "building", List.of("BANGUNAN", "GUDANG", "GEDUNG", "BUILDING", "BLD"),
            "equipment", List.of("EQUIPMENT", "PERALATAN", "ALAT_TIMBANG", "EQP"));

    private static final Map<String, List<String>> NAME_KEYWORDS = Map.of(
            "vehicle", List.of("kendaraan", "armada", "vehicle"),
            "building", List.of("gedung", "bangunan", "gudang", "building"),
            "equipment", List.of("peralatan", "equipment", "alat"));

    /** Hasil parse: {@code absent} (tidak dikirim/kosong), {@code invalid}, atau kind yang valid. */
    record Parsed(boolean absent, boolean invalid, String kind) {
    }

    /** {@code parseCategoryKind}: undefined bila absen/kosong, null bila ada tapi tidak valid. */
    static Parsed parse(String value) {
        if (value == null || value.isEmpty()) {
            return new Parsed(true, false, null);
        }
        return KINDS.contains(value) ? new Parsed(false, false, value) : new Parsed(false, true, null);
    }

    record CategoryRow(String id, String code, String name) {
    }

    static List<String> pick(String kind, List<CategoryRow> categories) {
        List<String> legacy = categories.stream().filter(c -> LEGACY_IDS.get(kind).contains(c.id()))
                .map(CategoryRow::id).toList();
        if (!legacy.isEmpty()) {
            return legacy;
        }
        List<String> codes = CODES.get(kind);
        List<String> byCode = categories.stream()
                .filter(c -> codes.contains(c.code().trim().toUpperCase(Locale.ROOT)))
                .map(CategoryRow::id).toList();
        if (!byCode.isEmpty()) {
            return byCode;
        }
        List<String> words = NAME_KEYWORDS.get(kind);
        return categories.stream()
                .filter(c -> words.stream().anyMatch(w -> c.name().toLowerCase(Locale.ROOT).contains(w)))
                .map(CategoryRow::id).toList();
    }

    static List<String> resolveCategoryIds(EntityManager em, String kind) {
        List<CategoryRow> rows = em.createQuery(
                        "select c.id, c.code, c.name from GaAssetCategory c", Object[].class)
                .getResultList().stream()
                .map(r -> new CategoryRow((String) r[0], (String) r[1], (String) r[2]))
                .toList();
        return pick(kind, rows);
    }
}
