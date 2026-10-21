package id.enterpriseapps.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Laporan keuangan — port {@code routes/reports.ts} di {@code /api/erp/reports}. Gerbang modul
 * {@code reports} (GET view, POST export edit) dijalankan ErpRouteGuard. {@code /po-outstanding} dan
 * {@code /supplier-performance} milik {@code ErpPurchaseReportController}; laporan operasional di
 * {@link ErpOperationalReportController}.
 */
@RestController
@RequestMapping("/api/erp/reports")
@RequiredArgsConstructor
public class ErpFinancialReportController {

    private final ErpFinancialReportService reports;

    /** Query: startDate, endDate → baris neraca saldo. */
    @GetMapping("/trial-balance")
    public List<Map<String, Object>> trialBalance(@RequestParam Map<String, String> query) {
        return reports.trialBalance(query);
    }

    /** Query: startDate, endDate → { revenue, expense, totalRevenue, totalExpense, netProfit }. */
    @GetMapping("/profit-loss")
    public Map<String, Object> profitLoss(@RequestParam Map<String, String> query) {
        return reports.profitLoss(query);
    }

    /** Query: date → { assets, liabilities, equity, totalAssets, totalLiabilities, totalEquity }. */
    @GetMapping("/balance-sheet")
    public Map<String, Object> balanceSheet(@RequestParam Map<String, String> query) {
        return reports.balanceSheet(query);
    }

    /** Query: startDate, endDate → { operating, investing, financing, netChange, details }. */
    @GetMapping("/cash-flow")
    public Map<String, Object> cashFlow(@RequestParam Map<String, String> query) {
        return reports.cashFlow(query);
    }

    /** Query: type=Customer|Supplier → [{ name, 1, 31, 61, 91, current, total }]. */
    @GetMapping("/aging")
    public List<Map<String, Object>> aging(@RequestParam Map<String, String> query) {
        return reports.aging(query);
    }

    /** Query: accountId (wajib), startDate, endDate. */
    @GetMapping("/ledger-detail")
    public Map<String, Object> ledgerDetail(@RequestParam Map<String, String> query) {
        return reports.ledgerDetail(query);
    }

    /** Query: startDate, endDate → buku besar per akun. */
    @GetMapping("/ledger-book")
    public List<Map<String, Object>> ledgerBook(@RequestParam Map<String, String> query) {
        return reports.ledgerBook(query);
    }

    /** Body { filename, data } → unduhan .xlsx (tidak dibungkus Response). */
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody(required = false) JsonNode body) {
        ErpFinancialReportService.Export file = reports.export(body);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, ReportExcel.CONTENT_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .body(file.content());
    }
}
