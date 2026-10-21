package id.enterpriseapps.erp.report;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard diagnostik integritas data — port {@code routes/healthCheck.ts} +
 * {@code routes/health/checks.ts}. Gerbang modul {@code audit} (GET view, POST fix edit) oleh
 * ErpRouteGuard. Respons dibungkus {@code { success, data }} persis seperti sumber.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpHealthCheckService {

    /** Check yang bisa diperbaiki otomatis → action audit. */
    private static final Map<String, String> FIX_ACTIONS = Map.of(
            "ale-sync", "FIX_ALE_SYNC",
            "account-balance-drift", "FIX_ACCOUNT_BALANCE_DRIFT",
            "orphan-ale", "FIX_ORPHAN_ALE");

    private final ErpHealthFixService fixes;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Konteks request untuk audit fix. */
    public record Caller(String userId, String ip, String userAgent) {
    }

    private static Map<String, Object> result(String name, String status, String message, int count,
                                              List<Map<String, Object>> details, boolean fixable) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("status", status);
        m.put("message", message);
        m.put("count", count);
        m.put("details", details);
        m.put("fixable", fixable);
        return m;
    }

    private static Map<String, Object> envelope(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("data", data);
        return m;
    }

    private static List<Map<String, Object>> first(List<Map<String, Object>> rows, int n) {
        return new ArrayList<>(rows.subList(0, Math.min(n, rows.size())));
    }

    // ─────────────────────────────── checks ───────────────────────────────

    /** 1. Total journal item vs ALE per akun. */
    Map<String, Object> checkAleSync() {
        List<Map<String, Object>> rows = ReportRows.maps(em, HealthSql.ALE_SYNC, null,
                "account_id", "account_code", "account_name", "ji_debit", "ji_credit", "ale_debit", "ale_credit");
        boolean bad = !rows.isEmpty();
        return result("ALE vs JournalItem Sync", bad ? "error" : "ok",
                bad ? rows.size() + " akun memiliki perbedaan antara ALE dan JournalItem" : "ALE dan JournalItem sinkron",
                rows.size(), first(rows, 20), true);
    }

    /** 2. accounts.balance vs saldo hasil hitung dari journal item. */
    Map<String, Object> checkAccountBalanceDrift() {
        List<Map<String, Object>> rows = ReportRows.maps(em, HealthSql.BALANCE_DRIFT, null,
                "account_id", "account_code", "account_name", "root_type", "stored_balance", "computed_balance", "drift");
        boolean bad = !rows.isEmpty();
        return result("Account Balance Drift", bad ? "warning" : "ok",
                bad ? rows.size() + " akun memiliki saldo yang tidak sesuai dengan jurnal" : "Semua saldo akun sesuai",
                rows.size(), rows, true);
    }

    /** 3. Jurnal Submitted yang debit ≠ kredit. */
    Map<String, Object> checkUnbalancedJournals() {
        List<Map<String, Object>> rows = ReportRows.maps(em, HealthSql.UNBALANCED_JOURNALS, null,
                "journal_id", "entry_number", "total_debit", "total_credit", "difference");
        boolean bad = !rows.isEmpty();
        return result("Unbalanced Journal Entries", bad ? "error" : "ok",
                bad ? rows.size() + " jurnal tidak balance (debit ≠ kredit)" : "Semua jurnal balance",
                rows.size(), rows, false);
    }

    /** 4. ALE yang record sumbernya sudah tidak ada. */
    Map<String, Object> checkOrphanAle() {
        List<Map<String, Object>> rows = ReportRows.maps(em, HealthSql.ORPHAN_ALE, null,
                "ale_id", "reference_type", "reference_id", "account_code", "debit", "credit");
        boolean bad = !rows.isEmpty();
        return result("Orphan ALE Entries", bad ? "warning" : "ok",
                bad ? rows.size() + " entri ALE merujuk ke record yang tidak ada" : "Tidak ada ALE orphan",
                rows.size(), first(rows, 20), true);
    }

    /** 5. Stok item vs jumlah gerakan stok. */
    Map<String, Object> checkStockMovementMismatch() {
        List<Map<String, Object>> rows = ReportRows.maps(em, HealthSql.STOCK_MOVEMENT_MISMATCH, null,
                "item_id", "item_name", "code", "stored_stock", "computed_stock", "difference");
        boolean bad = !rows.isEmpty();
        return result("Stock vs Movement Mismatch", bad ? "warning" : "ok",
                bad ? rows.size() + " produk memiliki stok tidak sesuai dengan movement" : "Semua stok produk sesuai",
                rows.size(), rows, false);
    }

    /** 6. inventory_items.current_stock vs Σ inventory_stocks per gudang. */
    Map<String, Object> checkWarehouseStockDrift() {
        List<Map<String, Object>> rows = ReportRows.maps(em, HealthSql.WAREHOUSE_STOCK_DRIFT, null,
                "item_id", "item_name", "code", "item_total", "warehouse_sum", "difference");
        boolean bad = !rows.isEmpty();
        return result("Drift Stok per Gudang", bad ? "warning" : "ok",
                bad ? rows.size() + " item: total inventory_items ≠ SUM(inventory_stocks per gudang)"
                        : "Total stok per item konsisten dengan saldo per gudang",
                rows.size(), rows, false);
    }

    /** GET /health-check — semua check + status keseluruhan (error &gt; warning &gt; ok). */
    @ErpTx(readOnly = true)
    public Map<String, Object> runAll() {
        List<Map<String, Object>> checks = List.of(checkAleSync(), checkAccountBalanceDrift(), checkUnbalancedJournals(),
                checkOrphanAle(), checkStockMovementMismatch(), checkWarehouseStockDrift());
        String status = checks.stream().anyMatch(c -> "error".equals(c.get("status"))) ? "error"
                : checks.stream().anyMatch(c -> "warning".equals(c.get("status"))) ? "warning" : "ok";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("checks", checks);
        return envelope(data);
    }

    // ─────────────────────────────── preview & fix ───────────────────────────────

    /** GET /health-check/preview/:check — apa yang akan diubah fix. */
    public Map<String, Object> preview(String check) {
        Map<String, Object> preview = switch (check) {
            case "ale-sync" -> fixes.previewAleSync();
            case "account-balance-drift" -> fixes.previewAccountBalanceDrift();
            case "orphan-ale" -> fixes.previewOrphanAle();
            default -> throw ErpException.business("Check tidak dapat di-preview").with("success", false);
        };
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("check", check);
        data.putAll(preview);
        return envelope(data);
    }

    /**
     * POST /health-check/fix/:check — jalankan fix (transaksi sendiri) lalu catat audit
     * FIX_* (atau FIX_*_FAILED bila gagal; respons 500 seperti sumber).
     */
    public Map<String, Object> fix(String check, Caller caller) {
        String action = FIX_ACTIONS.get(check);
        if (action == null) {
            throw ErpException.business("Check tidak dapat diperbaiki otomatis").with("success", false);
        }
        Instant startedAt = Instant.now();
        ErpHealthFixService.FixResult result;
        try {
            result = switch (check) {
                case "ale-sync" -> fixes.fixAleSync();
                case "account-balance-drift" -> fixes.fixAccountBalanceDrift();
                default -> fixes.fixOrphanAle();
            };
        } catch (RuntimeException e) {
            log.error("Health check fix failed: {}", check, e);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("check", check);
            values.put("error", e.getMessage());
            values.put("startedAt", startedAt);
            values.put("finishedAt", Instant.now());
            audit(caller, action + "_FAILED", check, values);
            throw ErpException.of(500, "Gagal memperbaiki masalah").with("success", false);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("check", check);
        values.put("fixed", result.fixed());
        values.put("message", result.message());
        values.put("snapshot", result.snapshot());
        values.put("startedAt", startedAt);
        values.put("finishedAt", Instant.now());
        audit(caller, action, check, values);
        log.info("Health check fix applied check={} userId={} fixed={}", check, caller.userId(), result.fixed());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fixed", result.fixed());
        data.put("message", result.message());
        return envelope(data);
    }

    /** Kegagalan menulis audit tidak menggagalkan respons (sumber: {@code .catch(logger.error)}). */
    private void audit(Caller caller, String action, String check, Map<String, Object> values) {
        if (caller.userId() == null) {
            return;
        }
        try {
            fixes.recordAudit(caller.userId(), action, check, values, caller.ip(), caller.userAgent());
        } catch (RuntimeException e) {
            log.error("Audit log write failed for health fix {}: {}", action, e.getMessage());
        }
    }
}
