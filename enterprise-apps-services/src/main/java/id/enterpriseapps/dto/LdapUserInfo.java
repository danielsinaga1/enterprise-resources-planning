package id.enterpriseapps.dto;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable view of the data returned by Active Directory after a successful bind.
 *
 * <p>Implements {@link UserDetails} so it can be carried as the authentication principal
 * out of {@code ActiveDirectoryLdapAuthenticationProvider}. The {@code authorities} are the
 * AD group memberships (derived from {@code memberOf}).
 */
@Getter
@Builder
public class LdapUserInfo implements UserDetails {

    /** sAMAccountName used to bind (i.e. the value the user typed). */
    private final String username;

    /** AD {@code employeeID} (matches the local {@code users.npk} column at KPI). */
    private final String employeeId;

    /** AD {@code displayName} (falls back to {@code cn}). */
    private final String displayName;

    /** AD {@code mail}. */
    private final String email;

    /** AD {@code department}. */
    private final String department;

    /** AD group memberships, as granted authorities. */
    @Builder.Default
    private final Collection<? extends GrantedAuthority> authorities = List.of();

    /**
     * @return the AD group names (authority strings) the user belongs to.
     */
    public List<String> getGroupNames() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    // --- UserDetails contract: AD owns the credentials, so there is no local password ---

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
