package id.enterpriseapps.service;

import id.enterpriseapps.auth.entity.AuthUser;
import id.enterpriseapps.auth.entity.AuthUserRole;
import id.enterpriseapps.auth.repository.AuthRoleRepository;
import id.enterpriseapps.auth.repository.AuthUserRepository;
import id.enterpriseapps.auth.repository.AuthUserRoleRepository;
import id.enterpriseapps.erp.core.security.ErpIdentityPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapter direktori pengguna platform (db_auth) untuk modul ERP — implementasi
 * {@link ErpIdentityPort}. Modul :erp tidak boleh bergantung balik ke aplikasi, jadi
 * aksesnya lewat port ini (pola yang sama dengan WorkflowMailAdapter).
 */
@Component
@RequiredArgsConstructor
public class ErpIdentityAdapter implements ErpIdentityPort {

    private final AuthUserRepository users;
    private final AuthUserRoleRepository userRoles;
    private final AuthRoleRepository roles;

    @Override
    @Transactional(transactionManager = "authTransactionManager", readOnly = true)
    public Optional<PlatformUser> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return users.findByEmail(email).map(this::toPlatformUser);
    }

    @Override
    @Transactional(transactionManager = "authTransactionManager", readOnly = true)
    public Optional<PlatformUser> findById(Long id) {
        return id == null ? Optional.empty() : users.findById(id).map(this::toPlatformUser);
    }

    @Override
    @Transactional(transactionManager = "authTransactionManager", readOnly = true)
    public List<PlatformUser> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase();
        Specification<AuthUser> spec = (root, cq, cb) -> {
            var active = cb.or(cb.isNull(root.get("status")), cb.equal(root.get("status"), (short) 1));
            if (q.isEmpty()) {
                return active;
            }
            String like = "%" + q + "%";
            return cb.and(active, cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("npk"), "")), like)));
        };
        return users.findAll(spec, PageRequest.of(0, Math.max(1, Math.min(limit, 100)), Sort.by("name")))
                .map(this::toPlatformUser).getContent();
    }

    @Override
    @Transactional(transactionManager = "authTransactionManager", readOnly = true)
    public List<PlatformRole> listRoles() {
        return roles.findAllActiveOrderByPriority().stream()
                .map(r -> new PlatformRole(
                        r.getCode() != null && !r.getCode().isBlank() ? r.getCode() : r.getTitle(),
                        r.getName() != null ? r.getName() : r.getTitle()))
                .filter(r -> r.code() != null)
                .toList();
    }

    private PlatformUser toPlatformUser(AuthUser u) {
        List<String> roles = userRoles.findActiveRolesByUserId(u.getId()).stream()
                .map(this::roleCode)
                .filter(Objects::nonNull)
                .toList();
        boolean active = u.getStatus() == null || u.getStatus() == 1;
        return new PlatformUser(u.getId(), u.getNpk(), u.getName(), u.getEmail(), u.getParentId(), active, roles);
    }

    /** Sama dengan AuthService#resolveRoleLabel: kode role, jatuh ke judul bila kode kosong. */
    private String roleCode(AuthUserRole ur) {
        try {
            String code = ur.getRole().getCode();
            return code != null && !code.isBlank() ? code : ur.getRole().getTitle();
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return null;
        }
    }
}
