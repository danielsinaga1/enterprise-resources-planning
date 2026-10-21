package id.enterpriseapps.erp.fulfillment;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpAccountBalanceService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpPartyBalanceService;
import id.enterpriseapps.erp.core.inventory.ErpWarehouseStockService;
import id.enterpriseapps.erp.entity.DocumentStatus;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.Payment;
import id.enterpriseapps.erp.entity.SalesInvoice;
import id.enterpriseapps.erp.entity.SalesReturn;
import id.enterpriseapps.erp.entity.StockMovement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Batal retur penjualan + balik semua efeknya (cermin pembatalan invoice) — port
 * {@code services/salesReturnCancel.ts}:
 * <ul>
 *   <li>JV-RN (dan JV-COGS-RN) → Cancelled, ledger ditandai batal, saldo akun dibalik</li>
 *   <li>gerakan stok yang dibuatnya dibatalkan dan stok dikurangi kembali</li>
 *   <li>outstanding invoice asal dipulihkan sebesar arReducedAmount</li>
 *   <li>Payment CustomerDeposit tertaut dibatalkan (ditolak bila kreditnya sudah dipakai)</li>
 *   <li>outstanding mitra dihitung ulang</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ErpSalesReturnCancelService {

    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpPartyBalanceService partyBalances;
    private final ErpWarehouseStockService stock;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx(timeout = 15)
    public Map<String, Object> cancelInTx(String returnId) {
        SalesReturn ret = em.find(SalesReturn.class, returnId);
        if (ret == null) {
            throw ErpException.business("Retur tidak ditemukan.");
        }
        if ("Cancelled".equals(ret.getStatus())) {
            throw ErpException.business("Retur sudah dibatalkan.");
        }

        // Tolak bila kredit (uang muka pelanggan) dari retur ini sudah dipakai.
        if (ret.getCustomerDepositId() != null) {
            Long applied = em.createQuery("select count(a) from CustomerDepositApplication a "
                            + "where a.depositPaymentId = :id and a.isCancelled = false", Long.class)
                    .setParameter("id", ret.getCustomerDepositId()).getSingleResult();
            if (applied > 0) {
                throw ErpException.business("Kredit customer dari retur ini sudah dipakai di invoice lain. "
                        + "Batalkan penggunaan kredit terlebih dahulu.");
            }
        }

        // Balik jurnal utama + jurnal HPP.
        for (String jvNumber : List.of("JV-" + ret.getReturnNumber(), "JV-COGS-" + ret.getReturnNumber())) {
            List<Object[]> je = em.createQuery("select j.id, j.status from JournalEntry j where j.entryNumber = :n", Object[].class)
                    .setParameter("n", jvNumber).getResultList();
            if (je.isEmpty() || je.get(0)[1] == DocumentStatus.Cancelled) {
                continue;
            }
            gl.cancelJournal((String) je.get(0)[0]);
        }

        // Batalkan gerakan stok retur + kurangi stok kembali.
        List<StockMovement> movements = em.createQuery(
                        "select m from StockMovement m where m.referenceType = :type and m.referenceId = :id "
                                + "and m.isCancelled = false order by m.createdAt, m.id", StockMovement.class)
                .setParameter("type", ErpSalesReturnSubmitService.REFERENCE_TYPE)
                .setParameter("id", ret.getId())
                .getResultList();
        for (StockMovement sm : movements) {
            stock.adjustItemStock(sm.getItemId(), Money.nz(sm.getQuantity()).negate());
            if (sm.getWarehouseId() != null) {
                stock.adjustWarehouseStock(sm.getItemId(), sm.getWarehouseId(), Money.nz(sm.getQuantity()).negate());
            }
            sm.setIsCancelled(true);
        }
        em.flush();

        // Pulihkan outstanding invoice sebesar porsi AR yang dikurangi.
        BigDecimal arReduced = Money.nz(ret.getArReducedAmount());
        SalesInvoice invoice = em.find(SalesInvoice.class, ret.getSalesInvoiceId());
        if (arReduced.signum() > 0 && invoice != null && invoice.getStatus() != InvoiceStatus.Cancelled) {
            em.createQuery("update SalesInvoice i set i.outstanding = i.outstanding + :v, i.updatedAt = :now where i.id = :id")
                    .setParameter("v", arReduced)
                    .setParameter("now", Instant.now())
                    .setParameter("id", invoice.getId())
                    .executeUpdate();
        }

        // Batalkan Payment uang muka pelanggan (liabilitas sudah dibalik lewat jurnal utama).
        if (ret.getCustomerDepositId() != null) {
            Payment dep = em.find(Payment.class, ret.getCustomerDepositId());
            if (dep == null) {
                // Padanan P2025 (update baris yang tidak ada) di handleRouteError.
                throw ErpException.business("Data terkait tidak ditemukan. Periksa referensi yang dipilih.");
            }
            dep.setStatus(DocumentStatus.Cancelled);
            dep.setCancelledAt(Instant.now());
            em.flush();
            // Cermin submit: kembalikan saldo uang muka yang di-cache di mitra.
            partyBalances.incrementParty(ret.getPartyId(), "customer_deposit_balance", Money.nz(dep.getAmount()).negate());
        }

        ret.setStatus("Cancelled");
        ret.setCancelledAt(Instant.now());
        em.flush();
        balances.recalcPartyOutstanding(ret.getPartyId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", ret.getId());
        out.put("status", "Cancelled");
        return out;
    }
}
