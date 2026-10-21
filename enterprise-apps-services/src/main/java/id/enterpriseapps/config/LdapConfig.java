package id.enterpriseapps.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;

import id.enterpriseapps.property.LdapProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires LDAP authentication using a <b>search-then-bind</b> strategy so users can log in with
 * their employeeID (NPK) rather than their AD account name:
 *
 * <ol>
 *   <li>A service account ({@code app.ldap.manager-dn}) searches AD for the entry matching
 *       {@code app.ldap.user-search-filter} (default {@code (employeeID={0})}).</li>
 *   <li>The application then binds as the located account using the password the user supplied,
 *       which validates the credentials against Active Directory.</li>
 * </ol>
 *
 * <p>The bean is only created when {@code app.ldap.enabled=true}, so environments without a
 * reachable AD keep using the local password flow.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LdapConfig {

    private final LdapProperties ldapProperties;

    @Bean
    @ConditionalOnProperty(prefix = "app.ldap", name = "enabled", havingValue = "true")
    public LdapAuthenticationProvider ldapAuthenticationProvider() {
        String baseDn = resolveBaseDn();

        DefaultSpringSecurityContextSource contextSource =
                new DefaultSpringSecurityContextSource(List.of(ldapProperties.getUrl()), baseDn);
        contextSource.setUserDn(ldapProperties.getManagerDn());
        contextSource.setPassword(ldapProperties.getManagerPassword());
        // AD often returns referrals when users live in sub-domains or different OUs.
        // Without "follow", Spring LDAP throws PartialResultException and login appears broken.
        contextSource.setReferral("follow");
        // Disable connection pooling: AD bind-per-user doesn't play well with pooled contexts.
        contextSource.setPooled(false);
        contextSource.afterPropertiesSet();

        FilterBasedLdapUserSearch userSearch = new FilterBasedLdapUserSearch(
                ldapProperties.getUserSearchBase(), ldapProperties.getUserSearchFilter(), contextSource);
        userSearch.setSearchSubtree(true);

        // Bukan BindAuthenticator biasa: yang bawaan membuang alasan penolakan AD sebelum
        // sempat dibaca, sehingga semua kegagalan tampak sebagai "Bad credentials".
        BindAuthenticator authenticator = new DiagnosticBindAuthenticator(contextSource);
        authenticator.setUserSearch(userSearch);

        LdapAuthenticationProvider provider = new LdapAuthenticationProvider(authenticator);
        provider.setUserDetailsContextMapper(new AdUserDetailsContextMapper());
        // Secara bawaan Spring menyamarkan UsernameNotFoundException menjadi "Bad credentials",
        // sehingga "employeeID tidak ketemu di AD" tidak bisa dibedakan dari "password salah".
        // Keduanya butuh penanganan yang sangat berbeda, jadi biarkan exception aslinya lewat.
        provider.setHideUserNotFoundExceptions(false);

        log.info("LDAP authentication ENABLED (search-then-bind; url={}, baseDn={}, filter={})",
                ldapProperties.getUrl(), baseDn, ldapProperties.getUserSearchFilter());
        return provider;
    }

    /**
     * Returns the configured root DN, or derives one from the domain
     * (e.g. {@code kpi.co.id} -> {@code dc=kpi,dc=co,dc=id}).
     */
    private String resolveBaseDn() {
        String rootDn = ldapProperties.getRootDn();
        if (rootDn != null && !rootDn.isBlank()) {
            return rootDn;
        }
        return Arrays.stream(ldapProperties.getDomain().split("\\."))
                .map(part -> "dc=" + part)
                .collect(Collectors.joining(","));
    }
}
