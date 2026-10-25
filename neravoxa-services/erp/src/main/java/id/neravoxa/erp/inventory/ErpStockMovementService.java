package id.neravoxa.erp.inventory;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpAccountBalanceService;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.core.inventory.ErpCogsService;
import id.neravoxa.erp.core.inventory.ErpWarehouseStockService;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.InventoryItem;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.StockMovement;
import id.neravoxa.erp.entity.StockMovementType;
import id.neravoxa.erp.entity.Warehouse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gerakan stok manual — port {@code routes/inventory/movements.ts} (+ stockMovementList.ts).
 *
 * <p>Aturan posting GL: totalValue &gt; 0 selalu dijurnal. Stok masuk = DR Persediaan (akun item atau
 * akun sistem INVENTORY) / CR akun lawan; stok keluar sebaliknya. Akun lawan default = Ekuitas Saldo
 * Awal (OPENING_EQUITY) dan tidak boleh sama dengan akun persediaan (akan saling meniadakan).
 * Semua perubahan stok lewat helper kernel (SQL atomik).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpStockMovementService {

    private static final String NO_FY = "Tidak ada tahun fiskal aktif untuk tanggal transaksi ini.";
    private static final String NOT_FOUND = "Gerakan stok tidak ditemukan.";

    private final ErpWarehouseStockService stock;
    private final ErpCogsService cogs;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpSystemAccountService systemAccounts;
    private final ErpDocumentNumberService numbers;
    private final ErpFiscalYearService fiscalYears;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── baca ─────────────────────────────

    /** GET /inventory/movements — paging (limit ≤ 200); tanpa {@code sort} urut nomor gerakan desc. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        JpqlWhere where = StockMovementList.where(query);
        String orderBy = !query.containsKey("sort")
                ? StockMovementList.LEGACY_ORDER
                : StockMovementList.orderBy(ListQuery.sort(query.get("sort"), StockMovementList.SORT_FIELDS,
                StockMovementList.DEFAULT_SORT));
        ErpQuery<StockMovement> q = where.applyTo(ErpQuery.of(em, StockMovement.class, "m")
                .join(StockMovementList.JOIN)
                .fetch("left join fetch m.item", "left join fetch m.warehouse", "left join fetch m.offsetAccount")
                .orderBy(orderBy));
        long total = q.count();
        List<Map<String, Object>> data = q.list(paging.skip(), paging.limit()).stream().map(m -> {
            Map<String, Object> json = movementJson(m);
            json.put("warehouse", InvJson.pick(m.getWarehouse(), "id", "name", "code"));
            json.put("offsetAccount", InvJson.pick(m.getOffsetAccount(), "id", "name", "accountNumber"));
            return json;
        }).toList();
        return ListQuery.result(data, total, paging);
    }

    /** GET /inventory/movements/summary — KPI untuk filter yang sama dengan daftar. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        JpqlWhere where = StockMovementList.where(query);
        TypedQuery<Object[]> q = em.createQuery("select m.movementType, m.isCancelled, count(m), sum(m.quantity), "
                + "sum(m.totalValue) from StockMovement m " + StockMovementList.JOIN + where.clause()
                + " group by m.movementType, m.isCancelled", Object[].class);
        where.bind(q);
        return StockMovementList.summarize(q.getResultList());
    }

    /** GET /inventory/movements/:id — satu gerakan untuk form edit. */
    @ErpTx(readOnly = true)
    public Map<String, Object> get(String id) {
        List<StockMovement> rows = em.createQuery("select m from StockMovement m left join fetch m.item "
                        + "left join fetch m.offsetAccount where m.id = :id", StockMovement.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound(NOT_FOUND);
        }
        Map<String, Object> json = movementJson(rows.get(0));
        json.put("offsetAccount", InvJson.pick(rows.get(0).getOffsetAccount(), "id", "name", "accountNumber"));
        return json;
    }

    /** Gerakan + {@code item: { id, name, code, unit }}. */
    private static Map<String, Object> movementJson(StockMovement m) {
        Map<String, Object> json = InvJson.scalars(m);
        json.put("item", InvJson.pick(m.getItem(), "id", "name", "code", "unit"));
        return json;
    }

    // ───────────────────────────── buat ─────────────────────────────

    /** POST /inventory/movements — catat gerakan, sinkronkan stok gudang + total, WAC, GL, COGS backfill. */
    @ErpTx(timeout = 15)
    public Map<String, Object> create(StockMovementCreateRequest body, String userId) {
        Instant date = ErpDates.toInstant(body.date());
        FiscalYear fy = fiscalYears.findOpen(date).orElseThrow(() -> ErpException.business(NO_FY));

        InventoryItem item = em.find(InventoryItem.class, body.itemId());
        if (item == null) {
            throw ErpException.business("Item stok tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(item.getIsActive())) {
            throw ErpException.business("Item stok tidak aktif.");
        }

        // Gudang dari body, selain itu gudang default.
        String warehouseId = blankToNull(body.warehouseId());
        if (warehouseId == null) {
            warehouseId = stock.resolveWarehouseId(null);
        } else if (em.find(Warehouse.class, warehouseId) == null) {
            throw ErpException.business("Referensi data tidak valid. Periksa data yang dipilih.");
        }
        if (warehouseId == null) {
            throw ErpException.business("Gudang tidak ditemukan dan tidak ada gudang default aktif.");
        }

        StockMovementType type = StockMovementType.valueOf(body.movementType());
        BigDecimal qty = body.quantity();
        boolean isOut = type == StockMovementType.Out || type == StockMovementType.AdjustmentOut;
        boolean isIn = type == StockMovementType.In || type == StockMovementType.AdjustmentIn;

        // Cek stok per gudang (sumber kebenaran), hanya untuk gerakan keluar.
        BigDecimal atWarehouse = stock.warehouseStock(item.getId(), warehouseId);
        if (isOut && atWarehouse.compareTo(qty) < 0) {
            Warehouse wh = em.find(Warehouse.class, warehouseId);
            throw ErpException.business("Stok di " + (wh != null ? wh.getName() : "gudang") + " tidak cukup. Tersedia: "
                    + InvJson.idNum(atWarehouse, 3) + " " + item.getUnit());
        }

        String movementNumber = numbers.generate("SM", date, fy.getId());

        // Aritmetika desimal agar nilai GL tanpa ekor float.
        BigDecimal unitCost = body.unitCost() != null ? body.unitCost() : BigDecimal.ZERO;
        BigDecimal totalValue = Money.round2(qty.multiply(unitCost));

        // inventory_stocks (per gudang) dan inventory_items.current_stock (denormalisasi) selalu sinkron.
        BigDecimal delta = isIn ? qty : qty.negate();
        stock.adjustWarehouseStock(item.getId(), warehouseId, delta);
        stock.adjustItemStock(item.getId(), delta);
        if (isIn && unitCost.signum() > 0) {
            stock.setAverageCost(item.getId(), id.neravoxa.erp.core.inventory.WeightedAverage.calc(
                    item.getCurrentStock(), item.getAverageCost(), qty, unitCost));
        }

        String resolvedOffset = blankToNull(body.offsetAccountId());
        assertAccountExists(resolvedOffset);
        String journalEntryId = null;
        if (totalValue.signum() > 0) {
            String inventoryAccountId = item.getAccountId() != null ? item.getAccountId()
                    : systemAccounts.getAccountId(SystemAccountRole.INVENTORY);
            // Tanpa akun lawan → Ekuitas Saldo Awal (stok awal); sama dengan akun persediaan juga dialihkan.
            if (resolvedOffset == null || resolvedOffset.equals(inventoryAccountId)) {
                resolvedOffset = systemAccounts.getAccountId(SystemAccountRole.OPENING_EQUITY);
            }
            String debit = isIn ? inventoryAccountId : resolvedOffset;
            String credit = isIn ? resolvedOffset : inventoryAccountId;
            // Baris jurnal & ledger membawa narasi agar Buku Besar/rekonsiliasi tidak kosong.
            String narration = typeLabel(type) + ": " + item.getName() + " " + InvJson.js(qty) + " " + item.getUnit()
                    + (notEmpty(body.notes()) ? " - " + body.notes() : "");
            JournalEntry je = gl.journal().date(date).narration(narration).fiscalYear(fy.getId()).createdBy(userId)
                    .line(debit, totalValue, BigDecimal.ZERO, narration)
                    .line(credit, BigDecimal.ZERO, totalValue, narration)
                    .post();
            journalEntryId = je.getId();
        }

        StockMovement m = new StockMovement();
        m.setMovementNumber(movementNumber);
        m.setDate(date);
        m.setItemId(item.getId());
        m.setWarehouseId(warehouseId);
        m.setMovementType(type);
        m.setQuantity(qty);
        m.setUnitCost(unitCost);
        m.setTotalValue(totalValue);
        m.setNotes(blankToNull(body.notes()));
        m.setReferenceType(blankToNull(body.referenceType()));
        m.setReferenceId(blankToNull(body.referenceId()));
        m.setReferenceNumber(blankToNull(body.referenceNumber()));
        m.setOffsetAccountId(resolvedOffset);
        // Penyesuaian keluar boleh menautkan batch masuk agar konsumsi batch tetap akurat.
        m.setSourceMovementId(isOut ? blankToNull(body.sourceMovementId()) : null);
        m.setJournalEntryId(journalEntryId);
        m.setFiscalYearId(fy.getId());
        m.setCreatedById(userId);
        em.persist(m);
        em.flush();

        // Stok masuk otomatis menyelesaikan antrian koreksi HPP.
        if (isIn) {
            cogs.settleCogsBackfillForItem(item.getId(), qty, unitCost, fy.getId(), userId, date,
                    new ErpCogsService.SettleSource("StockMovement", m.getId(), movementNumber));
        }
        return reload(m.getId());
    }

    // ───────────────────────────── edit ─────────────────────────────

    /** PUT /inventory/movements/:id — batalkan efek lama lalu terapkan nilai baru (gudang tetap). */
    @ErpTx(timeout = 15)
    public Map<String, Object> update(String id, StockMovementUpdateRequest body, String userId) {
        StockMovement old = em.find(StockMovement.class, id);
        if (old == null) {
            throw ErpException.business(NOT_FOUND);
        }
        if (Boolean.TRUE.equals(old.getIsCancelled())) {
            throw ErpException.business("Gerakan stok sudah dibatalkan, tidak bisa diedit.");
        }
        // Gerakan dari STT/surat jalan/retur diedit dari dokumennya: edit per baris membuat jurnal dokumen yatim.
        String lock = ErpCogsService.documentMovementLockMessage(old.getReferenceType(), true);
        if (lock != null) {
            throw ErpException.business(lock);
        }
        if ("ProductionRun".equals(old.getReferenceType())) {
            throw ErpException.business("Gerakan stok dari proses produksi tidak dapat diedit secara langsung.");
        }
        InventoryItem item = em.find(InventoryItem.class, old.getItemId());

        boolean oldIsIn = old.getMovementType() == StockMovementType.In
                || old.getMovementType() == StockMovementType.AdjustmentIn;
        BigDecimal oldQty = Money.nz(old.getQuantity());
        BigDecimal oldReverse = oldIsIn ? oldQty.negate() : oldQty;

        if (Money.nz(item.getCurrentStock()).add(oldReverse).signum() < 0) {
            throw ErpException.business("Tidak dapat mengedit: pembalikan stok lama menyebabkan stok negatif.");
        }
        stock.adjustItemStock(old.getItemId(), oldReverse);
        stock.adjustWarehouseStock(old.getItemId(), old.getWarehouseId(), oldReverse);

        Set<String> oldAffected = new LinkedHashSet<>();
        if (old.getJournalEntryId() != null) {
            JournalEntry oldJe = em.find(JournalEntry.class, old.getJournalEntryId());
            if (oldJe != null) {
                oldAffected.addAll(journalAccountIds(oldJe.getId()));
                cancelJournalNoBalance(oldJe);
            }
        }

        StockMovementType newType = StockMovementType.valueOf(body.movementType());
        boolean newIsIn = newType == StockMovementType.In || newType == StockMovementType.AdjustmentIn;
        BigDecimal newQty = body.quantity();

        // Stok terkini setelah pembalikan.
        ErpWarehouseStockService.ItemStock after = stock.currentItemStock(old.getItemId());
        if (after == null) {
            throw ErpException.business("Item stok tidak ditemukan.");
        }
        if (newType == StockMovementType.Out && after.currentStock().compareTo(newQty) < 0) {
            throw ErpException.business("Stok tidak cukup. Stok saat ini (setelah pembalikan): "
                    + InvJson.idNum(after.currentStock(), 3) + " " + item.getUnit());
        }
        BigDecimal newDelta = newIsIn ? newQty : newQty.negate();
        stock.adjustItemStock(old.getItemId(), newDelta);
        // Gudang tetap gudang tempat gerakan semula diposting.
        stock.adjustWarehouseStock(old.getItemId(), old.getWarehouseId(), newDelta);

        BigDecimal newUnitCost = body.unitCost() != null ? body.unitCost() : BigDecimal.ZERO;
        BigDecimal newTotal = Money.round2(newQty.multiply(newUnitCost));
        String newJournalEntryId = null;
        String resolvedOffset = blankToNull(body.offsetAccountId());
        assertAccountExists(resolvedOffset);
        Instant date = ErpDates.toInstant(body.date());

        if (newTotal.signum() > 0) {
            String inventoryAccountId = item.getAccountId() != null ? item.getAccountId()
                    : systemAccounts.getAccountId(SystemAccountRole.INVENTORY);
            if (resolvedOffset == null || resolvedOffset.equals(inventoryAccountId)) {
                resolvedOffset = systemAccounts.getAccountId(SystemAccountRole.OPENING_EQUITY);
            }
            String debit = newIsIn ? inventoryAccountId : resolvedOffset;
            String credit = newIsIn ? resolvedOffset : inventoryAccountId;
            FiscalYear fy = fiscalYears.findOpen(date).orElseThrow(() -> ErpException.business(NO_FY));
            String narration = typeLabel(newType) + ": " + item.getName() + " " + InvJson.js(newQty) + " "
                    + item.getUnit() + (notEmpty(body.notes()) ? " - " + body.notes() : "") + " (Edit)";
            JournalEntry je = gl.journal().date(date).narration(narration).fiscalYear(fy.getId()).createdBy(userId)
                    .line(debit, newTotal, BigDecimal.ZERO, narration)
                    .line(credit, BigDecimal.ZERO, newTotal, narration)
                    .post();
            // Hitung ulang saldo semua akun terdampak (lama + baru) dari ledger.
            Set<String> all = new LinkedHashSet<>(oldAffected);
            all.add(debit);
            all.add(credit);
            balances.recalculateAccountBalances(all);
            newJournalEntryId = je.getId();
        } else if (!oldAffected.isEmpty()) {
            // Tanpa GL baru tetapi jurnal lama dibatalkan — hitung ulang akun lama.
            balances.recalculateAccountBalances(oldAffected);
        }

        old.setDate(date);
        old.setMovementType(newType);
        old.setQuantity(newQty);
        old.setUnitCost(newUnitCost);
        old.setTotalValue(newTotal);
        old.setNotes(blankToNull(body.notes()));
        old.setReferenceType(blankToNull(body.referenceType()));
        old.setReferenceNumber(blankToNull(body.referenceNumber()));
        old.setOffsetAccountId(resolvedOffset);
        old.setJournalEntryId(newJournalEntryId);
        em.flush();

        // Edit gerakan masuk membuat auto-settle HPP basi: balik dulu, lalu settle ulang bila masih stok masuk.
        cogs.reverseCogsSettlementsByTrigger("StockMovement", old.getId());
        if (newIsIn && newQty.signum() > 0) {
            cogs.settleCogsBackfillForItem(old.getItemId(), newQty, newUnitCost, old.getFiscalYearId(), userId, date,
                    new ErpCogsService.SettleSource("StockMovement", old.getId(), old.getMovementNumber()));
        }
        // Edit bisa mengubah tipe/qty/harga → WAC diturunkan ulang dengan replay semua gerakan aktif.
        cogs.recomputeAverageCostFromReplay(old.getItemId());
        return reload(old.getId());
    }

    // ───────────────────────────── batal ─────────────────────────────

    /** PUT /inventory/movements/:id/cancel — balik stok, batalkan jurnal, balik auto-settle HPP, replay WAC. */
    @ErpTx(timeout = 15)
    public Map<String, Object> cancel(String id) {
        StockMovement m = em.find(StockMovement.class, id);
        if (m == null) {
            throw ErpException.business(NOT_FOUND);
        }
        if (Boolean.TRUE.equals(m.getIsCancelled())) {
            throw ErpException.business("Gerakan stok sudah dibatalkan.");
        }
        String lock = ErpCogsService.documentMovementLockMessage(m.getReferenceType(), false);
        if (lock != null) {
            throw ErpException.business(lock);
        }
        InventoryItem item = em.find(InventoryItem.class, m.getItemId());
        boolean isIn = m.getMovementType() == StockMovementType.In || m.getMovementType() == StockMovementType.AdjustmentIn;
        boolean isOut = m.getMovementType() == StockMovementType.Out || m.getMovementType() == StockMovementType.AdjustmentOut;
        BigDecimal qty = Money.nz(m.getQuantity());

        // Pembalikan tidak boleh membuat stok negatif.
        if (isIn && Money.nz(item.getCurrentStock()).compareTo(qty) < 0) {
            throw ErpException.business("Tidak dapat membatalkan: stok tidak mencukupi untuk pembalikan.");
        }
        BigDecimal reverse = isOut ? qty : qty.negate();
        stock.adjustItemStock(m.getItemId(), reverse);
        stock.adjustWarehouseStock(m.getItemId(), m.getWarehouseId(), reverse);

        if (m.getJournalEntryId() != null) {
            JournalEntry je = em.find(JournalEntry.class, m.getJournalEntryId());
            if (je != null) {
                Set<String> affected = new LinkedHashSet<>(journalAccountIds(je.getId()));
                cancelJournalNoBalance(je);
                // Saldo dihitung ulang dari jumlah ledger aktif (mencegah drift).
                balances.recalculateAccountBalances(affected);
            }
        }
        m.setIsCancelled(true);
        em.flush();
        // Balik auto-settle COGS yang dipicu gerakan manual ini.
        cogs.reverseCogsSettlementsByTrigger("StockMovement", id);
        // Gerakan batal tidak lagi berkontribusi: WAC diturunkan ulang dengan replay.
        cogs.recomputeAverageCostFromReplay(m.getItemId());
        return Map.of("message", "Gerakan stok dibatalkan.");
    }

    // ───────────────────────────── helper ─────────────────────────────

    private List<String> journalAccountIds(String journalEntryId) {
        return em.createQuery("select i.accountId from JournalItem i where i.journalEntryId = :id", String.class)
                .setParameter("id", journalEntryId).getResultList();
    }

    /**
     * Status jurnal Cancelled + ledger is_cancelled, TANPA menyentuh saldo — pemanggil menghitung ulang
     * saldo dari ledger ({@code recalculateAccountBalances}) seperti sumber.
     */
    private void cancelJournalNoBalance(JournalEntry je) {
        je.setStatus(DocumentStatus.Cancelled);
        je.setCancelledAt(Instant.now());
        em.flush();
        em.createQuery("update AccountingLedgerEntry e set e.isCancelled = true where e.referenceId = :id")
                .setParameter("id", je.getId()).executeUpdate();
    }

    private Map<String, Object> reload(String id) {
        em.flush();
        em.clear();
        StockMovement m = em.createQuery("select m from StockMovement m left join fetch m.item where m.id = :id",
                StockMovement.class).setParameter("id", id).getSingleResult();
        return movementJson(m);
    }

    /** FK akun lawan tidak valid → pesan P2003 ENTARIS (bukan 500 dari flush). */
    private void assertAccountExists(String accountId) {
        if (accountId != null && em.find(Account.class, accountId) == null) {
            throw ErpException.business("Referensi data tidak valid. Periksa data yang dipilih.");
        }
    }

    private static String typeLabel(StockMovementType t) {
        return switch (t) {
            case In -> "Stok Masuk";
            case Out -> "Stok Keluar";
            case AdjustmentIn -> "Penyesuaian Stok +";
            case AdjustmentOut -> "Penyesuaian Stok −";
            default -> "undefined";
        };
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
