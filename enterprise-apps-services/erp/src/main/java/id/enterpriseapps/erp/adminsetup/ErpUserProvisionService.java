package id.enterpriseapps.erp.adminsetup;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.security.ErpActorResolver;
import id.enterpriseapps.erp.core.security.ErpIdentityPort.PlatformUser;
import id.enterpriseapps.erp.core.security.ErpPermissionService;
import id.enterpriseapps.erp.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Provisi & sinkron snapshot pengguna ERP dari direktori platform (pengganti "Tambah User" ENTARIS).
 *
 * <p>Semantik snapshot sama dengan provisi otomatis di {@code ErpActorResolver} (helper-nya privat,
 * jadi disalin di sini): nama (cadangan = bagian lokal email), NPK, platformUserId, username unik dari
 * NPK/email, CSV kode role platform + role ERP utama, dan atasan langsung dari {@code parent_id}
 * platform — atasan yang belum ada di ERP ikut diprovisi sebagai snapshot.
 */
@Slf4j
@Service
public class ErpUserProvisionService {

    static final int SEARCH_LIMIT = 20;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private final ErpPlatformDirectory directory;
    private final ErpPermissionService permissions;
    private final ErpActorResolver actors;

    public ErpUserProvisionService(ErpPlatformDirectory directory, ErpPermissionService permissions,
                                   ErpActorResolver actors) {
        this.directory = directory;
        this.permissions = permissions;
        this.actors = actors;
    }

    /** GET /users/platform-search?q= — kandidat akun platform (maks 20) + status provisi di ERP. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> platformSearch(String q) {
        String term = q == null ? "" : q.strip();
        if (term.length() < 2) {
            return List.of();
        }
        List<PlatformUser> found = directory.search(term, SEARCH_LIMIT);
        if (found.size() > SEARCH_LIMIT) {
            found = found.subList(0, SEARCH_LIMIT);
        }
        List<Long> ids = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        for (PlatformUser p : found) {
            if (p.id() != null) {
                ids.add(p.id());
            }
            if (p.email() != null && !p.email().isBlank()) {
                emails.add(p.email().toLowerCase(Locale.ROOT));
            }
        }
        Map<Long, User> byPlatformId = new HashMap<>();
        Map<String, User> byEmail = new HashMap<>();
        if (!found.isEmpty()) {
            List<User> matches = em.createQuery(
                            "select u from User u where u.platformUserId in :ids or lower(u.email) in :emails", User.class)
                    .setParameter("ids", ErpQuery.nonEmpty(ids, -1L))
                    .setParameter("emails", ErpQuery.nonEmpty(emails, ""))
                    .getResultList();
            for (User u : matches) {
                if (u.getPlatformUserId() != null) {
                    byPlatformId.put(u.getPlatformUserId(), u);
                }
                byEmail.put(u.getEmail().toLowerCase(Locale.ROOT), u);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlatformUser p : found) {
            User erp = p.id() == null ? null : byPlatformId.get(p.id());
            if (erp == null && p.email() != null) {
                erp = byEmail.get(p.email().toLowerCase(Locale.ROOT));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("npk", p.npk());
            m.put("name", p.name());
            m.put("email", p.email());
            m.put("roles", p.roles() == null ? List.of() : p.roles());
            m.put("active", p.active());
            m.put("provisioned", erp != null);
            m.put("erpUserId", erp == null ? null : erp.getId());
            out.add(m);
        }
        return out;
    }

    /** POST /users {@code { platformUserId } | { email }} → 201 + UserRow. */
    @ErpTx
    public Map<String, Object> provision(Long platformUserId, String email) {
        if (platformUserId == null && (email == null || email.isBlank())) {
            throw ErpException.business("platformUserId atau email wajib diisi.");
        }
        Optional<PlatformUser> found = platformUserId != null
                ? directory.findById(platformUserId)
                : directory.findByEmail(email.strip());
        PlatformUser pu = found.orElseThrow(() -> ErpException.notFound("User platform tidak ditemukan."));
        if (existing(pu) != null) {
            throw ErpException.business("User sudah terdaftar di ERP.");
        }
        if (!pu.active()) {
            throw ErpException.business("User platform tidak aktif.");
        }
        if (pu.email() == null || pu.email().isBlank()) {
            // Subject JWT platform = email; tanpa email pengguna tidak bisa masuk ERP.
            throw ErpException.business("User platform tidak memiliki email.");
        }
        User u = snapshot(pu, true);
        if (pu.parentId() != null && !pu.parentId().equals(pu.id())) {
            u.setSupervisorId(resolveSupervisorId(pu.parentId()));
        }
        em.persist(u);
        String mail = pu.email();
        AfterCommit.run(() -> actors.evict(mail));
        return UserRows.reload(em, u.getId());
    }

    /**
     * POST /users/sync — segarkan nama/NPK/email/roles/platformUserId semua pengguna ERP dari platform.
     * Atasan hanya diisi bila masih kosong (pilihan admin ERP tidak ditimpa). Pengguna yang hilang atau
     * nonaktif di platform dinonaktifkan; yang aktif lagi TIDAK diaktifkan otomatis (status aktif ERP
     * juga keputusan admin ERP).
     */
    @ErpTx
    public Map<String, Object> sync() {
        List<User> all = em.createQuery("select u from User u order by u.createdAt asc, u.id asc", User.class)
                .getResultList();
        int updated = 0;
        int deactivated = 0;
        Instant now = Instant.now();
        for (User u : all) {
            PlatformUser pu = null;
            if (u.getPlatformUserId() != null) {
                pu = directory.findById(u.getPlatformUserId()).orElse(null);
            }
            if (pu == null && u.getEmail() != null) {
                pu = directory.findByEmail(u.getEmail()).orElse(null);
            }
            boolean changed = pu != null && refresh(u, pu);
            boolean off = (pu == null || !pu.active()) && Boolean.TRUE.equals(u.getIsActive());
            if (off) {
                u.setIsActive(false);
                deactivated++;
            }
            if (changed) {
                updated++;
            }
            if (changed || off) {
                u.setUpdatedAt(now);
            }
        }
        em.flush();
        AfterCommit.run(() -> actors.evict(null));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("updated", updated);
        out.put("deactivated", deactivated);
        out.put("total", all.size());
        return out;
    }

    /** Terapkan data platform ke snapshot; true bila ada yang berubah. */
    private boolean refresh(User u, PlatformUser pu) {
        boolean changed = false;
        if (pu.name() != null && !pu.name().isBlank() && !pu.name().equals(u.getFullName())) {
            u.setFullName(pu.name());
            changed = true;
        }
        if (!Objects.equals(pu.npk(), u.getNpk())) {
            u.setNpk(pu.npk());
            changed = true;
        }
        if (pu.email() != null && !pu.email().isBlank() && !pu.email().equals(u.getEmail())) {
            if (emailTakenByOther(pu.email(), u.getId())) {
                log.warn("ERP sync: email {} sudah dipakai pengguna ERP lain; email {} dibiarkan", pu.email(), u.getEmail());
            } else {
                u.setEmail(pu.email());
                changed = true;
            }
        }
        String csv = UserRows.csv(pu.roles());
        if (!csv.equals(u.getRoles() == null ? "" : u.getRoles())) {
            u.setRoles(csv);
            changed = true;
        }
        String primary = permissions.primaryRole(UserRows.roles(csv));
        if (!primary.equals(u.getRole())) {
            u.setRole(primary);
            changed = true;
        }
        if (pu.id() != null && !pu.id().equals(u.getPlatformUserId())) {
            if (platformIdTakenByOther(pu.id(), u.getId())) {
                log.warn("ERP sync: platformUserId {} sudah tertaut ke pengguna ERP lain", pu.id());
            } else {
                u.setPlatformUserId(pu.id());
                changed = true;
            }
        }
        if (u.getSupervisorId() == null && pu.parentId() != null && !pu.parentId().equals(pu.id())) {
            String sup = resolveSupervisorId(pu.parentId());
            if (sup != null && !sup.equals(u.getId())) {
                u.setSupervisorId(sup);
                changed = true;
            }
        }
        return changed;
    }

    /** Pengguna ERP yang sudah mewakili akun platform ini (platformUserId atau email). */
    private User existing(PlatformUser pu) {
        if (pu.id() != null) {
            List<User> byId = em.createQuery("select u from User u where u.platformUserId = :pid", User.class)
                    .setParameter("pid", pu.id()).getResultList();
            if (!byId.isEmpty()) {
                return byId.get(0);
            }
        }
        return pu.email() == null ? null : findByEmail(pu.email()).orElse(null);
    }

    /**
     * Atasan langsung dari parent platform — semantik {@code ErpActorResolver.resolveSupervisorId}:
     * pakai pengguna ERP yang sudah ada, atau provisi snapshot atasan (status aktif mengikuti platform).
     * Kegagalan hanya dicatat; provisi pengguna tetap berjalan tanpa atasan.
     */
    private String resolveSupervisorId(Long parentPlatformId) {
        try {
            Optional<PlatformUser> parent = directory.findById(parentPlatformId);
            if (parent.isEmpty() || parent.get().email() == null || parent.get().email().isBlank()) {
                return null;
            }
            PlatformUser p = parent.get();
            User sup = existing(p);
            if (sup != null) {
                return sup.getId();
            }
            User s = snapshot(p, p.active());
            em.persist(s);
            em.flush();
            return s.getId();
        } catch (ErpException e) {
            log.warn("ERP: gagal menautkan atasan platform {}: {}", parentPlatformId, e.getMessage());
            return null;
        }
    }

    /** Snapshot baru dari akun platform (lastLoginAt kosong: belum pernah membuka ERP). */
    private User snapshot(PlatformUser p, boolean active) {
        User s = new User();
        s.setEmail(p.email());
        s.setFullName(p.name() == null || p.name().isBlank() ? UserRows.localPart(p.email()) : p.name());
        s.setNpk(p.npk());
        s.setPlatformUserId(p.id());
        s.setUsername(uniqueUsername(p.npk() != null && !p.npk().isBlank() ? p.npk() : UserRows.localPart(p.email())));
        String csv = UserRows.csv(p.roles());
        s.setRoles(csv);
        s.setRole(permissions.primaryRole(UserRows.roles(csv)));
        s.setIsActive(active);
        return s;
    }

    private Optional<User> findByEmail(String email) {
        return em.createQuery("select u from User u where lower(u.email) = :email", User.class)
                .setParameter("email", email.toLowerCase(Locale.ROOT)).getResultList().stream().findFirst();
    }

    private boolean emailTakenByOther(String email, String userId) {
        return em.createQuery("select count(u) from User u where lower(u.email) = :email and u.id <> :id", Long.class)
                .setParameter("email", email.toLowerCase(Locale.ROOT)).setParameter("id", userId)
                .getSingleResult() > 0;
    }

    private boolean platformIdTakenByOther(Long platformUserId, String userId) {
        return em.createQuery("select count(u) from User u where u.platformUserId = :pid and u.id <> :id", Long.class)
                .setParameter("pid", platformUserId).setParameter("id", userId)
                .getSingleResult() > 0;
    }

    /** Sama dengan ErpActorResolver: basis, lalu basis2, basis3, ... */
    private String uniqueUsername(String base) {
        String candidate = base;
        int n = 2;
        while (em.createQuery("select count(u) from User u where u.username = :c", Long.class)
                .setParameter("c", candidate).getSingleResult() > 0) {
            candidate = base + n++;
        }
        return candidate;
    }
}
