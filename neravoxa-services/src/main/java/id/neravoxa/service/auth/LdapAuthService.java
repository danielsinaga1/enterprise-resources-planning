package id.neravoxa.service.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.stereotype.Service;

import id.neravoxa.dto.LdapUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around {@link ActiveDirectoryLdapAuthenticationProvider} that performs the
 * bind and returns the resolved {@link LdapUserInfo} principal.
 *
 * <p>The provider is injected through an {@link ObjectProvider} because it only exists when
 * {@code app.ldap.enabled=true}; this lets the bean wire cleanly even when LDAP is disabled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LdapAuthService {

    private final ObjectProvider<LdapAuthenticationProvider> providerProvider;

    /**
     * Authenticates the given credentials against Active Directory.
     *
     * @return the AD user info (attributes + group authorities) on success.
     * @throws IllegalStateException if LDAP is not enabled/configured.
     * @throws org.springframework.security.core.AuthenticationException if the bind fails
     *         (bad credentials, locked/disabled account, etc.).
     */
    public LdapUserInfo authenticate(String username, String password) {
        LdapAuthenticationProvider provider = providerProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("LDAP authentication is not enabled (app.ldap.enabled=false)");
        }

        Authentication authentication = provider.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        Object principal = authentication.getPrincipal();
        if (principal instanceof LdapUserInfo info) {
            return info;
        }

        // Should not happen given the configured context mapper, but stay defensive.
        log.warn("AD authentication returned unexpected principal type: {}",
                principal == null ? "null" : principal.getClass().getName());
        return LdapUserInfo.builder()
                .username(username)
                .authorities(authentication.getAuthorities())
                .build();
    }
}
