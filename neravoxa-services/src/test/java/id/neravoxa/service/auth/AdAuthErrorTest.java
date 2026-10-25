package id.neravoxa.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Memastikan alasan penolakan bind AD berhasil digali dari rantai exception.
 *
 * <p>Bentuk pesan pada test ini disalin dari error asli Active Directory; nilai itulah yang
 * sebelumnya hilang karena Spring Security hanya meneruskan "Bad credentials".
 */
class AdAuthErrorTest {

    /** Meniru pembungkusan berlapis Spring Security di atas javax.naming. */
    private static BadCredentialsException adFailure(String dataCode) {
        String raw = "[LDAP: error code 49 - 80090308: LdapErr: DSID-0C0903A9, comment: "
                + "AcceptSecurityContext error, data " + dataCode + ", v4563]";
        return new BadCredentialsException("Bad credentials",
                new javax.naming.AuthenticationException(raw));
    }

    @Test
    @DisplayName("password kedaluwarsa terbaca sebagai PASSWORD_EXPIRED, bukan password salah")
    void mengenaliPasswordKedaluwarsa() {
        AdAuthError result = AdAuthError.from(adFailure("532"));

        assertThat(result).isEqualTo(AdAuthError.PASSWORD_EXPIRED);
        assertThat(result.getUserMessage()).contains("kedaluwarsa");
    }

    @Test
    @DisplayName("flag 'must change password at next logon' terbaca sebagai MUST_CHANGE_PASSWORD")
    void mengenaliWajibGantiPassword() {
        assertThat(AdAuthError.from(adFailure("773"))).isEqualTo(AdAuthError.MUST_CHANGE_PASSWORD);
    }

    @Test
    @DisplayName("akun terkunci terbaca sebagai ACCOUNT_LOCKED")
    void mengenaliAkunTerkunci() {
        assertThat(AdAuthError.from(adFailure("775"))).isEqualTo(AdAuthError.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("password yang memang salah tetap dilaporkan sebagai BAD_PASSWORD")
    void mengenaliPasswordSalah() {
        AdAuthError result = AdAuthError.from(adFailure("52e"));

        assertThat(result).isEqualTo(AdAuthError.BAD_PASSWORD);
        assertThat(result.getUserMessage()).isEqualTo("NPK atau password salah");
    }

    @Test
    @DisplayName("kode heksadesimal huruf besar tetap dikenali")
    void kodeHurufBesarTetapDikenali() {
        assertThat(AdAuthError.from(adFailure("52E"))).isEqualTo(AdAuthError.BAD_PASSWORD);
    }

    @Test
    @DisplayName("tanpa potongan 'data' jatuh ke UNKNOWN dengan pesan netral")
    void tanpaKodeJatuhKeUnknown() {
        AdAuthError result = AdAuthError.from(new BadCredentialsException("Bad credentials"));

        assertThat(result).isEqualTo(AdAuthError.UNKNOWN);
        assertThat(result.getUserMessage()).isEqualTo("NPK atau password salah");
    }

    @Test
    @DisplayName("kode yang belum dipetakan tetap aman dan pesan mentahnya bisa dicatat")
    void kodeAsingTetapAman() {
        BadCredentialsException error = adFailure("999");

        assertThat(AdAuthError.from(error)).isEqualTo(AdAuthError.UNKNOWN);
        assertThat(AdAuthError.rootMessage(error)).contains("data 999");
    }

    @Test
    @DisplayName("rantai penyebab yang saling menunjuk tidak membuat penelusuran menggantung")
    void rantaiMelingkarTidakMenggantung() {
        // A menyebabkan B, B menyebabkan A — tanpa batas kedalaman ini menggantungkan thread login.
        RuntimeException a = new RuntimeException("tanpa kode A");
        RuntimeException b = new RuntimeException("tanpa kode B", a);
        a.initCause(b);

        assertThat(AdAuthError.from(a)).isEqualTo(AdAuthError.UNKNOWN);
        assertThat(AdAuthError.rootMessage(a)).isNotNull();
    }

    @Test
    @DisplayName("kode AD tetap terbaca walau terkubur dalam beberapa lapis pembungkus")
    void kodeTerbacaDariLapisanDalam() {
        Throwable berlapis = new BadCredentialsException("Bad credentials",
                new RuntimeException("wrapper",
                        new javax.naming.AuthenticationException(
                                "[LDAP: error code 49 - ... AcceptSecurityContext error, data 775, v4563]")));

        assertThat(AdAuthError.from(berlapis)).isEqualTo(AdAuthError.ACCOUNT_LOCKED);
    }
}
