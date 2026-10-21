package id.enterpriseapps.nonconformance;

/**
 * Penunjuk ke NCR yang sudah terbentuk: id untuk tautan, nomor untuk dibaca
 * manusia. Sengaja hanya dua field — modul fitur tidak perlu tahu bentuk
 * entitas safety observation.
 */
public record NonConformanceRef(Long id, String reportNumber) {
}
