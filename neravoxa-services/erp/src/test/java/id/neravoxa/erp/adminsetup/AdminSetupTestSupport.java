package id.neravoxa.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.security.ErpIdentityPort;
import id.neravoxa.erp.core.security.ErpIdentityPort.PlatformRole;
import id.neravoxa.erp.core.security.ErpIdentityPort.PlatformUser;
import id.neravoxa.erp.support.ErpIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Basis uji paket adminsetup: direktori platform palsu (in-memory) di balik {@link ErpIdentityPort}.
 *
 * <p>Port dipasang lewat {@code @MockitoBean} (bukan {@code @Component} di paket test) supaya hanya
 * konteks uji paket ini yang memakainya — konteks uji paket lain tetap berjalan tanpa adapter identitas.
 */
abstract class AdminSetupTestSupport extends ErpIntegrationTest {

    static final String ADMIN_EMAIL = "erp.admin@test.local";

    @MockitoBean
    protected ErpIdentityPort identity;

    /** Akun platform berdasarkan id. */
    static final Map<Long, PlatformUser> DIRECTORY = new ConcurrentHashMap<>();
    /** Cermin akun tanpa id platform (snapshot ERP lama yang hanya dikenal lewat email). */
    static final List<PlatformUser> MIRRORS = new CopyOnWriteArrayList<>();
    static final List<PlatformRole> PLATFORM_ROLES = new CopyOnWriteArrayList<>(List.of(
            new PlatformRole("SUPER_ADMIN", "Super Admin"),
            new PlatformRole("ERP_ADMIN", "Admin ERP"),
            new PlatformRole("ERP_ACCOUNTANT", "Akuntan / Keuangan"),
            new PlatformRole("ERP_PRODUCTION", "Staff Produksi"),
            new PlatformRole("ERP_VIEWER", "Viewer ERP"),
            new PlatformRole("HR_STAFF", "Staf HR")));
    private static final AtomicLong SEQ = new AtomicLong(7000);

    static {
        // Admin uji ada di direktori platform (seperti produksi) agar /users/sync tidak menonaktifkannya.
        // Nama sama dengan provisi bawaan ErpIntegrationTest: DB dipakai bersama paket lain yang memeriksa nama ini.
        platformUser("erp.admin", null, ADMIN_EMAIL, null, true, "SUPER_ADMIN");
    }

    @BeforeEach
    void stubDirectory() {
        when(identity.findByEmail(any())).thenAnswer(inv -> byEmail(inv.getArgument(0)));
        when(identity.findById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return id == null ? Optional.empty() : Optional.ofNullable(DIRECTORY.get(id));
        });
        when(identity.search(any(), anyInt())).thenAnswer(inv -> search(inv.getArgument(0), inv.getArgument(1)));
        when(identity.listRoles()).thenAnswer(inv -> List.copyOf(PLATFORM_ROLES));
    }

    static Optional<PlatformUser> byEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        Optional<PlatformUser> u = DIRECTORY.values().stream()
                .filter(p -> p.email() != null && p.email().equalsIgnoreCase(email)).findFirst();
        return u.isPresent() ? u : MIRRORS.stream().filter(p -> p.email().equalsIgnoreCase(email)).findFirst();
    }

    /** Seperti ErpIdentityAdapter.search: hanya akun aktif, cocok nama/email/NPK, urut nama. */
    static List<PlatformUser> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return DIRECTORY.values().stream()
                .filter(PlatformUser::active)
                .filter(p -> q.isEmpty()
                        || (p.name() != null && p.name().toLowerCase(Locale.ROOT).contains(q))
                        || (p.email() != null && p.email().toLowerCase(Locale.ROOT).contains(q))
                        || (p.npk() != null && p.npk().toLowerCase(Locale.ROOT).contains(q)))
                .sorted(Comparator.comparing(PlatformUser::name))
                .limit(Math.max(1, Math.min(limit, 100)))
                .toList();
    }

    static PlatformUser platformUser(String name, String npk, String email, Long parentId, boolean active, String... roles) {
        PlatformUser u = new PlatformUser(SEQ.incrementAndGet(), npk, name, email, parentId, active, List.of(roles));
        DIRECTORY.put(u.id(), u);
        return u;
    }

    /** Ganti data akun platform (id tetap). */
    static PlatformUser changePlatform(PlatformUser u, String name, String npk, String email, Long parentId,
                                       boolean active, String... roles) {
        PlatformUser n = new PlatformUser(u.id(), npk, name, email, parentId, active, List.of(roles));
        DIRECTORY.put(n.id(), n);
        return n;
    }

    static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    String erpUserId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?)", String.class, email);
    }

    /** Login (subject JWT = email) dan pastikan snapshot ERP-nya terprovisi; kembalikan id pengguna ERP. */
    String login(String email, String... roles) throws Exception {
        asUser(email, roles);
        ok(get("/api/erp/me"));
        return erpUserId(email);
    }

    String adminId() throws Exception {
        return login(ADMIN_EMAIL, "SUPER_ADMIN");
    }

    /** Pastikan respons error: status + pesan persis. */
    void assertError(Result r, int status, String message) {
        assertEquals(status, r.status(), () -> "body: " + r.body());
        assertEquals(message, r.error(), () -> "body: " + r.body());
    }

    /**
     * Daftarkan semua pengguna ERP yang belum dikenal direktori palsu sebagai akun platform aktif dengan
     * data snapshot yang sama — database dipakai bersama test lain, dan sinkron tidak boleh mengubah
     * pengguna milik test lain.
     */
    void mirrorUnknownErpUsers() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT email, full_name, npk, roles, platform_user_id FROM users");
        for (Map<String, Object> r : rows) {
            String email = (String) r.get("email");
            if (byEmail(email).isPresent()) {
                continue;
            }
            String csv = (String) r.get("roles");
            List<String> roles = csv == null || csv.isBlank() ? List.of() : List.of(csv.split(","));
            Long pid = r.get("platform_user_id") == null ? null : ((Number) r.get("platform_user_id")).longValue();
            PlatformUser p = new PlatformUser(pid, (String) r.get("npk"), (String) r.get("full_name"), email, null, true,
                    new ArrayList<>(roles));
            if (pid != null) {
                DIRECTORY.put(pid, p);
            } else {
                MIRRORS.add(p);
            }
        }
    }

    static List<String> texts(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.path(field).asText(null)));
        return out;
    }
}
