package id.neravoxa.erp.fulfillment;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.inventory.ErpCogsService;
import id.neravoxa.erp.core.inventory.ErpWarehouseStockService;
import id.neravoxa.erp.entity.DeliveryOrder;
import id.neravoxa.erp.entity.DeliveryOrderItem;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.InventoryItem;
import id.neravoxa.erp.entity.Party;
import id.neravoxa.erp.entity.PartyType;
import id.neravoxa.erp.entity.StockMovement;
import id.neravoxa.erp.entity.StockMovementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Edit penuh Surat Jalan — port {@code services/deliveryOrderEdit.ts}. Hanya DO Submitted yang belum
 * diinvoice: gerakan lama dibalik, baris baru diterapkan dengan unit cost dikunci dari averageCost saat
 * edit. Nomor DO dipertahankan agar cetakan tetap valid.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpDeliveryOrderEditService {

    private final ErpDocumentNumberService numbers;
    private final ErpFiscalYearService fiscalYears;
    private final ErpWarehouseStockService stock;
    private final ErpCogsService cogs;
    private final ErpDeliveryOrderStockGuard stockGuard;
    private final ErpSalesOrderDeliveryService soDelivery;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx(timeout = 15)
    public DeliveryOrder editInTx(String doId, FulfillmentRequests.DoInput body, String userId) {
        DeliveryOrder old = em.find(DeliveryOrder.class, doId);
        if (old == null) {
            throw ErpException.business("Surat jalan tidak ditemukan.");
        }
        if (old.getStatus() == DocumentStatus.Cancelled) {
            throw ErpException.business("Surat jalan sudah dibatalkan dan tidak bisa diedit.");
        }
        if (old.getInvoiceId() != null) {
            throw ErpException.business("Surat jalan sudah masuk invoice. Batalkan dulu invoice terkait sebelum edit.");
        }
        String doNumber = old.getDoNumber();

        List<StockMovement> oldMovements = em.createQuery(
                        "select m from StockMovement m where m.referenceType = :type and m.referenceId = :id "
                                + "and m.isCancelled = false order by m.createdAt, m.id", StockMovement.class)
                .setParameter("type", ErpDeliveryOrderSubmitService.REFERENCE_TYPE)
                .setParameter("id", doId)
                .getResultList();
        for (StockMovement mov : oldMovements) {
            stock.adjustItemStock(mov.getItemId(), mov.getQuantity());
            stock.adjustWarehouseStock(mov.getItemId(), mov.getWarehouseId(), mov.getQuantity());
            mov.setIsCancelled(true);
        }
        em.flush();

        // Header dipakai ulang (nomor DO tetap); baris dibuat ulang.
        em.createQuery("delete from DeliveryOrderItem i where i.deliveryOrderId = :id")
                .setParameter("id", doId).executeUpdate();

        // Customer boleh berganti selama Customer/Both (aturan sama dengan submit).
        Party party = em.find(Party.class, body.partyId());
        if (party == null) {
            throw ErpException.business("Data customer tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(party.getIsActive())) {
            throw ErpException.business("Customer sudah tidak aktif.");
        }
        if (party.getPartyType() != PartyType.Customer && party.getPartyType() != PartyType.Both) {
            throw ErpException.business("Surat jalan hanya bisa untuk Customer.");
        }

        Instant txDate = ErpDates.tryInstant(body.date());
        if (txDate == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }

        // Cek stok per gudang SETELAH pembalikan (inventory_stocks, bukan total lintas gudang).
        stockGuard.check(body.items(), body.allowNegativeStock(), body.negativeStockReason(), "",
                "DO edit doId=" + doId);

        old.setDate(txDate);
        old.setPartyId(body.partyId());
        old.setNomorMobil(body.nomorMobil());
        old.setDriverName(body.driverName());
        old.setRecipientName(body.recipientName());
        old.setNotes(body.notes());
        em.flush();

        FiscalYear fy = fiscalYears.findOpen(txDate).orElseThrow(() ->
                ErpException.business("Tidak ada tahun fiskal aktif untuk tanggal transaksi ini."));

        // Akun lawan yang dipakai invoice untuk HPP; disimpan di setiap gerakan Out.
        String cogsAccountId = cogs.resolveCogsAccountId();

        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        int seq = 0;
        for (FulfillmentRequests.DoItem it : body.items()) {
            DeliveryOrderItem row = new DeliveryOrderItem();
            row.setDeliveryOrderId(doId);
            row.setItemName(it.itemName());
            row.setUnit(it.unit());
            row.setDescription(it.description());
            row.setCreatedAt(base.plusMillis(seq++));
            if (it.inventoryItemId() != null) {
                InventoryItem inv = em.find(InventoryItem.class, it.inventoryItemId());
                if (inv == null) {
                    throw ErpException.business("Item '" + it.itemName() + "' tidak ditemukan.");
                }
                String warehouseId = it.warehouseId() != null ? it.warehouseId() : stock.resolveWarehouseId(null);
                if (warehouseId == null) {
                    throw ErpException.business("Item \"" + inv.getName() + "\" perlu dipilih gudang sumber.");
                }
                BigDecimal qty = it.quantity();
                BigDecimal unitCost = Money.nz(stock.currentItemStock(inv.getId()).averageCost());
                BigDecimal totalValue = Money.round2(qty.multiply(unitCost));

                stock.adjustItemStock(inv.getId(), qty.negate());
                stock.adjustWarehouseStock(inv.getId(), warehouseId, qty.negate());

                StockMovement sm = new StockMovement();
                sm.setMovementNumber(numbers.generate("SM", txDate, fy.getId()));
                sm.setDate(txDate);
                sm.setItemId(inv.getId());
                sm.setWarehouseId(warehouseId);
                sm.setMovementType(StockMovementType.Out);
                sm.setQuantity(qty);
                sm.setUnitCost(unitCost);
                sm.setTotalValue(totalValue);
                sm.setNotes("Auto dari " + doNumber + " (edited)");
                sm.setReferenceType(ErpDeliveryOrderSubmitService.REFERENCE_TYPE);
                sm.setReferenceId(doId);
                sm.setReferenceNumber(doNumber);
                sm.setOffsetAccountId(totalValue.signum() > 0 ? cogsAccountId : null);
                sm.setFiscalYearId(fy.getId());
                sm.setCreatedById(userId);
                em.persist(sm);

                row.setInventoryItemId(inv.getId());
                row.setWarehouseId(warehouseId);
                row.setQuantity(qty);
                row.setStockMovementId(sm.getId());
            } else {
                // Baris non-persediaan: tanpa gerakan, hanya disimpan.
                row.setWarehouseId(it.warehouseId());
                row.setQuantity(it.quantity());
            }
            em.persist(row);
        }
        em.flush();

        // Baris yang dibuat ulang kehilangan tautan SO: pulihkan lalu turunkan ulang qty terkirim + status SO.
        if (old.getSalesOrderId() != null) {
            soDelivery.relinkDoItemsToSO(doId);
            soDelivery.recomputeDeliveredQty(old.getSalesOrderId());
        }

        log.info("Delivery order edited doId={} doNumber={} items={} partyId={}", doId, doNumber, body.items().size(),
                body.partyId());
        return old;
    }
}
