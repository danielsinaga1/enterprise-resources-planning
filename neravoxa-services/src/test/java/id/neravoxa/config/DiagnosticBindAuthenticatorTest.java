package id.neravoxa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import id.neravoxa.service.auth.AdAuthError;

/**
 * Membuktikan alasan asli penolakan AD selamat sampai ke pemanggil.
 *
 * <p>Tanpa kelas yang diuji di sini, {@code BindAuthenticator} bawaan melempar
 * {@code BadCredentialsException} tanpa cause sehingga kode {@code data <hex>} milik AD —
 * satu-satunya petunjuk apakah password kedaluwarsa, wajib diganti, atau akun terkunci —
 * hilang sebelum bisa dibaca.
 */
class DiagnosticBindAuthenticatorTest {

    /** Pesan error AD sungguhan untuk "user must change password at next logon". */
    private static final String AD_MUST_CHANGE =
            "[LDAP: error code 49 - 80090308: LdapErr: DSID-0C0903A9, comment: "
                    + "AcceptSecurityContext error, data 773, v4563]";

    private static final Authentication TOKEN =
            new UsernamePasswordAuthenticationToken("2502426", "rahasia");

    private static Throwable errorAd() {
        return new org.springframework.ldap.AuthenticationException(
                new javax.naming.AuthenticationException(AD_MUST_CHANGE));
    }

    /**
     * Meniru perilaku Spring: bind ditolak, penyebabnya diserahkan ke handleBindException,
     * lalu dilempar BadCredentialsException tanpa cause.
     */
    private static DiagnosticBindAuthenticator adMenolak(Throwable bindError) {
        return new DiagnosticBindAuthenticator(mock(BaseLdapPathContextSource.class)) {
            @Override
            protected DirContextOperations delegateAuthenticate(Authentication authentication) {
                if (bindError != null) {
                    handleBindException("CN=Merisa Veronika Suparto,OU=Users", "2502426", bindError);
                }
                throw new BadCredentialsException("Bad credentials");
            }
        };
    }

    @Test
    @DisplayName("penyebab asli AD dilampirkan ke exception sehingga kodenya bisa dibaca")
    void penyebabAdDilampirkan() {
        Throwable adError = errorAd();

        assertThatThrownBy(() -> adMenolak(adError).authenticate(TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasCauseReference(adError);
    }

    @Test
    @DisplayName("AdAuthError kini mengenali kode 773 dari exception yang dilempar")
    void kodeAdTerbacaSetelahDilampirkan() {
        Throwable thrown = catchThrowable(() -> adMenolak(errorAd()).authenticate(TOKEN));

        assertThat(AdAuthError.from(thrown)).isEqualTo(AdAuthError.MUST_CHANGE_PASSWORD);
    }

    @Test
    @DisplayName("tanpa kegagalan bind tersimpan, exception diteruskan apa adanya")
    void tanpaPenyebabTersimpanDiteruskanApaAdanya() {
        assertThatThrownBy(() -> adMenolak(null).authenticate(TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasNoCause();
    }

    @Test
    @DisplayName("sisa kegagalan tidak bocor ke request berikutnya di thread yang sama")
    void tidakBocorAntarRequest() {
        // Request pertama gagal dan sempat menyimpan penyebab di ThreadLocal.
        catchThrowable(() -> adMenolak(errorAd()).authenticate(TOKEN));

        // Request kedua di thread yang sama tidak boleh mewarisi penyebab tadi.
        assertThatThrownBy(() -> adMenolak(null).authenticate(TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasNoCause();
    }
}
