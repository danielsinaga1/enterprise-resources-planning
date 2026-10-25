package id.neravoxa.erp.core.inventory;

import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpAccountBalanceService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.CogsBackfillQueue;
import id.neravoxa.erp.entity.CogsBackfillSettlement;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.InventoryItem;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.StockMovementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * HPP, antrian koreksi HPP (COGS backfill) dan re-derivasi harga rata-rata — port
 * {@code services/cogsAccount.ts}, {@code cogsBackfillService.ts}, {@code cogsBackfillReverse.ts},
 * {@code averageCostRecompute.ts} dan {@code documentMovementLock.ts}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpCogsService {

    private static final BigDecimal NEAR_ZERO_QTY = new BigDecimal("0.0005");
    private static final BigDecimal NEAR_ZERO_AMT = new BigDecimal("0.005");
    private static final BigDecimal STOCK_TOLERANCE = new BigDecimal("0.001");

    private final ErpSystemAccountService systemAccounts;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpWarehouseStockService stock;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── akun HPP ─────────────────────────────

    /**
     * Akun yang didebit invoice penjualan untuk HPP barang terkirim: 5.1 (HPP Beras) bila ada,
     * selain itu akun sistem COGS. Null (bukan error) agar pemetaan yang hilang tidak memblokir pengiriman.
     */
    @ErpTx(readOnly = true)
    public String resolveCogsAccountId() {
        Account hpp = systemAccounts.findByNumber("5.1");
        if (hpp != null) {
            return hpp.getId();
        }
        List<ErpSystemAccountService.MappedAccount> cogs = systemAccounts.getAccounts(SystemAccountRole.COGS);
        return cogs.isEmpty() ? null : cogs.get(0).id();
    }

    // ─────────────────────── kunci gerakan dokumen ───────────────────────

    /**
     * Gerakan stok milik dokumen sumber (STT, surat jalan, retur) tidak boleh diedit/dibatalkan per baris.
     * Mengembalikan pesan penolakan, atau null bila boleh.
     */
    public static String documentMovementLockMessage(String referenceType, boolean edit) {
        String label;
        String where;
        if ("GoodsReceipt".equals(referenceType)) {
            label = "STT";
            where = "halaman STT";
        } else if ("DeliveryOrder".equals(referenceType)) {
            label = "surat jalan";
            where = "halaman Surat Jalan";
        } else if ("SalesReturn".equals(referenceType)) {
            label = "retur penjualan";
            where = "halaman Retur Penjualan";
        } else {
            return null;
        }
        return edit
                ? "Gerakan stok dari " + label + " tidak dapat diedit per baris. Ubah atau batalkan dokumennya dari " + where + "."
                : "Gerakan stok dari " + label + " tidak dapat dibatalkan per baris. Batalkan dokumennya dari " + where + ".";
    }

    // ───────────────────────── replay harga rata-rata ─────────────────────────

    /**
     * Turunkan ulang averageCost dengan me-replay gerakan aktif item. Dilewati (peringatan di log)
     * bila gerakan tidak menjelaskan stok saat ini (saldo awal tanpa gerakan). Mengembalikan avg akhir.
     */
    @ErpTx
    public BigDecimal recomputeAverageCostFromReplay(String itemId) {
        em.flush();
        ErpWarehouseStockService.ItemStock item = stock.currentItemStock(itemId);
        if (item == null) {
            return BigDecimal.ZERO;
        }
        List<Object[]> rows = em.createQuery(
                        "select m.id, m.movementType, m.quantity, m.unitCost, m.createdAt, m.referenceType "
                                + "from StockMovement m where m.itemId = :id and m.isCancelled = false "
                                + "order by m.createdAt asc, m.id asc", Object[].class)
                .setParameter("id", itemId).getResultList();
        List<WeightedAverage.ReplayMovement> movements = new ArrayList<>();
        for (Object[] r : rows) {
            movements.add(new WeightedAverage.ReplayMovement((String) r[0], (StockMovementType) r[1],
                    (BigDecimal) r[2], (BigDecimal) r[3], (Instant) r[4], (String) r[5], false));
        }
        WeightedAverage.ReplayResult res = WeightedAverage.replay(movements, item.averageCost(), null, null);
        BigDecimal residual = item.currentStock().subtract(res.stock());
        if (residual.abs().compareTo(STOCK_TOLERANCE) > 0) {
            log.warn("Average cost recompute skipped: item {} stock {} not explained by movements (replayed {}, residual {})",
                    itemId, item.currentStock(), res.stock(), residual);
            return item.averageCost();
        }
        stock.setAverageCost(itemId, res.averageCost());
        return res.averageCost();
    }

    // ───────────────────────── antrian koreksi HPP ─────────────────────────

    public record SettleSource(String type, String refId, String refNo) {
    }

    public record SettleResult(int settledCount, BigDecimal totalQty, BigDecimal totalDifferential, String journalEntryId) {
    }

    /**
     * Setelah stok masuk, selesaikan antrian koreksi HPP item (FIFO) dan posting satu jurnal selisih
     * (DR HPP / CR Persediaan, atau sebaliknya). Null tanpa menulis DB bila tidak ada antrian.
     *
     * @param source type = PurchaseInvoice | ProductionRun | StockMovement (+ GoodsReceipt)
     */
    @ErpTx
    public SettleResult settleCogsBackfillForItem(String itemId, BigDecimal qtyAvailable, BigDecimal unitCostNow,
                                                  String fiscalYearId, String userId, Instant settleDate,
                                                  SettleSource source) {
        if (qtyAvailable == null || qtyAvailable.compareTo(NEAR_ZERO_QTY) <= 0) {
            return null;
        }
        List<CogsBackfillQueue> pendings = em.createQuery(
                        "select q from CogsBackfillQueue q where q.inventoryItemId = :id "
                                + "and q.status in ('Pending', 'PartiallySettled') order by q.createdAt asc",
                        CogsBackfillQueue.class)
                .setParameter("id", itemId).getResultList();
        if (pendings.isEmpty()) {
            return null;
        }
        record S(CogsBackfillQueue q, BigDecimal qty, BigDecimal diff) {
        }
        BigDecimal remaining = qtyAvailable;
        BigDecimal totalDiff = BigDecimal.ZERO;
        BigDecimal totalQty = BigDecimal.ZERO;
        List<S> settlements = new ArrayList<>();
        for (CogsBackfillQueue p : pendings) {
            if (remaining.compareTo(NEAR_ZERO_QTY) <= 0) {
                break;
            }
            BigDecimal qtyToSettle = Money.min(Money.nz(p.getQtyPending()), remaining);
            BigDecimal diff = Money.round2(qtyToSettle.multiply(Money.nz(unitCostNow).subtract(Money.nz(p.getCostAtSale()))));
            settlements.add(new S(p, qtyToSettle, diff));
            totalDiff = totalDiff.add(diff);
            totalQty = totalQty.add(qtyToSettle);
            remaining = remaining.subtract(qtyToSettle);
        }
        if (settlements.isEmpty()) {
            return null;
        }

        String journalEntryId = null;
        if (totalDiff.abs().compareTo(NEAR_ZERO_AMT) >= 0) {
            InventoryItem item = em.find(InventoryItem.class, itemId);
            if (item == null) {
                throw new IllegalStateException("Inventory item " + itemId + " not found during COGS backfill settle.");
            }
            String invAccountId = item.getAccountId() != null ? item.getAccountId()
                    : systemAccounts.getAccountId(SystemAccountRole.INVENTORY);
            Account cogs = systemAccounts.findByNumber("5.1");
            if (cogs == null) {
                cogs = systemAccounts.findByNumber("5");
            }
            if (cogs == null) {
                throw id.neravoxa.erp.core.ErpException.business(
                        "Akun HPP (5 atau 5.1) tidak ditemukan untuk auto-settle COGS backfill.");
            }
            boolean positive = totalDiff.signum() > 0;
            String debitAcc = positive ? cogs.getId() : invAccountId;
            String creditAcc = positive ? invAccountId : cogs.getId();
            BigDecimal amt = totalDiff.abs();
            String narration = "Auto-settle COGS " + item.getName() + ": " + settlements.size() + " entry, trigger "
                    + source.type() + (source.refNo() != null ? " " + source.refNo() : "");
            JournalEntry je = gl.journal()
                    .date(settleDate).submittedAt(settleDate).narration(narration)
                    .fiscalYear(fiscalYearId).createdBy(userId)
                    .line(new ErpGlPostingService.Line(debitAcc, amt, BigDecimal.ZERO,
                            (positive ? "DR HPP " : "DR Persediaan ") + item.getName(), null, null))
                    .line(new ErpGlPostingService.Line(creditAcc, BigDecimal.ZERO, amt,
                            (positive ? "CR Persediaan " : "CR HPP ") + item.getName(), null, null))
                    .post();
            journalEntryId = je.getId();

            // Selaraskan averageCost: nilai GL persediaan berkurang sebesar selisih yang dibebankan.
            ErpWarehouseStockService.ItemStock fresh = stock.currentItemStock(itemId);
            if (fresh != null && fresh.currentStock().abs().compareTo(NEAR_ZERO_QTY) > 0) {
                BigDecimal oldGl = fresh.currentStock().multiply(fresh.averageCost());
                BigDecimal newAvg = oldGl.subtract(totalDiff).divide(fresh.currentStock(), 2, RoundingMode.HALF_UP);
                stock.setAverageCost(itemId, newAvg.signum() < 0 ? BigDecimal.ZERO : newAvg);
            }
        }

        for (S s : settlements) {
            BigDecimal newPending = Money.nz(s.q().getQtyPending()).subtract(s.qty());
            boolean full = newPending.compareTo(NEAR_ZERO_QTY) <= 0;
            s.q().setQtyPending(full ? BigDecimal.ZERO : newPending.setScale(3, RoundingMode.HALF_UP));
            s.q().setStatus(full ? "Settled" : "PartiallySettled");
            s.q().setSettledAt(full ? settleDate : null);

            CogsBackfillSettlement st = new CogsBackfillSettlement();
            st.setQueueId(s.q().getId());
            st.setQtySettled(s.qty());
            st.setCostAtSettle(Money.round2(unitCostNow));
            st.setDifferential(s.diff());
            st.setJournalEntryId(journalEntryId);
            st.setTriggerSource(source.type());
            st.setTriggerRefId(source.refId());
            st.setTriggerRefNo(source.refNo());
            em.persist(st);
        }
        log.info("COGS backfill settled item={} count={} qty={} diff={} je={} source={}",
                itemId, settlements.size(), totalQty, totalDiff, journalEntryId, source);
        return new SettleResult(settlements.size(), totalQty, totalDiff, journalEntryId);
    }

    public record ReverseResult(int reversed, int journalsCancelled) {
    }

    /**
     * Balik semua auto-settle yang dipicu satu dokumen stok-masuk (saat batal / sebelum dibuat ulang
     * pada edit): jurnal auto-settle dibatalkan, qtyPending antrian dipulihkan, baris settlement dihapus.
     * averageCost TIDAK dibalik aritmetis — pemanggil yang mengubah stok memanggil
     * {@link #recomputeAverageCostFromReplay(String)}.
     */
    @ErpTx
    public ReverseResult reverseCogsSettlementsByTrigger(String triggerSource, String triggerRefId) {
        List<CogsBackfillSettlement> settlements = em.createQuery(
                        "select s from CogsBackfillSettlement s join fetch s.queue "
                                + "where s.triggerSource = :src and s.triggerRefId = :ref order by s.createdAt desc",
                        CogsBackfillSettlement.class)
                .setParameter("src", triggerSource).setParameter("ref", triggerRefId).getResultList();
        if (settlements.isEmpty()) {
            return new ReverseResult(0, 0);
        }
        Set<String> jeIds = new LinkedHashSet<>();
        settlements.forEach(s -> {
            if (s.getJournalEntryId() != null) {
                jeIds.add(s.getJournalEntryId());
            }
        });
        Set<String> affected = new LinkedHashSet<>();
        int cancelled = 0;
        for (String jeId : jeIds) {
            JournalEntry je = em.find(JournalEntry.class, jeId);
            if (je == null || je.getStatus() == DocumentStatus.Cancelled) {
                continue;
            }
            je.setStatus(DocumentStatus.Cancelled);
            je.setCancelledAt(Instant.now());
            em.flush();
            em.createQuery("update AccountingLedgerEntry e set e.isCancelled = true where e.referenceId = :id")
                    .setParameter("id", jeId).executeUpdate();
            em.createQuery("select i.accountId from JournalItem i where i.journalEntryId = :id", String.class)
                    .setParameter("id", jeId).getResultList().forEach(affected::add);
            cancelled++;
        }
        for (CogsBackfillSettlement s : settlements) {
            CogsBackfillQueue q = s.getQueue();
            BigDecimal restored = Money.nz(q.getQtyPending()).add(Money.nz(s.getQtySettled()));
            BigDecimal original = Money.nz(q.getQtyOriginal());
            BigDecimal capped = Money.min(restored, original);
            boolean untouched = capped.subtract(original).abs().compareTo(NEAR_ZERO_QTY) <= 0;
            q.setQtyPending(capped.setScale(3, RoundingMode.HALF_UP));
            q.setStatus(untouched ? "Pending" : "PartiallySettled");
            q.setSettledAt(null);
        }
        em.flush();
        for (CogsBackfillSettlement s : settlements) {
            em.remove(s);
        }
        em.flush();
        if (!affected.isEmpty()) {
            balances.recalculateAccountBalances(affected);
        }
        log.info("COGS backfill settlements reversed source={} ref={} reversed={} journals={}",
                triggerSource, triggerRefId, settlements.size(), cancelled);
        return new ReverseResult(settlements.size(), cancelled);
    }
}
