package id.neravoxa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey",
                "myTestSecretKeyThatIsLongEnoughForHMACSHA256Algorithm1234567890");
        ReflectionTestUtils.setField(jwtService, "accessExpiration", 900000L); // 15 min
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604800000L); // 7 days
    }

    @Nested
    @DisplayName("Generate Token")
    class GenerateTokenTests {

        @Test
        @DisplayName("Generate access token - tidak null dan tidak kosong")
        void generateAccessToken_NotEmpty() {
            String token = jwtService.generateAccessToken("user@test.com");

            assertThat(token).isNotNull().isNotEmpty();
            assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts: header.payload.signature
        }

        @Test
        @DisplayName("Generate access token dengan claims tambahan")
        void generateAccessToken_WithClaims() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", List.of("ADMIN", "MANAGER"));

            String token = jwtService.generateAccessToken(claims, "user@test.com");

            assertThat(token).isNotNull();
            String extractedUsername = jwtService.extractUsername(token);
            assertThat(extractedUsername).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("Generate refresh token - berbeda dari access token")
        void generateRefreshToken_DifferentFromAccess() {
            String accessToken = jwtService.generateAccessToken("user@test.com");
            String refreshToken = jwtService.generateRefreshToken("user@test.com");

            assertThat(refreshToken).isNotEqualTo(accessToken);
        }
    }

    @Nested
    @DisplayName("Extract & Validate Token")
    class ExtractValidateTests {

        @Test
        @DisplayName("Extract username dari token")
        void extractUsername_Success() {
            String token = jwtService.generateAccessToken("admin@neravoxa.local");

            String username = jwtService.extractUsername(token);

            assertThat(username).isEqualTo("admin@neravoxa.local");
        }

        @Test
        @DisplayName("Token valid - signature dan expiration tepat")
        void isTokenValid_True() {
            String token = jwtService.generateAccessToken("admin@neravoxa.local");

            boolean valid = jwtService.isTokenValid(token, "admin@neravoxa.local");

            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("Token tidak valid - username berbeda")
        void isTokenValid_WrongUsername() {
            String token = jwtService.generateAccessToken("admin@neravoxa.local");

            boolean valid = jwtService.isTokenValid(token, "other@neravoxa.local");

            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("Token expired - harus tidak valid")
        void isTokenValid_Expired() {
            // Set expiration to -1ms (already expired)
            ReflectionTestUtils.setField(jwtService, "accessExpiration", -1L);

            String token = jwtService.generateAccessToken("admin@neravoxa.local");

            assertThatThrownBy(() -> jwtService.isTokenValid(token, "admin@neravoxa.local"))
                    .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
        }

        @Test
        @DisplayName("Token dengan signature salah - harus throw exception")
        void isTokenValid_WrongSignature() {
            String token = jwtService.generateAccessToken("admin@neravoxa.local");

            // Tamper with token (modify last character)
            String tamperedToken = token.substring(0, token.length() - 1) + "X";

            assertThatThrownBy(() -> jwtService.extractUsername(tamperedToken))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Refresh Expiration")
    class RefreshExpirationTests {

        @Test
        @DisplayName("getRefreshExpiration mengembalikan nilai yang benar")
        void getRefreshExpiration_CorrectValue() {
            long expiration = jwtService.getRefreshExpiration();
            assertThat(expiration).isEqualTo(604800000L);
        }
    }
}

