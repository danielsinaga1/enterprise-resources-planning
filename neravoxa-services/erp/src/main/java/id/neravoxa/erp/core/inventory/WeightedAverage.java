package id.neravoxa.erp.core.inventory;

import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.StockMovementType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Harga rata-rata tertimbang (WAC) — port {@code utils/weightedAverage.ts} +
 * {@code utils/weightedAverageReplay.ts}. Fungsi murni, tanpa I/O.
 */
public final class WeightedAverage {

    private WeightedAverage() {
    }

    /**
     * Rata-rata baru setelah stok masuk:
     * {@code (qty×avg + inQty×inCost) / (qty + inQty)}, dibulatkan 2 desimal. Stok ≤ 0 (atau total ≤ 0)
     * menjangkarkan rata-rata ke harga masuk.
     */
    public static BigDecimal calc(BigDecimal currentQty, BigDecimal currentAvgCost, BigDecimal inQty, BigDecimal inUnitCost) {
        BigDecimal cQty = Money.nz(currentQty);
        BigDecimal cCost = Money.nz(currentAvgCost);
        BigDecimal iQty = Money.nz(inQty);
        BigDecimal iCost = Money.nz(inUnitCost);
        if (cQty.signum() <= 0) {
            return Money.round2(iCost);
        }
        BigDecimal totalQty = cQty.add(iQty);
        if (totalQty.signum() <= 0) {
            return Money.round2(iCost);
        }
        BigDecimal totalValue = cQty.multiply(cCost).add(iQty.multiply(iCost));
        return totalValue.divide(totalQty, 2, RoundingMode.HALF_UP);
    }

    /** Gerakan untuk replay (urutan proses = createdAt lalu id). */
    public record ReplayMovement(String id, StockMovementType movementType, BigDecimal quantity, BigDecimal unitCost,
                                 Instant createdAt, String referenceType, boolean cancelled) {
    }

    public record ReplayResult(BigDecimal averageCost, BigDecimal stock, boolean hasCostedInbound) {
    }

    private static final Set<StockMovementType> COSTED_IN = Set.of(StockMovementType.In, StockMovementType.AdjustmentIn);
    private static final Set<StockMovementType> STOCK_IN = Set.of(StockMovementType.In, StockMovementType.AdjustmentIn,
            StockMovementType.TransferIn);

    /** True bila gerakan ini ikut memadukan rata-rata pada jalur live. */
    public static boolean blendsAverage(StockMovementType type, BigDecimal unitCost, String referenceType) {
        if (!COSTED_IN.contains(type)) {
            return false;
        }
        if (Money.nz(unitCost).signum() <= 0) {
            return false;
        }
        if (type == StockMovementType.In && "SalesReturn".equals(referenceType)) {
            return false;
        }
        return !(type == StockMovementType.AdjustmentIn && "StockOpname".equals(referenceType));
    }

    public static boolean isStockIn(StockMovementType type) {
        return STOCK_IN.contains(type);
    }

    /**
     * Replay seluruh gerakan aktif berurutan (createdAt, id) untuk menurunkan ulang rata-rata
     * setelah batal/edit. Tanpa inbound bercost → {@code fallbackAverageCost}.
     */
    public static ReplayResult replay(List<ReplayMovement> movements, BigDecimal fallbackAverageCost,
                                      BigDecimal openingStock, BigDecimal openingAverageCost) {
        BigDecimal stock = Money.nz(openingStock);
        BigDecimal avg = Money.nz(openingAverageCost);
        boolean hasCosted = false;
        List<ReplayMovement> sorted = new ArrayList<>(movements);
        sorted.sort(Comparator.comparing(ReplayMovement::createdAt).thenComparing(ReplayMovement::id));
        for (ReplayMovement m : sorted) {
            if (m.cancelled()) {
                continue;
            }
            BigDecimal qty = Money.nz(m.quantity());
            if (blendsAverage(m.movementType(), m.unitCost(), m.referenceType())) {
                avg = calc(stock, avg, qty, m.unitCost());
                hasCosted = true;
            }
            stock = isStockIn(m.movementType()) ? stock.add(qty) : stock.subtract(qty);
        }
        BigDecimal resolved = hasCosted ? avg : Money.nz(fallbackAverageCost);
        return new ReplayResult(Money.round2(resolved), stock, hasCosted);
    }
}
