package id.enterpriseapps.erp.report;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Laporan operasional — port {@code routes/reports/operational.ts} (di-mount di bawah
 * {@code /api/erp/reports}; gerbang modul {@code reports} oleh ErpRouteGuard).
 */
@RestController
@RequestMapping("/api/erp/reports")
@RequiredArgsConstructor
public class ErpOperationalReportController {

    private final ErpOperationalReportService reports;
    private final ErpDailyReportService daily;

    /** Query: startDate, endDate → { period, items, summary }. */
    @GetMapping("/hpp")
    public Map<String, Object> hpp(@RequestParam Map<String, String> query) {
        return reports.hpp(query);
    }

    /** Query: startDate, endDate → { period, items, summary }. */
    @GetMapping("/profitability-per-sku")
    public Map<String, Object> profitabilityPerSku(@RequestParam Map<String, String> query) {
        return reports.profitabilityPerSku(query);
    }

    /** Query: startDate, endDate, warehouseId → { period, summary, total, runs }. */
    @GetMapping("/production-cost-summary")
    public Map<String, Object> productionCostSummary(@RequestParam Map<String, String> query) {
        return reports.productionCostSummary(query);
    }

    @GetMapping("/payable-schedule")
    public Map<String, Object> payableSchedule() {
        return reports.payableSchedule();
    }

    @GetMapping("/receivable-schedule")
    public Map<String, Object> receivableSchedule() {
        return reports.receivableSchedule();
    }

    /** Query: date (default hari ini UTC) → laporan harian terkonsolidasi. */
    @GetMapping("/daily")
    public Map<String, Object> daily(@RequestParam Map<String, String> query) {
        return daily.daily(query);
    }
}
