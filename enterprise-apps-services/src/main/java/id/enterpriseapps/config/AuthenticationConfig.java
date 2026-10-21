package id.enterpriseapps.config;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.logging.LoggingService;
import id.enterpriseapps.utils.CommonUtil;
import id.enterpriseapps.utils.ConvertUtil;
import id.enterpriseapps.utils.ServletRequestUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
// @WebFilter(urlPatterns = "/api/*")
public class AuthenticationConfig extends OncePerRequestFilter {
    private final ConvertUtil convertUtil;
    private final CommonUtil commonUtil;
    private final LoggingService loggingService;
    private final id.enterpriseapps.service.JwtService jwtService;
    private final id.enterpriseapps.service.RedisService redisService;

    private final String[] EXCLUDE_URL = {
            "/api/v1/profile",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh-token",
            "/api/procurement/vendor/create",
            // ERP: formulir inquiry publik, branding & scan QR aset (tanpa login).
            "/api/erp/public/",
            "/api/erp/ga/public/"
    };

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        // Skip auth filter for static file serving and GET file requests
        if (path.startsWith("/uploads/")) return true;
        // Branding (Pengaturan Umum) is shown on the login page before a token exists.
        // GET only — the PUT stays token-guarded so this filter must still run for it.
        if (path.equals("/api/v1/settings/general") && "GET".equalsIgnoreCase(method)) return true;
        // Actuator (health/info/prometheus) is scraped by infra tooling (Prometheus, container
        // healthchecks, ...) that never sends a browser-shaped User-Agent — the check below
        // rejected every one of them with 400 "Invalid User Agent". These endpoints carry no
        // sensitive data and are only reachable from localhost/the LAN (see application.yaml /
        // the monitoring stack's network binding), so skip this filter for them entirely rather
        // than special-casing the UA allowlist.
        if (path.startsWith("/actuator/")) return true;
        return false;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain) throws IOException {
        try {
            // Skip body caching for multipart requests (file uploads) to preserve the stream
            boolean isMultipart = request.getContentType() != null
                    && request.getContentType().toLowerCase().startsWith("multipart/");

            HttpServletRequest effectiveRequest;
            Map<String, Object> requestBody;
            Map<String, Object> requestHeader;
            if (isMultipart) {
                effectiveRequest = request;
                requestBody = new java.util.HashMap<>();
                requestHeader = new java.util.HashMap<>(ServletRequestUtil.header(request));
            } else {
                CachedBodyHttpServletRequest cachedBodyRequest = new CachedBodyHttpServletRequest(request);
                effectiveRequest = cachedBodyRequest;
                Map<String, Object> requestData = ServletRequestUtil.extractRequestData(cachedBodyRequest);
                requestHeader = convertUtil.objectToMap(requestData.get("header"));
                requestBody = convertUtil.objectToMap(requestData.get("body"));
            }

            if (requestHeader.get("user-agent") != null) {
                if (!validateUserAgent((String) requestHeader.get("user-agent"))) {
                    Response body = Response.builder().status(StatusConstant.FAILED).httpStatus(HttpStatus.BAD_REQUEST)
                            .errorCode("400").errorMessage("Invalid User Agent").build();
                    this.responseException(response, HttpServletResponse.SC_BAD_REQUEST, body);
                    return;
                }
            }

            response.setHeader("Set-Cookie", "SameSite=Strict; Secure; HttpOnly; Path=/");

            String requestId = commonUtil.generateRefNum();
            String method = commonUtil.checkString(effectiveRequest.getMethod());
            String url = commonUtil.checkString(effectiveRequest.getRequestURI());

            // >>> NEW: satukan payload agar tipe-nya konsisten untuk GET/POST
            Map<String, Object> requestPayload = convertUtil.unifyPayload(effectiveRequest, requestBody);

            // Set request id
            effectiveRequest.setAttribute("requestId", requestId);

            // Logging init
            loggingService.startDoFilterInternal("Authentication", method, url, request, requestPayload);

            // Exclude the specific URL (using startsWith for path patterns)
            String requestUri = effectiveRequest.getRequestURI();
            boolean isExcluded = Arrays.stream(EXCLUDE_URL).anyMatch(requestUri::startsWith);
            if (isExcluded) {
                filterChain.doFilter(effectiveRequest, response);
                return;
            }

            // === common read (GET/POST/DELETE sama) ===
            String authorization = commonUtil.checkString(requestHeader.get("authorization"));
            // String username = commonUtil.checkString(requestPayload.get("username")); //
            // Not reliable from payload for auth

            String token = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            }

            if (token == null || token.isEmpty()) {
                Response body = Response.builder().status(StatusConstant.FAILED).httpStatus(HttpStatus.UNAUTHORIZED)
                        .errorCode("401").errorMessage("Authorization token is required").build();
                this.responseException(response, HttpServletResponse.SC_UNAUTHORIZED, body);
                return;
            }

            // Check Blacklist
            if (redisService.hasKey("blacklist:" + token)) {
                Response body = Response.builder().status(StatusConstant.FAILED).httpStatus(HttpStatus.UNAUTHORIZED)
                        .errorCode("401").errorMessage("Token has been invalidated (logout)").build();
                this.responseException(response, HttpServletResponse.SC_UNAUTHORIZED, body);
                return;
            }

            // Validate Token
            String username = null;
            try {
                username = jwtService.extractUsername(token);
                if (!jwtService.isTokenValid(token, username)) {
                    throw new Exception("Invalid token");
                }
            } catch (Exception e) {
                Response body = Response.builder().status(StatusConstant.FAILED).httpStatus(HttpStatus.UNAUTHORIZED)
                        .errorCode("401").errorMessage("Invalid or expired token").build();
                this.responseException(response, HttpServletResponse.SC_UNAUTHORIZED, body);
                return;
            }

            // Set Security Context
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Extract roles from token
                @SuppressWarnings("unchecked")
                java.util.List<String> roles = jwtService.extractClaim(token,
                        claims -> claims.get("roles", java.util.List.class));
                java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();

                if (roles != null) {
                    for (Object role : roles) {
                        // Ensure role starts with ROLE_ if needed, or just use as is.
                        // Spring Security usually expects ROLE_ prefix for hasRole(), but
                        // hasAuthority() takes raw.
                        // Let's assume raw codes like "DIREKTUR" are used.
                        // If using @PreAuthorize("hasRole('DIREKTUR')"), Spring adds ROLE_.
                        // If using @PreAuthorize("hasAuthority('DIREKTUR')"), it keeps as is.
                        // Given I will likely use hasAuthority or PreAuthorize with full name if
                        // implementation varies.
                        // To be safe and standard with typical Spring Security, let's just add them as
                        // authorities.
                        // If the codes in DB are like "DIREKTUR", let's map them to "ROLE_DIREKTUR" if
                        // using hasRole.
                        // But for now, let's keep it raw and use hasAuthority('DIREKTUR') or customize.
                        // Actually, common practice: if roles are "ADMIN", map to "ROLE_ADMIN".
                        // However, checking previous context or preferences... existing roles seemed to
                        // be "DIREKTUR", etc.
                        // Let's just add "ROLE_" prefix here to be safe for hasRole usage.
                        // Wait, let's check if the raw strings already have prefixes. Usually not in
                        // the DB shown in DAFTAR_USER_DEMO_LENGKAP.
                        // Codes: DIREKTUR, MANAGER_KEUANGAN.
                        // So I will prefix them.
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString()));
                    }
                }

                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        username, null, authorities);
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(effectiveRequest));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);

                // Allow downstream to access user info if needed
                effectiveRequest.setAttribute("username", username);
            }

            filterChain.doFilter(effectiveRequest, response);
        } catch (Exception e) {
            loggingService.error("AuthenticationConfig doFilterInternal", e.getMessage(),
                    Arrays.toString(e.getStackTrace()));
            Response body = Response.builder().status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR).errorCode("500")
                    .errorMessage("Internal server error. Please try again later.").build();
            this.responseException(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, body);
        }
    }

    private void responseException(HttpServletResponse response, Integer status, Response body) throws IOException {
        loggingService.endDoFilterInternal(body);
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(convertUtil.objectToJsonString(body));
    }

    private boolean validateUserAgent(String userAgent) {
        return userAgent.contains("Mozilla") || userAgent.contains("Chrome") || userAgent.contains("Edg")
                || userAgent.contains("PostmanRuntime") || userAgent.contains("Dart") || userAgent.contains("Safari")
                || userAgent.contains("okhttp") || userAgent.contains("kube-probe");
    }

}

