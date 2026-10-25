package id.neravoxa.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;

import id.neravoxa.dto.LdapUserInfo;

/**
 * Maps the Active Directory entry returned after a successful bind into an
 * {@link LdapUserInfo} principal, capturing the attributes the application cares about
 * (displayName, mail, department) along with the AD group authorities.
 */
public class AdUserDetailsContextMapper implements UserDetailsContextMapper {

    @Override
    public UserDetails mapUserFromContext(DirContextOperations ctx, String username,
            Collection<? extends GrantedAuthority> authorities) {

        String displayName = ctx.getStringAttribute("displayName");
        if (displayName == null || displayName.isBlank()) {
            displayName = ctx.getStringAttribute("cn");
        }

        // Collect AD group memberships. Use any authorities already supplied by the provider,
        // plus the user's own memberOf attribute (the source of truth in Active Directory).
        Set<GrantedAuthority> groups = new LinkedHashSet<>();
        if (authorities != null) {
            groups.addAll(authorities);
        }
        String[] memberOf = ctx.getStringAttributes("memberOf");
        if (memberOf != null) {
            for (String groupDn : memberOf) {
                groups.add(new SimpleGrantedAuthority(groupDn));
            }
        }

        return LdapUserInfo.builder()
                .username(username)
                .employeeId(ctx.getStringAttribute("employeeID"))
                .displayName(displayName)
                .email(ctx.getStringAttribute("mail"))
                .department(ctx.getStringAttribute("department"))
                .authorities(groups)
                .build();
    }

    @Override
    public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {
        // The application never writes back to Active Directory.
        throw new UnsupportedOperationException("Writing to Active Directory is not supported");
    }
}
