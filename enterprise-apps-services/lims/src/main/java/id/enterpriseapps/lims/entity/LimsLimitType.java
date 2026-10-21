package id.enterpriseapps.lims.entity;

/**
 * Cara sebuah standar dibandingkan dengan hasil uji.
 *
 * <p>Disimpan sebagai teks di {@code lims_standards.limit_type} (lihat
 * constraint {@code ck_lims_standards_limit_type}), bukan ordinal, supaya
 * urutan konstanta di sini tidak mengikat data lama.
 */
public enum LimsLimitType {

    /** Tidak dievaluasi secara numerik, mis. "Jernih", "Tidak berbau". */
    QUALITATIVE,

    /** lower_limit &lt;= nilai &lt;= upper_limit. */
    RANGE,

    /** nilai &lt;= upper_limit. */
    MAX,

    /** nilai &gt;= lower_limit. */
    MIN
}
