package id.neravoxa.erp.purchase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Three-way match (PO ↔ STT ↔ Faktur Pembelian) — port murni {@code threeWayMatch.ts}, tidak
 * pernah memblokir.
 *
 * <p>Per baris invoice:
 * <ul>
 *   <li>qty: qty invoice vs qty baris STT tertaut; dan bila baris STT memenuhi baris PO, total qty
 *       invoice per baris PO vs qty dipesan (hanya kelebihan tagih — tagih kurang wajar untuk
 *       penerimaan parsial).</li>
 *   <li>harga: harga satuan invoice (setelah diskon baris, sebelum pajak) vs estimasi PO (setelah
 *       diskon baris PO). Hanya bila tertaut PO.</li>
 * </ul>
 * Toleransi konstanta modul (CompanySettings belum punya slot kebijakan pembelian). Perhitungan
 * memakai {@code double} seperti sumber (nilai hanya untuk tampilan).
 */
final class ThreeWayMatch {

    /** Selisih qty yang diizinkan, % dari qty acuan (0 = harus sama persis). */
    static final double MATCH_QTY_TOLERANCE_PCT = 0;
    /** Selisih harga satuan yang diizinkan, % dari harga net PO. */
    static final double MATCH_PRICE_TOLERANCE_PCT = 2;

    private static final double QTY_EPS = 0.0005;
    private static final double PRICE_EPS = 0.005;

    private ThreeWayMatch() {
    }

    record Tolerance(double qtyPct, double pricePct) {
    }

    static final Tolerance DEFAULT_TOLERANCE = new Tolerance(MATCH_QTY_TOLERANCE_PCT, MATCH_PRICE_TOLERANCE_PCT);

    /** Satu baris input (receiptQty null = tidak tertaut STT). */
    record LineInput(String key, String itemName, String unit, double invoiceQty, double invoiceUnitPrice,
                     Double receiptQty, String sttNumber, String poItemId, String poNumber, Double orderedQty,
                     Double poUnitPrice) {
    }

    private static Double pctOf(double diff, double base) {
        if (Math.abs(base) < 1e-12) {
            return null;
        }
        return JsNumbers.roundEps((diff / Math.abs(base)) * 100, 2);
    }

    /** True bila |diff| melewati epsilon pembulatan dan toleransi % dari base. */
    static boolean exceedsTolerance(double diff, double base, double tolPct, double eps) {
        double abs = Math.abs(diff);
        if (abs <= eps) {
            return false;
        }
        if (Math.abs(base) < 1e-12) {
            return true;
        }
        return (abs / Math.abs(base)) * 100 > tolPct + 1e-9;
    }

    /** Hasil berbentuk {@code { status, tolerance, lines, summary }} (angka gaya JSON JS). */
    static Map<String, Object> compute(List<LineInput> inputs, Tolerance tolerance) {
        // Qty invoice dijumlah per baris PO (multi-STT: beberapa baris invoice → satu baris PO).
        Map<String, Double> billedPerPoItem = new HashMap<>();
        for (LineInput l : inputs) {
            if (l.poItemId() != null) {
                billedPerPoItem.merge(l.poItemId(), l.invoiceQty(), Double::sum);
            }
        }

        List<Map<String, Object>> lines = new ArrayList<>();
        int matched = 0;
        int qtyVariance = 0;
        int priceVariance = 0;
        int unlinked = 0;
        double varianceSum = 0;
        for (LineInput l : inputs) {
            double invoiceQty = JsNumbers.roundEps(l.invoiceQty(), 3);
            double invoiceUnitPrice = JsNumbers.roundEps(l.invoiceUnitPrice(), 2);

            Double qtyDiff = null;
            Double qtyDiffPct = null;
            boolean qtyBad = false;
            if (l.receiptQty() != null) {
                double ref = JsNumbers.roundEps(l.receiptQty(), 3);
                qtyDiff = JsNumbers.roundEps(invoiceQty - ref, 3);
                qtyDiffPct = pctOf(qtyDiff, ref);
                qtyBad = exceedsTolerance(qtyDiff, ref, tolerance.qtyPct(), QTY_EPS);
            }

            Double overOrderedQty = null;
            if (l.poItemId() != null && l.orderedQty() != null) {
                double ordered = JsNumbers.roundEps(l.orderedQty(), 3);
                double over = JsNumbers.roundEps(billedPerPoItem.getOrDefault(l.poItemId(), 0d) - ordered, 3);
                if (over > 0 && exceedsTolerance(over, ordered, tolerance.qtyPct(), QTY_EPS)) {
                    overOrderedQty = over;
                    qtyBad = true;
                }
            }

            Double priceDiff = null;
            Double priceDiffPct = null;
            Double priceVarianceAmount = null;
            boolean priceBad = false;
            if (l.poItemId() != null && l.poUnitPrice() != null) {
                double ref = JsNumbers.roundEps(l.poUnitPrice(), 2);
                priceDiff = JsNumbers.roundEps(invoiceUnitPrice - ref, 2);
                priceDiffPct = pctOf(priceDiff, ref);
                priceVarianceAmount = JsNumbers.roundEps(priceDiff * invoiceQty, 2);
                priceBad = exceedsTolerance(priceDiff, ref, tolerance.pricePct(), PRICE_EPS);
            }

            boolean linked = l.receiptQty() != null || l.poItemId() != null;
            String status = !linked ? "unlinked"
                    : qtyBad && priceBad ? "qty_price"
                    : qtyBad ? "qty"
                    : priceBad ? "price"
                    : "match";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", l.key());
            row.put("itemName", l.itemName());
            row.put("unit", l.unit());
            row.put("invoiceQty", JsNumbers.num(invoiceQty));
            row.put("invoiceUnitPrice", JsNumbers.num(invoiceUnitPrice));
            row.put("receiptQty", JsNumbers.num(l.receiptQty()));
            row.put("sttNumber", l.sttNumber());
            row.put("poItemId", l.poItemId());
            row.put("poNumber", l.poNumber());
            row.put("orderedQty", JsNumbers.num(l.orderedQty()));
            row.put("poUnitPrice", JsNumbers.num(l.poUnitPrice()));
            row.put("status", status);
            row.put("qtyDiff", JsNumbers.num(qtyDiff));
            row.put("qtyDiffPct", JsNumbers.num(qtyDiffPct));
            row.put("overOrderedQty", JsNumbers.num(overOrderedQty));
            row.put("priceDiff", JsNumbers.num(priceDiff));
            row.put("priceDiffPct", JsNumbers.num(priceDiffPct));
            row.put("priceVarianceAmount", JsNumbers.num(priceVarianceAmount));
            lines.add(row);

            switch (status) {
                case "match" -> matched++;
                case "qty" -> qtyVariance++;
                case "price" -> priceVariance++;
                case "qty_price" -> {
                    qtyVariance++;
                    priceVariance++;
                }
                default -> unlinked++;
            }
            varianceSum += priceVarianceAmount == null ? 0 : priceVarianceAmount;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("lines", lines.size());
        summary.put("matched", matched);
        summary.put("qtyVariance", qtyVariance);
        summary.put("priceVariance", priceVariance);
        summary.put("unlinked", unlinked);
        summary.put("priceVarianceAmount", JsNumbers.num(JsNumbers.roundEps(varianceSum, 2)));

        String overall = qtyVariance + priceVariance > 0 ? "variance" : matched > 0 ? "match" : "unlinked";

        Map<String, Object> tol = new LinkedHashMap<>();
        tol.put("qtyPct", JsNumbers.num(tolerance.qtyPct()));
        tol.put("pricePct", JsNumbers.num(tolerance.pricePct()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", overall);
        out.put("tolerance", tol);
        out.put("lines", lines);
        out.put("summary", summary);
        return out;
    }
}
