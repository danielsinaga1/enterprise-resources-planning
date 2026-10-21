package id.enterpriseapps.erp.eam.asset;

import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Port {@code services/gaCategoryKind.ts}: himpunan kategori aset untuk halaman Kendaraan / Gedung /
 * Peralatan. Bila ada id warisan yang di-hard-code, pakai hanya itu; selain itu cocokkan kode kategori,
 * lalu kata kunci nama.
 */
final class GaCategoryKind {

    enum Kind { vehicle, building, equipment }

    private static final Map<Kind, List<String>> LEGACY_CATEGORY_IDS = Map.of(
            Kind.vehicle, List.of("cmq7ja1dc000001lpil7negq7"),
            Kind.building, List.of("cmq6bx8z30003vvqjbog7voys", "cmq7ja1kg000401lp4k5obtk8"),
            Kind.equipment, List.of("cmq7ja1lo000501lpk8lwwi2q"));

    private static final Map<Kind, List<String>> CODES = Map.of(
            Kind.vehicle, List.of("KENDARAAN", "VEHICLE", "KND"),
            Kind.building, List.of("BANGUNAN", "GUDANG", "GEDUNG", "BUILDING", "BLD"),
            Kind.equipment, List.of("EQUIPMENT", "PERALATAN", "ALAT_TIMBANG", "EQP"));

    private static final Map<Kind, List<String>> NAME_KEYWORDS = Map.of(
            Kind.vehicle, List.of("kendaraan", "armada", "vehicle"),
            Kind.building, List.of("gedung", "bangunan", "gudang", "building"),
            Kind.equipment, List.of("peralatan", "equipment", "alat"));

    /** Hasil parse: {@code absent} (tidak dikirim), {@code invalid}, atau kind. */
    record Parsed(boolean absent, boolean invalid, Kind kind) {
    }

    private GaCategoryKind() {
    }

    /** undefined bila absen/kosong, invalid bila ada tapi tidak dikenal. */
    static Parsed parse(String value) {
        if (value == null || value.isEmpty()) {
            return new Parsed(true, false, null);
        }
        for (Kind k : Kind.values()) {
            if (k.name().equals(value)) {
                return new Parsed(false, false, k);
            }
        }
        return new Parsed(false, true, null);
    }

    record CategoryRow(String id, String code, String name) {
    }

    static List<String> pickCategoryIds(Kind kind, List<CategoryRow> categories) {
        List<String> legacy = categories.stream()
                .filter(c -> LEGACY_CATEGORY_IDS.get(kind).contains(c.id())).map(CategoryRow::id).toList();
        if (!legacy.isEmpty()) {
            return legacy;
        }
        List<String> codes = CODES.get(kind);
        List<String> byCode = categories.stream()
                .filter(c -> codes.contains(c.code().strip().toUpperCase(Locale.ROOT))).map(CategoryRow::id).toList();
        if (!byCode.isEmpty()) {
            return byCode;
        }
        List<String> words = NAME_KEYWORDS.get(kind);
        return categories.stream()
                .filter(c -> words.stream().anyMatch(w -> c.name().toLowerCase(Locale.ROOT).contains(w)))
                .map(CategoryRow::id).toList();
    }

    static List<String> resolveCategoryIds(EntityManager em, Kind kind) {
        List<CategoryRow> rows = em.createQuery("select c.id, c.code, c.name from GaAssetCategory c", Object[].class)
                .getResultList().stream()
                .map(r -> new CategoryRow((String) r[0], (String) r[1], (String) r[2]))
                .toList();
        return pickCategoryIds(kind, rows);
    }
}
