package id.enterpriseapps.lims.entity;

/**
 * Kesimpulan satu hasil uji terhadap batas spesifikasinya.
 *
 * <p>Selalu dihitung backend ({@code LimsSpecEvaluator}) — tidak pernah diketik
 * pengguna, supaya status "lulus" tidak bisa diakali dari form.
 */
public enum LimsSpecStatus {

    /** Masuk spesifikasi. */
    PASS,

    /** Di luar spesifikasi (OOS — out of specification). */
    FAIL,

    /**
     * Belum/tidak dapat disimpulkan: standar kualitatif, batas belum diisi,
     * hasil belum berupa angka, atau hasil batas deteksi ("&lt;0,01") yang
     * tidak menentukan terhadap batas yang berlaku.
     */
    NOT_EVALUATED
}
