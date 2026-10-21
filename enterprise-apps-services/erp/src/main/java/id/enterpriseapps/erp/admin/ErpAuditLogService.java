package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.entity.AuditLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Jejak audit — port {@code routes/audit.ts} + {@code services/auditLogList.ts}. Baris ditulis kernel
 * ({@code ErpAuditService}: entityType = segmen pertama path, oldValues/newValues JSON, rahasia sudah
 * disamarkan saat ditulis); di sini hanya pembacaan.
 *
 * <p>Filter: userId & action persis, entityType mengandung (tanpa beda huruf), search = entityId mengandung,
 * startDate/endDate = hari kalender zona bisnis. Halaman default 50 (maks. 200), terbaru dulu.
 */
@Service
public class ErpAuditLogService {

    static final List<String> SORT_FIELDS = List.of("createdAt", "action", "entityType");
    private static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("createdAt", true);

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private record Where(String clause, Map<String, Object> params) {
    }

    private static Where where(Map<String, String> query) {
        String startDate = ListQuery.dateParam(query, "startDate", "Format tanggal harus YYYY-MM-DD.");
        String endDate = ListQuery.dateParam(query, "endDate", "Format tanggal harus YYYY-MM-DD.");
        List<String> conds = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        String userId = ListQuery.str(query, "userId");
        if (userId != null) {
            conds.add("a.userId = :userId");
            params.put("userId", userId);
        }
        String action = ListQuery.str(query, "action");
        if (action != null) {
            conds.add("a.action = :action");
            params.put("action", action);
        }
        String entityType = ListQuery.str(query, "entityType");
        if (entityType != null) {
            conds.add("lower(a.entityType) like :entityType");
            params.put("entityType", ErpQuery.contains(entityType));
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            conds.add("lower(a.entityId) like :search");
            params.put("search", ErpQuery.contains(search));
        }
        Instant[] range = ErpDates.localDayRange(startDate, endDate);
        if (range[0] != null) {
            conds.add("a.createdAt >= :gte");
            params.put("gte", range[0]);
        }
        if (range[1] != null) {
            conds.add("a.createdAt < :lt");
            params.put("lt", range[1]);
        }
        return new Where(conds.isEmpty() ? "" : " where " + String.join(" and ", conds), params);
    }

    /**
     * Tanpa {@code sort} → persis {@code createdAt desc} (kontrak lama); sort eksplisit menambah
     * tie-breaker createdAt/id agar halaman tidak bergeser.
     */
    private static String orderBy(String sortParam) {
        if (ListQuery.firstString(sortParam) == null) {
            return " order by a.createdAt desc";
        }
        ListQuery.Sort sort = ListQuery.sort(sortParam, SORT_FIELDS, DEFAULT_SORT);
        StringBuilder sb = new StringBuilder(" order by a.").append(sort.field()).append(' ').append(sort.dir());
        if (!sort.field().equals("createdAt")) {
            sb.append(", a.createdAt desc");
        }
        sb.append(", a.id ").append(sort.dir());
        return sb.toString();
    }

    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        Where w = where(query);
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        TypedQuery<AuditLog> q = em.createQuery("select a from AuditLog a" + w.clause() + orderBy(query.get("sort")), AuditLog.class);
        w.params().forEach(q::setParameter);
        List<AuditLog> rows = q.setFirstResult(paging.skip()).setMaxResults(paging.limit()).getResultList();
        TypedQuery<Long> c = em.createQuery("select count(a) from AuditLog a" + w.clause(), Long.class);
        w.params().forEach(c::setParameter);
        return ListQuery.result(withUser(rows), c.getSingleResult(), paging);
    }

    /** Jumlah per aksi dengan filter yang sama dengan daftar → {@code { total, byAction }}. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        Where w = where(query);
        TypedQuery<Object[]> q = em.createQuery("select a.action, count(a) from AuditLog a" + w.clause()
                + " group by a.action order by a.action", Object[].class);
        w.params().forEach(q::setParameter);
        Map<String, Object> byAction = new LinkedHashMap<>();
        long total = 0;
        for (Object[] r : q.getResultList()) {
            long n = ((Number) r[1]).longValue();
            byAction.put((String) r[0], n);
            total += n;
        }
        return ErpJson.map("total", total, "byAction", byAction);
    }

    /** Opsi filter: aksi, tipe entitas, dan pengguna yang muncul di log. */
    @ErpTx(readOnly = true)
    public Map<String, Object> facets() {
        List<String> actions = em.createQuery("select distinct a.action from AuditLog a order by a.action asc", String.class)
                .getResultList();
        List<String> entityTypes = em.createQuery(
                "select distinct a.entityType from AuditLog a order by a.entityType asc", String.class).getResultList();
        List<Map<String, Object>> users = new ArrayList<>();
        for (Object[] u : em.createQuery("select u.id, u.fullName from User u where u.id in "
                + "(select distinct a.userId from AuditLog a) order by u.fullName asc", Object[].class).getResultList()) {
            users.add(ErpJson.map("id", u[0], "fullName", u[1]));
        }
        return ErpJson.map("actions", actions, "entityTypes", entityTypes, "users", users);
    }

    /** Satu catatan (deep link {@code ?log=<id>}). */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        AuditLog log = em.find(AuditLog.class, id);
        if (log == null) {
            throw ErpException.notFound("Catatan audit tidak ditemukan.");
        }
        return withUser(List.of(log)).get(0);
    }

    /** Baris + {@code user: { fullName, email }} (include select parsial). */
    private List<ObjectNode> withUser(List<AuditLog> rows) {
        Set<String> ids = new LinkedHashSet<>();
        rows.forEach(r -> ids.add(r.getUserId()));
        Map<String, Object[]> users = users(ids);
        List<ObjectNode> out = new ArrayList<>(rows.size());
        for (AuditLog r : rows) {
            ObjectNode o = AdminShape.scalars(r);
            Object[] u = users.get(r.getUserId());
            o.set("user", ErpJson.tree(u == null ? null : ErpJson.map("fullName", u[1], "email", u[2])));
            out.add(o);
        }
        return out;
    }

    private Map<String, Object[]> users(Collection<String> ids) {
        Map<String, Object[]> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Object[] u : em.createQuery("select u.id, u.fullName, u.email from User u where u.id in :ids", Object[].class)
                .setParameter("ids", ids).getResultList()) {
            out.put((String) u[0], u);
        }
        return out;
    }
}
