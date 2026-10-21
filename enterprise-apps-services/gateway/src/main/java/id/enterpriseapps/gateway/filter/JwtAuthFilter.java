package id.enterpriseapps.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Global JWT authentication filter for Spring Cloud Gateway.
 * Validates JWT tokens before forwarding requests to downstream services.
 * Public endpoints (login, refresh-token) bypass validation.
 */
@Component
@Slf4j
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // Endpoints that don't require a JWT token
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/refresh-token",
        "/api/v1/auth/register",
        "/actuator/health",
        "/actuator/info"
    );

    // Prefixes for Swagger/OpenAPI docs (allowed in dev)
    private static final List<String> PUBLIC_PREFIXES = List.of(
        "/swagger-ui",
        "/v3/api-docs",
        "/webjars",
        // ERP: formulir inquiry publik (/tanya), branding, dan scan QR aset fisik — tanpa login.
        // Endpoint-nya sendiri membatasi data yang dibuka (lihat modul :erp).
        "/api/erp/public/",
        "/api/erp/ga/public/"
    );

    // Uploaded files (incident photos) are rendered by <img> tags, and a browser cannot attach
    // an Authorization header to an image request — behind the filter every photo came back 401.
    //
    // READ ONLY. The same prefix also serves POST (upload) and DELETE (remove file), so this is
    // matched on GET alone: opening the whole prefix would let anyone delete any photo without
    // logging in. Reads are guarded only by the unguessable UUID filename.
    private static final List<String> PUBLIC_GET_PREFIXES = List.of(
        "/api/v1/uploads/",
        "/uploads/"
    );

    // GET-only exact paths. Branding (Pengaturan Umum) is rendered on the login
    // page before any token exists; the PUT stays behind JWT like everything else.
    private static final Set<String> PUBLIC_GET_PATHS = Set.of(
        "/api/v1/settings/general"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Pass through pre-flight CORS OPTIONS requests
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // Pass through public endpoints
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Pass through public reads (uploaded images, branding) — GET only; writes stay authenticated.
        if (request.getMethod() == HttpMethod.GET
                && (PUBLIC_GET_PATHS.contains(path)
                        || PUBLIC_GET_PREFIXES.stream().anyMatch(path::startsWith))) {
            return chain.filter(exchange);
        }

        // Require Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = validateToken(token);
            String userId = claims.getSubject();
            Object rolesRaw = claims.get("roles");
            String roles = rolesRaw != null ? rolesRaw.toString() : "";

            // Forward user identity to downstream services as headers
            ServerHttpRequest mutated = request.mutate()
                .header("X-User-Id", userId)
                .header("X-User-Roles", roles)
                .header("X-Gateway-Validated", "true")
                .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException e) {
            log.debug("JWT validation failed for path {}: {}", path, e.getMessage());
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    @Override
    public int getOrder() {
        // Run before rate limiter (order -1) so invalid JWTs are rejected first
        return -2;
    }

    private boolean isPublicPath(String path) {
        if (PUBLIC_PATHS.contains(path)) return true;
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Claims validateToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
            jwtSecret.getBytes(StandardCharsets.UTF_8)
        );
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        byte[] body = ("{\"status\":\"FAILED\",\"errorMessage\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(
            Mono.just(response.bufferFactory().wrap(body))
        );
    }
}
