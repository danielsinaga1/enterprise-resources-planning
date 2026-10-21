package id.enterpriseapps.erp.cashbank;

import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
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
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pemeriksaan "uang jaminan jatuh tempo" — bagian {@code runNotificationChecks} (notificationService.ts):
 * jaminan berstatus Disetor yang tanggal kembalinya ≤ 7 hari lagi (atau sudah lewat) dan masih bersisa
 * &gt; 0,01 setelah settlement aktif. Deduplikasi {@code metadata.guaranteeId} dari notifikasi
 * {@code guarantee_due} 24 jam terakhir. Tidak pernah melempar; berjalan di transaksi terpisah.
 */
@Slf4j
@Component
public class ErpGuaranteeDueCheck implements ErpNotificationCheck {

    static final String TYPE = "guarantee_due";
    /** Jendela pengingat notifikasi (berbeda dari jendela "segera" 30 hari di daftar). */
    private static final Duration DUE_SOON = Duration.ofDays(7);

    private final ErpNotificationService notifications;
    private final TransactionTemplate tx;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public ErpGuaranteeDueCheck(ErpNotificationService notifications,
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
            log.error("guarantee_due check gagal (diabaikan): {}", e.getMessage());
            return 0;
        }
    }

    private int check(List<String> userIds, Instant now, Instant since) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        List<Object[]> due = em.createQuery("select g.id, g.guaranteeNumber, g.projectName, g.amount, g.expectedReturnDate, p.name "
                        + "from ProjectGuarantee g join g.party p where g.status = :open "
                        + "and g.expectedReturnDate is not null and g.expectedReturnDate <= :dueSoon "
                        + "order by g.expectedReturnDate asc, g.guaranteeNumber asc", Object[].class)
                .setParameter("open", ProjectGuaranteeList.OPEN)
                .setParameter("dueSoon", now.plus(DUE_SOON))
                .getResultList();
        if (due.isEmpty()) {
            return 0;
        }
        Map<String, BigDecimal> settled = new HashMap<>();
        em.createQuery("select s.guaranteeId, coalesce(sum(s.returnAmount + s.forfeitAmount), 0) "
                        + "from ProjectGuaranteeSettlement s where s.isCancelled = false and s.guaranteeId in :ids "
                        + "group by s.guaranteeId", Object[].class)
                .setParameter("ids", due.stream().map(r -> (String) r[0]).toList())
                .getResultList()
                .forEach(r -> settled.put((String) r[0], r[1] == null ? BigDecimal.ZERO : new BigDecimal(r[1].toString())));
        Set<String> notified = CashBankNotificationSupport.notifiedIds(em, TYPE, "guaranteeId", since);

        int created = 0;
        for (Object[] g : due) {
            String id = (String) g[0];
            if (notified.contains(id)) {
                continue;
            }
            BigDecimal outstanding = Money.nz((BigDecimal) g[3]).subtract(Money.nz(settled.get(id)));
            if (outstanding.compareTo(Money.CENT) <= 0) {
                continue;
            }
            String number = (String) g[1];
            Instant dueDate = (Instant) g[4];
            boolean overdue = dueDate.isBefore(now);
            created += notifications.notifyUsers(userIds, new ErpNotificationService.Notice(
                    TYPE,
                    overdue ? "Uang Jaminan Lewat Jatuh Tempo" : "Uang Jaminan Mendekati Jatuh Tempo",
                    number + " (" + g[2] + ") ke " + g[5] + ": sisa Rp " + CashBankFormat.idNumber(outstanding) + ", "
                            + (overdue ? "jatuh tempo pengembalian sudah lewat" : "jatuh tempo pengembalian") + " "
                            + CashBankFormat.idLongDate(dueDate) + ". Tagih pengembalian ke pemberi proyek.",
                    ErpJson.map("guaranteeId", id, "guaranteeNumber", number)));
        }
        return created;
    }
}
