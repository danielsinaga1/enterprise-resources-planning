package id.enterpriseapps.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import id.enterpriseapps.dto.LoginRequest;
import id.enterpriseapps.dto.LoginResponse;
import id.enterpriseapps.dto.RefreshTokenRequest;
import id.enterpriseapps.entity.RoleEntity;
import id.enterpriseapps.entity.UserEntity;
import id.enterpriseapps.entity.UserRoleEntity;
import id.enterpriseapps.repository.UserRepository;
import id.enterpriseapps.repository.UserRoleRepository;
import id.enterpriseapps.service.auth.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RedisService redisService;

    @InjectMocks
    private AuthService authService;

    private UserEntity testUser;
    private RoleEntity adminRole;
    private UserRoleEntity userRoleEntity;

    @BeforeEach
    void setUp() {
        testUser = new UserEntity();
        testUser.setId(1L);
        testUser.setNpk("12345");
        testUser.setNpp("NPP001");
        testUser.setName("John Doe");
        testUser.setEmail("john@enterpriseapps.local");
        testUser.setMobilePhone("08123456789");
        testUser.setPassword("$2a$10$hashedPassword");
        testUser.setStatus((short) 1);
        testUser.setDeptId(1L);
        testUser.setAttemptLoginFailed(0);

        adminRole = new RoleEntity();
        adminRole.setId(1L);
        adminRole.setCode("ADMIN");

        userRoleEntity = new UserRoleEntity();
        userRoleEntity.setRole(adminRole);
    }

    // ===================== LOGIN SERVICE TESTS =====================

    @Nested
    @DisplayName("AuthService.login()")
    class LoginServiceTests {

        @Test
        @DisplayName("Login berhasil - return token dan user data")
        void login_Success() {
            LoginRequest request = LoginRequest.builder()
                    .npk("12345")
                    .password("password123")
                    .build();

            when(userRepository.findByNpk("12345")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("password123", testUser.getPassword())).thenReturn(true);
            when(userRoleRepository.findActiveRolesByUserId(1L)).thenReturn(List.of(userRoleEntity));
            when(jwtService.generateAccessToken(any(), eq("john@enterpriseapps.local"))).thenReturn("access-token");
            when(jwtService.generateRefreshToken("john@enterpriseapps.local")).thenReturn("refresh-token");
            when(jwtService.getRefreshExpiration()).thenReturn(604800000L);

            LoginResponse response = authService.login(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Login berhasil");
            assertThat(response.getToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getName()).isEqualTo("John Doe");
            assertThat(response.getRoles()).containsExactly("ADMIN");

            verify(userRepository).save(testUser);
            verify(redisService).save(eq("refresh_token:refresh-token"), eq("john@enterpriseapps.local"), anyLong());
        }

        @Test
        @DisplayName("Login gagal - user tidak ditemukan")
        void login_UserNotFound() {
            LoginRequest request = LoginRequest.builder()
                    .npk("nonexistent")
                    .password("password123")
                    .build();

            when(userRepository.findByNpk("nonexistent")).thenReturn(Optional.empty());

            LoginResponse response = authService.login(request);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("NPK atau password salah");
            verify(jwtService, never()).generateAccessToken(any(), anyString());
        }

        @Test
        @DisplayName("Login gagal - user tidak aktif (status != 1)")
        void login_InactiveUser() {
            testUser.setStatus((short) 0);
            LoginRequest request = LoginRequest.builder()
                    .npk("12345")
                    .password("password123")
                    .build();

            when(userRepository.findByNpk("12345")).thenReturn(Optional.of(testUser));

            LoginResponse response = authService.login(request);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("Akun tidak aktif. Hubungi administrator.");
        }

        @Test
        @DisplayName("Login gagal - password salah, increment attemptLoginFailed")
        void login_WrongPassword() {
            LoginRequest request = LoginRequest.builder()
                    .npk("12345")
                    .password("wrongPassword")
                    .build();

            when(userRepository.findByNpk("12345")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongPassword", testUser.getPassword())).thenReturn(false);

            LoginResponse response = authService.login(request);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("NPK atau password salah");
            verify(userRepository).save(testUser);
            assertThat(testUser.getAttemptLoginFailed()).isEqualTo(1);
        }

        @Test
        @DisplayName("Login berhasil - password123 rehash fallback")
        void login_Password123Rehash() {
            LoginRequest request = LoginRequest.builder()
                    .npk("12345")
                    .password("password123")
                    .build();

            when(userRepository.findByNpk("12345")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("password123", testUser.getPassword())).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("$2a$10$newHashedPassword");
            when(userRoleRepository.findActiveRolesByUserId(1L)).thenReturn(List.of(userRoleEntity));
            when(jwtService.generateAccessToken(any(), eq("john@enterpriseapps.local"))).thenReturn("access-token");
            when(jwtService.generateRefreshToken("john@enterpriseapps.local")).thenReturn("refresh-token");
            when(jwtService.getRefreshExpiration()).thenReturn(604800000L);

            LoginResponse response = authService.login(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(testUser.getPassword()).isEqualTo("$2a$10$newHashedPassword");
        }

        @Test
        @DisplayName("Login gagal - status null dianggap tidak aktif")
        void login_NullStatus() {
            testUser.setStatus(null);
            LoginRequest request = LoginRequest.builder()
                    .npk("12345")
                    .password("password123")
                    .build();

            when(userRepository.findByNpk("12345")).thenReturn(Optional.of(testUser));

            LoginResponse response = authService.login(request);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("Akun tidak aktif. Hubungi administrator.");
        }
    }

    // ===================== REFRESH TOKEN SERVICE TESTS =====================

    @Nested
    @DisplayName("AuthService.refreshToken()")
    class RefreshTokenServiceTests {

        @Test
        @DisplayName("Refresh token berhasil - rotasi token")
        void refreshToken_Success() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("old-refresh-token")
                    .build();

            when(jwtService.extractUsername("old-refresh-token")).thenReturn("john@enterpriseapps.local");
            when(jwtService.isTokenValid("old-refresh-token", "john@enterpriseapps.local")).thenReturn(true);
            when(redisService.hasKey("refresh_token:old-refresh-token")).thenReturn(true);
            when(redisService.get("refresh_token:old-refresh-token")).thenReturn("john@enterpriseapps.local");
            when(userRepository.findByEmail("john@enterpriseapps.local")).thenReturn(Optional.of(testUser));
            when(userRoleRepository.findActiveRolesByUserId(1L)).thenReturn(List.of(userRoleEntity));
            when(jwtService.generateAccessToken(any(), eq("john@enterpriseapps.local"))).thenReturn("new-access-token");
            when(jwtService.generateRefreshToken("john@enterpriseapps.local")).thenReturn("new-refresh-token");
            when(jwtService.getRefreshExpiration()).thenReturn(604800000L);

            LoginResponse response = authService.refreshToken(request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");

            // Verify old token deleted and new saved
            verify(redisService).delete("refresh_token:old-refresh-token");
            verify(redisService).save(eq("refresh_token:new-refresh-token"), eq("john@enterpriseapps.local"), anyLong());
        }

        @Test
        @DisplayName("Refresh token gagal - token tidak ada di Redis")
        void refreshToken_NotInRedis() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("expired-token")
                    .build();

            when(jwtService.extractUsername("expired-token")).thenReturn("john@enterpriseapps.local");
            when(jwtService.isTokenValid("expired-token", "john@enterpriseapps.local")).thenReturn(true);
            when(redisService.hasKey("refresh_token:expired-token")).thenReturn(false);

            LoginResponse response = authService.refreshToken(request);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).contains("Refresh Token Failed");
        }

        @Test
        @DisplayName("Refresh token gagal - email mismatch")
        void refreshToken_EmailMismatch() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("stolen-token")
                    .build();

            when(jwtService.extractUsername("stolen-token")).thenReturn("john@enterpriseapps.local");
            when(jwtService.isTokenValid("stolen-token", "john@enterpriseapps.local")).thenReturn(true);
            when(redisService.hasKey("refresh_token:stolen-token")).thenReturn(true);
            when(redisService.get("refresh_token:stolen-token")).thenReturn("other@enterpriseapps.local");

            LoginResponse response = authService.refreshToken(request);

            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("Refresh token gagal - user tidak ditemukan di database")
        void refreshToken_UserNotFound() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("valid-token")
                    .build();

            when(jwtService.extractUsername("valid-token")).thenReturn("deleted@enterpriseapps.local");
            when(jwtService.isTokenValid("valid-token", "deleted@enterpriseapps.local")).thenReturn(true);
            when(redisService.hasKey("refresh_token:valid-token")).thenReturn(true);
            when(redisService.get("refresh_token:valid-token")).thenReturn("deleted@enterpriseapps.local");
            when(userRepository.findByEmail("deleted@enterpriseapps.local")).thenReturn(Optional.empty());

            LoginResponse response = authService.refreshToken(request);

            assertThat(response.isSuccess()).isFalse();
        }
    }
}

