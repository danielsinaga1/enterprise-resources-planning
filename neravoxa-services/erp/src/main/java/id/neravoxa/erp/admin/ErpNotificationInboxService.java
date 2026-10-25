package id.neravoxa.erp.admin;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.Notification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kotak notifikasi per pengguna — port handler {@code routes/notifications.ts} (selain {@code /check}).
 * Semua query dibatasi {@code userId} pengguna saat ini.
 */
@Service
public class ErpNotificationInboxService {

    /** Parameter yang mengubah GET /notifications ke mode daftar berhalaman. */
    private static final List<String> LIST_PARAMS = List.of("page", "limit", "isRead", "type", "search");

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * Tanpa parameter (lonceng): 50 terbaru + unreadCount. Dengan page/limit/isRead/type/search:
     * {@code { data, total, page, limit, unreadCount }} untuk halaman /notifications.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(String userId, Map<String, String> query) {
        boolean paged = LIST_PARAMS.stream().anyMatch(k -> ListQuery.str(query, k) != null);
        Map<String, Object> out = new LinkedHashMap<>();
        if (!paged) {
            List<Notification> rows = em.createQuery(
                            "select n from Notification n where n.userId = :u order by n.createdAt desc", Notification.class)
                    .setParameter("u", userId).setMaxResults(50).getResultList();
            out.put("data", AdminShape.scalars(rows));
            out.put("unreadCount", unread(userId));
            return out;
        }
        String isRead = ListQuery.str(query, "isRead");
        if (isRead != null && !isRead.equals("true") && !isRead.equals("false")) {
            throw ErpException.business("Filter status baca tidak valid.");
        }
        String type = ListQuery.str(query, "type");
        String search = ListQuery.str(query, "search");
        ErpQuery<Notification> q = ErpQuery.of(em, Notification.class, "n")
                .where("n.userId = :u", "u", userId)
                .whereIf(isRead != null, "n.isRead = :r", "r", "true".equals(isRead))
                .whereIf(type != null, "n.type = :t", "t", type)
                .whereIf(search != null, "lower(n.title) like :s or lower(n.message) like :s", "s",
                        search == null ? null : ErpQuery.contains(search))
                .orderBy("n.createdAt desc, n.id desc");
        ListQuery.Paging paging = ListQuery.paging(query, 25, 100);
        List<Notification> rows = q.list(paging.skip(), paging.limit());
        out.put("data", AdminShape.scalars(rows));
        out.put("total", q.count());
        out.put("page", paging.page());
        out.put("limit", paging.limit());
        out.put("unreadCount", unread(userId));
        return out;
    }

    /** Jenis notifikasi milik pengguna + jumlahnya (filter jenis di halaman /notifications). */
    @ErpTx(readOnly = true)
    public Map<String, Object> types(String userId) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (Object[] r : em.createQuery("select n.type, count(n) from Notification n where n.userId = :u "
                        + "group by n.type order by n.type asc", Object[].class)
                .setParameter("u", userId).getResultList()) {
            data.add(ErpJson.map("type", r[0], "count", ((Number) r[1]).longValue()));
        }
        return ErpJson.map("data", data);
    }

    @ErpTx(readOnly = true)
    public long unread(String userId) {
        return em.createQuery("select count(n) from Notification n where n.userId = :u and n.isRead = false", Long.class)
                .setParameter("u", userId).getSingleResult();
    }

    @ErpTx
    public void markRead(String id, String userId) {
        int n = em.createQuery("update Notification n set n.isRead = true where n.id = :id and n.userId = :u")
                .setParameter("id", id).setParameter("u", userId).executeUpdate();
        if (n == 0) {
            throw ErpException.notFound("Notifikasi tidak ditemukan.");
        }
    }

    @ErpTx
    public void markAllRead(String userId) {
        em.createQuery("update Notification n set n.isRead = true where n.userId = :u and n.isRead = false")
                .setParameter("u", userId).executeUpdate();
    }
}
