package id.neravoxa.erp.core.security;

import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.Role;
import id.neravoxa.erp.entity.RoleCapability;
import id.neravoxa.erp.entity.RolePermission;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolusi izin RBAC ERP + cache in-memory (port {@code services/permissionService.ts}).
 *
 * <p>Role diambil dari JWT platform (bisa lebih dari satu). Izin efektif = union dari semua
 * role pengguna yang terdaftar & aktif di tabel {@code roles} db_erp; role super-admin aktif
 * meloloskan semua cek. Panggil {@link #invalidate()} setiap kali role/izin diubah.
 */
@Slf4j
@Service
public class ErpPermissionService {

    public static final String VIEW = "view";
    public static final String EDIT = "edit";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private volatile Map<String, EffectiveRole> cache;

    private record ModulePerm(boolean view, boolean edit) {
    }

    private record EffectiveRole(boolean superAdmin, boolean active, Map<String, ModulePerm> modules, Set<String> caps) {
    }

    public void invalidate() {
        cache = null;
    }

    @ErpTx(readOnly = true)
    protected Map<String, EffectiveRole> load() {
        Map<String, EffectiveRole> map = new HashMap<>();
        try {
            List<Role> roles = em.createQuery(
                    "select distinct r from Role r left join fetch r.permissions", Role.class).getResultList();
            List<RoleCapability> caps = em.createQuery("select c from RoleCapability c", RoleCapability.class)
                    .getResultList();
            Map<String, Set<String>> capsByRole = new HashMap<>();
            for (RoleCapability c : caps) {
                capsByRole.computeIfAbsent(c.getRoleId(), k -> new HashSet<>()).add(c.getCapability());
            }
            for (Role r : roles) {
                Map<String, ModulePerm> modules = new HashMap<>();
                if (r.getPermissions() != null) {
                    for (RolePermission p : r.getPermissions()) {
                        boolean edit = Boolean.TRUE.equals(p.getCanEdit());
                        boolean view = edit || Boolean.TRUE.equals(p.getCanView());
                        modules.put(p.getModule(), new ModulePerm(view, edit));
                    }
                }
                map.put(r.getKey(), new EffectiveRole(
                        Boolean.TRUE.equals(r.getIsSuperAdmin()),
                        Boolean.TRUE.equals(r.getIsActive()),
                        modules,
                        capsByRole.getOrDefault(r.getId(), Set.of())));
            }
        } catch (RuntimeException e) {
            log.error("ErpPermissionService: gagal memuat role", e);
        }
        return map;
    }

    private Map<String, EffectiveRole> roles() {
        Map<String, EffectiveRole> c = cache;
        if (c == null) {
            c = load();
            cache = c;
        }
        return c;
    }

    private List<EffectiveRole> activeRoles(Collection<String> roleKeys) {
        Map<String, EffectiveRole> all = roles();
        List<EffectiveRole> out = new java.util.ArrayList<>();
        if (roleKeys != null) {
            for (String k : roleKeys) {
                EffectiveRole r = all.get(k);
                if (r != null && r.active()) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    /** Apakah salah satu role boleh {@code action} ("view"/"edit") pada modul. Super-admin selalu true. */
    public boolean can(Collection<String> roleKeys, String module, String action) {
        for (EffectiveRole r : activeRoles(roleKeys)) {
            if (r.superAdmin()) {
                return true;
            }
            ModulePerm m = r.modules().get(module);
            if (m != null && (EDIT.equals(action) ? m.edit() : m.view())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCap(Collection<String> roleKeys, String capability) {
        for (EffectiveRole r : activeRoles(roleKeys)) {
            if (r.superAdmin() || r.caps().contains(capability)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSuperAdmin(Collection<String> roleKeys) {
        return activeRoles(roleKeys).stream().anyMatch(EffectiveRole::superAdmin);
    }

    /** True bila pengguna punya minimal satu role ERP aktif (dipakai menu & akses modul). */
    public boolean hasAnyErpRole(Collection<String> roleKeys) {
        return !activeRoles(roleKeys).isEmpty();
    }

    /** Role ERP pertama yang dimiliki (urutan sesuai roleKeys) — "role utama" untuk tampilan. */
    public String primaryRole(Collection<String> roleKeys) {
        Map<String, EffectiveRole> all = roles();
        if (roleKeys != null) {
            for (String k : roleKeys) {
                EffectiveRole r = all.get(k);
                if (r != null && r.active() && r.superAdmin()) {
                    return k;
                }
            }
            for (String k : roleKeys) {
                EffectiveRole r = all.get(k);
                if (r != null && r.active()) {
                    return k;
                }
            }
        }
        return roleKeys == null || roleKeys.isEmpty() ? "Viewer" : roleKeys.iterator().next();
    }

    /** Izin lengkap untuk klien: {@code { isSuperAdmin, permissions: {module:{view,edit}}, caps: [] }}. */
    public Map<String, Object> effectiveForClient(Collection<String> roleKeys) {
        boolean superAdmin = false;
        Map<String, Map<String, Boolean>> perms = new LinkedHashMap<>();
        Set<String> caps = new LinkedHashSet<>();
        for (EffectiveRole r : activeRoles(roleKeys)) {
            superAdmin |= r.superAdmin();
            r.modules().forEach((mod, p) -> {
                Map<String, Boolean> cur = perms.computeIfAbsent(mod, k -> new LinkedHashMap<>(Map.of("view", false, "edit", false)));
                cur.put("view", cur.get("view") || p.view());
                cur.put("edit", cur.get("edit") || p.edit());
            });
            caps.addAll(r.caps());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("isSuperAdmin", superAdmin);
        out.put("permissions", perms);
        out.put("caps", List.copyOf(caps));
        return out;
    }

    /** Kunci semua role aktif yang boleh {@code action} pada modul (termasuk super-admin). */
    public List<String> roleKeysWithPerm(String module, String action) {
        List<String> keys = new java.util.ArrayList<>();
        roles().forEach((key, r) -> {
            if (!r.active()) {
                return;
            }
            ModulePerm m = r.modules().get(module);
            if (r.superAdmin() || (m != null && (EDIT.equals(action) ? m.edit() : m.view()))) {
                keys.add(key);
            }
        });
        return keys;
    }

    /** Kunci semua role aktif yang punya capability (termasuk super-admin). */
    public List<String> roleKeysWithCap(String capability) {
        List<String> keys = new java.util.ArrayList<>();
        roles().forEach((key, r) -> {
            if (r.active() && (r.superAdmin() || r.caps().contains(capability))) {
                keys.add(key);
            }
        });
        return keys;
    }

    /** Kunci role super-admin aktif (padanan "Admin" di ENTARIS untuk notifyAdmins). */
    public List<String> superAdminRoleKeys() {
        List<String> keys = new java.util.ArrayList<>();
        roles().forEach((key, r) -> {
            if (r.active() && r.superAdmin()) {
                keys.add(key);
            }
        });
        return keys;
    }
}
