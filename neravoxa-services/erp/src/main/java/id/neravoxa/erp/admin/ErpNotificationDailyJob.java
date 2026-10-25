package id.neravoxa.erp.admin;

import id.neravoxa.erp.core.ErpJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job harian 07:00 WIB: pemeriksaan notifikasi (invoice jatuh tempo, stok rendah, …) — port jadwal
 * {@code '0 7 * * *'} di {@code jobs/scheduler.ts}. Deduplikasi 24 jam di tiap pemeriksaan membuat job ini
 * aman diulang bila sempat terlewat; advisory lock memastikan hanya satu replika yang menjalankan.
 * Pengingat pertanggungjawaban pengajuan pembayaran (bagian lain dari tick yang sama di sumber) dijadwalkan
 * modul pengajuan pembayaran sendiri.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpNotificationDailyJob {

    private final ErpJobLock jobLock;
    private final ErpNotificationCheckRunner runner;

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Jakarta")
    public void runDaily() {
        jobLock.runExclusive("erp-notification-checks", () -> {
            int created = runner.run();
            if (created > 0) {
                log.info("Notification checks daily run: created={}", created);
            }
        });
    }
}
