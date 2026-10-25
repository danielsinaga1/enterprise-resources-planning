package id.neravoxa.erp.core.security;

import java.util.Set;

/**
 * Pengguna yang sedang dilayani — padanan {@code req.user} di sumber ENTARIS.
 *
 * @param userId         id baris {@code users} di db_erp (dipakai untuk createdBy, requestedBy, dst.)
 * @param email          subject JWT platform
 * @param fullName       nama tampilan
 * @param role           role utama (role ERP pertama yang dimiliki; untuk tampilan/kompatibilitas)
 * @param roleKeys       SEMUA kode role platform pengguna; cek izin memakai union dari semuanya
 * @param platformUserId auth_users.id (boleh null bila adapter identitas tidak tersedia)
 */
public record ErpActor(
        String userId,
        String email,
        String fullName,
        String role,
        Set<String> roleKeys,
        Long platformUserId) {
}
