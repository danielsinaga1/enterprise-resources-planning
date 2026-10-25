package id.neravoxa.erp.sales;

import id.neravoxa.erp.core.ErpJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sweeper siklus hidup penawaran — port {@code jobs/quotationSweeper.ts}, dijadwalkan tiap jam
 * menit ke-15 (cron {@code '15 * * * *'} di scheduler.ts) dengan advisory lock agar hanya satu
 * replika yang menjalankan:
 * <ol>
 *   <li>penawaran {@code Sent} yang lewat {@code validUntil} → {@code Expired};</li>
 *   <li>notifikasi ke pembuat untuk penawaran yang akan kadaluarsa ≤ 3 hari (sekali per penawaran).</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpQuotationSweeperJob {

    static final String JOB_NAME = "quotationSweeper";

    private final ErpJobLock jobLock;
    private final ErpQuotationSweeperService sweeper;

    @Scheduled(cron = "0 15 * * * *", zone = "Asia/Jakarta")
    public void scheduled() {
        jobLock.runExclusive(JOB_NAME, this::runQuotationSweeper);
    }

    /** Port {@code runQuotationSweeper}: kegagalan hanya dicatat. */
    public void runQuotationSweeper() {
        try {
            int expired = sweeper.expireOverdueQuotations();
            int notified = notifyExpiringQuotations();
            if (expired > 0 || notified > 0) {
                log.info("Quotation sweeper run expired={} notified={}", expired, notified);
            }
        } catch (RuntimeException e) {
            log.error("Quotation sweeper failed", e);
        }
    }

    /** Notifikasi per kandidat di transaksinya sendiri; kegagalan satu kandidat dicatat lalu lanjut. */
    int notifyExpiringQuotations() {
        int created = 0;
        for (ErpQuotationSweeperService.Candidate q : sweeper.findExpiringCandidates()) {
            try {
                sweeper.notifyExpiring(q);
                created++;
            } catch (RuntimeException e) {
                log.error("Failed to create expiry notification quotationId={}", q.id(), e);
            }
        }
        return created;
    }
}
