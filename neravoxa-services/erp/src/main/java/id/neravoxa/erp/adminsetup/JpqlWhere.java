package id.neravoxa.erp.adminsetup;

import id.neravoxa.erp.core.ErpQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * Kumpulan kondisi JPQL (AND) + parameternya — dipakai bersama oleh query daftar ({@link ErpQuery})
 * dan query agregat/ringkasan yang ditulis manual, agar filter keduanya selalu identik.
 */
final class JpqlWhere {

    /**
     * Klausa escape eksplisit untuk pola LIKE dari {@link #contains}/{@link #startsWith}/{@link #escape}.
     * Hibernate 6 (PostgreSQL) menambahkan {@code escape ''} pada LIKE tanpa ESCAPE, sehingga backslash
     * dari {@code ErpQuery.escapeLike} menjadi karakter biasa dan kata kunci berisi {@code _}/{@code %}
     * tidak pernah cocok. Karakter escape '!' dipakai agar literal HQL/SQL-nya tidak ambigu.
     */
    static final String ESC = " escape '!'";

    /** Escape karakter khusus LIKE dengan '!' (pasangan {@link #ESC}). */
    static String escape(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** Pola "mengandung" huruf kecil ter-trim — padanan {@code contains + mode insensitive}. */
    static String contains(String term) {
        return containsRaw(term.trim());
    }

    /** Pola "mengandung" huruf kecil tanpa trim (rute yang memakai string mentah). */
    static String containsRaw(String term) {
        return "%" + escape(term.toLowerCase(java.util.Locale.ROOT)) + "%";
    }

    /** Pola "diawali" (nilai dipakai apa adanya, mis. sudah huruf kecil). */
    static String startsWith(String term) {
        return escape(term) + "%";
    }

    private record Cond(String jpql, Object[] nameValues) {
    }

    private final List<Cond> conds = new ArrayList<>();

    /** Tambah kondisi; pasangan (nama, nilai) parameter mengikuti. */
    JpqlWhere and(String jpql, Object... nameValues) {
        conds.add(new Cond(jpql, nameValues));
        return this;
    }

    /** {@code " where (a) and (b)"} atau string kosong. */
    String clause() {
        return conds.isEmpty() ? ""
                : " where " + String.join(" and ", conds.stream().map(c -> "(" + c.jpql() + ")").toList());
    }

    void bind(jakarta.persistence.Query q) {
        for (Cond c : conds) {
            for (int i = 0; i + 1 < c.nameValues().length; i += 2) {
                q.setParameter(String.valueOf(c.nameValues()[i]), c.nameValues()[i + 1]);
            }
        }
    }

    <T> ErpQuery<T> applyTo(ErpQuery<T> q) {
        conds.forEach(c -> q.where(c.jpql(), c.nameValues()));
        return q;
    }
}
