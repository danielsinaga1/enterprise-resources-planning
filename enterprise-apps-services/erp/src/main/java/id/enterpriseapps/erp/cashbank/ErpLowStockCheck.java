package id.enterpriseapps.erp.cashbank;

import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.notification.ErpNotificationCheck;
import id.enterpriseapps.erp.core.notification.ErpNotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Pemeriksaan "stok rendah" — bagian {@code runNotificationChecks} (notificationService.ts): barang aktif dengan
 * stok minimum &gt; 0 dan stok saat ini ≤ minimum. Deduplikasi {@code metadata.itemId} dari notifikasi
 * {@code low_stock} 24 jam terakhir. Tidak pernah melempar; berjalan di transaksi terpisah.
 */
@Slf4j
@Component
public class ErpLowStockCheck implements ErpNotificationCheck {

    static final String TYPE = "low_stock";

    private final ErpNotificationService notifications;
    private final TransactionTemplate tx;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public ErpLowStockCheck(ErpNotificationService notifications,
                            @Qualifier(ErpTx.MANAGER) PlatformTransactionManager tm) {
        this.notifications = notifications;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public int run(List<String> userIds, Instant now, Instant since) {
        try {
            Integer created = tx.execute(s -> check(userIds, since));
            return created == null ? 0 : created;
        } catch (RuntimeException e) {
            log.error("low_stock check gagal (diabaikan): {}", e.getMessage());
            return 0;
        }
    }

    private int check(List<String> userIds, Instant since) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> lowItems = em.createNativeQuery(
                        "SELECT id, code, name, unit, current_stock, minimum_stock FROM inventory_items "
                                + "WHERE is_active = true AND minimum_stock > 0 AND current_stock <= minimum_stock "
                                + "ORDER BY code")
                .getResultList();
        if (lowItems.isEmpty()) {
            return 0;
        }
        Set<String> notified = CashBankNotificationSupport.notifiedIds(em, TYPE, "itemId", since);
        int created = 0;
        for (Object[] item : lowItems) {
            String id = (String) item[0];
            if (notified.contains(id)) {
                continue;
            }
            String code = (String) item[1];
            String unit = (String) item[3];
            created += notifications.notifyUsers(userIds, new ErpNotificationService.Notice(
                    TYPE,
                    "Stok Rendah",
                    "Stok " + item[2] + " (" + code + ") tersisa " + CashBankFormat.jsNumber(decimal(item[4])) + " " + unit
                            + ", di bawah minimum " + CashBankFormat.jsNumber(decimal(item[5])) + " " + unit + ".",
                    ErpJson.map("itemId", id, "itemCode", code)));
        }
        return created;
    }

    private static BigDecimal decimal(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }
}
