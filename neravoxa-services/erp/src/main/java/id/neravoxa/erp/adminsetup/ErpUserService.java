package id.neravoxa.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.security.ErpActorResolver;
import id.neravoxa.erp.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pengguna ERP — port {@code routes/users.ts} + {@code services/userList.ts}, DIADAPTASI ke identitas
 * platform: nama, email, NPK, password, dan role milik auth platform; ERP hanya mengatur status aktif,
 * atasan langsung (approver Pengajuan Pembayaran), dan bahasa. Tidak ada password/Telegram/AIRA.
 */
@Slf4j
@Service
public class ErpUserService {

    static final List<String> SORT_FIELDS = List.of("fullName", "username", "email", "npk", "role", "lastLoginAt", "createdAt");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("createdAt", true);
    static final String NOT_FOUND = "User tidak ditemukan.";
    static final String SELF_DEACTIVATE = "Tidak dapat menonaktifkan akun sendiri.";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private final ErpActorResolver actors;

    public ErpUserService(ErpActorResolver actors) {
        this.actors = actors;
    }

    /**
     * Port {@code buildUserWhere}: search (nama/email/username/NPK, contains insensitive — string mentah
     * tanpa trim seperti rute lama), role (CSV {@code users.roles} memuat kode itu), status.
     */
    static JpqlWhere filter(Map<String, String> query) {
        JpqlWhere where = new JpqlWhere();
        String rawSearch = query.get("search");
        if (rawSearch != null && !rawSearch.strip().isEmpty()) {
            where.and("lower(u.fullName) like :search" + JpqlWhere.ESC + " or lower(u.email) like :search" + JpqlWhere.ESC
                    + " or lower(u.username) like :search" + JpqlWhere.ESC + " or lower(u.npk) like :search" + JpqlWhere.ESC,
                    "search", JpqlWhere.containsRaw(rawSearch));
        }
        String role = ListQuery.str(query, "role");
        if (role != null) {
            where.and("concat(',', coalesce(u.roles, ''), ',') like :rolePattern" + JpqlWhere.ESC,
                    "rolePattern", UserRows.csvMemberPattern(role));
        }
        String status = ListQuery.str(query, "status");
        if (status != null) {
            if (!status.equals("active") && !status.equals("inactive")) {
                throw ErpException.business("Status pengguna tidak dikenal.");
            }
            where.and("u.isActive = :active", "active", status.equals("active"));
        }
        return where;
    }

    /**
     * Port {@code buildUserOrderBy}: tanpa {@code sort} → {@code createdAt desc} persis rute lama;
     * dengan sort → field whitelist + id sebagai tie-breaker; lastLoginAt selalu nulls last.
     */
    static String orderBy(String rawSort) {
        if (ListQuery.firstString(rawSort) == null) {
            return "u.createdAt desc";
        }
        ListQuery.Sort sort = ListQuery.sort(rawSort, SORT_FIELDS, DEFAULT_SORT);
        String dir = sort.dir();
        String primary = sort.field().equals("lastLoginAt")
                ? "u.lastLoginAt " + dir + " nulls last"
                : "u." + sort.field() + " " + dir;
        return primary + ", u.id " + dir;
    }

    /** GET /users → {@code { data: UserRow[], total, page, limit }}. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        JpqlWhere f = filter(query);
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        ErpQuery<User> q = f.applyTo(ErpQuery.of(em, User.class, "u").fetch("left join fetch u.supervisor"))
                .orderBy(orderBy(query.get("sort")));
        long total = q.count();
        List<Map<String, Object>> data = q.list(paging.skip(), paging.limit()).stream().map(UserRows::row).toList();
        return ListQuery.result(data, total, paging);
    }

    /** GET /users/summary → {@code { total, active, inactive }} dengan filter yang sama. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        JpqlWhere f = filter(query);
        TypedQuery<Object[]> q = em.createQuery("select count(u), sum(case when u.isActive = true then 1 else 0 end)"
                + " from User u" + f.clause(), Object[].class);
        f.bind(q);
        Object[] r = q.getSingleResult();
        long total = r[0] == null ? 0 : ((Number) r[0]).longValue();
        long active = r[1] == null ? 0 : ((Number) r[1]).longValue();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("active", active);
        out.put("inactive", total - active);
        return out;
    }

    /**
     * PUT /users/:id — hanya {@code isActive}, {@code supervisorId} (null = kosongkan), {@code language}
     * ('id'|'en'|null). Field lain (nama/email/role/password) milik platform dan diabaikan.
     */
    @ErpTx
    public Map<String, Object> update(String id, JsonNode raw, String actorUserId) {
        JsonNode body = AdminZod.body(raw);
        JsonNode isActive = AdminZod.field(body, "isActive");
        JsonNode supervisorId = AdminZod.field(body, "supervisorId");
        JsonNode language = AdminZod.field(body, "language");

        // Urutan shape Zod: isActive → supervisorId → language, lalu refine "minimal satu field".
        AdminZod.fail(AdminZod.optionalBoolean(isActive));
        AdminZod.fail(supervisorIdError(supervisorId));
        if (AdminZod.present(language) && !language.isNull() && !AdminZod.isLanguage(language)) {
            AdminZod.fail(AdminZod.LANGUAGE_MSG);
        }
        if (!AdminZod.present(isActive) && !AdminZod.present(supervisorId) && !AdminZod.present(language)) {
            AdminZod.fail("Minimal satu field harus diisi.");
        }

        if (AdminZod.present(supervisorId)) {
            AdminZod.fail(checkSupervisor(AdminZod.text(supervisorId), id));
        }
        // Sama dengan toggle: admin tidak boleh mengunci dirinya sendiri keluar dari ERP.
        if (AdminZod.present(isActive) && !isActive.asBoolean() && id.equals(actorUserId)) {
            AdminZod.fail(SELF_DEACTIVATE);
        }
        User u = em.find(User.class, id);
        if (u == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        if (AdminZod.present(isActive)) {
            u.setIsActive(isActive.asBoolean());
        }
        if (AdminZod.present(supervisorId)) {
            u.setSupervisorId(AdminZod.text(supervisorId));
        }
        if (AdminZod.present(language)) {
            u.setLanguage(AdminZod.text(language));
        }
        u.setUpdatedAt(Instant.now());
        evictAfterCommit(u.getEmail());
        return UserRows.reload(em, id);
    }

    /** {@code z.string().min(1).nullable().optional()}. */
    private static String supervisorIdError(JsonNode v) {
        if (!AdminZod.present(v) || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            return AdminZod.expected("string", v);
        }
        return v.asText().isEmpty() ? "Too small: expected string to have >=1 characters" : null;
    }

    /** Port {@code checkSupervisor}: atasan harus ada dan bukan pengguna itu sendiri. */
    private String checkSupervisor(String supervisorId, String userId) {
        if (supervisorId == null || supervisorId.isEmpty()) {
            return null;
        }
        if (userId != null && supervisorId.equals(userId)) {
            return "Atasan langsung tidak boleh diri sendiri.";
        }
        return em.find(User.class, supervisorId) != null ? null : "Atasan langsung tidak ditemukan.";
    }

    /** PATCH /users/:id/toggle — balik status aktif; akun sendiri ditolak. */
    @ErpTx
    public Map<String, Object> toggle(String id, String actorUserId) {
        if (id.equals(actorUserId)) {
            throw ErpException.business(SELF_DEACTIVATE);
        }
        User u = em.find(User.class, id);
        if (u == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        u.setIsActive(!Boolean.TRUE.equals(u.getIsActive()));
        u.setUpdatedAt(Instant.now());
        evictAfterCommit(u.getEmail());
        return UserRows.reload(em, id);
    }

    /** DELETE /users/:id — identitas milik platform, jadi "hapus" = nonaktifkan akses ERP. */
    @ErpTx
    public Map<String, Object> deactivate(String id, String actorUserId) {
        if (id.equals(actorUserId)) {
            throw ErpException.business(SELF_DEACTIVATE);
        }
        User u = em.find(User.class, id);
        if (u == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        if (Boolean.TRUE.equals(u.getIsActive())) {
            u.setIsActive(false);
            u.setUpdatedAt(Instant.now());
        }
        evictAfterCommit(u.getEmail());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /** PUT /users/me/language — bahasa UI sendiri ('id' | 'en', null = default perusahaan). */
    @ErpTx
    public Map<String, Object> updateMyLanguage(String userId, JsonNode raw) {
        JsonNode body = AdminZod.body(raw);
        JsonNode language = AdminZod.field(body, "language");
        if (!AdminZod.present(language) || !(language.isNull() || AdminZod.isLanguage(language))) {
            AdminZod.fail(AdminZod.LANGUAGE_MSG);
        }
        User u = em.find(User.class, userId);
        if (u == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        u.setLanguage(AdminZod.text(language));
        u.setUpdatedAt(Instant.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("language", resolveLanguage(u.getLanguage(), companyDefaultLanguage()));
        out.put("languagePreference", u.getLanguage());
        return out;
    }

    /** Port {@code companyDefaultLanguage}. */
    private String companyDefaultLanguage() {
        List<String> rows = em.createQuery(
                "select c.defaultLanguage from CompanySettings c where c.slug = 'default'", String.class).getResultList();
        return resolveLanguage(null, rows.isEmpty() ? null : rows.get(0));
    }

    /** Port {@code resolveLanguage}: pilihan pengguna → default perusahaan → 'id'. */
    static String resolveLanguage(String userLanguage, String companyDefault) {
        if ("id".equals(userLanguage) || "en".equals(userLanguage)) {
            return userLanguage;
        }
        if ("id".equals(companyDefault) || "en".equals(companyDefault)) {
            return companyDefault;
        }
        return "id";
    }

    /** Snapshot aktor ERP (status aktif/role utama) di-cache 60 dtk; buang setelah commit. */
    private void evictAfterCommit(String email) {
        AfterCommit.run(() -> actors.evict(email));
    }
}
