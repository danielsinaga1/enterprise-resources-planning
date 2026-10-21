package id.enterpriseapps.erp.report;

import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpAccountBalanceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Preview & perbaikan otomatis health check — port {@code routes/health/fixes.ts}. Tiap fix
 * berjalan dalam SATU transaksi (gagal = rollback penuh); saldo akun dihitung ulang lewat kernel
 * {@link ErpAccountBalanceService#recalculateAccountBalances} (sumber: ALE aktif).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpHealthFixService {

    private final ErpAccountBalanceService balances;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    record FixResult(long fixed, String message, Map<String, Object> snapshot) {
    }

    private long count(String sql) {
        return ReportRows.lng(em.createNativeQuery(sql).getSingleResult());
    }

    private static Map<String, Object> preview(String description, String warning, long count, List<?> details) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("description", description);
        if (warning != null) {
            m.put("warning", warning);
        }
        m.put("count", count);
        m.put("details", details);
        return m;
    }

    private static Map<String, Object> detail(String action, long jumlah) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", action);
        m.put("jumlah", jumlah);
        return m;
    }

    private static String idNumber(long n) {
        return Money.idr(BigDecimal.valueOf(n));
    }

    // ─────────────────────────────── preview ───────────────────────────────

    /** Preview ale-sync: jumlah ALE yang dihapus & dibuat ulang. */
    @ErpTx(readOnly = true)
    public Map<String, Object> previewAleSync() {
        long deleteCount = count("SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_type = 'JournalEntry'");
        long createCount = count("SELECT COUNT(*) FROM journal_entry_accounts");
        long jeTotal = count("SELECT COUNT(*) FROM journal_entries");
        return preview("Akan MENGHAPUS " + idNumber(deleteCount) + " baris ALE referensi JournalEntry, lalu membuat ulang "
                        + idNumber(createCount) + " baris dari " + idNumber(jeTotal) + " jurnal.",
                "Operasi ini intensif (mungkin 1-2 menit) dan akan menulis ulang seluruh buku besar dari jurnal. "
                        + "Pastikan tidak ada transaksi berjalan.",
                deleteCount,
                List.of(detail("Hapus ALE JournalEntry", deleteCount),
                        detail("Buat ulang dari JournalItem", createCount),
                        detail("Journal entries diproses", jeTotal)));
    }

    /** Preview account-balance-drift: akun yang saldonya akan diubah (maks. 50). */
    @ErpTx(readOnly = true)
    public Map<String, Object> previewAccountBalanceDrift() {
        List<Map<String, Object>> drifts = driftedAccountsText();
        return preview(drifts.isEmpty()
                        ? "Tidak ada akun yang drift. Semua saldo sudah sesuai dengan jurnal. Klik \"Perbaiki\" hanya akan "
                        + "menghitung ulang tanpa mengubah nilai."
                        : "Akan MENGUBAH saldo " + drifts.size() + " akun berikut agar sesuai dengan hasil hitung dari jurnal:",
                null, drifts.size(), drifts);
    }

    /** Preview orphan-ale: entri yang akan ditandai cancelled (maks. 100). */
    @ErpTx(readOnly = true)
    public Map<String, Object> previewOrphanAle() {
        List<Map<String, Object>> orphans = ReportRows.maps(em, HealthSql.ORPHAN_ALE_PREVIEW, null,
                "ale_id", "reference_type", "reference_id", "account_code", "account_name", "debit", "credit");
        return preview(orphans.isEmpty()
                        ? "Tidak ada ALE orphan. Tidak ada yang akan berubah."
                        : "Akan MENANDAI " + orphans.size() + " entri ALE berikut sebagai cancelled (tidak dihapus):",
                null, orphans.size(), orphans);
    }

    private List<Map<String, Object>> driftedAccountsText() {
        return ReportRows.maps(em, HealthSql.BALANCE_DRIFT_TEXT, null,
                "account_code", "account_name", "root_type", "stored_balance", "computed_balance", "drift");
    }

    // ─────────────────────────────── fix ───────────────────────────────

    /** Hapus semua ALE referensi JournalEntry lalu buat ulang dari journal item (jurnal batal → ALE batal). */
    @ErpTx(timeout = 120)
    public FixResult fixAleSync() {
        em.flush();
        int deleted = em.createNativeQuery("DELETE FROM accounting_ledger_entries WHERE reference_type = 'JournalEntry'")
                .executeUpdate();
        long journals = count("SELECT COUNT(*) FROM journal_entries");
        int created = em.createNativeQuery(HealthSql.REBUILD_JOURNAL_ALE).executeUpdate();
        em.clear();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("deletedCount", deleted);
        snapshot.put("createdCount", created);
        snapshot.put("journalEntriesProcessed", journals);
        return new FixResult(created, "Dihapus " + deleted + " ALE lama, dibuat " + created + " ALE baru dari JournalItem",
                snapshot);
    }

    /** Hitung ulang saldo semua akun dari ledger; snapshot akun yang drift sebelum perbaikan. */
    @ErpTx(timeout = 60)
    public FixResult fixAccountBalanceDrift() {
        List<Map<String, Object>> before = ReportRows.maps(em, HealthSql.BALANCE_DRIFT_SNAPSHOT, null,
                "account_code", "account_name", "root_type", "stored_balance", "computed_balance");
        balances.recalculateAccountBalances(null);
        long accounts = em.createQuery("select count(a) from Account a", Long.class).getSingleResult();
        em.clear();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("driftedAccountsBefore", before);
        snapshot.put("totalDrifted", before.size());
        snapshot.put("totalAccountsRecalc", accounts);
        return new FixResult(accounts, "Dihitung ulang saldo " + accounts + " akun dari jurnal", snapshot);
    }

    /** Tandai ALE orphan sebagai cancelled (tidak dihapus); snapshot entri terdampak. */
    @ErpTx(timeout = 60)
    public FixResult fixOrphanAle() {
        List<Map<String, Object>> before = ReportRows.maps(em, HealthSql.ORPHAN_ALE_SNAPSHOT, null,
                "ale_id", "reference_type", "reference_id", "account_code", "debit", "credit");
        int result = em.createNativeQuery(HealthSql.CANCEL_ORPHAN_ALE).executeUpdate();
        em.clear();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orphansAffected", before);
        snapshot.put("totalAffected", result);
        return new FixResult(result, result + " entri ALE orphan ditandai sebagai cancelled", snapshot);
    }

    // ─────────────────────────────── audit ───────────────────────────────

    /**
     * Catat aksi fix ke {@code audit_trail} (action FIX_* / FIX_*_FAILED, entityType health-check)
     * dalam transaksi tersendiri — sesudah fix COMMIT/rollback, seperti {@code auditLog.create} di sumber.
     */
    @ErpTx(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(String userId, String action, String check, Map<String, Object> newValues,
                            String ip, String userAgent) {
        em.createNativeQuery("INSERT INTO audit_trail (id, user_id, action, entity_type, entity_id, new_values, "
                        + "ip_address, user_agent, created_at) VALUES (:id, :user, :action, 'health-check', :check, "
                        + "CAST(:values AS jsonb), CAST(:ip AS text), CAST(:ua AS text), CAST(:ts AS timestamp))")
                .setParameter("id", UUID.randomUUID().toString())
                .setParameter("user", userId)
                .setParameter("action", action)
                .setParameter("check", check)
                .setParameter("values", ErpJson.stringify(newValues))
                .setParameter("ip", ip)
                .setParameter("ua", userAgent)
                .setParameter("ts", ReportDates.sqlTs(Instant.now()))
                .executeUpdate();
    }
}
