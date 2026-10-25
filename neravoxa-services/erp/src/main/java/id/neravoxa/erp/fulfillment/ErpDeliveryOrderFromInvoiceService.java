package id.neravoxa.erp.fulfillment;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.entity.DeliveryOrder;
import id.neravoxa.erp.entity.DeliveryOrderItem;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.SalesInvoice;
import id.neravoxa.erp.entity.SalesInvoiceItem;
import id.neravoxa.erp.entity.StockMovementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Surat Jalan retroaktif untuk invoice yang sudah memindahkan stok & memposting HPP (penjualan
 * langsung) — port {@code services/deliveryOrderFromInvoice.ts}. Baris DO menunjuk gerakan stok milik
 * invoice; baris non-persediaan dilewati. TANPA perubahan stok, gerakan baru, maupun GL (submit DO
 * biasa akan menggandakan stok keluar).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpDeliveryOrderFromInvoiceService {

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx(timeout = 15)
    public DeliveryOrder createInTx(String invoiceId, FulfillmentRequests.DoFromInvoiceInput body, String userId) {
        Instant date = ErpDates.tryInstant(body.date());
        if (date == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        SalesInvoice invoice = em.find(SalesInvoice.class, invoiceId);
        if (invoice == null) {
            throw ErpException.business("Invoice tidak ditemukan.");
        }
        if (invoice.getStatus() == InvoiceStatus.Cancelled) {
            throw ErpException.business("Invoice " + invoice.getInvoiceNumber() + " sudah dibatalkan.");
        }

        // Cegah DO kedua untuk invoice yang sama.
        List<String> existing = em.createQuery(
                        "select d.doNumber from DeliveryOrder d where d.invoiceId = :inv and d.status <> :cancelled "
                                + "order by d.createdAt", String.class)
                .setParameter("inv", invoice.getId())
                .setParameter("cancelled", DocumentStatus.Cancelled)
                .setMaxResults(1).getResultList();
        if (!existing.isEmpty()) {
            throw ErpException.business("Invoice " + invoice.getInvoiceNumber() + " sudah punya surat jalan: "
                    + existing.get(0) + ".");
        }

        // Hanya baris barang persediaan yang menjadi baris DO; baris jasa dilewati. Tabel baris invoice
        // tidak punya created_at: urutan simpan = urutan fisik baris (ctid), seperti include Prisma.
        @SuppressWarnings("unchecked")
        List<String> lineIds = em.createNativeQuery("SELECT id FROM sales_invoice_items WHERE sales_invoice_id = :inv "
                        + "AND inventory_item_id IS NOT NULL ORDER BY ctid")
                .setParameter("inv", invoice.getId()).getResultList();
        List<SalesInvoiceItem> inventoryLines = lineIds.stream().map(id -> em.find(SalesInvoiceItem.class, id)).toList();
        if (inventoryLines.isEmpty()) {
            throw ErpException.business("Invoice " + invoice.getInvoiceNumber()
                    + " tidak punya baris barang fisik untuk dikirim.");
        }

        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
        String doNumber = numbers.generate("DO", date, fy.getId());

        Instant now = Instant.now();
        DeliveryOrder d = new DeliveryOrder();
        d.setDoNumber(doNumber);
        d.setDate(date);
        d.setPartyId(invoice.getPartyId());
        d.setStatus(DocumentStatus.Submitted);
        d.setNotes(Zod.truthy(body.notes()));
        d.setNomorMobil(Zod.truthy(body.nomorMobil()));
        d.setDriverName(Zod.truthy(body.driverName()));
        d.setRecipientName(Zod.truthy(body.recipientName()));
        d.setFiscalYearId(fy.getId());
        d.setCreatedBy(userId);
        d.setSubmittedAt(now);
        d.setInvoiceId(invoice.getId());
        d.setInvoicedAt(invoice.getDate());
        em.persist(d);
        em.flush();

        Instant base = now.truncatedTo(ChronoUnit.MILLIS);
        int seq = 0;
        List<String> usedMovements = new ArrayList<>();
        for (SalesInvoiceItem line : inventoryLines) {
            // Gerakan Out yang dibuat saat submit invoice untuk baris ini (item + qty sama). Gerakan yang
            // sudah dipakai baris sebelumnya dilewati: stock_movement_id unik di delivery_order_items.
            List<String> sm = em.createQuery(
                            "select m.id from StockMovement m where m.referenceType = 'SalesInvoice' and m.referenceId = :inv "
                                    + "and m.itemId = :item and m.movementType = :out and m.isCancelled = false "
                                    + "and m.quantity = :qty and m.id not in :used order by m.createdAt, m.id", String.class)
                    .setParameter("inv", invoice.getId())
                    .setParameter("item", line.getInventoryItemId())
                    .setParameter("out", StockMovementType.Out)
                    .setParameter("qty", line.getQuantity())
                    .setParameter("used", usedMovements.isEmpty() ? List.of("") : usedMovements)
                    .setMaxResults(1).getResultList();
            String smId = sm.isEmpty() ? null : sm.get(0);
            if (smId != null) {
                usedMovements.add(smId);
            }

            DeliveryOrderItem row = new DeliveryOrderItem();
            row.setDeliveryOrderId(d.getId());
            row.setInventoryItemId(line.getInventoryItemId());
            row.setWarehouseId(line.getWarehouseId());
            row.setItemName(line.getItemName());
            row.setQuantity(line.getQuantity());
            row.setUnit(line.getUnit());
            row.setDescription(line.getDescription());
            row.setStockMovementId(smId);
            row.setCreatedAt(base.plusMillis(seq++));
            em.persist(row);
            em.flush();

            // Tautan balik baris invoice agar drawer invoice menampilkan DO baru.
            line.setDeliveryOrderItemId(row.getId());
        }
        em.flush();

        log.info("Delivery order created retroactively from existing invoice doId={} doNumber={} invoiceId={} invoiceNumber={} lines={}",
                d.getId(), doNumber, invoice.getId(), invoice.getInvoiceNumber(), inventoryLines.size());
        return d;
    }
}
