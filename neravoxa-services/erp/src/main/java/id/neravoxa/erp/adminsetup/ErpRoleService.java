package id.neravoxa.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.security.ErpActorResolver;
import id.neravoxa.erp.core.security.ErpIdentityPort.PlatformRole;
import id.neravoxa.erp.core.security.ErpModules;
import id.neravoxa.erp.core.security.ErpPermissionService;
import id.neravoxa.erp.entity.Role;
import id.neravoxa.erp.entity.RoleCapability;
import id.neravoxa.erp.entity.RolePermission;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Role & matriks izin ERP (RBAC dinamis) — port {@code routes/roles.ts}.
 *
 * <p>Diadaptasi: kunci role = KODE role platform (auth_roles.code), bukan slug buatan ERP. Role baru
 * hanya boleh untuk kode yang ada di direktori platform; pengguna mendapat role lewat platform, jadi
 * hapus role cukup menghapus konfigurasi izinnya (tidak ada cek "masih dipakai"). Setiap perubahan
 * meng-invalidate cache izin + cache aktor SETELAH commit.
 */
@Slf4j
@Service
public class ErpRoleService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private final ErpPlatformDirectory directory;
    private final ErpPermissionService permissions;
    private final ErpActorResolver actors;

    public ErpRoleService(ErpPlatformDirectory directory, ErpPermissionService permissions, ErpActorResolver actors) {
        this.directory = directory;
        this.permissions = permissions;
        this.actors = actors;
    }

    /** GET /roles/assignable — role ERP aktif {@code [{ key, name }]} untuk filter/dropdown pengguna. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> assignable() {
        List<Object[]> rows = em.createQuery(
                "select r.key, r.name from Role r where r.isActive = true order by r.isSystem desc, r.name asc",
                Object[].class).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", r[0]);
            m.put("name", r[1]);
            out.add(m);
        }
        return out;
    }

    /** GET /roles/catalog — modul & capability untuk membangun UI matriks. */
    public Map<String, Object> catalog() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modules", ErpModules.MODULES);
        out.put("capabilities", ErpModules.CAPABILITIES);
        return out;
    }

    /** GET /roles — role + matriks + caps + jumlah pengguna ERP yang memegang kode role itu. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> list() {
        List<Role> roles = em.createQuery("select r from Role r order by r.isSystem desc, r.name asc", Role.class)
                .getResultList();
        Map<String, List<Map<String, Object>>> permsByRole = new HashMap<>();
        for (RolePermission p : em.createQuery("select p from RolePermission p", RolePermission.class).getResultList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("module", p.getModule());
            m.put("canView", p.getCanView());
            m.put("canEdit", p.getCanEdit());
            permsByRole.computeIfAbsent(p.getRoleId(), k -> new ArrayList<>()).add(m);
        }
        Map<String, List<String>> capsByRole = new HashMap<>();
        for (RoleCapability c : em.createQuery("select c from RoleCapability c", RoleCapability.class).getResultList()) {
            capsByRole.computeIfAbsent(c.getRoleId(), k -> new ArrayList<>()).add(c.getCapability());
        }
        Map<String, Integer> userCount = new HashMap<>();
        for (String csv : em.createQuery("select u.roles from User u where u.roles is not null", String.class)
                .getResultList()) {
            for (String code : UserRows.roles(csv)) {
                userCount.merge(code, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Role r : roles) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("key", r.getKey());
            m.put("name", r.getName());
            m.put("description", r.getDescription());
            m.put("isSystem", r.getIsSystem());
            m.put("isSuperAdmin", r.getIsSuperAdmin());
            m.put("isActive", r.getIsActive());
            m.put("userCount", userCount.getOrDefault(r.getKey(), 0));
            m.put("permissions", permsByRole.getOrDefault(r.getId(), List.of()));
            m.put("caps", capsByRole.getOrDefault(r.getId(), List.of()));
            out.add(m);
        }
        return out;
    }

    /** GET /roles/platform — role platform aktif + apakah sudah dikonfigurasi di ERP. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> platformRoles() {
        List<PlatformRole> platform = directory.listRoles();
        Map<String, String> erpIdByKey = new HashMap<>();
        for (Object[] r : em.createQuery("select r.key, r.id from Role r", Object[].class).getResultList()) {
            erpIdByKey.put((String) r[0], (String) r[1]);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlatformRole p : platform) {
            String erpId = erpIdByKey.get(p.code());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.code());
            m.put("name", p.name());
            m.put("configured", erpId != null);
            m.put("erpRoleId", erpId);
            out.add(m);
        }
        return out;
    }

    /** POST /roles {@code { key, name?, description?, permissions?, caps? }} → 201 + role (permissions & capabilities). */
    @ErpTx
    public Role create(JsonNode raw) {
        JsonNode body = raw != null && raw.isObject() ? raw : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        JsonNode keyNode = AdminZod.field(body, "key");
        String key = keyNode.isTextual() ? keyNode.asText().strip() : "";
        if (key.isEmpty()) {
            throw ErpException.business("Key role tidak valid.");
        }
        PlatformRole platformRole = directory.listRoles().stream()
                .filter(p -> key.equals(p.code())).findFirst()
                .orElseThrow(() -> ErpException.business("Role platform tidak ditemukan."));
        boolean exists = !em.createQuery("select r.id from Role r where r.key = :key", String.class)
                .setParameter("key", key).getResultList().isEmpty();
        if (exists) {
            throw ErpException.business("Role dengan key \"" + key + "\" sudah ada.");
        }
        JsonNode nameNode = AdminZod.field(body, "name");
        String name = nameNode.isTextual() && !nameNode.asText().strip().isEmpty()
                ? nameNode.asText().strip() : platformRole.name();
        if (name == null || name.strip().length() < 2) {
            throw ErpException.business("Nama role minimal 2 karakter.");
        }
        JsonNode descNode = AdminZod.field(body, "description");

        Role role = new Role();
        role.setKey(key);
        role.setName(name.strip());
        role.setDescription(descNode.isTextual() ? descNode.asText().strip() : null);
        role.setIsSystem(false);
        role.setIsSuperAdmin(false);
        role.setIsActive(true);
        em.persist(role);
        em.flush();
        insertMatrix(role.getId(), sanitizePermissions(AdminZod.field(body, "permissions")),
                sanitizeCaps(AdminZod.field(body, "caps")));
        log.info("ERP RBAC: role {} dibuat", key);
        afterRbacChange();
        return reload(role.getId());
    }

    /**
     * PUT /roles/:id — nama/deskripsi/aktif + ganti matriks & caps. Role super-admin: matriks/caps
     * diabaikan (selalu akses penuh). Role sistem tidak bisa dinonaktifkan (anti-lockout).
     */
    @ErpTx
    public Map<String, Object> update(String id, JsonNode raw) {
        Role role = em.find(Role.class, id);
        if (role == null) {
            throw ErpException.notFound("Role tidak ditemukan.");
        }
        JsonNode body = raw != null && raw.isObject() ? raw : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        JsonNode name = AdminZod.field(body, "name");
        JsonNode description = AdminZod.field(body, "description");
        JsonNode isActive = AdminZod.field(body, "isActive");
        if (name.isTextual() && name.asText().strip().length() >= 2) {
            role.setName(name.asText().strip());
        }
        if (description.isTextual()) {
            role.setDescription(description.asText().strip());
        }
        if (isActive.isBoolean() && !Boolean.TRUE.equals(role.getIsSystem())) {
            role.setIsActive(isActive.asBoolean());
        }
        role.setUpdatedAt(Instant.now());
        if (!Boolean.TRUE.equals(role.getIsSuperAdmin())) {
            JsonNode perms = AdminZod.field(body, "permissions");
            JsonNode caps = AdminZod.field(body, "caps");
            if (perms.isArray()) {
                em.createQuery("delete from RolePermission p where p.roleId = :id").setParameter("id", id).executeUpdate();
            }
            if (caps.isArray()) {
                em.createQuery("delete from RoleCapability c where c.roleId = :id").setParameter("id", id).executeUpdate();
            }
            insertMatrix(id, perms.isArray() ? sanitizePermissions(perms) : List.of(),
                    caps.isArray() ? sanitizeCaps(caps) : List.of());
        }
        em.flush();
        log.info("ERP RBAC: role {} diperbarui", role.getKey());
        afterRbacChange();
        return Map.of("success", true);
    }

    /** DELETE /roles/:id — role bawaan ditolak; konfigurasi izin role custom dihapus. */
    @ErpTx
    public Map<String, Object> delete(String id) {
        Role role = em.find(Role.class, id);
        if (role == null) {
            throw ErpException.notFound("Role tidak ditemukan.");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw ErpException.business("Role bawaan tidak dapat dihapus.");
        }
        String key = role.getKey();
        em.createQuery("delete from RolePermission p where p.roleId = :id").setParameter("id", id).executeUpdate();
        em.createQuery("delete from RoleCapability c where c.roleId = :id").setParameter("id", id).executeUpdate();
        em.remove(role);
        em.flush();
        log.info("ERP RBAC: role {} dihapus", key);
        afterRbacChange();
        return Map.of("success", true);
    }

    record Perm(String module, boolean canView, boolean canEdit) {
    }

    /**
     * Port {@code sanitizePermissions}: modul harus terdaftar; edit menyiratkan view; baris tanpa
     * view/edit dibuang. Kebenaran nilai mengikuti JavaScript ({@code !!p.canEdit}).
     */
    static List<Perm> sanitizePermissions(JsonNode raw) {
        List<Perm> out = new ArrayList<>();
        if (raw == null || !raw.isArray()) {
            return out;
        }
        for (JsonNode p : raw) {
            if (p == null || !p.isObject()) {
                continue;
            }
            JsonNode module = p.get("module");
            if (module == null || !module.isTextual() || !ErpModules.isModule(module.asText())) {
                continue;
            }
            boolean canEdit = AdminZod.truthy(p.get("canEdit"));
            boolean canView = AdminZod.truthy(p.get("canView")) || canEdit;
            if (!canView) {
                continue;
            }
            out.add(new Perm(module.asText(), canView, canEdit));
        }
        return out;
    }

    /** Port {@code sanitizeCaps}: hanya capability terdaftar, tanpa duplikat. */
    static List<String> sanitizeCaps(JsonNode raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode c : raw) {
                if (c != null && c.isTextual() && ErpModules.isCapability(c.asText())) {
                    out.add(c.asText());
                }
            }
        }
        return List.copyOf(out);
    }

    private void insertMatrix(String roleId, List<Perm> perms, List<String> caps) {
        for (Perm p : perms) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setModule(p.module());
            rp.setCanView(p.canView());
            rp.setCanEdit(p.canEdit());
            em.persist(rp);
        }
        for (String c : caps) {
            RoleCapability rc = new RoleCapability();
            rc.setRoleId(roleId);
            rc.setCapability(c);
            em.persist(rc);
        }
    }

    private Role reload(String id) {
        em.flush();
        em.clear();
        Role role = em.createQuery("select r from Role r left join fetch r.permissions where r.id = :id", Role.class)
                .setParameter("id", id).getSingleResult();
        Hibernate.initialize(role.getCapabilities());
        return role;
    }

    /** Cache izin & aktor dibuang setelah commit agar request berikutnya memakai matriks baru. */
    private void afterRbacChange() {
        AfterCommit.run(() -> {
            permissions.invalidate();
            actors.evict(null);
        });
    }
}
