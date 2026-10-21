package id.enterpriseapps.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import id.enterpriseapps.auth.entity.AuthUser;
import id.enterpriseapps.auth.repository.AuthUserRepository;
import id.enterpriseapps.auth.repository.AuthUserRoleRepository;
import id.enterpriseapps.dto.LdapUserInfo;
import id.enterpriseapps.dto.LoginRequest;
import id.enterpriseapps.dto.LoginResponse;
import id.enterpriseapps.observability.AuthMetrics;
import id.enterpriseapps.property.LdapProperties;
import id.enterpriseapps.service.JwtService;
import id.enterpriseapps.service.RedisService;

/**
 * Memastikan hash bcrypt lokal ikut terbarui saat pengguna mengganti password di AD.
 *
 * <p>Ini menutup penyebab kasus "sudah ganti password AD tapi tidak bisa login": hash lokal
 * membeku pada nilai lama, lalu jalur bcrypt lokal (dipakai {@code local-login-npks} dan saat
 * LDAP dimatikan) menolak password AD yang baru.
 */
@ExtendWith(MockitoExtension.class)
class LdapPasswordSyncTest {

    private static final String NPK = "2002196";
    private static final String PASSWORD_LAMA = "PasswordLama123";
    private static final String PASSWORD_BARU = "PasswordBaru456";

    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private AuthUserRoleRepository authUserRoleRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private RedisService redisService;
    @Mock
    private LdapAuthService ldapAuthService;
    @Mock
    private AuthMetrics authMetrics;

    private PasswordEncoder passwordEncoder;
    private LdapProperties ldapProperties;
    private AuthService authService;
    private AuthUser user;

    @BeforeEach
    void setUp() {
        // Encoder asli, bukan mock: yang diuji justru apakah hash tersimpan benar-benar berubah.
        passwordEncoder = new BCryptPasswordEncoder();

        ldapProperties = new LdapProperties();
        ldapProperties.setEnabled(true);
        ldapProperties.setUserMatch("npk");
        ldapProperties.setMergeDbRoles(false);
        ldapProperties.setSyncPassword(true);

        user = new AuthUser();
        user.setId(1L);
        user.setNpk(NPK);
        user.setEmail("user@kpi.co.id");
        user.setStatus((short) 1);
        user.setPassword(passwordEncoder.encode(PASSWORD_LAMA));

        authService = new AuthService(authUserRepository, authUserRoleRepository, passwordEncoder,
                jwtService, redisService, ldapProperties, ldapAuthService, authMetrics);

        lenient().when(authUserRepository.findByNpk(NPK)).thenReturn(Optional.of(user));
        lenient().when(authUserRoleRepository.findActiveRolesByUserId(anyLong())).thenReturn(List.of());
        lenient().when(jwtService.generateAccessToken(any(), anyString())).thenReturn("access-token");
        lenient().when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh-token");
    }

    private LoginRequest loginDengan(String password) {
        LoginRequest request = new LoginRequest();
        request.setNpk(NPK);
        request.setPassword(password);
        return request;
    }

    private void adMenerima(String password) {
        when(ldapAuthService.authenticate(NPK, password))
                .thenReturn(LdapUserInfo.builder().username(NPK).employeeId(NPK).build());
    }

    @Test
    @DisplayName("spasi di ujung NPK dirapikan sebelum dikirim ke AD")
    void npkDenganSpasiDirapikan() {
        // Regresi nyata: input "2502426 " (spasi di belakang) membuat filter LDAP
        // (employeeID=2502426 ) tidak menemukan entri apa pun.
        adMenerima(PASSWORD_BARU);
        LoginRequest request = new LoginRequest();
        request.setNpk("  " + NPK + "  ");
        request.setPassword(PASSWORD_BARU);

        LoginResponse response = authService.login(request);

        assertThat(response.isSuccess()).isTrue();
        // AD dipanggil dengan NPK yang sudah bersih, bukan versi berspasi.
        verify(ldapAuthService).authenticate(NPK, PASSWORD_BARU);
    }

    @Test
    @DisplayName("password AD yang baru menggantikan hash lokal yang sudah basi")
    void hashLokalIkutTerbaruiSetelahGantiPasswordDiAd() {
        String hashSebelum = user.getPassword();
        adMenerima(PASSWORD_BARU);

        LoginResponse response = authService.login(loginDengan(PASSWORD_BARU));

        assertThat(response.isSuccess()).isTrue();
        assertThat(user.getPassword()).isNotEqualTo(hashSebelum);
        // Inti perbaikan: jalur lokal kini menerima password AD yang baru, bukan yang lama.
        assertThat(passwordEncoder.matches(PASSWORD_BARU, user.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD_LAMA, user.getPassword())).isFalse();
    }

    @Test
    @DisplayName("hash tidak ditulis ulang bila password tidak berubah")
    void hashTidakDitulisUlangBilaSama() {
        String hashSebelum = user.getPassword();
        adMenerima(PASSWORD_LAMA);

        authService.login(loginDengan(PASSWORD_LAMA));

        assertThat(user.getPassword()).isEqualTo(hashSebelum);
    }

    @Test
    @DisplayName("akun tanpa hash lokal ikut terisi saat login AD pertama")
    void hashKosongIkutTerisi() {
        user.setPassword(null);
        adMenerima(PASSWORD_BARU);

        authService.login(loginDengan(PASSWORD_BARU));

        assertThat(user.getPassword()).isNotNull();
        assertThat(passwordEncoder.matches(PASSWORD_BARU, user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("sinkronisasi bisa dimatikan lewat konfigurasi")
    void sinkronisasiBisaDimatikan() {
        ldapProperties.setSyncPassword(false);
        String hashSebelum = user.getPassword();
        adMenerima(PASSWORD_BARU);

        authService.login(loginDengan(PASSWORD_BARU));

        assertThat(user.getPassword()).isEqualTo(hashSebelum);
    }

    @Test
    @DisplayName("bind AD yang ditolak tidak boleh menyentuh hash lokal")
    void bindGagalTidakMengubahHash() {
        String hashSebelum = user.getPassword();
        when(ldapAuthService.authenticate(NPK, "tebakan"))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginResponse response = authService.login(loginDengan("tebakan"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(user.getPassword()).isEqualTo(hashSebelum);
    }
}
