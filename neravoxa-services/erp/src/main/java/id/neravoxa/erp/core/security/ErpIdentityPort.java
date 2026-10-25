package id.neravoxa.erp.core.security;

import java.util.List;
import java.util.Optional;

/**
 * Port ke direktori pengguna platform (db_auth). Diimplementasikan aplikasi utama
 * ({@code ErpIdentityAdapter}) karena modul ERP tidak boleh bergantung balik ke aplikasi.
 *
 * <p>Bila tidak ada bean implementasi (mis. di test), ERP tetap jalan: pengguna baru
 * diprovisi hanya dari email + role di JWT.
 */
public interface ErpIdentityPort {

    Optional<PlatformUser> findByEmail(String email);

    Optional<PlatformUser> findById(Long id);

    /** Cari pengguna aktif berdasarkan nama/NPK/email (untuk pemilih atasan, dsb.). */
    List<PlatformUser> search(String query, int limit);

    /** Role platform aktif (kode + nama) — pilihan kunci role saat mengatur matriks Hak Akses ERP. */
    List<PlatformRole> listRoles();

    record PlatformRole(String code, String name) {
    }

    /**
     * @param parentId auth_users.parent_id — atasan langsung (approver tahap 1 Pengajuan Pembayaran)
     * @param roles    kode role aktif
     */
    record PlatformUser(Long id, String npk, String name, String email, Long parentId, boolean active,
                        List<String> roles) {
    }
}
