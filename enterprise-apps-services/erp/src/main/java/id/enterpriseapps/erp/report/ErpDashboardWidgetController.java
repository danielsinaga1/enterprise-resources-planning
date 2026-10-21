package id.enterpriseapps.erp.report;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Widget dashboard — port {@code routes/dashboard.ts} + {@code routes/dashboard/fixedAssets.ts} di
 * {@code /api/erp/dashboard}. Gerbang (ErpRouteGuard): {@code reports}, kecuali stock-alerts &
 * negative-stock (reports ATAU inventory). {@code /dashboard/maintenance} milik slice EAM.
 */
@RestController
@RequestMapping("/api/erp/dashboard")
@RequiredArgsConstructor
public class ErpDashboardWidgetController {

    private final ErpFinanceDashboardService finance;
    private final ErpOperationsDashboardService operations;
    private final ErpFixedAssetDashboardService fixedAssets;

    /** Query: startDate, endDate (periode laba; default bulan ini). */
    @GetMapping("/metrics")
    public Map<String, Object> metrics(@RequestParam Map<String, String> query) {
        return finance.metrics(query);
    }

    /** Query: startDate, endDate (default 6 bulan) → [{ name, pendapatan, beban }]. */
    @GetMapping("/charts")
    public List<Map<String, Object>> charts(@RequestParam Map<String, String> query) {
        return finance.charts(query);
    }

    @GetMapping("/top-customers")
    public List<Map<String, Object>> topCustomers() {
        return finance.topCustomers();
    }

    @GetMapping("/overdue")
    public List<Map<String, Object>> overdue() {
        return finance.overdue();
    }

    @GetMapping("/upcoming-due")
    public List<Map<String, Object>> upcomingDue() {
        return finance.upcomingDue();
    }

    /** Query: startDate, endDate (default bulan ini). */
    @GetMapping("/expense-breakdown")
    public List<Map<String, Object>> expenseBreakdown(@RequestParam Map<String, String> query) {
        return finance.expenseBreakdown(query);
    }

    @GetMapping("/stock-alerts")
    public List<Map<String, Object>> stockAlerts() {
        return operations.stockAlerts();
    }

    @GetMapping("/pending-do")
    public List<Map<String, Object>> pendingDeliveryOrders() {
        return operations.pendingDeliveryOrders();
    }

    @GetMapping("/negative-stock")
    public List<Map<String, Object>> negativeStock() {
        return operations.negativeStock();
    }

    /** Query: startDate, endDate (default bulan ini) → { days, totalIn, totalOut, net }. */
    @GetMapping("/cash-flow")
    public Map<String, Object> cashFlow(@RequestParam Map<String, String> query) {
        return finance.cashFlow(query);
    }

    @GetMapping("/financial-ratios")
    public Map<String, Object> financialRatios() {
        return finance.financialRatios();
    }

    /** Query: startDate, endDate (default 12 bulan) → [{ name, revenue, expense, profit }]. */
    @GetMapping("/monthly-profit")
    public List<Map<String, Object>> monthlyProfit(@RequestParam Map<String, String> query) {
        return finance.monthlyProfit(query);
    }

    @GetMapping("/aging-summary")
    public Map<String, Object> agingSummary() {
        return finance.agingSummary();
    }

    @GetMapping("/cash-position")
    public Map<String, Object> cashPosition() {
        return finance.cashPosition();
    }

    @GetMapping("/top-vendors")
    public List<Map<String, Object>> topVendors() {
        return finance.topVendors();
    }

    /** { summary, byCategory, topAssets, depreciationDue }. */
    @GetMapping({"/fixed-assets", "/fixed-assets/"})
    public Map<String, Object> fixedAssets() {
        return fixedAssets.dashboard();
    }
}
