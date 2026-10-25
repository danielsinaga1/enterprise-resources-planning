package id.neravoxa.erp.cashbank;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.BankReconciliation;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Filter + urutan daftar rekonsiliasi bank — port {@code services/reconciliationList.ts}.
 * GET /reconciliation?page&amp;limit&amp;accountId&amp;status&amp;startDate&amp;endDate&amp;search&amp;sort → { data, total, page, limit }.
 * Hanya baca: tidak menyentuh pencocokan, penyelesaian, atau data ledger.
 */
final class ReconciliationList {

    static final List<String> STATUSES = List.of("Draft", "Completed");
    static final List<String> SORT_FIELDS = List.of("statementDate", "account", "bookBalance", "statementBalance", "createdAt");
    /** Urutan yang selalu dipakai daftar. */
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("statementDate", true);

    private ReconciliationList() {
    }

    /**
     * Filter: accountId, status (Draft | Completed), startDate/endDate (tanggal statement, YYYY-MM-DD, hari
     * zona bisnis), search (nama/nomor akun atau catatan). Nilai tak valid → 400.
     */
    static ErpQuery<BankReconciliation> query(EntityManager em, Map<String, String> query) {
        String status = ListQuery.str(query, "status");
        if (status != null && !STATUSES.contains(status)) {
            throw ErpException.business("Status rekonsiliasi tidak dikenal.");
        }
        String accountId = ListQuery.str(query, "accountId");
        String startDate = ListQuery.str(query, "startDate");
        String endDate = ListQuery.str(query, "endDate");
        if ((startDate != null && !ErpDates.isIsoDate(startDate)) || (endDate != null && !ErpDates.isIsoDate(endDate))) {
            throw ErpException.business("Format tanggal harus YYYY-MM-DD.");
        }
        Instant[] range = ErpDates.localDayRange(startDate, endDate);
        String search = ListQuery.str(query, "search");

        return ErpQuery.of(em, BankReconciliation.class, "r")
                .join("left join r.account a")
                .fetch("left join fetch r.account")
                .whereIf(status != null, "r.status = :status", "status", status)
                .whereIf(accountId != null, "r.accountId = :accountId", "accountId", accountId)
                .whereIf(range[0] != null, "r.statementDate >= :gte", "gte", range[0])
                .whereIf(range[1] != null, "r.statementDate < :lt", "lt", range[1])
                .whereIf(search != null, "(lower(a.name) like :s or lower(a.accountNumber) like :s or lower(r.notes) like :s)",
                        "s", search == null ? null : ErpQuery.contains(search));
    }

    /** ORDER BY whitelist dengan pemecah seri stabil agar baris tidak berpindah halaman. */
    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        String primary = "account".equals(sort.field()) ? "a.name " + dir : "r." + sort.field() + " " + dir;
        String tieBreakers = "statementDate".equals(sort.field())
                ? "r.createdAt " + dir
                : "r.statementDate desc, r.createdAt desc";
        return primary + ", " + tieBreakers + ", r.id " + dir;
    }
}
