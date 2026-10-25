package id.neravoxa.erp.purchase;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.accounting.ErpAccountBalanceService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.core.inventory.ErpCogsService;
import id.neravoxa.erp.core.inventory.ErpWarehouseStockService;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.PurchaseInvoice;
import id.neravoxa.erp.entity.StockMovement;
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
 * Pembatalan invoice pembelian — port {@code cancelPurchaseInvoiceInTx} (invoiceCancellation.ts).
 *
 * <p>Cermin pembatalan invoice penjualan dengan arah stok terbalik. Hanya gerakan stok milik PI
 * (baris standalone/legacy) yang dibalik; stok dari STT tetap milik STT. STT tertaut dilepas
 * (invoiceId dikosongkan) agar bisa ditagih ulang.
 */
@Service
@RequiredArgsConstructor
public class ErpPurchaseInvoiceCancelService {

    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpSystemAccountService systemAccounts;
    private final ErpWarehouseStockService stock;
    private final ErpCogsService cogs;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx
    public Map<String, Object> cancel(String invoiceId) {
        PurchaseInvoice invoice = em.find(PurchaseInvoice.class, invoiceId);
        if (invoice == null) {
            throw ErpException.business("Invoice tidak ditemukan.");
        }
        if (invoice.getStatus() == InvoiceStatus.Cancelled) {
            throw ErpException.business("Invoice sudah dibatalkan.");
        }
        if (invoice.getStatus() == InvoiceStatus.Paid) {
            throw ErpException.business("Invoice sudah lunas, tidak bisa dibatalkan.");
        }

        Long allocations = em.createQuery("select count(a) from PaymentAllocation a "
                        + "where a.invoiceType = 'PurchaseInvoice' and a.invoiceId = :id", Long.class)
                .setParameter("id", invoice.getId()).getSingleResult();
        if (allocations > 0) {
            throw ErpException.business("Invoice memiliki pembayaran teralokasi. Batalkan pembayaran terlebih dahulu.");
        }
        Long activeDepositApps = em.createQuery("select count(d) from VendorDepositApplication d "
                        + "where d.purchaseInvoiceId = :id and d.isCancelled = false", Long.class)
                .setParameter("id", invoice.getId()).getSingleResult();
        if (activeDepositApps > 0) {
            throw ErpException.business("Invoice memiliki alokasi uang muka aktif. Batalkan alokasi terlebih dahulu.");
        }

        Instant now = Instant.now();
        invoice.setStatus(InvoiceStatus.Cancelled);
        invoice.setOutstanding(BigDecimal.ZERO);
        invoice.setCancelledAt(now);
        PurchaseDb.flush(em);

        List<JournalEntry> journals = em.createQuery("select j from JournalEntry j where j.entryNumber = :n", JournalEntry.class)
                .setParameter("n", "JV-" + invoice.getInvoiceNumber()).getResultList();
        if (!journals.isEmpty()) {
            // Status Cancelled + ledger is_cancelled + saldo dibalik per akun baris jurnal.
            gl.cancelJournal(journals.get(0).getId());
        } else {
            // Fallback legacy untuk invoice sebelum ada baris jurnal per item.
            String ap = systemAccounts.getAccountId(SystemAccountRole.AP);
            String inventory = systemAccounts.getAccountId(SystemAccountRole.INVENTORY);
            balances.updateAccountBalance(inventory, BigDecimal.ZERO, invoice.getGrandTotal());
            balances.updateAccountBalance(ap, invoice.getGrandTotal(), BigDecimal.ZERO);
        }

        balances.recalcPartyOutstanding(invoice.getPartyId());

        // Hanya gerakan milik PI (baris standalone/legacy) yang dibalik; stok STT milik STT.
        List<StockMovement> movements = em.createQuery("select m from StockMovement m where m.referenceType = 'PurchaseInvoice' "
                        + "and m.referenceId = :id and m.isCancelled = false", StockMovement.class)
                .setParameter("id", invoice.getId()).getResultList();
        for (StockMovement sm : movements) {
            stock.adjustItemStock(sm.getItemId(), sm.getQuantity().negate());
            if (sm.getWarehouseId() != null) {
                stock.adjustWarehouseStock(sm.getItemId(), sm.getWarehouseId(), sm.getQuantity().negate());
            }
            sm.setIsCancelled(true);
        }
        PurchaseDb.flush(em);

        cogs.reverseCogsSettlementsByTrigger("PurchaseInvoice", invoice.getId());

        // Lepas STT tertaut agar bisa ditagih ulang; baris & gerakan stoknya tidak disentuh.
        em.createQuery("update GoodsReceipt g set g.invoiceId = null, g.invoicedAt = null, g.updatedAt = :now "
                        + "where g.invoiceId = :id")
                .setParameter("now", now).setParameter("id", invoice.getId()).executeUpdate();

        // goods_receipt_item_id unik: dikosongkan agar invoice baru bisa menaut STT yang sama.
        em.createQuery("update PurchaseInvoiceItem it set it.goodsReceiptItemId = null "
                        + "where it.purchaseInvoiceId = :id and it.goodsReceiptItemId is not null")
                .setParameter("id", invoice.getId()).executeUpdate();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", invoice.getId());
        out.put("status", "Cancelled");
        return out;
    }
}
