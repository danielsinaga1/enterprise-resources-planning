package id.enterpriseapps.config;

import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.authentication.BindAuthenticator;

/**
 * {@link BindAuthenticator} yang mempertahankan alasan asli penolakan Active Directory.
 *
 * <p>Masalahnya ada di {@code BindAuthenticator#bindWithDn}: ketika AD menolak bind, exception
 * {@code org.springframework.ldap.AuthenticationException} diserahkan ke
 * {@link #handleBindException} — yang implementasi bawaannya <em>hanya mencatat ke DEBUG</em> —
 * lalu metode itu mengembalikan {@code null}. Akhirnya {@code BindAuthenticator} melempar
 * {@code BadCredentialsException("Bad credentials")} <b>tanpa cause sama sekali</b>.
 *
 * <p>Akibatnya kode {@code data <hex>} milik AD (532 kedaluwarsa, 773 wajib ganti password,
 * 775 terkunci) hilang sebelum sempat dibaca lapisan mana pun di atasnya. Menelusuri rantai
 * penyebab saja tidak cukup — rantainya memang kosong. Kelas ini menangkap penyebab tersebut
 * lalu melampirkannya ke exception yang dilempar, sehingga {@code AdAuthError} bisa membacanya.
 *
 * <p>Penyimpanan memakai {@link ThreadLocal} dan bukan langsung melempar dari
 * {@link #handleBindException}, karena metode itu sengaja dirancang "menelan" agar beberapa pola
 * DN bisa dicoba berurutan. Melempar di sana akan menghentikan percobaan DN berikutnya.
 */
public class DiagnosticBindAuthenticator extends BindAuthenticator {

    private static final ThreadLocal<Throwable> LAST_BIND_ERROR = new ThreadLocal<>();

    public DiagnosticBindAuthenticator(BaseLdapPathContextSource contextSource) {
        super(contextSource);
    }

    @Override
    public DirContextOperations authenticate(Authentication authentication) {
        LAST_BIND_ERROR.remove();
        try {
            return delegateAuthenticate(authentication);
        } catch (BadCredentialsException ex) {
            Throwable captured = LAST_BIND_ERROR.get();
            // Hanya dilampirkan bila Spring memang tidak menyertakan cause apa pun.
            if (captured != null && ex.getCause() == null) {
                throw new BadCredentialsException(ex.getMessage(), captured);
            }
            throw ex;
        } finally {
            // Wajib dibersihkan: thread pool Tomcat dipakai ulang lintas request.
            LAST_BIND_ERROR.remove();
        }
    }

    /**
     * Titik sambung ke perilaku bawaan Spring. Dipisah sebagai metode tersendiri supaya
     * pembungkusan penyebab di atas bisa diuji tanpa Active Directory sungguhan.
     */
    protected DirContextOperations delegateAuthenticate(Authentication authentication) {
        return super.authenticate(authentication);
    }

    @Override
    protected void handleBindException(String userDn, String username, Throwable cause) {
        super.handleBindException(userDn, username, cause);
        LAST_BIND_ERROR.set(cause);
    }
}
