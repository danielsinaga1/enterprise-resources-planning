package id.enterpriseapps.erp.cashbank;

import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.notification.ErpNotificationCheck;
import id.enterpriseapps.erp.core.notification.ErpNotificationService;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Pemeriksaan "invoice jatuh tempo" — bagian {@code runNotificationChecks} (notificationService.ts): invoice
 * penjualan lalu pembelian yang belum Paid/Cancelled/Draft dan jatuh temponya sudah lewat. Keduanya memakai
 * satu set deduplikasi {@code metadata.invoiceId} dari notifikasi {@code invoice_overdue} 24 jam terakhir.
 * Tidak pernah melempar; berjalan di transaksi terpisah.
 */
@Slf4j
@Component
public class ErpInvoiceOverdueCheck implements ErpNotificationCheck {

    static final String TYPE = "invoice_overdue";
    private static final List<InvoiceStatus> EXCLUDED = List.of(InvoiceStatus.Paid, InvoiceStatus.Cancelled, InvoiceStatus.Draft);

    private final ErpNotificationService notifications;
    private final TransactionTemplate tx;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public ErpInvoiceOverdueCheck(ErpNotificationService notifications,
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
            log.error("invoice_overdue check gagal (diabaikan): {}", e.getMessage());
            return 0;
        }
    }

    private int check(List<String> userIds, Instant now, Instant since) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        Set<String> notified = CashBankNotificationSupport.notifiedIds(em, TYPE, "invoiceId", since);
        int created = 0;

        List<Object[]> sales = em.createQuery("select i.id, i.invoiceNumber, c.name from SalesInvoice i join i.customer c "
                        + "where i.status not in :excluded and i.dueDate < :now order by i.dueDate asc, i.invoiceNumber asc",
                        Object[].class)
                .setParameter("excluded", EXCLUDED).setParameter("now", now).getResultList();
        for (Object[] r : sales) {
            String id = (String) r[0];
            if (notified.contains(id)) {
                continue;
            }
            created += notifications.notifyUsers(userIds, new ErpNotificationService.Notice(
                    TYPE,
                    "Invoice Penjualan Jatuh Tempo",
                    "Invoice " + r[1] + " untuk " + r[2] + " sudah melewati jatuh tempo.",
                    ErpJson.map("invoiceId", id, "invoiceNumber", r[1], "invoiceType", "sales")));
        }

        List<Object[]> purchases = em.createQuery("select i.id, i.invoiceNumber, s.name from PurchaseInvoice i join i.supplier s "
                        + "where i.status not in :excluded and i.dueDate < :now order by i.dueDate asc, i.invoiceNumber asc",
                        Object[].class)
                .setParameter("excluded", EXCLUDED).setParameter("now", now).getResultList();
        for (Object[] r : purchases) {
            String id = (String) r[0];
            if (notified.contains(id)) {
                continue;
            }
            created += notifications.notifyUsers(userIds, new ErpNotificationService.Notice(
                    TYPE,
                    "Invoice Pembelian Jatuh Tempo",
                    "Invoice " + r[1] + " untuk " + r[2] + " sudah melewati jatuh tempo.",
                    ErpJson.map("invoiceId", id, "invoiceNumber", r[1], "invoiceType", "purchase")));
        }
        return created;
    }
}
