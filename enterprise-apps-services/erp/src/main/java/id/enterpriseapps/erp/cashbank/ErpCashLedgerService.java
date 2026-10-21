package id.enterpriseapps.erp.cashbank;

import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Daftar "Transaksi Kas &amp; Bank" berpaginasi server (GET /payments/ledger) dan ringkasan KPI-nya
 * (GET /payments/ledger/summary) — port {@code listCashLedger} / {@code summarizeCashLedgerQuery}.
 */
@Service
public class ErpCashLedgerService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Query: page, limit (≤500, default 25), search, type, direction, status, partyId, accountId, startDate, endDate, sort. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 25, 500);
        CashLedger.Filter filter = CashLedger.buildFilter(query);
        ListQuery.Sort sort = ListQuery.sort(ListQuery.str(query, "sort"), CashLedger.SORT_FIELDS, CashLedger.DEFAULT_SORT);
        CashLedger.Where where = CashLedger.where(filter);

        Query rowsQuery = em.createNativeQuery(CashLedger.CTE + CashLedger.SELECT_COLUMNS + where.sql()
                + CashLedger.orderBy(sort) + "LIMIT :take OFFSET :skip");
        where.params().forEach(rowsQuery::setParameter);
        rowsQuery.setParameter("take", paging.limit());
        rowsQuery.setParameter("skip", paging.skip());
        @SuppressWarnings("unchecked")
        List<Object[]> rows = rowsQuery.getResultList();

        Query countQuery = em.createNativeQuery(CashLedger.CTE + "SELECT CAST(count(*) AS int) AS total FROM ledger l " + where.sql());
        where.params().forEach(countQuery::setParameter);
        Number total = (Number) countQuery.getSingleResult();

        return ListQuery.result(rows.stream().map(CashLedger::mapRow).toList(), total == null ? 0 : total.longValue(), paging);
    }

    /** KPI (jumlah, uang masuk/keluar, net) untuk filter yang sama dengan daftar. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        CashLedger.Where where = CashLedger.where(CashLedger.buildFilter(query));
        Query q = em.createNativeQuery(CashLedger.CTE
                + "SELECT l.direction, l.status, CAST(count(*) AS int) AS count, COALESCE(sum(l.amount), 0) AS amount "
                + "FROM ledger l " + where.sql() + "GROUP BY l.direction, l.status");
        where.params().forEach(q::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> groups = q.getResultList();
        return CashLedger.summarize(groups);
    }
}
