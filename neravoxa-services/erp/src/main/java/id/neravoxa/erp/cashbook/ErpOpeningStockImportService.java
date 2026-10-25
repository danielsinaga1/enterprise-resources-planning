package id.neravoxa.erp.cashbook;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.core.inventory.ErpWarehouseStockService;
import id.neravoxa.erp.core.inventory.WeightedAverage;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.InventoryItem;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.StockMovement;
import id.neravoxa.erp.entity.StockMovementType;
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
 * Stok awal hasil impor — port {@code services/openingStockImport.ts}: GL DR Persediaan / CR Ekuitas Saldo
 * Awal, lalu gerakan In yang tertaut ke jurnal itu (gudang, akun lawan, jurnal tersimpan) dan stok per
 * gudang, sama seperti jalur barang masuk lain. Stok & rata-rata lewat helper kernel (SQL atomik).
 */
@Service
@RequiredArgsConstructor
public class ErpOpeningStockImportService {

    private final ErpFiscalYearService fiscalYears;
    private final ErpSystemAccountService systemAccounts;
    private final ErpDocumentNumberService numbers;
    private final ErpGlPostingService gl;
    private final ErpWarehouseStockService stock;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Gudang baris impor: kode eksplisit dulu, selain itu gudang default. */
    private String resolveImportWarehouseId(String warehouseCode) {
        if (warehouseCode != null && !warehouseCode.isEmpty()) {
            List<String> ids = em.createQuery("select w.id from Warehouse w where w.code = :c", String.class)
                    .setParameter("c", warehouseCode).setMaxResults(1).getResultList();
            if (ids.isEmpty()) {
                throw ErpException.business("Gudang dengan kode \"" + warehouseCode + "\" tidak ditemukan.");
            }
            return ids.get(0);
        }
        String id = stock.resolveWarehouseId(null);
        if (id == null) {
            throw ErpException.business("Gudang belum ditentukan: isi kolom warehouseCode atau set salah satu gudang sebagai default.");
        }
        return id;
    }

    /**
     * Posting stok awal satu barang. {@code unitCost} tidak dibulatkan (dipakai untuk WAC seperti sumber);
     * nilai yang disimpan dibulatkan sesuai skala kolom.
     */
    @ErpTx
    public Map<String, Object> post(String itemId, BigDecimal quantity, BigDecimal unitCost, BigDecimal totalValue,
                                    String warehouseCode, String userId) {
        InventoryItem item = em.find(InventoryItem.class, itemId);
        if (item == null) {
            throw ErpException.business("Barang tidak ditemukan.");
        }
        Instant now = Instant.now();
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(now);
        String warehouseId = resolveImportWarehouseId(warehouseCode);

        // GL: DR Persediaan (akun barang, atau akun sistem INVENTORY) / CR Ekuitas Saldo Awal.
        String invAccountId;
        if (item.getAccountId() != null) {
            Account acc = em.find(Account.class, item.getAccountId());
            invAccountId = acc != null ? acc.getId() : null;
        } else {
            invAccountId = systemAccounts.getAccountId(SystemAccountRole.INVENTORY);
        }
        String openingEquityId = systemAccounts.getAccountId(SystemAccountRole.OPENING_EQUITY);

        String journalEntryId = null;
        if (invAccountId != null) {
            String description = "Saldo awal stok: " + item.getCode();
            JournalEntry journal = gl.journal().date(now)
                    .narration("Saldo awal stok: " + item.getCode() + " - " + item.getName())
                    .fiscalYear(fiscalYear.getId()).createdBy(userId).submittedAt(now)
                    .line(invAccountId, totalValue, BigDecimal.ZERO, description)
                    .line(openingEquityId, BigDecimal.ZERO, totalValue, description)
                    .post();
            journalEntryId = journal.getId();
        }

        // Stok & rata-rata dibaca langsung dari DB sebelum gerakan masuk (bukan dari entity yang bisa basi).
        ErpWarehouseStockService.ItemStock before = stock.currentItemStock(itemId);

        StockMovement mv = new StockMovement();
        mv.setMovementNumber(numbers.generate("SM", now, fiscalYear.getId()));
        mv.setItemId(itemId);
        mv.setWarehouseId(warehouseId);
        mv.setMovementType(StockMovementType.In);
        mv.setQuantity(Money.round(quantity, 3));
        mv.setUnitCost(Money.round2(unitCost));
        mv.setTotalValue(Money.round2(totalValue));
        mv.setDate(now);
        mv.setReferenceType("OpeningBalance");
        mv.setOffsetAccountId(journalEntryId != null ? openingEquityId : null);
        mv.setJournalEntryId(journalEntryId);
        mv.setNotes("Stok awal dari import: " + item.getCode() + " - " + item.getName());
        mv.setCreatedById(userId);
        mv.setFiscalYearId(fiscalYear.getId());
        em.persist(mv);

        BigDecimal newAvgCost = WeightedAverage.calc(before.currentStock(), before.averageCost(), quantity, unitCost);
        stock.adjustItemStock(itemId, quantity);
        stock.setAverageCost(itemId, newAvgCost);
        stock.adjustWarehouseStock(itemId, warehouseId, quantity);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("movementId", mv.getId());
        out.put("journalEntryId", journalEntryId);
        out.put("warehouseId", warehouseId);
        return out;
    }
}
