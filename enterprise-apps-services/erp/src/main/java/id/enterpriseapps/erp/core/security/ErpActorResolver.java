package id.enterpriseapps.erp.core.security;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.User;
import id.enterpriseapps.erp.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menentukan {@link ErpActor} dari SecurityContext platform (subject JWT = email, authority
 * {@code ROLE_<kode>}) dan menjaga snapshot pengguna di tabel {@code users} db_erp.
 *
 * <p>Pengguna diprovisi otomatis pada akses ERP pertama (nama/NPK/atasan diambil lewat
 * {@link ErpIdentityPort} bila tersedia). Snapshot role diperbarui bila role di JWT berubah.
 * Hasil di-cache per email selama {@link #TTL} agar tidak menulis DB di setiap request.
 */
@Slf4j
@Component
public class ErpActorResolver {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final UserRepository users;
    private final ErpPermissionService permissions;
    private final ObjectProvider<ErpIdentityPort> identity;
    private final TransactionTemplate requiresNew;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(ErpActor actor, String rolesKey, Instant expiresAt) {
    }

    public ErpActorResolver(UserRepository users,
                            ErpPermissionService permissions,
                            ObjectProvider<ErpIdentityPort> identity,
                            @Qualifier(ErpTx.MANAGER) PlatformTransactionManager txManager) {
        this.users = users;
        this.permissions = permissions;
        this.identity = identity;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Aktor saat ini; 401 bila tidak ada autentikasi. */
    public ErpActor current() {
        return currentOptional().orElseThrow(() ->
                ErpException.of(HttpStatus.UNAUTHORIZED, "Akses ditolak: Token tidak ditemukan."));
    }

    public Optional<ErpActor> currentOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || "anonymousUser".equals(auth.getName())) {
            return Optional.empty();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority a : auth.getAuthorities()) {
            String s = a.getAuthority();
            if (s != null && s.startsWith("ROLE_")) {
                roles.add(s.substring(5));
            }
        }
        return Optional.of(resolve(auth.getName(), roles));
    }

    /** Lupakan cache (mis. setelah admin mengubah profil pengguna ERP). */
    public void evict(String email) {
        if (email == null) {
            cache.clear();
        } else {
            cache.remove(email.toLowerCase());
        }
    }

    public ErpActor resolve(String email, Set<String> roleKeys) {
        String key = email.toLowerCase();
        String rolesKey = String.join(",", roleKeys);
        Cached c = cache.get(key);
        if (c != null && c.rolesKey().equals(rolesKey) && c.expiresAt().isAfter(Instant.now())) {
            return c.actor();
        }
        User u = requiresNew.execute(status -> upsert(email, roleKeys));
        if (u == null) {
            throw ErpException.of(HttpStatus.UNAUTHORIZED, "Akses ditolak: pengguna ERP tidak dapat dipastikan.");
        }
        if (!Boolean.TRUE.equals(u.getIsActive())) {
            throw ErpException.forbidden("Akun ERP Anda dinonaktifkan. Hubungi administrator.");
        }
        ErpActor actor = new ErpActor(u.getId(), u.getEmail(), u.getFullName(), u.getRole(),
                Set.copyOf(new LinkedHashSet<>(roleKeys)), u.getPlatformUserId());
        cache.put(key, new Cached(actor, rolesKey, Instant.now().plus(TTL)));
        return actor;
    }

    private User upsert(String email, Set<String> roleKeys) {
        String primary = permissions.primaryRole(roleKeys);
        String rolesCsv = String.join(",", roleKeys);
        Optional<User> existing = findByEmail(email);
        if (existing.isPresent()) {
            User u = existing.get();
            boolean dirty = false;
            if (!rolesCsv.equals(u.getRoles())) {
                u.setRoles(rolesCsv);
                dirty = true;
            }
            if (!primary.equals(u.getRole())) {
                u.setRole(primary);
                dirty = true;
            }
            Instant now = Instant.now();
            if (u.getLastLoginAt() == null || u.getLastLoginAt().isBefore(now.minus(Duration.ofMinutes(5)))) {
                u.setLastLoginAt(now);
                dirty = true;
            }
            return dirty ? users.save(u) : u;
        }
        ErpIdentityPort.PlatformUser pu = lookup(email);
        User u = new User();
        u.setEmail(email);
        u.setFullName(pu != null && pu.name() != null && !pu.name().isBlank() ? pu.name() : localPart(email));
        u.setNpk(pu == null ? null : pu.npk());
        u.setPlatformUserId(pu == null ? null : pu.id());
        u.setUsername(uniqueUsername(pu != null && pu.npk() != null && !pu.npk().isBlank() ? pu.npk() : localPart(email)));
        u.setRole(primary);
        u.setRoles(rolesCsv);
        u.setIsActive(true);
        u.setLastLoginAt(Instant.now());
        if (pu != null && pu.parentId() != null) {
            u.setSupervisorId(resolveSupervisorId(pu.parentId()));
        }
        try {
            return users.saveAndFlush(u);
        } catch (DataIntegrityViolationException race) {
            // Permintaan paralel pertama dari pengguna yang sama: baris sudah dibuat proses lain.
            return findByEmail(email).orElse(null);
        }
    }

    private String resolveSupervisorId(Long parentPlatformId) {
        try {
            ErpIdentityPort port = identity.getIfAvailable();
            if (port == null) {
                return null;
            }
            Optional<ErpIdentityPort.PlatformUser> parent = port.findById(parentPlatformId);
            if (parent.isEmpty() || parent.get().email() == null) {
                return null;
            }
            Optional<User> sup = findByEmail(parent.get().email());
            if (sup.isPresent()) {
                return sup.get().getId();
            }
            ErpIdentityPort.PlatformUser p = parent.get();
            User s = new User();
            s.setEmail(p.email());
            s.setFullName(p.name() == null || p.name().isBlank() ? localPart(p.email()) : p.name());
            s.setNpk(p.npk());
            s.setPlatformUserId(p.id());
            s.setUsername(uniqueUsername(p.npk() != null && !p.npk().isBlank() ? p.npk() : localPart(p.email())));
            List<String> pr = p.roles() == null ? List.of() : p.roles();
            s.setRoles(String.join(",", pr));
            s.setRole(permissions.primaryRole(pr));
            s.setIsActive(p.active());
            return users.saveAndFlush(s).getId();
        } catch (RuntimeException e) {
            log.warn("ERP: gagal menautkan atasan platform {}: {}", parentPlatformId, e.getMessage());
            return null;
        }
    }

    private ErpIdentityPort.PlatformUser lookup(String email) {
        try {
            ErpIdentityPort port = identity.getIfAvailable();
            return port == null ? null : port.findByEmail(email).orElse(null);
        } catch (RuntimeException e) {
            log.warn("ERP: lookup identitas platform gagal untuk {}: {}", email, e.getMessage());
            return null;
        }
    }

    private Optional<User> findByEmail(String email) {
        Specification<User> spec = (root, q, cb) -> cb.equal(cb.lower(root.get("email")), email.toLowerCase());
        return users.findAll(spec).stream().findFirst();
    }

    private String uniqueUsername(String base) {
        String candidate = base;
        int n = 2;
        while (true) {
            String c = candidate;
            boolean taken = users.exists((root, q, cb) -> cb.equal(root.get("username"), c));
            if (!taken) {
                return candidate;
            }
            candidate = base + n++;
        }
    }

    private static String localPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /** Untuk proses latar (scheduler): aktor sistem tanpa role. */
    @ErpTx(readOnly = true)
    public Optional<User> findUser(String userId) {
        return userId == null ? Optional.empty() : users.findById(userId);
    }
}
