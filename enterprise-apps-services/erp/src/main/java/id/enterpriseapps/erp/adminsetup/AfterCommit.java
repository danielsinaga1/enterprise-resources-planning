package id.enterpriseapps.erp.adminsetup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Menjalankan aksi setelah transaksi db_erp selesai.
 *
 * <p>Cache izin/aktor harus dibuang SETELAH commit: bila dibuang di tengah transaksi, request lain
 * bisa memuat ulang cache dari data lama (belum commit) dan menyimpannya sampai invalidasi berikutnya.
 * Sama halnya berkas logo lama baru boleh dihapus setelah baris pengaturan yang baru tersimpan.
 */
@Slf4j
final class AfterCommit {

    private AfterCommit() {
    }

    /** Jalankan setelah commit (langsung bila tidak ada transaksi aktif). */
    static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safely(action);
                }
            });
        } else {
            safely(action);
        }
    }

    /** Jalankan bila transaksi di-rollback (mis. hapus berkas yang terlanjur ditulis). */
    static void onRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        safely(action);
                    }
                }
            });
        }
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("ERP adminsetup: aksi pasca-transaksi gagal: {}", e.getMessage());
        }
    }
}
