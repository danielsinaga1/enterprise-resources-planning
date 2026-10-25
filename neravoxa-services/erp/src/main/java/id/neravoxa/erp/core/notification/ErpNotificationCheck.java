package id.neravoxa.erp.core.notification;

import java.time.Instant;
import java.util.List;

/**
 * Satu pemeriksaan notifikasi terjadwal (padanan bagian-bagian {@code runNotificationChecks()} di
 * notificationService.ts: invoice jatuh tempo, stok rendah, uang jaminan, PO terlambat, …).
 *
 * <p>Modul domain mendaftarkan bean implementasi; job harian & {@code POST /api/erp/notifications/check}
 * menjalankan semuanya. Tidak boleh melempar — kembalikan jumlah notifikasi yang dibuat.
 */
public interface ErpNotificationCheck {

    /**
     * @param userIds penerima (pengguna aktif dengan role keuangan/admin)
     * @param now     waktu pemeriksaan
     * @param since   batas deduplikasi (now − 24 jam): jangan buat ulang notifikasi yang sudah ada sejak ini
     */
    int run(List<String> userIds, Instant now, Instant since);
}
