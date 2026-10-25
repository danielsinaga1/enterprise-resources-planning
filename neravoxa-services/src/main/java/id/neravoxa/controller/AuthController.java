package id.neravoxa.controller;

import java.time.Duration;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import id.neravoxa.dto.LoginRequest;
import id.neravoxa.dto.LoginResponse;
import id.neravoxa.service.auth.AuthService;
import id.neravoxa.service.auth.LoginThrottleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API untuk autentikasi pengguna")
public class AuthController {

    private final AuthService authService;
    private final LoginThrottleService loginThrottleService;

    private final id.neravoxa.service.RedisService redisService;
    private final id.neravoxa.service.JwtService jwtService;

    @PostMapping("/login")
    @Operation(summary = "Login pengguna", description = "Autentikasi dengan NPK dan password")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String clientIp = loginThrottleService.resolveClientIp(httpRequest);

        // Kunci diperiksa sebelum kredensial menyentuh AD, supaya serangan tebak-password
        // tidak ikut mengunci akun domain korban di Active Directory.
        Optional<Duration> lock = loginThrottleService.activeLock(request.getNpk(), clientIp);
        if (lock.isPresent()) {
            return tooManyAttempts(lock.get());
        }

        LoginResponse response = authService.login(request);

        // Null-safe: a missing response (e.g. blank credentials) is a failed login, not a 500.
        if (response != null && response.isSuccess()) {
            loginThrottleService.recordSuccess(request.getNpk(), clientIp);
            return ResponseEntity.ok(response);
        }
        loginThrottleService.recordFailure(request.getNpk(), clientIp);
        return ResponseEntity.status(401).body(response != null ? response
                : LoginResponse.builder().success(false).message("NPK atau password salah").build());
    }

    /**
     * 429 + {@code Retry-After}. Pesannya sengaja tidak menyebut apakah NPK-nya ada atau tidak —
     * kalau berbeda, endpoint ini jadi alat pencacah NPK yang valid.
     */
    private ResponseEntity<LoginResponse> tooManyAttempts(Duration retryAfter) {
        long minutes = Math.max(1, (retryAfter.getSeconds() + 59) / 60);
        return ResponseEntity.status(429)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.getSeconds()))
                .body(LoginResponse.builder()
                        .success(false)
                        .message("Terlalu banyak percobaan login gagal. Coba lagi dalam " + minutes + " menit.")
                        .build());
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh Token", description = "Mendapatkan access token baru dengan refresh token")
    public ResponseEntity<LoginResponse> refreshToken(@RequestBody id.neravoxa.dto.RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request);
        if (response != null && response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(401).body(response != null ? response
                : LoginResponse.builder().success(false).message("Refresh token tidak valid").build());
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout pengguna", description = "Inaktivasi token (blacklist) dan hapus refresh token")
    public ResponseEntity<String> logout(jakarta.servlet.http.HttpServletRequest request,
            @RequestBody(required = false) id.neravoxa.dto.RefreshTokenRequest body) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // Calculate TTL (time remaining until expiration)
            try {
                long expirationTime = jwtService.extractClaim(token, io.jsonwebtoken.Claims::getExpiration).getTime();
                long currentTime = System.currentTimeMillis();
                long ttl = expirationTime - currentTime;

                if (ttl > 0) {
                    // Save to Redis Blacklist
                    redisService.save("blacklist:" + token, "true", ttl);
                }
            } catch (Exception e) {
                // Token might be expired already, ignore
            }

            // Delete Refresh Token if provided
            if (body != null && body.getRefreshToken() != null) {
                redisService.delete("refresh_token:" + body.getRefreshToken());
            }

            return ResponseEntity.ok("Logout successful");
        }
        return ResponseEntity.badRequest().body("Invalid token");
    }
}
