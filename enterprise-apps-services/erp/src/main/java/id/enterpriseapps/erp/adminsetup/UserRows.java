package id.enterpriseapps.erp.adminsetup;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Bentuk baris pengguna ERP ({@code UserRow}) yang dipakai halaman Pengguna ERP dan semua picker
 * pengguna ({@code GET /users?limit=200&sort=fullName}). Kolom {@code users.roles} menyimpan CSV kode
 * role platform (ditulis ErpActorResolver) dan dikirim sebagai array.
 */
final class UserRows {

    private UserRows() {
    }

    static Map<String, Object> row(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("fullName", u.getFullName());
        m.put("email", u.getEmail());
        m.put("npk", u.getNpk());
        m.put("role", u.getRole());
        m.put("roles", roles(u.getRoles()));
        m.put("isActive", u.getIsActive());
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("createdAt", u.getCreatedAt());
        m.put("supervisorId", u.getSupervisorId());
        m.put("supervisor", supervisor(u));
        m.put("platformUserId", u.getPlatformUserId());
        m.put("language", u.getLanguage());
        return m;
    }

    private static Map<String, Object> supervisor(User u) {
        if (u.getSupervisorId() == null) {
            return null;
        }
        User s = u.getSupervisor();
        if (s == null) {
            return null;
        }
        // Relasi dimuat lewat join fetch / em.find; proxy yang belum terinisialisasi dimuat di sini
        // (selalu di dalam transaksi service).
        Hibernate.initialize(s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("fullName", s.getFullName());
        return m;
    }

    /**
     * Baris segar setelah perubahan: relasi atasan read-only harus dimuat ulang dari DB setelah
     * {@code supervisorId} diubah, jadi flush + clear lalu muat dengan join fetch.
     */
    static Map<String, Object> reload(EntityManager em, String id) {
        em.flush();
        em.clear();
        List<User> rows = em.createQuery("select u from User u left join fetch u.supervisor where u.id = :id", User.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound(ErpUserService.NOT_FOUND);
        }
        return row(rows.get(0));
    }

    /** CSV kode role → daftar (urutan dipertahankan, tanpa duplikat/kosong). */
    static List<String> roles(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList()));
    }

    /** Daftar kode role → CSV snapshot (format yang sama dengan ErpActorResolver). */
    static String csv(Collection<String> roles) {
        if (roles == null) {
            return "";
        }
        return String.join(",", new LinkedHashSet<>(roles.stream()
                .filter(r -> r != null && !r.isBlank()).map(String::trim).toList()));
    }

    /** Pola LIKE untuk mencari satu kode utuh di dalam CSV ({@code ,KODE,}). */
    static String csvMemberPattern(String code) {
        return "%," + JpqlWhere.escape(code) + ",%";
    }

    /** Bagian lokal email — nama/username cadangan seperti ErpActorResolver. */
    static String localPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
