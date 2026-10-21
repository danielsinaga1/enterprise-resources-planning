package id.enterpriseapps.erp.core.accounting;

import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.entity.RootType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Saldo ter-denormalisasi pada mitra (party) dari baris jurnal yang ditandai party —
 * port {@code utils/partyBalance.ts}.
 *
 * <pre>
 *   2.1.2 (Uang Muka Penjualan) → customer_deposit_balance (credit-normal)
 *   1.3   (Uang Muka Pembelian) → deposit_balance          (debit-normal)
 *   lainnya (1.2.1, 2.1.1, …)   → outstanding_amount       (mengikuti root type)
 * </pre>
 * Posting (apply) dan pembatalan (reverse) WAJIB memakai helper ini.
 */
@Service
public class ErpPartyBalanceService {

    public static final String CUSTOMER_DEPOSIT_ACCOUNT = "2.1.2";
    public static final String VENDOR_DEPOSIT_ACCOUNT = "1.3";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public record PartyImpactItem(String partyId, BigDecimal debit, BigDecimal credit, String accountNumber,
                                  RootType rootType) {
    }

    public enum Direction { APPLY, REVERSE }

    @ErpTx
    public void applyPartyImpact(Collection<PartyImpactItem> items, Direction direction) {
        for (PartyImpactItem it : items) {
            if (it.partyId() == null) {
                continue;
            }
            BigDecimal debit = direction == Direction.APPLY ? Money.nz(it.debit()) : Money.nz(it.credit());
            BigDecimal credit = direction == Direction.APPLY ? Money.nz(it.credit()) : Money.nz(it.debit());
            if (debit.signum() == 0 && credit.signum() == 0) {
                continue;
            }
            String column;
            BigDecimal impact;
            if (CUSTOMER_DEPOSIT_ACCOUNT.equals(it.accountNumber())) {
                column = "customer_deposit_balance";
                impact = credit.subtract(debit);
            } else if (VENDOR_DEPOSIT_ACCOUNT.equals(it.accountNumber())) {
                column = "deposit_balance";
                impact = debit.subtract(credit);
            } else {
                column = "outstanding_amount";
                impact = it.rootType() == RootType.ASSET || it.rootType() == RootType.EXPENSE
                        ? debit.subtract(credit) : credit.subtract(debit);
            }
            if (impact.signum() == 0) {
                continue;
            }
            incrementParty(it.partyId(), column, impact);
        }
    }

    /**
     * Increment atomik satu kolom saldo mitra ({@code outstanding_amount}, {@code deposit_balance},
     * {@code customer_deposit_balance}). Pakai ini, bukan setter entity, untuk menghindari lost update.
     */
    @ErpTx
    public void incrementParty(String partyId, String column, BigDecimal delta) {
        if (!column.equals("outstanding_amount") && !column.equals("deposit_balance")
                && !column.equals("customer_deposit_balance")) {
            throw new IllegalArgumentException("Kolom saldo mitra tidak dikenal: " + column);
        }
        em.createNativeQuery("UPDATE parties SET " + column + " = " + column + " + :v, updated_at = now() WHERE id = :id")
                .setParameter("v", delta)
                .setParameter("id", partyId)
                .executeUpdate();
    }
}
