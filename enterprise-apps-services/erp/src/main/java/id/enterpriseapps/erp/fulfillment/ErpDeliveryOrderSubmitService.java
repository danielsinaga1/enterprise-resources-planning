package id.enterpriseapps.erp.fulfillment;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpDocumentNumberService;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.core.inventory.ErpCogsService;
import id.enterpriseapps.erp.core.inventory.ErpWarehouseStockService;
import id.enterpriseapps.erp.entity.DeliveryOrder;
import id.enterpriseapps.erp.entity.DeliveryOrderItem;
import id.enterpriseapps.erp.entity.DocumentStatus;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.InventoryItem;
import id.enterpriseapps.erp.entity.Party;
import id.enterpriseapps.erp.entity.PartyType;
import id.enterpriseapps.erp.entity.StockMovement;
import id.enterpriseapps.erp.entity.StockMovementType;
import id.enterpriseapps.erp.entity.Warehouse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Submit Surat Jalan — port {@code services/deliveryOrderSubmit.ts}.
 *
 * <p>Stok keluar per gudang dengan unit cost DIKUNCI dari averageCost (WAC) saat kirim, sehingga invoice
 * penjualan berikutnya memposting HPP memakai biaya saat pengiriman. Akun HPP lawan
 * ({@link ErpCogsService#resolveCogsAccountId()}) disimpan di gerakan Out sebagai metadata. TIDAK ada
 * dampak GL di sini (invoice yang memposting). Pemanggil memiliki transaksi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpDeliveryOrderSubmitService {

    public static final String REFERENCE_TYPE = "DeliveryOrder";

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpWarehouseStockService stock;
    private final ErpCogsService cogs;
    private final ErpDeliveryOrderStockGuard stockGuard;
    private final ErpTemporaryDeliveryNoteService temporaryNotes;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** DO yang baru dibuat + barisnya sesuai urutan input (dipakai generate-do untuk menautkan item SO). */
    public record Submitted(DeliveryOrder deliveryOrder, List<DeliveryOrderItem> items) {
    }

    @ErpTx(timeout = 15)
    public Submitted submitInTx(FulfillmentRequests.DoInput body, String userId) {
        Instant date = ErpDates.tryInstant(body.date());
        if (date == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);

        Party party = em.find(Party.class, body.partyId());
        if (party == null) {
            throw ErpException.business("Customer tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(party.getIsActive())) {
            throw ErpException.business("Customer '" + party.getName() + "' tidak aktif.");
        }
        if (party.getPartyType() != PartyType.Customer && party.getPartyType() != PartyType.Both) {
            throw ErpException.business("Party '" + party.getName() + "' bukan customer.");
        }

        String doNumber = numbers.generate("DO", date, fy.getId());

        stockGuard.check(body.items(), body.allowNegativeStock(), body.negativeStockReason(),
                " Buat produksi/transfer/penerimaan dulu.", "DO submitted partyId=" + body.partyId() + " userId=" + userId);

        // Akun lawan yang dipakai invoice untuk HPP; disimpan di setiap gerakan Out.
        String cogsAccountId = cogs.resolveCogsAccountId();

        Instant now = Instant.now();
        DeliveryOrder d = new DeliveryOrder();
        d.setDoNumber(doNumber);
        d.setDate(date);
        d.setPartyId(body.partyId());
        d.setStatus(DocumentStatus.Submitted);
        d.setNotes(Zod.truthy(body.notes()));
        d.setNomorMobil(Zod.truthy(body.nomorMobil()));
        d.setDriverName(Zod.truthy(body.driverName()));
        d.setRecipientName(Zod.truthy(body.recipientName()));
        d.setFiscalYearId(fy.getId());
        d.setCreatedBy(userId);
        d.setSubmittedAt(now);
        em.persist(d);
        em.flush();

        // created_at baris bertambah 1 ms agar urutan input terjaga (kolom presisi milidetik).
        Instant base = now.truncatedTo(ChronoUnit.MILLIS);
        List<DeliveryOrderItem> created = new ArrayList<>();
        int seq = 0;
        for (FulfillmentRequests.DoItem it : body.items()) {
            DeliveryOrderItem row = new DeliveryOrderItem();
            row.setDeliveryOrderId(d.getId());
            row.setItemName(it.itemName());
            row.setUnit(it.unit());
            row.setDescription(Zod.truthy(it.description()));
            row.setCreatedAt(base.plusMillis(seq++));
            if (it.inventoryItemId() != null) {
                InventoryItem inv = em.find(InventoryItem.class, it.inventoryItemId());
                if (inv == null) {
                    throw ErpException.business("Item '" + it.itemName() + "' tidak ditemukan.");
                }
                String warehouseId = it.warehouseId() != null ? it.warehouseId() : stock.resolveWarehouseId(null);
                if (warehouseId == null) {
                    throw ErpException.business("Item '" + inv.getName() + "' perlu pilih gudang sumber.");
                }
                BigDecimal qty = it.quantity();
                BigDecimal unitCost = Money.nz(stock.currentItemStock(inv.getId()).averageCost());
                BigDecimal totalValue = Money.round2(qty.multiply(unitCost));

                // Baris oversold ditolak: stok gudang minus merusak WAC & HPP. allowNegativeStock tetap bypass.
                BigDecimal availableQty = stock.warehouseStock(inv.getId(), warehouseId);
                if (availableQty.compareTo(qty) < 0 && !body.allowNegativeStock()) {
                    Warehouse wh = em.find(Warehouse.class, warehouseId);
                    throw ErpException.business("Stok '" + inv.getName() + "' di gudang "
                            + (wh != null ? wh.getCode() : warehouseId) + " kurang. "
                            + "Tersedia " + FulfillmentJson.js(availableQty) + " " + inv.getUnit() + ", diminta "
                            + FulfillmentJson.js(qty) + " " + inv.getUnit() + ". "
                            + "Buat produksi/transfer/penerimaan dulu sebelum kirim.");
                }

                stock.adjustItemStock(inv.getId(), qty.negate());
                stock.adjustWarehouseStock(inv.getId(), warehouseId, qty.negate());

                StockMovement sm = new StockMovement();
                sm.setMovementNumber(numbers.generate("SM", date, fy.getId()));
                sm.setDate(date);
                sm.setItemId(inv.getId());
                sm.setWarehouseId(warehouseId);
                sm.setMovementType(StockMovementType.Out);
                sm.setQuantity(qty);
                sm.setUnitCost(unitCost);
                sm.setTotalValue(totalValue);
                sm.setNotes("Auto dari " + doNumber);
                sm.setReferenceType(REFERENCE_TYPE);
                sm.setReferenceId(d.getId());
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
                row.setInventoryItemId(null);
                row.setWarehouseId(it.warehouseId());
                row.setQuantity(it.quantity());
                row.setStockMovementId(null);
            }
            em.persist(row);
            created.add(row);
        }
        em.flush();

        // Tautkan Surat Jalan Sementara (Open, pelanggan sama) — satu transaksi dengan pembuatan DO.
        if (body.temporaryNoteIds() != null && !body.temporaryNoteIds().isEmpty()) {
            temporaryNotes.linkToDeliveryOrder(d.getId(), body.partyId(), body.temporaryNoteIds());
        }

        log.info("Delivery order submitted doNumber={} items={} partyId={}", doNumber, body.items().size(), body.partyId());
        return new Submitted(d, created);
    }
}
