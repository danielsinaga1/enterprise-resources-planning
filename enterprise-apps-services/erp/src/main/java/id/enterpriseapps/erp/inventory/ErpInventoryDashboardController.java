package id.enterpriseapps.erp.inventory;

import id.enterpriseapps.erp.core.ErpException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Dashboard gudang & produksi — port {@code routes/inventory/dashboard.ts}. Gerbang
 * {@code requirePerm('inventory')} dijalankan ErpRouteGuard.
 */
@RestController
@RequestMapping("/api/erp/inventory/dashboard")
@RequiredArgsConstructor
public class ErpInventoryDashboardController {

    private final ErpInventoryDashboardService service;

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return service.metrics();
    }

    @GetMapping("/by-category")
    public List<Map<String, Object>> byCategory() {
        return service.byCategory();
    }

    @GetMapping("/stock-value")
    public Map<String, Object> stockValue(@RequestParam(required = false) String groupBy) {
        String g = InventoryDashboardCalc.parseGroupBy(groupBy);
        if (g == null) {
            throw ErpException.business("Pengelompokan harus category atau warehouse.");
        }
        return service.stockValue(g);
    }

    @GetMapping("/movement-trend")
    public List<Map<String, Object>> movementTrend() {
        return service.movementTrend();
    }

    @GetMapping("/top-items")
    public List<Map<String, Object>> topItems() {
        return service.topItems();
    }

    @GetMapping("/recent-movements")
    public List<Map<String, Object>> recentMovements() {
        return service.recentMovements();
    }

    @GetMapping("/production-stats")
    public Map<String, Object> productionStats() {
        return service.productionStats();
    }

    @GetMapping("/production")
    public Map<String, Object> production(@RequestParam(required = false) String itemId,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate) {
        // itemId kosong = tanpa filter (falsy di sumber); tanggal kosong/tidak valid tetap diteruskan (sumber → 500).
        return service.production(itemId == null || itemId.isEmpty() ? null : itemId, startDate, endDate);
    }

    @GetMapping("/yield-trend")
    public Map<String, Object> yieldTrend() {
        return service.yieldTrend();
    }

    @GetMapping("/oee")
    public Map<String, Object> oee(@RequestParam(required = false) String startDate,
                                   @RequestParam(required = false) String endDate) {
        return service.oee(startDate, endDate);
    }
}
