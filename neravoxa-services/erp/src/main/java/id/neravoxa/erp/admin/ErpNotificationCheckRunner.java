package id.neravoxa.erp.admin;

import id.neravoxa.erp.core.notification.ErpNotificationCheck;
import id.neravoxa.erp.core.notification.ErpNotificationService;
import id.neravoxa.erp.core.security.ErpPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pemeriksaan notifikasi terjadwal — port {@code runNotificationChecks()} (services/notificationService.ts).
 *
 * <p>Isi pemeriksaannya (invoice jatuh tempo, stok rendah, uang jaminan, PO terlambat, …) adalah bean
 * {@link ErpNotificationCheck} milik modul domain; di sini hanya penentuan penerima, jendela deduplikasi
 * 24 jam, dan penjumlahan hasil. Dipakai {@code POST /notifications/check} dan job harian 07:00 WIB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpNotificationCheckRunner {

    /** Padanan role "Accountant" ENTARIS (role "Admin" = semua role super-admin ERP). */
    static final String ACCOUNTANT_ROLE = "ERP_ACCOUNTANT";

    private final ObjectProvider<ErpNotificationCheck> checks;
    private final ErpNotificationService notifications;
    private final ErpPermissionService permissions;

    /** Jalankan semua pemeriksaan; kembalikan jumlah notifikasi baru. Tidak pernah melempar. */
    public int run() {
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofHours(24));
        Set<String> roles = new LinkedHashSet<>(permissions.superAdminRoleKeys());
        roles.add(ACCOUNTANT_ROLE);
        List<String> userIds = notifications.activeUserIdsWithRoles(roles);
        if (userIds.isEmpty()) {
            return 0;
        }
        int created = 0;
        for (ErpNotificationCheck check : checks.orderedStream().toList()) {
            try {
                created += check.run(userIds, now, since);
            } catch (RuntimeException e) {
                log.error("Pemeriksaan notifikasi {} gagal (diabaikan): {}", check.getClass().getSimpleName(), e.getMessage());
            }
        }
        return created;
    }
}
