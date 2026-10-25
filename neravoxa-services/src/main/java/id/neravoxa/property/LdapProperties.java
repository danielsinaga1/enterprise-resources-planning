package id.neravoxa.property;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration for LDAP / Active Directory authentication.
 *
 * <p>When {@link #enabled} is {@code false} (the default), the application keeps using the
 * local bcrypt password flow in {@code AuthService}. When enabled, credentials are validated
 * against Active Directory and the matching local user record is used to issue the JWT.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.ldap")
public class LdapProperties {

    /** Master switch. When false, login falls back to the local password flow. */
    private boolean enabled = false;

    /** AD domain used to build the bind principal, e.g. {@code corp.example.com}. */
    private String domain;

    /** AD LDAP URL, e.g. {@code ldap://dc01.corp.example.com:389} (use ldaps:// for TLS). */
    private String url;

    /** Optional root/base DN. When blank, it is derived from {@link #domain}. */
    private String rootDn;

    /**
     * Service account used for the initial search-then-bind lookup (UPN or DN), e.g.
     * {@code svc-ldap@kpi.co.id}. Required because users log in with their employeeID, which
     * is not a bindable name -- the app must first search AD to resolve the real account.
     */
    private String managerDn;

    /** Password for {@link #managerDn}. */
    private String managerPassword;

    /** Search base for the user lookup, relative to the base DN. Blank = whole tree. */
    private String userSearchBase = "";

    /**
     * LDAP filter used to locate the account by the value typed at login ({@code {0}}).
     *
     * <p>Default menerima <b>NPK (employeeID) maupun username AD (sAMAccountName)</b>, sehingga
     * pengguna bisa masuk dengan salah satunya. Pencocokan ke tabel lokal tetap memakai employeeID
     * dari entri AD (lihat {@code userMatch}), jadi login lewat username pun tetap tersambung ke
     * record yang benar selama akun AD punya atribut employeeID.
     */
    private String userSearchFilter = "(|(employeeID={0})(sAMAccountName={0}))";

    /** How an AD account maps to the local {@code users} table: {@code npk}, {@code email}, or {@code employeeID}. */
    private String userMatch = "employeeID";

    /** Also merge roles assigned in the local {@code user_role} table (recommended). */
    private boolean mergeDbRoles = true;

    /**
     * Selaraskan hash bcrypt lokal dengan password AD setiap kali bind AD berhasil.
     *
     * <p>Tanpa ini hash lokal membeku pada nilai saat migrasi: begitu pengguna mengganti
     * password di AD, salinan lokalnya basi. Akibatnya fatal untuk akun yang masuk
     * {@link #localLoginNpks} — jalur bcrypt lokal menolak password AD yang baru dan pengguna
     * terkunci total. Dengan sinkronisasi, password terakhir yang terbukti benar di AD selalu
     * ikut tersimpan sehingga jalur lokal tetap sejalan.
     */
    private boolean syncPassword = true;

    /** Optional AD group (CN) to application role code mapping. */
    private Map<String, String> groupRoleMappings = new HashMap<>();

    /**
     * NPKs that exist in the local {@code users} table but NOT in Active Directory.
     * These accounts bypass the AD bind and authenticate via the local bcrypt password
     * flow even when {@link #enabled} is {@code true}.
     */
    private List<String> localLoginNpks = new ArrayList<>();
}
