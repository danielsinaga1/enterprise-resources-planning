package id.enterpriseapps.erp.purchase;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Laporan pembelian di router laporan — port {@code routes/reports/purchasing.ts}
 * ({@code /api/reports/po-outstanding}, {@code /api/reports/supplier-performance}).
 * Gerbang modul {@code reports} dijalankan ErpRouteGuard. Hanya dua sub-path ini.
 */
@RestController
@RequestMapping("/api/erp/reports")
@RequiredArgsConstructor
public class ErpPurchaseReportController {

    private final ErpPurchaseReportService reports;

    /** Query: startDate, endDate, partyId, warehouseId, overdue=1 → { rows, summary }. */
    @GetMapping("/po-outstanding")
    public Map<String, Object> poOutstanding(@RequestParam Map<String, String> query) {
        return reports.poOutstanding(query);
    }

    /** Query: startDate, endDate, partyId → { rows }. */
    @GetMapping("/supplier-performance")
    public Map<String, Object> supplierPerformance(@RequestParam Map<String, String> query) {
        return reports.supplierPerformance(query);
    }
}
