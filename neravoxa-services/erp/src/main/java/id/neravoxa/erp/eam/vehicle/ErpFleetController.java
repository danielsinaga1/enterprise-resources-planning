package id.neravoxa.erp.eam.vehicle;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Daftar, ringkasan dan laporan armada — port {@code routes/ga/vehicle.ts} bagian fleet
 * (mount {@code /api/ga/vehicle}, gerbang modul {@code ga_vehicle} oleh ErpRouteGuard).
 */
@RestController
@RequestMapping("/api/erp/ga/vehicle")
@RequiredArgsConstructor
public class ErpFleetController {

    private final ErpFleetService fleet;
    private final ErpFleetReportService reports;

    /**
     * Daftar FleetRow berhalaman untuk aset di kategori {@code categoryKind} (default vehicle) + status
     * jatuh tempo per kewajiban. Query: page, limit (≤200), id, search/q, status, due, doc, sort.
     */
    @GetMapping("/fleet")
    public Map<String, Object> fleet(@RequestParam Map<String, String> query) {
        return fleet.fleet(query);
    }

    @GetMapping("/fleet-summary")
    public Map<String, Object> fleetSummary(@RequestParam Map<String, String> query) {
        return fleet.summary(query);
    }

    /** Laporan satu kendaraan: TCO, trend bulanan, kepatuhan, kondisi. Query from/to (YYYY-MM-DD). */
    @GetMapping("/assets/{id}/report")
    public Map<String, Object> assetReport(@PathVariable String id, @RequestParam Map<String, String> query) {
        return reports.assetReport(id, query.get("from"), query.get("to"));
    }

    /** Laporan armada: biaya per kendaraan, distribusi, top spender, trend, kepatuhan. */
    @GetMapping("/fleet-report")
    public Map<String, Object> fleetReport(@RequestParam Map<String, String> query) {
        return reports.fleetReport(query.get("from"), query.get("to"), query.get("categoryId"));
    }
}
