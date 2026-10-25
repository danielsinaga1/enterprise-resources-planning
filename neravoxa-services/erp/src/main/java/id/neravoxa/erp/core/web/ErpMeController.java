package id.neravoxa.erp.core.web;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.security.ErpActor;
import id.neravoxa.erp.core.security.ErpActorResolver;
import id.neravoxa.erp.core.security.ErpPermissionService;
import id.neravoxa.erp.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Profil + izin ERP pengguna yang login — padanan {@code GET /api/auth/me} ENTARIS.
 * Login/logout tetap milik auth platform; endpoint ini hanya memberi UI ERP data izin.
 */
@RestController
@RequestMapping("/api/erp/me")
@RequiredArgsConstructor
@Tag(name = "ERP - Profil", description = "Profil dan hak akses ERP pengguna saat ini")
public class ErpMeController {

    private final ErpActorResolver actors;
    private final ErpPermissionService permissions;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @GetMapping
    @Operation(summary = "Profil & izin ERP pengguna saat ini")
    @ErpTx(readOnly = true)
    public Map<String, Object> me() {
        ErpActor actor = actors.current();
        User u = em.find(User.class, actor.userId());
        if (u == null || !Boolean.TRUE.equals(u.getIsActive())) {
            throw ErpException.of(401, "User tidak ditemukan atau tidak aktif.");
        }
        List<String> rows = em.createQuery(
                        "select c.defaultLanguage from CompanySettings c where c.slug = 'default'", String.class)
                .getResultList();
        String companyLanguage = rows.isEmpty() || rows.get(0) == null ? "id" : rows.get(0);
        String pref = u.getLanguage();
        String language = "id".equals(pref) || "en".equals(pref) ? pref
                : ("en".equals(companyLanguage) ? "en" : "id");

        Map<String, Object> access = permissions.effectiveForClient(actor.roleKeys());
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", u.getId());
        user.put("username", u.getUsername());
        user.put("fullName", u.getFullName());
        user.put("email", u.getEmail());
        user.put("npk", u.getNpk());
        user.put("role", u.getRole());
        user.put("roles", List.copyOf(actor.roleKeys()));
        user.put("isActive", u.getIsActive());
        user.put("language", language);
        user.put("languagePreference", pref);
        user.put("supervisorId", u.getSupervisorId());
        user.put("hasErpAccess", permissions.hasAnyErpRole(actor.roleKeys()));
        user.putAll(access);
        return Map.of("user", user);
    }
}
