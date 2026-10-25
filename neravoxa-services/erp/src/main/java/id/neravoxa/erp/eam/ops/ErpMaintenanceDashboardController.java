package id.neravoxa.erp.eam.ops;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.core.security.ErpPermissionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * GET /api/erp/dashboard/maintenance — port {@code routes/dashboardMaintenance.ts}. Rute ini OPEN di
 * ErpRouteGuard (bukan gerbang 'reports'): akses diputuskan per sumber modul (ga_maintenance /
 * ga_vehicle / ga_documents) dan hanya sumber yang diizinkan yang dimuat & dikembalikan.
 * Terjemahan Inggris (translateFields) di luar cakupan port.
 */
@Slf4j
@RestController
@RequestMapping("/api/erp/dashboard/maintenance")
@RequiredArgsConstructor
@Tag(name = "ERP - Dashboard Pemeliharaan", description = "Jadwal, WO, pajak/asuransi kendaraan, dokumen legal")
public class ErpMaintenanceDashboardController {

    private final ErpMaintenanceDashboardService service;
    private final ErpAccess access;

    @GetMapping
    public Map<String, Object> dashboard() {
        boolean maintenance = access.can("ga_maintenance", ErpPermissionService.VIEW);
        boolean vehicle = access.can("ga_vehicle", ErpPermissionService.VIEW);
        boolean documents = access.can("ga_documents", ErpPermissionService.VIEW);
        if (!maintenance && !vehicle && !documents) {
            throw ErpException.forbidden("Akses ditolak: tidak punya izin modul pemeliharaan.");
        }
        try {
            return service.load(new MaintenanceDashboardCalc.Access(maintenance, vehicle, documents), Instant.now());
        } catch (RuntimeException e) {
            log.error("GET /dashboard/maintenance error", e);
            throw ErpException.of(500, "Gagal mengambil data pemeliharaan.");
        }
    }
}
