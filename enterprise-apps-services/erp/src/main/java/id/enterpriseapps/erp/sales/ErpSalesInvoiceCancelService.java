package id.enterpriseapps.erp.sales;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.accounting.ErpAccountBalanceService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.inventory.ErpWarehouseStockService;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.SalesInvoice;
import id.enterpriseapps.erp.entity.StockMovement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pembatalan invoice penjualan — port {@code cancelSalesInvoiceInTx} (services/invoiceCancellation.ts).
 *
 * <p>Jurnal pendapatan {@code JV-<nomor>} dan jurnal HPP {@code JV-COGS-<nomor>} dibatalkan (saldo dibalik
 * per akun baris jurnal, sehingga sub-akun HPP per item ikut pulih), gerakan stok milik invoice
 * sendiri (penjualan langsung lama) dibalik, Surat Jalan yang tertaut dilepas kembali ke Submitted
 * (gerakan stoknya tetap sah), lalu outstanding mitra dihitung ulang.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpSalesInvoiceCancelService {

    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpWarehouseStockService stock;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** POST /sales/invoices/:id/cancel → {@code { id, status: 'Cancelled' }}. */
    @ErpTx(timeout = 15)
    public Map<String, Object> cancel(String invoiceId) {
        SalesInvoice invoice = cancelInTx(invoiceId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", invoice.getId());
        out.put("status", InvoiceStatus.Cancelled.name());
        return out;
    }

    /** Inti pembatalan; pemanggil memiliki transaksi. */
    @ErpTx(timeout = 15)
    public SalesInvoice cancelInTx(String invoiceId) {
        SalesInvoice invoice = em.find(SalesInvoice.class, invoiceId);
        if (invoice == null) {
            throw ErpException.business("Invoice tidak ditemukan.");
        }
        if (invoice.getStatus() == InvoiceStatus.Cancelled) {
            throw ErpException.business("Invoice sudah dibatalkan.");
        }
        // Invoice Rp 0 (sumbangan) otomatis Paid tanpa pembayaran → tetap boleh dibatalkan.
        if (invoice.getStatus() == InvoiceStatus.Paid && invoice.getGrandTotal().signum() != 0) {
            throw ErpException.business("Invoice sudah lunas, tidak bisa dibatalkan.");
        }

        Long allocations = em.createQuery(
                        "select count(a) from PaymentAllocation a where a.invoiceType = 'SalesInvoice' and a.invoiceId = :id",
                        Long.class)
                .setParameter("id", invoice.getId()).getSingleResult();
        if (allocations > 0) {
            throw ErpException.business("Invoice memiliki pembayaran teralokasi. Batalkan pembayaran terlebih dahulu.");
        }

        Long activeDepositApps = em.createQuery(
                        "select count(d) from CustomerDepositApplication d where d.salesInvoiceId = :id and d.isCancelled = false",
                        Long.class)
                .setParameter("id", invoice.getId()).getSingleResult();
        if (activeDepositApps > 0) {
            throw ErpException.business("Invoice memiliki alokasi uang muka customer aktif. Batalkan alokasi terlebih dahulu.");
        }

        Instant now = Instant.now();
        invoice.setStatus(InvoiceStatus.Cancelled);
        invoice.setOutstanding(java.math.BigDecimal.ZERO);
        invoice.setCancelledAt(now);
        SalesSupport.flush(em);

        // Jurnal pendapatan/piutang utama, lalu jurnal HPP (entri JV-COGS-… terpisah).
        cancelJournalByNumber("JV-" + invoice.getInvoiceNumber());
        cancelJournalByNumber("JV-COGS-" + invoice.getInvoiceNumber());

        // Hanya gerakan stok penjualan langsung (milik invoice) yang dibalik; gerakan dari DO milik DO
        // dan tetap sah.
        List<StockMovement> movements = em.createQuery(
                        "select m from StockMovement m where m.referenceType = 'SalesInvoice' and m.referenceId = :id "
                                + "and m.isCancelled = false", StockMovement.class)
                .setParameter("id", invoice.getId()).getResultList();
        for (StockMovement sm : movements) {
            stock.adjustItemStock(sm.getItemId(), sm.getQuantity());
            if (sm.getWarehouseId() != null) {
                stock.adjustWarehouseStock(sm.getItemId(), sm.getWarehouseId(), sm.getQuantity());
            }
            sm.setIsCancelled(true);
        }
        SalesSupport.flush(em);

        // Lepas Surat Jalan tertaut kembali ke Submitted agar bisa ditagih invoice lain.
        em.createQuery("update DeliveryOrder d set d.invoiceId = null, d.invoicedAt = null, d.updatedAt = :now "
                        + "where d.invoiceId = :id")
                .setParameter("now", now)
                .setParameter("id", invoice.getId())
                .executeUpdate();

        // delivery_order_item_id unik: dikosongkan agar invoice baru bisa menautkan DO yang sama.
        // Barisnya tetap disimpan untuk jejak audit.
        em.createQuery("update SalesInvoiceItem i set i.deliveryOrderItemId = null "
                        + "where i.salesInvoiceId = :id and i.deliveryOrderItemId is not null")
                .setParameter("id", invoice.getId())
                .executeUpdate();

        balances.recalcPartyOutstanding(invoice.getPartyId());
        log.info("Sales invoice cancelled id={} number={}", invoice.getId(), invoice.getInvoiceNumber());
        return invoice;
    }

    /** Batalkan jurnal ber-nomor tertentu bila ada (status, ledger, saldo dibalik per baris). */
    private void cancelJournalByNumber(String entryNumber) {
        List<String> ids = em.createQuery("select j.id from JournalEntry j where j.entryNumber = :n", String.class)
                .setParameter("n", entryNumber).getResultList();
        for (String id : ids) {
            gl.cancelJournal(id);
        }
    }
}
