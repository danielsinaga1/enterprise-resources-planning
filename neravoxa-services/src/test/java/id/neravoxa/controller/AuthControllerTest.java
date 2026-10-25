package id.neravoxa.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.neravoxa.dto.LoginRequest;
import id.neravoxa.dto.LoginResponse;
import id.neravoxa.dto.RefreshTokenRequest;
import id.neravoxa.service.JwtService;
import id.neravoxa.service.RedisService;
import id.neravoxa.service.auth.AuthService;
import id.neravoxa.service.auth.LoginThrottleService;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // disable security filters for unit tests
class AuthControllerTest extends id.neravoxa.support.AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private LoginThrottleService loginThrottleService;

    @MockitoBean
    private RedisService redisService;

    @MockitoBean
    private JwtService jwtService;

    // ===================== LOGIN TESTS =====================

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Login berhasil dengan NPK dan password valid")
        void login_Success() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .npk("12345")
                    .password("password123")
                    .build();

            LoginResponse response = LoginResponse.builder()
                    .success(true)
                    .message("Login berhasil")
                    .id(1L)
                    .npp("NPP001")
                    .name("John Doe")
                    .email("john@neravoxa.local")
                    .token("jwt-access-token")
                    .refreshToken("jwt-refresh-token")
                    .roles(List.of("ADMIN"))
                    .build();

            when(authService.login(any(LoginRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Login berhasil"))
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andExpect(jsonPath("$.name").value("John Doe"))
                    .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
        }

        @Test
        @DisplayName("Login gagal - NPK atau password salah")
        void login_InvalidCredentials() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .npk("wrong-npk")
                    .password("wrong-password")
                    .build();

            LoginResponse response = LoginResponse.builder()
                    .success(false)
                    .message("NPK atau password salah")
                    .build();

            when(authService.login(any(LoginRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("NPK atau password salah"));
        }

        @Test
        @DisplayName("Login gagal - akun tidak aktif")
        void login_InactiveAccount() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .npk("12345")
                    .password("password123")
                    .build();

            LoginResponse response = LoginResponse.builder()
                    .success(false)
                    .message("Akun tidak aktif. Hubungi administrator.")
                    .build();

            when(authService.login(any(LoginRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Akun tidak aktif. Hubungi administrator."));
        }

        @Test
        @DisplayName("Login gagal - request body kosong")
        void login_EmptyBody() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    // NPK null → AuthService should handle and return failure
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===================== REFRESH TOKEN TESTS =====================

    @Nested
    @DisplayName("POST /api/v1/auth/refresh-token")
    class RefreshTokenTests {

        @Test
        @DisplayName("Refresh token berhasil")
        void refreshToken_Success() throws Exception {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("valid-refresh-token")
                    .build();

            LoginResponse response = LoginResponse.builder()
                    .success(true)
                    .message("Token Refreshed Successfully")
                    .id(1L)
                    .token("new-access-token")
                    .refreshToken("new-refresh-token")
                    .roles(List.of("ADMIN"))
                    .build();

            when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/refresh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.token").value("new-access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
        }

        @Test
        @DisplayName("Refresh token gagal - token tidak valid")
        void refreshToken_Invalid() throws Exception {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("invalid-refresh-token")
                    .build();

            LoginResponse response = LoginResponse.builder()
                    .success(false)
                    .message("Refresh Token Failed: Invalid Token Signature")
                    .build();

            when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/refresh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ===================== LOGOUT TESTS =====================

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("Logout berhasil dengan Bearer token")
        void logout_Success() throws Exception {
            when(jwtService.extractClaim(any(), any()))
                    .thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"refresh-token\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Logout gagal - tanpa Authorization header")
        void logout_NoToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===================== THROTTLE TESTS =====================

    @Nested
    @DisplayName("Throttle brute-force pada POST /api/v1/auth/login")
    class ThrottleTests {

        @Test
        @DisplayName("NPK terkunci ditolak 429 dengan Retry-After, tanpa memanggil AuthService")
        void login_Locked_Returns429() throws Exception {
            when(loginThrottleService.activeLock(any(), any()))
                    .thenReturn(Optional.of(Duration.ofSeconds(900)));

            LoginRequest request = LoginRequest.builder()
                    .npk("12345")
                    .password("salah")
                    .build();

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "900"))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(
                            "Terlalu banyak percobaan login gagal. Coba lagi dalam 15 menit."));

            // Inti proteksinya: kredensial tidak diteruskan sama sekali, jadi AD tidak
            // ikut menghitung kegagalan dan tidak mengunci akun domain korban.
            verify(authService, never()).login(any(LoginRequest.class));
        }
    }
}
