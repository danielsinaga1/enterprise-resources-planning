package id.enterpriseapps.service.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import id.enterpriseapps.auth.entity.AuthUser;
import id.enterpriseapps.auth.entity.AuthUserRole;
import id.enterpriseapps.auth.repository.AuthUserRepository;
import id.enterpriseapps.auth.repository.AuthUserRoleRepository;
import id.enterpriseapps.dto.LoginRequest;
import id.enterpriseapps.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import id.enterpriseapps.service.JwtService;
import id.enterpriseapps.service.RedisService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final AuthUserRoleRepository authUserRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisService redisService;
    private final id.enterpriseapps.property.LdapProperties ldapProperties;
    private final LdapAuthService ldapAuthService;
    private final id.enterpriseapps.observability.AuthMetrics authMetrics;

    /**
     * Entry point for authentication. Dispatches to Active Directory when LDAP is enabled,
     * otherwise uses the local bcrypt password flow. Every attempt is counted in the
     * {@code auth.login.attempts} metric (tagged by method + outcome).
     */
    public LoginResponse login(LoginRequest request) {
        // Rapikan NPK/username: spasi tak sengaja (mis. hasil paste atau autofill) di ujung input
        // membuat filter LDAP tidak menemukan entri apa pun — terbukti di log ("2502426 " gagal,
        // "2502426" berhasil). Password tidak di-trim: spasi bisa jadi bagian sah dari password.
        if (request.getNpk() != null) {
            request.setNpk(request.getNpk().trim());
        }

        // Some accounts exist in the local DB but not in Active Directory. When their NPK is in
        // the allowlist they skip the AD bind and use the local bcrypt flow, even with LDAP on.
        boolean useLdap = ldapProperties.isEnabled() && !isLocalLoginNpk(request.getNpk());
        String method = useLdap ? "ldap" : "local";
        LoginResponse response = useLdap ? ldapLogin(request) : localLogin(request);
        authMetrics.recordLogin(method, response != null && response.isSuccess());
        return response;
    }

    /**
     * True when the given NPK is explicitly allowed to authenticate via the local password flow
     * (configured by {@code app.ldap.local-login-npks}). Used for accounts absent from AD.
     */
    private boolean isLocalLoginNpk(String npk) {
        if (npk == null) {
            return false;
        }
        java.util.List<String> allow = ldapProperties.getLocalLoginNpks();
        if (allow == null || allow.isEmpty()) {
            return false;
        }
        String target = npk.trim();
        return allow.stream().anyMatch(n -> n != null && n.trim().equals(target));
    }

    /**
     * Resolves a display label (code, falling back to title) for a user-role assignment.
     * {@code ur.getRole()} is a lazy proxy over {@code role_id}; if that id no longer exists in
     * auth_roles (a stale assignment left behind after a role was deleted, or bad legacy data),
     * Hibernate throws EntityNotFoundException the moment the proxy is touched — not when it is
     * fetched. An unguarded {@code ur.getRole() != null} check does NOT catch this: the proxy
     * itself is never null. Without this guard, one bad row here failed login entirely for the
     * affected user (caught live: auth_user_roles had a row pointing at a role_id that had
     * already been deleted).
     * Returns null for a broken reference so the caller's null-filter drops it.
     */
    private String resolveRoleLabel(AuthUserRole ur) {
        try {
            String code = ur.getRole().getCode();
            return (code != null && !code.isBlank()) ? code : ur.getRole().getTitle();
        } catch (jakarta.persistence.EntityNotFoundException e) {
            log.warn("auth_user_roles id={} references missing role_id={} — skipping",
                    ur.getId(), ur.getRoleId());
            return null;
        }
    }

    /**
     * Local authentication: validates the supplied password against the bcrypt hash stored
     * in {@code auth_users}. Used when {@code app.ldap.enabled=false}.
     */
    private LoginResponse localLogin(LoginRequest request) {
        try {
            log.info("Login attempt for NPK: {}", request.getNpk());

            Optional<AuthUser> userOpt = authUserRepository.findByNpk(request.getNpk());

            if (userOpt.isEmpty()) {
                log.warn("User not found: {}", request.getNpk());
                return LoginResponse.builder()
                        .success(false)
                        .message("NPK atau password salah")
                        .build();
            }

            AuthUser user = userOpt.get();

            if (user.getStatus() == null || user.getStatus() != 1) {
                log.warn("User inactive: {}", request.getNpk());
                return LoginResponse.builder()
                        .success(false)
                        .message("Akun tidak aktif. Hubungi administrator.")
                        .build();
            }

            boolean passwordMatch = false;
            try {
                passwordMatch = passwordEncoder.matches(request.getPassword(), user.getPassword());
            } catch (Exception e) {
                log.error("Error matching password: {}", e.getMessage(), e);
            }

            if (!passwordMatch) {
                log.warn("Invalid password for user: {}", request.getNpk());
                user.setAttemptLoginFailed(
                        user.getAttemptLoginFailed() == null ? 1 : user.getAttemptLoginFailed() + 1);
                user.setLastLoginFailed(LocalDateTime.now());
                authUserRepository.save(user);
                return LoginResponse.builder()
                        .success(false)
                        .message("NPK atau password salah")
                        .build();
            }

            user.setLastLogin(LocalDateTime.now());
            user.setAttemptLoginFailed(0);
            authUserRepository.save(user);

            log.info("Login successful for user: {}", request.getNpk());

            java.util.List<String> roles = authUserRoleRepository.findActiveRolesByUserId(user.getId())
                    .stream()
                    .map(this::resolveRoleLabel)
                    .filter(r -> r != null)
                    .collect(java.util.stream.Collectors.toList());

            java.util.Map<String, Object> claims = new java.util.HashMap<>();
            claims.put("roles", roles);

            String accessToken = jwtService.generateAccessToken(claims, user.getEmail());
            String refreshToken = jwtService.generateRefreshToken(user.getEmail());

            redisService.save("refresh_token:" + refreshToken, user.getEmail(), jwtService.getRefreshExpiration());

            return LoginResponse.builder()
                    .success(true)
                    .message("Login berhasil")
                    .id(user.getId())
                    .npp(user.getNpp())
                    .npk(user.getNpk())
                    .name(user.getName())
                    .email(user.getEmail())
                    .mobilePhone(user.getMobilePhone())
                    .departmentId(user.getDeptId())
                    .token(accessToken)
                    .refreshToken(refreshToken)
                    .roles(roles)
                    .build();
        } catch (Exception e) {
            log.error("Login error: {}", e.getMessage(), e);
            return LoginResponse.builder()
                    .success(false)
                    .message("Terjadi kesalahan: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Active Directory authentication. Binds against AD with the supplied credentials, then
     * matches the AD account to a local {@code auth_users} record to reuse the existing JWT and
     * role machinery. Used when {@code app.ldap.enabled=true}.
     */
    private LoginResponse ldapLogin(LoginRequest request) {
        String npk = request.getNpk();
        log.info("LDAP login attempt for: {}", npk);

        id.enterpriseapps.dto.LdapUserInfo adUser;
        try {
            adUser = ldapAuthService.authenticate(npk, request.getPassword());
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            // Pencarian AD tidak menemukan entri apa pun — password belum sempat diuji.
            // Penyebab tersering: atribut employeeID di AD kosong/berbeda dengan NPK, atau
            // akun memang hanya ada di database lokal (seharusnya masuk app.ldap.local-login-npks).
            log.warn("AD search menemukan 0 entri untuk '{}' (filter={}, base='{}'). "
                    + "Periksa atribut employeeID akun ini di AD.",
                    npk, ldapProperties.getUserSearchFilter(), ldapProperties.getUserSearchBase());
            return LoginResponse.builder()
                    .success(false)
                    .message("NPK tidak terdaftar di Active Directory. Hubungi administrator.")
                    .build();
        } catch (org.springframework.security.core.AuthenticationException e) {
            // Bind ditolak AD. Kode "data <hex>" di rantai penyebab memuat alasan sebenarnya —
            // tanpa ini semua kasus tampak sebagai "password salah" dan mustahil dilacak.
            AdAuthError reason = AdAuthError.from(e);
            log.warn("AD bind ditolak untuk {}: {} (kode AD={}, detail={})",
                    npk, reason.name(), reason.getCode(), AdAuthError.rootMessage(e));
            return LoginResponse.builder()
                    .success(false)
                    .message(reason.getUserMessage())
                    .build();
        } catch (Exception e) {
            log.error("LDAP error during login for {}: {}", npk, e.getMessage(), e);
            return LoginResponse.builder()
                    .success(false)
                    .message("Layanan Active Directory tidak tersedia. Hubungi administrator.")
                    .build();
        }

        String matchMode = ldapProperties.getUserMatch() == null ? "npk"
                : ldapProperties.getUserMatch().trim().toLowerCase();
        Optional<AuthUser> userOpt;
        switch (matchMode) {
            case "email" -> userOpt = authUserRepository.findByEmail(
                    adUser.getEmail() != null ? adUser.getEmail() : npk);
            case "employeeid" -> userOpt = authUserRepository.findByNpk(
                    adUser.getEmployeeId() != null ? adUser.getEmployeeId() : npk);
            default -> userOpt = authUserRepository.findByNpk(npk);
        }

        if (userOpt.isEmpty()) {
            log.warn("AD user '{}' authenticated but no local record found (match={}, employeeID={})",
                    npk, matchMode, adUser.getEmployeeId());
            return LoginResponse.builder()
                    .success(false)
                    .message("User belum terdaftar di sistem. Hubungi administrator.")
                    .build();
        }

        AuthUser user = userOpt.get();

        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("AD user inactive in local system: {}", npk);
            return LoginResponse.builder()
                    .success(false)
                    .message("Akun tidak aktif. Hubungi administrator.")
                    .build();
        }

        if (adUser.getDisplayName() != null && !adUser.getDisplayName().isBlank()) {
            user.setName(adUser.getDisplayName());
        }
        if (adUser.getEmail() != null && !adUser.getEmail().isBlank()) {
            user.setEmail(adUser.getEmail());
        }
        syncLocalPassword(user, request.getPassword());
        user.setLastLogin(LocalDateTime.now());
        user.setAttemptLoginFailed(0);
        authUserRepository.save(user);

        log.info("LDAP login successful for: {}", npk);

        java.util.List<String> roles = resolveRoles(user, adUser);

        return issueTokens(user, roles);
    }

    /**
     * Menyelaraskan hash bcrypt lokal dengan password AD yang baru saja terbukti benar.
     *
     * <p>Hanya dipanggil setelah bind AD berhasil, jadi yang disimpan pasti password yang sah.
     * Tanpa langkah ini hash lokal membeku pada nilai saat migrasi; begitu pengguna mengganti
     * password di AD, jalur bcrypt lokal (dipakai oleh {@code app.ldap.local-login-npks} dan saat
     * AD dimatikan) masih menagih password lama dan pengguna terkunci total.
     *
     * <p>Penulisan dilewati bila hash yang tersimpan sudah cocok, supaya login normal tidak
     * menghasilkan UPDATE yang tidak perlu.
     */
    private void syncLocalPassword(AuthUser user, String rawPassword) {
        if (!ldapProperties.isSyncPassword() || rawPassword == null || rawPassword.isBlank()) {
            return;
        }
        try {
            if (user.getPassword() != null && !user.getPassword().isBlank()
                    && passwordEncoder.matches(rawPassword, user.getPassword())) {
                return;
            }
        } catch (Exception e) {
            // Hash lama kosong atau berformat lain (mis. sisa sistem lama) — tulis ulang saja.
            log.debug("Hash lokal {} tidak bisa dibandingkan, akan ditulis ulang: {}",
                    user.getNpk(), e.getMessage());
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        log.info("Password lokal untuk {} diselaraskan dengan Active Directory", user.getNpk());
    }

    /**
     * Combines the user's locally assigned roles (when {@code mergeDbRoles} is on) with any
     * roles derived from AD group membership via {@code groupRoleMappings}. Order is preserved
     * and duplicates removed.
     */
    private java.util.List<String> resolveRoles(AuthUser user, id.enterpriseapps.dto.LdapUserInfo adUser) {
        java.util.Set<String> roles = new java.util.LinkedHashSet<>();

        if (ldapProperties.isMergeDbRoles()) {
            authUserRoleRepository.findActiveRolesByUserId(user.getId()).stream()
                    .map(this::resolveRoleLabel)
                    .filter(r -> r != null)
                    .forEach(roles::add);
        }

        java.util.Map<String, String> mappings = ldapProperties.getGroupRoleMappings();
        if (mappings != null && !mappings.isEmpty()) {
            for (String group : adUser.getGroupNames()) {
                String groupCn = extractCn(group);
                mappings.entrySet().stream()
                        .filter(e -> e.getKey().equalsIgnoreCase(groupCn))
                        .map(java.util.Map.Entry::getValue)
                        .forEach(roles::add);
            }
        }

        return new java.util.ArrayList<>(roles);
    }

    /**
     * Extracts the CN component from an AD authority string. Authorities may arrive either as
     * a bare group name or as a full distinguished name (e.g. {@code CN=IT-Admins,OU=...}).
     */
    private String extractCn(String authority) {
        if (authority == null) {
            return null;
        }
        String upper = authority.toUpperCase();
        int cnIdx = upper.indexOf("CN=");
        if (cnIdx < 0) {
            return authority;
        }
        int start = cnIdx + 3;
        int comma = authority.indexOf(',', start);
        return (comma < 0 ? authority.substring(start) : authority.substring(start, comma)).trim();
    }

    /**
     * Issues the access/refresh token pair for an authenticated user and persists the refresh
     * token in Redis. Shared by both the local and LDAP login flows.
     */
    private LoginResponse issueTokens(AuthUser user, java.util.List<String> roles) {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("roles", roles);

        String accessToken = jwtService.generateAccessToken(claims, user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        redisService.save("refresh_token:" + refreshToken, user.getEmail(), jwtService.getRefreshExpiration());

        return LoginResponse.builder()
                .success(true)
                .message("Login berhasil")
                .id(user.getId())
                .npp(user.getNpp())
                .npk(user.getNpk())
                .name(user.getName())
                .email(user.getEmail())
                .mobilePhone(user.getMobilePhone())
                .departmentId(user.getDeptId())
                .token(accessToken)
                .refreshToken(refreshToken)
                .roles(roles)
                .build();
    }

    public LoginResponse refreshToken(id.enterpriseapps.dto.RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        try {
            String username = jwtService.extractUsername(requestRefreshToken);
            if (!jwtService.isTokenValid(requestRefreshToken, username)) {
                throw new Exception("Invalid Token Signature");
            }

            String redisKey = "refresh_token:" + requestRefreshToken;
            if (!redisService.hasKey(redisKey)) {
                log.warn("Refresh Token not found in Redis (or expired/revoked): {}", username);
                throw new Exception("Refresh Token Invalid or Revoked");
            }

            String storedEmail = redisService.get(redisKey);
            if (!storedEmail.equals(username)) {
                log.warn("Refresh token ownership mismatch. Request: {}, Stored: {}", username, storedEmail);
                throw new Exception("Invalid Refresh Token");
            }

            Optional<AuthUser> userOpt = authUserRepository.findByEmail(username);
            if (userOpt.isEmpty()) {
                throw new Exception("User Not Found");
            }
            AuthUser user = userOpt.get();

            redisService.delete(redisKey);

            java.util.List<String> roles = authUserRoleRepository.findActiveRolesByUserId(user.getId())
                    .stream()
                    .map(this::resolveRoleLabel)
                    .filter(r -> r != null)
                    .collect(java.util.stream.Collectors.toList());

            java.util.Map<String, Object> claims = new java.util.HashMap<>();
            claims.put("roles", roles);

            String newAccessToken = jwtService.generateAccessToken(claims, username);
            String newRefreshToken = jwtService.generateRefreshToken(username);

            redisService.save("refresh_token:" + newRefreshToken, username, jwtService.getRefreshExpiration());

            return LoginResponse.builder()
                    .success(true)
                    .message("Token Refreshed Successfully")
                    .id(user.getId())
                    .npp(user.getNpp())
                    .npk(user.getNpk())
                    .name(user.getName())
                    .email(user.getEmail())
                    .mobilePhone(user.getMobilePhone())
                    .token(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .roles(roles)
                    .build();

        } catch (Exception e) {
            log.error("Refresh Token Failed: {}", e.getMessage());
            return LoginResponse.builder()
                    .success(false)
                    .message("Refresh Token Failed: " + e.getMessage())
                    .build();
        }
    }
}
