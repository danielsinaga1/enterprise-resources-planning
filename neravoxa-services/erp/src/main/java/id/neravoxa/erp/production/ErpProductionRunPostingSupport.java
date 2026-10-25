package id.neravoxa.erp.production;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpAccountBalanceService;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.core.inventory.ErpCogsService;
import id.neravoxa.erp.core.inventory.ErpWarehouseStockService;
import id.neravoxa.erp.core.inventory.WeightedAverage;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.ProductionDowntime;
import id.neravoxa.erp.entity.ProductionRun;
import id.neravoxa.erp.entity.ProductionRunCost;
import id.neravoxa.erp.entity.ProductionRunItem;
import id.neravoxa.erp.entity.ProductionRunMachine;
import id.neravoxa.erp.entity.ProductionRunProcess;
import id.neravoxa.erp.entity.StockMovement;
import id.neravoxa.erp.entity.StockMovementType;
import id.neravoxa.erp.production.ProductionRequests.CostLine;
import id.neravoxa.erp.production.ProductionRequests.DowntimeLine;
import id.neravoxa.erp.production.ProductionRequests.InputLine;
import id.neravoxa.erp.production.ProductionRequests.MachineLine;
import id.neravoxa.erp.production.ProductionRequests.OutputLine;
import id.neravoxa.erp.production.ProductionRequests.ProductionRunRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Langkah posting Data Produksi yang dipakai bersama submit ({@code productionRunSubmit.ts}),
 * edit ({@code productionRunEdit.ts}) dan batal ({@code productionRunCancel.ts}): stok keluar bahan,
 * HPP loaded, stok masuk hasil + WAC + settle antrian koreksi HPP, baris jurnal produksi, baris
 * item/biaya/proses dan OEE. Semua method dipanggil di dalam transaksi pemanggil.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpProductionRunPostingSupport {

    static final String REF = "ProductionRun";
    static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    static final String NO_FISCAL_YEAR = "Tidak ada tahun fiskal aktif untuk tanggal transaksi ini.";
    static final String NO_WAREHOUSE =
            "Gudang produksi wajib dipilih. Pilih gudang di form atau set salah satu gudang sebagai default.";
    static final String RUN_NOT_FOUND = "Data proses produksi tidak ditemukan.";

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpWarehouseStockService stock;
    private final ErpCogsService cogs;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpSystemAccountService systemAccounts;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Hasil posting: id + nomor run (untuk respons & approval). */
    public record PostedRun(String id, String runNumber) {
    }

    /** Snapshot item saat dibaca (stok/avg dari DB, bukan entity yang mungkin basi). */
    record ItemInfo(String id, String name, String unit, boolean active, String accountId,
                    BigDecimal currentStock, BigDecimal averageCost) {
    }

    /** Batch sumber per indeks input: harga dikunci saat dipilih. */
    record BatchSnapshot(String sourceMovementId, BigDecimal batchUnitCost) {
    }

    record ResolvedCost(CostLine line, BigDecimal qtyBasis, BigDecimal totalAmount) {
    }

    /** Rincian HPP loaded (informatif, tanpa dampak GL selain harga output utama). */
    record Hpp(BigDecimal totalInputQty, BigDecimal totalMainOutputQty, BigDecimal totalMaterialCost,
               List<ResolvedCost> costs, BigDecimal totalCostInputSide, BigDecimal totalCostOutputSide,
               BigDecimal totalByproductValue, BigDecimal computedHpp, boolean overridden) {
        BigDecimal computedHppNum() {
            return computedHpp == null ? BigDecimal.ZERO : computedHpp;
        }
    }

    /**
     * createdAt bertingkat per milidetik untuk baris yang dibuat berurutan dalam satu posting, agar
     * urutan replay WAC (createdAt, id) dan urutan baris tetap = urutan pembuatan.
     */
    static final class Stamps {
        private Instant last;

        Instant next() {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            if (last != null && !now.isAfter(last)) {
                now = last.plusMillis(1);
            }
            last = now;
            return now;
        }
    }

    // ───────────────────────────── pembacaan ─────────────────────────────

    /** Tahun fiskal terbuka untuk tanggal transaksi (pesan khas modul produksi). */
    @ErpTx
    public FiscalYear openFiscalYear(Instant txDate) {
        return fiscalYears.findOpen(txDate).orElseThrow(() -> ErpException.business(NO_FISCAL_YEAR));
    }

    /** Item yang dirujuk body (stok & avg terkini dari DB). */
    @ErpTx
    public Map<String, ItemInfo> loadItems(ProductionRunRequest body) {
        Set<String> ids = new HashSet<>();
        body.inputs().forEach(i -> ids.add(i.itemId()));
        body.outputs().forEach(o -> ids.add(o.itemId()));
        Map<String, ItemInfo> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Object[] r : em.createQuery("select i.id, i.name, i.unit, i.isActive, i.accountId, i.currentStock, i.averageCost "
                        + "from InventoryItem i where i.id in :ids", Object[].class)
                .setParameter("ids", ids).getResultList()) {
            map.put((String) r[0], new ItemInfo((String) r[0], (String) r[1], (String) r[2], Boolean.TRUE.equals(r[3]),
                    (String) r[4], Money.nz((BigDecimal) r[5]), Money.nz((BigDecimal) r[6])));
        }
        return map;
    }

    /**
     * Pesan stok kurang. Varian submit menulis satuan hanya bila qty &gt; 0 (selalu benar karena
     * qty divalidasi positif), varian edit selalu menulis satuan.
     */
    static String stockShortMessage(ItemInfo item, BigDecimal qty, boolean submitVariant) {
        String needUnit = !submitVariant || qty.signum() > 0 ? item.unit() : "";
        return "Stok '" + item.name() + "' tidak cukup. Tersedia: " + ProdJson.idNum(item.currentStock(), 3) + " "
                + item.unit() + ", dibutuhkan: " + ProdJson.idNum(qty, 3) + " " + needUnit
                + ". Buat penerimaan/transfer dulu.";
    }

    /**
     * Validasi baris input: item ada, aktif, dan stok cukup per baris (kecuali bypass stok minus,
     * yang hanya dicatat di log). Pesan stok sesuai varian submit/edit.
     */
    public void validateInputs(ProductionRunRequest body, Map<String, ItemInfo> itemMap, boolean submitVariant) {
        for (InputLine input : body.inputs()) {
            ItemInfo item = itemMap.get(input.itemId());
            if (item == null) {
                throw ErpException.business("Item input tidak ditemukan.");
            }
            if (!item.active()) {
                throw ErpException.business("Item '" + item.name() + "' tidak aktif.");
            }
            if (item.currentStock().compareTo(input.quantity()) < 0) {
                if (!body.negativeStock()) {
                    throw ErpException.business(stockShortMessage(item, input.quantity(), submitVariant));
                }
                log.warn("Production {} input with negative stock bypass item={} name={} currentStock={} requested={} reason={}",
                        submitVariant ? "submitted" : "edit", item.id(), item.name(), item.currentStock(),
                        input.quantity(), body.negativeStockReason());
            }
        }
    }

    /** Gudang produksi: body atau gudang default; tanpa keduanya ditolak (stok per gudang harus sinkron). */
    public String productionWarehouse(String preferred) {
        String id = stock.resolveWarehouseId(preferred);
        if (id == null) {
            throw ErpException.business(NO_WAREHOUSE);
        }
        return id;
    }

    /**
     * Batch sumber harus gerakan MASUK (In/AdjustmentIn/TransferIn) item yang sama dan belum dibatalkan.
     * Harga: batchUnitCost dari klien (dikunci saat dipilih) atau unitCost batch. Sisa per batch tidak
     * di-enforce (rawan race); guard currentStock sudah menjaga stok total.
     */
    public List<BatchSnapshot> batchSnapshots(List<InputLine> inputs) {
        List<BatchSnapshot> out = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            InputLine input = inputs.get(i);
            if (input.sourceMovementId() == null || input.sourceMovementId().isEmpty()) {
                out.add(new BatchSnapshot(null, null));
                continue;
            }
            List<Object[]> rows = em.createQuery("select m.id, m.itemId, m.isCancelled, m.movementType, m.unitCost "
                            + "from StockMovement m where m.id = :id", Object[].class)
                    .setParameter("id", input.sourceMovementId()).getResultList();
            int n = i + 1;
            if (rows.isEmpty()) {
                throw ErpException.business("Batch sumber tidak ditemukan untuk input ke-" + n + ".");
            }
            Object[] b = rows.get(0);
            if (!input.itemId().equals(b[1])) {
                throw ErpException.business("Batch sumber tidak cocok dengan item input ke-" + n + ".");
            }
            if (Boolean.TRUE.equals(b[2])) {
                throw ErpException.business("Batch sumber sudah dibatalkan (input ke-" + n + ").");
            }
            StockMovementType type = (StockMovementType) b[3];
            if (type != StockMovementType.In && type != StockMovementType.AdjustmentIn && type != StockMovementType.TransferIn) {
                throw ErpException.business("Batch sumber bukan stok masuk (input ke-" + n + ").");
            }
            BigDecimal cost = input.batchUnitCost() != null && input.batchUnitCost().signum() >= 0
                    ? input.batchUnitCost() : Money.nz((BigDecimal) b[4]);
            out.add(new BatchSnapshot((String) b[0], cost));
        }
        return out;
    }

    /** Harga input: batch terkunci, selain itu WAC item (≤ 0 → 0). */
    static BigDecimal inputUnitCost(ItemInfo item, BatchSnapshot snap) {
        if (snap.batchUnitCost() != null) {
            return snap.batchUnitCost();
        }
        return item.averageCost().signum() > 0 ? item.averageCost() : BigDecimal.ZERO;
    }

    /** Harga output: produk samping = NRV; produk utama tanpa harga eksplisit (&gt; 0) memakai HPP loaded. */
    static BigDecimal outputUnitCost(OutputLine o, BigDecimal computedHppNum) {
        BigDecimal explicit = o.unitPrice();
        if (o.byProduct()) {
            return explicit != null ? explicit : BigDecimal.ZERO;
        }
        return explicit != null && explicit.signum() > 0 ? explicit : computedHppNum;
    }

    static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    static BigDecimal totalInputQty(ProductionRunRequest body) {
        BigDecimal t = BigDecimal.ZERO;
        for (InputLine i : body.inputs()) {
            t = t.add(i.quantity());
        }
        return t;
    }

    static BigDecimal totalMainOutputQty(ProductionRunRequest body) {
        BigDecimal t = BigDecimal.ZERO;
        for (OutputLine o : body.outputs()) {
            if (!o.byProduct()) {
                t = t.add(o.quantity());
            }
        }
        return t;
    }

    /** Yield utama: Σ output utama ÷ Σ input × 100 (null bila input 0). */
    static BigDecimal yieldPct(ProductionRunRequest body) {
        BigDecimal in = totalInputQty(body);
        if (in.signum() <= 0) {
            return null;
        }
        return totalMainOutputQty(body).divide(in, MC).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    /** Field header run dari body (dipakai create & edit). */
    static void applyHeader(ProductionRun run, ProductionRunRequest body, Instant txDate, String fiscalYearId) {
        run.setDate(txDate);
        run.setNotes(blankToNull(body.notes()));
        run.setReferenceType(blankToNull(body.referenceType()));
        run.setReferenceId(blankToNull(body.referenceId()));
        run.setReferenceNumber(blankToNull(body.referenceNumber()));
        run.setYieldPct(yieldPct(body));
        // Kolom OEE; machineId/startAt/endAt tunggal hanya disimpan (kompatibilitas), tidak dipakai OEE.
        run.setMachineId(body.machineId());
        run.setShift(body.shift());
        run.setShiftLead(body.shiftLead());
        run.setManpowerCount(body.manpowerCount() == null ? null : body.manpowerCount().intValueExact());
        run.setStartAt(ProdDates.optional(body.startAt()));
        run.setEndAt(ProdDates.optional(body.endAt()));
        run.setPlannedStart(ProdDates.optional(body.plannedStart()));
        run.setPlannedEnd(ProdDates.optional(body.plannedEnd()));
        run.setBreakStart(ProdDates.optional(body.breakStart()));
        run.setBreakEnd(ProdDates.optional(body.breakEnd()));
        run.setFiscalYearId(fiscalYearId);
    }

    // ───────────────────────────── stok & HPP ─────────────────────────────

    private StockMovement movement(String number, Instant date, String itemId, String warehouseId, StockMovementType type,
                                   BigDecimal qty, BigDecimal unitCost, BigDecimal totalValue, BigDecimal unitCostLoaded,
                                   String sourceMovementId, String runId, String runNumber, String notes,
                                   String fiscalYearId, String userId, Stamps stamps) {
        StockMovement m = new StockMovement();
        m.setMovementNumber(number);
        m.setDate(date);
        m.setItemId(itemId);
        m.setWarehouseId(warehouseId);
        m.setMovementType(type);
        m.setQuantity(qty);
        m.setUnitCost(Money.round2(unitCost));
        m.setTotalValue(totalValue);
        m.setUnitCostLoaded(unitCostLoaded == null ? null : Money.round(unitCostLoaded, 4));
        m.setSourceMovementId(sourceMovementId);
        m.setReferenceType(REF);
        m.setReferenceId(runId);
        m.setReferenceNumber(runNumber);
        m.setNotes(blankToNull(notes));
        m.setFiscalYearId(fiscalYearId);
        m.setCreatedById(userId);
        m.setCreatedAt(stamps.next());
        return m;
    }

    /**
     * Bahan baku: stok keluar per baris + gerakan Out (harga batch/WAC). Mengembalikan total nilai
     * bahan (Σ round2(qty × harga)).
     */
    public BigDecimal stockOutInputs(String runId, String runNumber, ProductionRunRequest body, Map<String, ItemInfo> itemMap,
                              List<BatchSnapshot> snaps, String warehouseId, Instant txDate, String fiscalYearId,
                              String userId, Stamps stamps) {
        BigDecimal totalMaterialCost = BigDecimal.ZERO;
        for (int i = 0; i < body.inputs().size(); i++) {
            InputLine input = body.inputs().get(i);
            ItemInfo item = itemMap.get(input.itemId());
            if (item == null) {
                continue;
            }
            BatchSnapshot snap = snaps.get(i);
            BigDecimal unitCost = inputUnitCost(item, snap);
            BigDecimal totalValue = Money.round2(input.quantity().multiply(unitCost));
            totalMaterialCost = totalMaterialCost.add(totalValue);

            stock.adjustItemStock(input.itemId(), input.quantity().negate());
            stock.adjustWarehouseStock(input.itemId(), warehouseId, input.quantity().negate());
            String number = numbers.generate("SM", txDate, fiscalYearId);
            // Back-link batch agar /items/:id/batches bisa menjumlah pemakaian dari tiap sumber.
            em.persist(movement(number, txDate, input.itemId(), warehouseId, StockMovementType.Out, input.quantity(),
                    unitCost, totalValue, null, snap.sourceMovementId(), runId, runNumber, body.notes(), fiscalYearId,
                    userId, stamps));
            em.flush();
        }
        return totalMaterialCost;
    }

    /**
     * HPP loaded per kg produk utama: override (≥ 0) apa adanya, selain itu (bahan + biaya sisi input
     * + biaya sisi output − NRV produk samping) ÷ qty produk utama; null bila tidak ada qty utama.
     * Pembilang negatif (NRV samping &gt; total biaya) di-clamp 0 agar WAC/GL produk utama tidak rusak.
     */
    public Hpp computeHpp(ProductionRunRequest body, BigDecimal totalMaterialCost, String logRef) {
        BigDecimal totalInputQty = totalInputQty(body);
        BigDecimal totalMainOutputQty = totalMainOutputQty(body);
        List<ResolvedCost> costs = new ArrayList<>();
        BigDecimal inputSide = BigDecimal.ZERO;
        BigDecimal outputSide = BigDecimal.ZERO;
        for (CostLine c : body.costs()) {
            BigDecimal qtyBasis = switch (c.basis()) {
                case "InputQty" -> totalInputQty;
                case "OutputQty" -> totalMainOutputQty;
                default -> BigDecimal.ONE; // Fixed: rate = total
            };
            BigDecimal totalAmount = c.rate().multiply(qtyBasis).setScale(4, RoundingMode.HALF_UP);
            costs.add(new ResolvedCost(c, qtyBasis, totalAmount));
            if ("InputSide".equals(c.costSide())) {
                inputSide = inputSide.add(totalAmount);
            } else if ("OutputSide".equals(c.costSide())) {
                outputSide = outputSide.add(totalAmount);
            }
        }
        BigDecimal byproductValue = BigDecimal.ZERO;
        for (OutputLine o : body.outputs()) {
            if (o.byProduct()) {
                byproductValue = byproductValue.add(o.quantity().multiply(Money.nz(o.unitPrice())));
            }
        }
        boolean overridden = body.hppOverride() != null && body.hppOverride().signum() >= 0;
        BigDecimal computedHpp = null;
        if (overridden) {
            computedHpp = body.hppOverride();
        } else if (totalMainOutputQty.signum() > 0) {
            BigDecimal numerator = totalMaterialCost.add(inputSide).add(outputSide).subtract(byproductValue);
            if (numerator.signum() < 0) {
                log.warn("Loaded HPP numerator negative (by-product NRV > total cost) — clamped to 0 ref={} material={} "
                        + "inputSide={} outputSide={} byproduct={}", logRef, totalMaterialCost, inputSide, outputSide, byproductValue);
                computedHpp = BigDecimal.ZERO;
            } else {
                computedHpp = numerator.divide(totalMainOutputQty, MC).setScale(4, RoundingMode.HALF_UP);
            }
        }
        return new Hpp(totalInputQty, totalMainOutputQty, totalMaterialCost, costs, inputSide, outputSide,
                byproductValue, computedHpp, overridden);
    }

    /**
     * Hasil: stok masuk + gerakan In + WAC baru (hanya bila harga &gt; 0) + settle antrian koreksi HPP
     * (penjualan stok minus sebelumnya). Output utama tanpa harga memakai HPP loaded agar WAC & GL seimbang.
     */
    public void stockInOutputs(String runId, String runNumber, ProductionRunRequest body, Hpp hpp, String warehouseId,
                        Instant txDate, String fiscalYearId, String userId, Stamps stamps) {
        BigDecimal computedHppNum = hpp.computedHppNum();
        for (OutputLine output : body.outputs()) {
            ErpWarehouseStockService.ItemStock fresh = stock.currentItemStock(output.itemId());
            BigDecimal unitCost = outputUnitCost(output, computedHppNum);
            BigDecimal newAvg = fresh != null && unitCost.signum() > 0
                    ? WeightedAverage.calc(fresh.currentStock(), fresh.averageCost(), output.quantity(), unitCost) : null;
            stock.adjustItemStock(output.itemId(), output.quantity());
            if (newAvg != null) {
                stock.setAverageCost(output.itemId(), newAvg);
            }
            stock.adjustWarehouseStock(output.itemId(), warehouseId, output.quantity());
            BigDecimal totalValue = Money.round2(output.quantity().multiply(unitCost));
            // Produk utama berbagi computedHpp; produk samping memakai NRV-nya (yang dikurangkan dari HPP utama).
            BigDecimal unitCostLoaded = output.byProduct() ? unitCost : hpp.computedHpp();
            String number = numbers.generate("SM", txDate, fiscalYearId);
            em.persist(movement(number, txDate, output.itemId(), warehouseId, StockMovementType.In, output.quantity(),
                    unitCost, totalValue, unitCostLoaded, null, runId, runNumber, body.notes(), fiscalYearId, userId, stamps));
            em.flush();
            cogs.settleCogsBackfillForItem(output.itemId(), output.quantity(), unitCost, fiscalYearId, userId, txDate,
                    new ErpCogsService.SettleSource(REF, runId, runNumber));
        }
    }

    // ───────────────────────────── jurnal ─────────────────────────────

    /**
     * Baris jurnal produksi: CR persediaan per input, DR persediaan per output, selisih: output &gt; input
     * → CR 5.12 (biaya konversi dibebankan ke persediaan, PSAK 14) s.d. total biaya konversi, sisanya CR
     * 5.1; output &lt; input (rugi produksi) → DR 5.1. Akun persediaan = akun item atau INVENTORY.
     */
    public List<ErpGlPostingService.Line> journalLines(ProductionRunRequest body, Map<String, ItemInfo> itemMap,
                                                List<BatchSnapshot> snaps, Hpp hpp, String runNumber) {
        String defaultInv = systemAccounts.getAccountId(SystemAccountRole.INVENTORY);
        List<ErpGlPostingService.Line> lines = new ArrayList<>();
        BigDecimal totalInputCost = BigDecimal.ZERO;
        for (int i = 0; i < body.inputs().size(); i++) {
            InputLine input = body.inputs().get(i);
            ItemInfo item = itemMap.get(input.itemId());
            if (item == null) {
                continue;
            }
            BigDecimal val = Money.round2(input.quantity().multiply(inputUnitCost(item, snaps.get(i))));
            if (val.signum() <= 0) {
                continue;
            }
            totalInputCost = totalInputCost.add(val);
            lines.add(new ErpGlPostingService.Line(accountOr(item.accountId(), defaultInv), BigDecimal.ZERO, val,
                    "Bahan baku keluar " + item.name() + ": " + runNumber, null, null));
        }
        BigDecimal totalOutputCost = BigDecimal.ZERO;
        for (OutputLine output : body.outputs()) {
            BigDecimal val = Money.round2(output.quantity().multiply(outputUnitCost(output, hpp.computedHppNum())));
            if (val.signum() <= 0) {
                continue;
            }
            totalOutputCost = totalOutputCost.add(val);
            ItemInfo item = itemMap.get(output.itemId());
            lines.add(new ErpGlPostingService.Line(accountOr(item == null ? null : item.accountId(), defaultInv), val,
                    BigDecimal.ZERO, "Produksi masuk " + (item == null || item.name() == null ? "" : item.name()) + ": " + runNumber,
                    null, null));
        }
        BigDecimal diff = Money.round2(totalOutputCost.subtract(totalInputCost));
        if (diff.signum() != 0) {
            Account hppBeras = systemAccounts.findByNumber("5.1");
            String cogsAccountId = hppBeras != null ? hppBeras.getId() : systemAccounts.getAccountId(SystemAccountRole.COGS);
            if (diff.signum() > 0) {
                List<Account> conv = em.createQuery("select a from Account a where a.accountNumber = '5.12' and a.isActive = true",
                        Account.class).setMaxResults(1).getResultList();
                Account conversion = conv.isEmpty() ? null : conv.get(0);
                BigDecimal convCost = Money.round2(hpp.totalCostInputSide().add(hpp.totalCostOutputSide()));
                BigDecimal to512 = conversion != null ? diff.min(convCost.max(BigDecimal.ZERO)) : BigDecimal.ZERO;
                BigDecimal to51 = Money.round2(diff.subtract(to512));
                if (to512.signum() > 0) {
                    lines.add(new ErpGlPostingService.Line(conversion.getId(), BigDecimal.ZERO, to512,
                            "Biaya konversi dibebankan: " + runNumber, null, null));
                }
                if (to51.signum() > 0) {
                    lines.add(new ErpGlPostingService.Line(cogsAccountId, BigDecimal.ZERO, to51,
                            "HPP konversi produksi: " + runNumber, null, null));
                }
            } else {
                lines.add(new ErpGlPostingService.Line(cogsAccountId, diff.abs(), BigDecimal.ZERO,
                        "HPP rugi produksi: " + runNumber, null, null));
            }
        }
        return lines;
    }

    private static String accountOr(String accountId, String fallback) {
        return accountId == null || accountId.isEmpty() ? fallback : accountId;
    }

    /** "GKP, Beras" — nama item baris (kosong bila tidak dikenal) untuk narasi jurnal. */
    static String names(List<String> itemIds, Map<String, ItemInfo> itemMap) {
        List<String> out = new ArrayList<>();
        for (String id : itemIds) {
            ItemInfo it = itemMap.get(id);
            out.add(it == null || it.name() == null ? "" : it.name());
        }
        return String.join(", ", out);
    }

    // ───────────────────────────── baris run ─────────────────────────────

    /**
     * Simpan baris item (input lalu output, gudang produksi di tiap baris), komponen biaya, OEE,
     * rincian HPP di header run, dan proses yang dijalankan (unik).
     */
    public void persistChildren(ProductionRun run, ProductionRunRequest body, String warehouseId, List<BatchSnapshot> snaps,
                         Hpp hpp, Stamps stamps) {
        String runId = run.getId();
        BigDecimal totalInputQty = hpp.totalInputQty();
        BigDecimal computedHppNum = hpp.computedHppNum();
        for (int i = 0; i < body.inputs().size(); i++) {
            InputLine in = body.inputs().get(i);
            BatchSnapshot snap = i < snaps.size() ? snaps.get(i) : new BatchSnapshot(null, null);
            ProductionRunItem x = new ProductionRunItem();
            x.setProductionRunId(runId);
            x.setItemId(in.itemId());
            x.setWarehouseId(warehouseId);
            x.setLineType("Input");
            x.setQuantity(in.quantity());
            x.setUnitPrice(null);
            x.setYieldPct(null);
            x.setIsByProduct(false);
            x.setSourceMovementId(snap.sourceMovementId());
            x.setBatchUnitCost(snap.batchUnitCost() == null ? null : Money.round(snap.batchUnitCost(), 4));
            x.setCreatedAt(stamps.next());
            em.persist(x);
        }
        for (OutputLine o : body.outputs()) {
            // Harga tersimpan = eksplisit atau HPP loaded (back-fill) agar query hilir tidak menghitung ulang.
            BigDecimal explicit = o.unitPrice();
            BigDecimal persisted = o.byProduct() ? explicit
                    : explicit != null && explicit.signum() > 0 ? explicit
                    : computedHppNum.signum() > 0 ? computedHppNum : null;
            ProductionRunItem x = new ProductionRunItem();
            x.setProductionRunId(runId);
            x.setItemId(o.itemId());
            x.setWarehouseId(warehouseId);
            x.setLineType(o.byProduct() ? "ByProduct" : "Output");
            x.setQuantity(o.quantity());
            x.setUnitPrice(persisted == null ? null : Money.round2(persisted));
            x.setYieldPct(totalInputQty.signum() > 0 && !o.byProduct()
                    ? o.quantity().divide(totalInputQty, MC).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP) : null);
            x.setIsByProduct(o.byProduct());
            x.setCreatedAt(stamps.next());
            em.persist(x);
        }

        for (ResolvedCost c : hpp.costs()) {
            ProductionRunCost rc = new ProductionRunCost();
            rc.setProductionRunId(runId);
            rc.setStandardId(c.line().standardId());
            rc.setDescription(c.line().description());
            rc.setCostSide(c.line().costSide());
            rc.setBasis(c.line().basis());
            rc.setRate(Money.round(c.line().rate(), 4));
            rc.setQtyBasis(c.qtyBasis().setScale(3, RoundingMode.HALF_UP));
            rc.setTotalAmount(c.totalAmount());
            rc.setCostAccountId(c.line().costAccountId());
            rc.setCreatedAt(stamps.next());
            em.persist(rc);
        }

        // OEE: kualitas/output dari baris item — input = Σ input, produk = output utama, samping = by-product.
        BigDecimal samping = BigDecimal.ZERO;
        for (OutputLine o : body.outputs()) {
            if (o.byProduct()) {
                samping = samping.add(o.quantity());
            }
        }
        persistOee(run, body, totalInputQty, hpp.totalMainOutputQty(), samping, stamps);

        run.setTotalMaterialCost(hpp.totalMaterialCost().setScale(4, RoundingMode.HALF_UP));
        run.setTotalCostInputSide(hpp.totalCostInputSide().setScale(4, RoundingMode.HALF_UP));
        run.setTotalCostOutputSide(hpp.totalCostOutputSide().setScale(4, RoundingMode.HALF_UP));
        run.setTotalByproductValue(hpp.totalByproductValue().setScale(4, RoundingMode.HALF_UP));
        run.setComputedHpp(hpp.computedHpp() == null ? null : Money.round(hpp.computedHpp(), 4));
        run.setHppManualOverride(hpp.overridden());

        // Traceability: proses yang dijalankan (junction), dedupe defensif.
        for (String processId : new LinkedHashSet<>(body.processIds())) {
            ProductionRunProcess p = new ProductionRunProcess();
            p.setProductionRunId(runId);
            p.setProcessId(processId);
            p.setCreatedAt(stamps.next());
            em.persist(p);
        }
        em.flush();
    }

    /**
     * Simpan downtime + baris mesin dengan metrik OEE, lalu agregat lini di header run — port
     * {@code persistOeeInTx}. Pada edit, baris lama sudah dihapus pemanggil.
     */
    public void persistOee(ProductionRun run, ProductionRunRequest body, BigDecimal inputQty, BigDecimal produkQty,
                    BigDecimal sampingQty, Stamps stamps) {
        for (DowntimeLine d : body.downtimes()) {
            ProductionDowntime row = new ProductionDowntime();
            row.setProductionRunId(run.getId());
            row.setMachineId(d.machineId());
            row.setCategory(d.category());
            row.setReason(d.reason());
            row.setDurationMin(Money.round2(d.durationMin()));
            row.setCreatedAt(stamps.next());
            em.persist(row);
        }
        Set<String> machineIds = new LinkedHashSet<>();
        body.runMachines().forEach(m -> machineIds.add(m.machineId()));
        Map<String, BigDecimal> rateById = new HashMap<>();
        if (!machineIds.isEmpty()) {
            for (Object[] r : em.createQuery("select m.id, m.idealRatePerHour from Machine m where m.id in :ids", Object[].class)
                    .setParameter("ids", machineIds).getResultList()) {
                rateById.put((String) r[0], (BigDecimal) r[1]);
            }
        }
        List<ProductionOee.MachineInput> machines = new ArrayList<>();
        for (MachineLine m : body.runMachines()) {
            machines.add(new ProductionOee.MachineInput(m.machineId(), ProdDates.optional(m.startAt()),
                    ProdDates.optional(m.endAt()), rateById.get(m.machineId())));
        }
        List<ProductionOee.DowntimeInput> downtimes = new ArrayList<>();
        for (DowntimeLine d : body.downtimes()) {
            downtimes.add(new ProductionOee.DowntimeInput(d.machineId(), d.durationMin()));
        }
        ProductionOee.Result oee = ProductionOee.compute(new ProductionOee.Input(
                ProdDates.optional(body.plannedStart()), ProdDates.optional(body.plannedEnd()),
                ProdDates.optional(body.breakStart()), ProdDates.optional(body.breakEnd()),
                machines, downtimes, inputQty, produkQty, sampingQty));

        for (int i = 0; i < body.runMachines().size(); i++) {
            MachineLine m = body.runMachines().get(i);
            ProductionOee.MachineResult res = oee.machines().get(i);
            ProductionRunMachine row = new ProductionRunMachine();
            row.setProductionRunId(run.getId());
            row.setMachineId(m.machineId());
            row.setStartAt(ProdDates.optional(m.startAt()));
            row.setEndAt(ProdDates.optional(m.endAt()));
            row.setRunMin(res.runMin());
            row.setDowntimeMin(res.downtimeMin());
            row.setAllocatedQty(res.allocatedQty());
            row.setAvailabilityPct(res.availabilityPct());
            row.setPerformancePct(res.performancePct());
            row.setQualityPct(res.qualityPct());
            row.setOeePct(res.oeePct());
            row.setCreatedAt(stamps.next());
            em.persist(row);
        }
        run.setDowntimeMin(oee.downtimeMin());
        run.setAvailabilityPct(oee.availabilityPct());
        run.setPerformancePct(oee.performancePct());
        run.setQualityPct(oee.qualityPct());
        run.setOeePct(oee.oeePct());
    }

    // ───────────────────────────── pembalikan ─────────────────────────────

    /**
     * Balik semua gerakan aktif run (stok total + per gudang, tandai batal). Membalik gerakan In
     * mengurangi stok: ditolak bila stok (snapshot sebelum pembalikan) kurang, kecuali bypass stok
     * minus (dicatat di log). Mengembalikan item terdampak (urutan pertama muncul).
     */
    public Set<String> reverseRunMovements(String runId, boolean allowNegativeStock, String negativeStockReason, boolean forEdit) {
        List<Object[]> rows = em.createQuery("select m.id, m.itemId, m.warehouseId, m.movementType, m.quantity, i.name, "
                        + "i.currentStock from StockMovement m join m.item i where m.referenceType = :ref "
                        + "and m.referenceId = :id and m.isCancelled = false order by m.createdAt asc, m.id asc", Object[].class)
                .setParameter("ref", REF).setParameter("id", runId).getResultList();
        Set<String> affected = new LinkedHashSet<>();
        for (Object[] r : rows) {
            String movementId = (String) r[0];
            String itemId = (String) r[1];
            String warehouseId = (String) r[2];
            StockMovementType type = (StockMovementType) r[3];
            BigDecimal qty = Money.nz((BigDecimal) r[4]);
            String name = (String) r[5];
            BigDecimal current = Money.nz((BigDecimal) r[6]);
            boolean isIn = type == StockMovementType.In || type == StockMovementType.AdjustmentIn;
            boolean isOut = type == StockMovementType.Out || type == StockMovementType.AdjustmentOut;
            if (isIn && current.compareTo(qty) < 0) {
                if (!allowNegativeStock) {
                    throw ErpException.business(forEdit
                            ? "Tidak dapat mengedit: stok '" + name + "' tidak mencukupi untuk pembalikan output (tersedia "
                            + ProdJson.js(current) + ", perlu " + ProdJson.js(qty)
                            + "). Output sudah dipakai produksi/penjualan berikutnya."
                            : "Tidak dapat membatalkan: stok '" + name + "' tidak mencukupi untuk pembalikan output (tersedia "
                            + ProdJson.js(current) + ", perlu " + ProdJson.js(qty)
                            + "). Output sudah dipakai produksi/penjualan berikutnya. Batalkan transaksi tersebut dulu.");
                }
                log.warn("Production {} reverse with negative stock bypass item={} name={} currentStock={} reverseQty={} reason={}",
                        forEdit ? "edit" : "cancel", itemId, name, current, qty, negativeStockReason);
            }
            affected.add(itemId);
            BigDecimal reverseDelta = isOut ? qty : qty.negate();
            stock.adjustItemStock(itemId, reverseDelta);
            stock.adjustWarehouseStock(itemId, warehouseId, reverseDelta);
            em.createQuery("update StockMovement m set m.isCancelled = true, m.updatedAt = :now where m.id = :id")
                    .setParameter("now", Instant.now()).setParameter("id", movementId).executeUpdate();
        }
        return affected;
    }

    /** Re-derivasi WAC dengan replay gerakan aktif untuk setiap item terdampak. */
    public void recomputeAverageCosts(Collection<String> itemIds) {
        for (String itemId : itemIds) {
            cogs.recomputeAverageCostFromReplay(itemId);
        }
    }

    /** Akun di jurnal berawalan {@code prefix}; {@code activeOnly} = hanya yang belum Cancelled. */
    public List<String> journalAccountIds(String prefix, boolean activeOnly) {
        var q = em.createQuery("select distinct ji.accountId from JournalItem ji join ji.journalEntry je "
                        + "where je.entryNumber like :p" + (activeOnly ? " and je.status <> :cancelled" : ""), String.class)
                .setParameter("p", likePrefix(prefix));
        if (activeOnly) {
            q.setParameter("cancelled", DocumentStatus.Cancelled);
        }
        return q.getResultList();
    }

    static String likePrefix(String prefix) {
        return prefix.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%") + "%";
    }

    public void recalcBalances(Collection<String> accountIds) {
        if (!accountIds.isEmpty()) {
            balances.recalculateAccountBalances(accountIds);
        }
    }

    // ───────────────────────────── respons ─────────────────────────────

    /** Run + items (+ item {id,name,unit,code}) — bentuk kembalian submit/edit ENTARIS. */
    @ErpTx
    public Map<String, Object> runWithItems(String runId) {
        em.flush();
        em.clear();
        ProductionRun run = em.find(ProductionRun.class, runId);
        if (run == null) {
            return null;
        }
        List<ProductionRunItem> items = em.createQuery("select x from ProductionRunItem x join fetch x.item "
                        + "where x.productionRunId = :id order by x.createdAt asc, x.id asc", ProductionRunItem.class)
                .setParameter("id", runId).getResultList();
        Map<String, Object> m = ProdJson.scalars(run);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductionRunItem x : items) {
            Map<String, Object> j = ProdJson.scalars(x);
            j.put("item", ProdJson.pick(x.getItem(), "id", "name", "unit", "code"));
            rows.add(j);
        }
        m.put("items", rows);
        return m;
    }

    /** Peta dari pasangan kunci/nilai berurutan. */
    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
