package id.enterpriseapps.erp.core.accounting;

import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.entity.PartyType;
import id.enterpriseapps.erp.entity.RootType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * Saldo akun & outstanding mitra — port {@code utils/accountBalance.ts}.
 *
 * <p>Arah saldo normal: ASSET/EXPENSE debit-normal; LIABILITY/EQUITY/REVENUE credit-normal.
 * {@code accounts.balance} hanyalah cache — sumber kebenaran laporan adalah
 * {@code accounting_ledger_entries} (lihat {@link #recalculateAccountBalances(Collection)}).
 * Update memakai SQL increment atomik; entity Account yang sudah dimuat di transaksi yang sama
 * bisa menyimpan nilai lama (baca ulang bila perlu).
 */
@Slf4j
@Service
public class ErpAccountBalanceService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Dampak saldo sesuai root type (port {@code computeImpact}). */
    public static BigDecimal computeImpact(RootType rootType, BigDecimal debit, BigDecimal credit) {
        BigDecimal d = Money.nz(debit);
        BigDecimal c = Money.nz(credit);
        return rootType == RootType.ASSET || rootType == RootType.EXPENSE ? d.subtract(c) : c.subtract(d);
    }

    public static BigDecimal computeImpact(String rootType, BigDecimal debit, BigDecimal credit) {
        return computeImpact(RootType.valueOf(rootType), debit, credit);
    }

    /** Ubah saldo satu akun dengan arah yang benar. No-op bila akun tidak ada. */
    @ErpTx
    public void updateAccountBalance(String accountId, BigDecimal debit, BigDecimal credit) {
        List<RootType> rt = em.createQuery("select a.rootType from Account a where a.id = :id", RootType.class)
                .setParameter("id", accountId).getResultList();
        if (rt.isEmpty()) {
            return;
        }
        BigDecimal impact = computeImpact(rt.get(0), debit, credit);
        if (impact.signum() == 0) {
            return;
        }
        em.createNativeQuery("UPDATE accounts SET balance = balance + :v, updated_at = now() WHERE id = :id")
                .setParameter("v", impact)
                .setParameter("id", accountId)
                .executeUpdate();
    }

    public record BalanceLine(String accountId, BigDecimal debit, BigDecimal credit) {
    }

    @ErpTx
    public void updateBalancesForItems(Collection<BalanceLine> items) {
        for (BalanceLine i : items) {
            updateAccountBalance(i.accountId(), i.debit(), i.credit());
        }
    }

    /**
     * Hitung ulang saldo dari ledger aktif (non-cancelled). {@code accountIds} kosong/null = semua akun.
     */
    @ErpTx
    public void recalculateAccountBalances(Collection<String> accountIds) {
        boolean all = accountIds == null || accountIds.isEmpty();
        String sql = "UPDATE accounts a SET balance = COALESCE(t.bal, 0), updated_at = now() "
                + "FROM (SELECT ac.id, CASE WHEN ac.root_type IN ('ASSET','EXPENSE') "
                + "        THEN COALESCE(SUM(le.debit),0) - COALESCE(SUM(le.credit),0) "
                + "        ELSE COALESCE(SUM(le.credit),0) - COALESCE(SUM(le.debit),0) END AS bal "
                + "      FROM accounts ac LEFT JOIN accounting_ledger_entries le "
                + "        ON le.account_id = ac.id AND le.is_cancelled = false "
                + (all ? "" : "      WHERE ac.id IN (:ids) ")
                + "      GROUP BY ac.id, ac.root_type) t "
                + "WHERE a.id = t.id";
        var q = em.createNativeQuery(sql);
        if (!all) {
            q.setParameter("ids", accountIds);
        }
        q.executeUpdate();
    }

    /**
     * Hitung ulang {@code parties.outstanding_amount} = opening + Σ outstanding invoice non-cancelled
     * (pembelian untuk Supplier/Both, penjualan untuk Customer/Both). Port {@code recalcPartyOutstanding}.
     */
    @ErpTx
    public void recalcPartyOutstanding(String partyId) {
        List<Object[]> rows = em.createQuery(
                        "select p.partyType, p.outstandingAmount, p.openingOutstanding from Party p where p.id = :id",
                        Object[].class)
                .setParameter("id", partyId).getResultList();
        if (rows.isEmpty()) {
            return;
        }
        PartyType type = (PartyType) rows.get(0)[0];
        BigDecimal old = Money.nz((BigDecimal) rows.get(0)[1]);
        BigDecimal outstanding = Money.nz((BigDecimal) rows.get(0)[2]);
        if (type == PartyType.Supplier || type == PartyType.Both) {
            BigDecimal pi = em.createQuery(
                            "select coalesce(sum(i.outstanding), 0) from PurchaseInvoice i "
                                    + "where i.partyId = :id and i.status <> id.enterpriseapps.erp.entity.InvoiceStatus.Cancelled",
                            BigDecimal.class)
                    .setParameter("id", partyId).getSingleResult();
            outstanding = outstanding.add(Money.nz(pi));
        }
        if (type == PartyType.Customer || type == PartyType.Both) {
            BigDecimal si = em.createQuery(
                            "select coalesce(sum(i.outstanding), 0) from SalesInvoice i "
                                    + "where i.partyId = :id and i.status <> id.enterpriseapps.erp.entity.InvoiceStatus.Cancelled",
                            BigDecimal.class)
                    .setParameter("id", partyId).getSingleResult();
            outstanding = outstanding.add(Money.nz(si));
        }
        if (old.compareTo(outstanding) != 0) {
            log.info("recalcPartyOutstanding: correcting drift party={} old={} new={}", partyId, old, outstanding);
        }
        em.createNativeQuery("UPDATE parties SET outstanding_amount = :v, updated_at = now() WHERE id = :id")
                .setParameter("v", Money.round2(outstanding))
                .setParameter("id", partyId)
                .executeUpdate();
    }
}
