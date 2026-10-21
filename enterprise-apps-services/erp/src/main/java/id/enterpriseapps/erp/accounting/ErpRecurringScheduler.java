package id.enterpriseapps.erp.accounting;

import id.enterpriseapps.erp.core.ErpJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job harian 02:00 WIB: eksekusi template berulang yang jatuh tempo — port jadwal
 * {@code '0 2 * * *'} di {@code jobs/scheduler.ts}. Di luar jam kerja agar tidak berebut dengan
 * pengguna; advisory lock memastikan hanya satu replika yang menjalankan.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpRecurringScheduler {

    private final ErpJobLock jobLock;
    private final ErpRecurringService recurring;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Jakarta")
    public void runDaily() {
        jobLock.runExclusive("erp-recurring-templates", () -> {
            ErpRecurringService.RunOutcome r = recurring.runDueScheduled();
            if (r.executed() > 0 || r.failed() > 0) {
                log.info("Recurring templates daily run: executed={}, failed={}, skipped={}",
                        r.executed(), r.failed(), r.skipped());
            }
        });
    }
}
