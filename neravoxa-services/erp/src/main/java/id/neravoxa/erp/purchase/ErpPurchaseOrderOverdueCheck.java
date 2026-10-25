package id.neravoxa.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.notification.ErpNotificationCheck;
import id.neravoxa.erp.core.notification.ErpNotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pemeriksaan "PO Terlambat Diterima" — port {@code notifyOverduePurchaseOrders}
 * (notificationService.ts): PO Submitted, tidak ditutup, target terima sebelum hari ini (WIB) dan
 * belum diterima penuh. Deduplikasi terhadap notifikasi {@code po_overdue} sejak {@code since}.
 * Tidak pernah melempar; berjalan di transaksi terpisah agar kegagalan tidak memengaruhi cek lain.
 */
@Slf4j
@Component
public class ErpPurchaseOrderOverdueCheck implements ErpNotificationCheck {

    private final ErpNotificationService notifications;
    private final TransactionTemplate tx;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public ErpPurchaseOrderOverdueCheck(ErpNotificationService notifications,
                                        @Qualifier(ErpTx.MANAGER) PlatformTransactionManager tm) {
        this.notifications = notifications;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public int run(List<String> userIds, Instant now, Instant since) {
        try {
            Integer created = tx.execute(s -> check(userIds, now, since));
            return created == null ? 0 : created;
        } catch (RuntimeException e) {
            log.error("po_overdue check gagal (diabaikan): {}", e.getMessage());
            return 0;
        }
    }

    private int check(List<String> userIds, Instant now, Instant since) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        Object[] p = PurchaseOrderOverdue.overdueParams(now);
        TypedQuery<Object[]> q = em.createQuery("select po.id, po.poNumber, po.expectedDeliveryDate, s.name "
                + "from PurchaseOrder po left join po.supplier s where " + PurchaseOrderOverdue.OVERDUE_JPQL
                + " order by po.expectedDeliveryDate asc", Object[].class);
        for (int i = 0; i < p.length; i += 2) {
            q.setParameter((String) p[i], p[i + 1]);
        }
        List<Object[]> overdue = q.setMaxResults(200).getResultList();
        if (overdue.isEmpty()) {
            return 0;
        }
        Set<String> notified = new HashSet<>();
        em.createQuery("select n.metadata from Notification n where n.type = 'po_overdue' and n.createdAt >= :since",
                        JsonNode.class)
                .setParameter("since", since).getResultList()
                .forEach(m -> {
                    String id = ErpJson.text(m, "purchaseOrderId");
                    if (id != null) {
                        notified.add(id);
                    }
                });
        String today = PurchaseOrderOverdue.businessToday(now);
        int created = 0;
        for (Object[] r : overdue) {
            String id = (String) r[0];
            if (notified.contains(id)) {
                continue;
            }
            String poNumber = (String) r[1];
            String supplier = (String) r[3];
            long days = PurchaseOrderOverdue.daysOverdue((Instant) r[2], today);
            created += notifications.notifyUsers(userIds, new ErpNotificationService.Notice(
                    "po_overdue",
                    "PO Terlambat Diterima",
                    poNumber + (supplier != null ? " (" + supplier + ")" : "") + " melewati target terima " + days
                            + " hari dan belum diterima penuh.",
                    ErpJson.map("purchaseOrderId", id, "poNumber", poNumber, "daysOverdue", days)));
        }
        return created;
    }
}
