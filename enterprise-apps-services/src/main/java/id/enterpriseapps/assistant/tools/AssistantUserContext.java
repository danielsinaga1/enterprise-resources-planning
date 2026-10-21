package id.enterpriseapps.assistant.tools;

import java.util.List;

/**
 * Identitas pengguna yang sedang login, diteruskan ke setiap eksekusi tool KPIntar.
 *
 * <p>Diturunkan dari Spring Security context: {@code userId} diresolusi dari email pada
 * subject JWT (lihat {@code AuthService.issueTokens} — subject = email) lewat
 * {@code AuthUserRepository.findByEmail}. {@code roles} berisi kode role tanpa awalan
 * {@code ROLE_} (mis. "MANAGER_QSHE"). Tool tulis memakai {@code userId} sebagai
 * pelaku aksi (mis. manager yang menugaskan), sehingga aksi selalu terikat ke
 * pengguna yang benar-benar login, bukan id yang dikirim dari browser.
 */
public record AssistantUserContext(Long userId, String email, List<String> roles) {

    public boolean hasRole(String roleCode) {
        return roles != null && roles.contains(roleCode);
    }

    /** true bila pengguna punya salah satu dari role yang diberikan. */
    public boolean hasAnyRole(String... roleCodes) {
        if (roles == null) return false;
        for (String r : roleCodes) {
            if (roles.contains(r)) return true;
        }
        return false;
    }
}
