package id.neravoxa.erp.paymentrequest;

import id.neravoxa.erp.core.ErpJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Job harian 07:00 WIB: pengingat uang muka yang belum dipertanggungjawabkan — port bagian
 * {@code runSettlementReminders()} pada jadwal {@code '0 7 * * *'} (Asia/Jakarta) di {@code jobs/scheduler.ts}.
 * Advisory lock memastikan hanya satu replika yang menjalankan; dedup 3 hari di dalam job membuatnya aman diulang.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpPaymentRequestReminderJob {

    private final ErpJobLock jobLock;
    private final ErpPaymentRequestNotifier notifier;

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Jakarta")
    public void runDaily() {
        jobLock.runExclusive("erp-payment-request-settlement-reminders", () -> {
            int created = notifier.runSettlementReminders(Instant.now());
            if (created > 0) {
                log.info("Advance settlement reminders daily run: created={}", created);
            }
        });
    }
}
