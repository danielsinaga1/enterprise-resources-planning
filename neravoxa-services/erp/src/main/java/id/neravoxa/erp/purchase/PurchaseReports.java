package id.neravoxa.erp.purchase;

import id.neravoxa.erp.entity.DocumentStatus;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Agregasi murni laporan pembelian — port {@code purchaseReports.ts}:
 * <ul>
 *   <li>PO Outstanding (PO terbuka, per baris qty dipesan/diterima/sisa + nilai)</li>
 *   <li>Kinerja Pemasok (per supplier: jumlah PO, tepat waktu, lead time, fill rate, selisih harga PI vs PO)</li>
 * </ul>
 * Tanggal dibandingkan sebagai hari kalender zona bisnis (default WIB). Angka double seperti sumber.
 */
final class PurchaseReports {

    private static final double QTY_EPS = 0.0005;

    private PurchaseReports() {
    }

    /** Harga net satuan PO: estimasi setelah diskon baris, ditambah PPN baris. */
    static double poLineUnitValue(BigDecimal estimatedRate, BigDecimal discount, BigDecimal taxPct) {
        return JsNumbers.n(estimatedRate) * (1 - JsNumbers.n(discount) / 100) * (1 + JsNumbers.n(taxPct) / 100);
    }

    // ───────────────────────────── PO Outstanding ─────────────────────────────

    record OutstandingItem(String id, String itemName, String unit, BigDecimal quantity, BigDecimal qtyReceived,
                           BigDecimal estimatedRate, BigDecimal discount, BigDecimal taxPct) {
    }

    record OutstandingPo(String id, String poNumber, Instant date, DocumentStatus status, Instant closedAt,
                         Instant expectedDeliveryDate, String supplierName, String warehouseName,
                         List<OutstandingItem> items) {
    }

    static Map<String, Object> buildPoOutstanding(List<OutstandingPo> pos, String today) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> poIds = new LinkedHashSet<>();
        Set<String> overdueIds = new LinkedHashSet<>();
        double remainingTotal = 0;
        for (OutstandingPo po : pos) {
            if (po.status() != DocumentStatus.Submitted || po.closedAt() != null) {
                continue;
            }
            List<PurchaseOrderRules.ReceiptLine> lines = po.items().stream()
                    .map(i -> new PurchaseOrderRules.ReceiptLine(i.quantity(), i.qtyReceived())).toList();
            boolean overdue = PurchaseOrderOverdue.isOverdue(po.status(), po.closedAt(), po.expectedDeliveryDate(),
                    lines, today);
            long late = overdue && po.expectedDeliveryDate() != null
                    ? PurchaseOrderOverdue.daysOverdue(po.expectedDeliveryDate(), today) : 0;
            for (OutstandingItem it : po.items()) {
                double ordered = JsNumbers.roundEps(JsNumbers.n(it.quantity()), 3);
                double received = JsNumbers.roundEps(JsNumbers.n(it.qtyReceived()), 3);
                double remaining = JsNumbers.roundEps(Math.max(0, ordered - received), 3);
                if (remaining <= QTY_EPS) {
                    continue;
                }
                double unitValue = JsNumbers.roundEps(poLineUnitValue(it.estimatedRate(), it.discount(), it.taxPct()), 2);
                double remainingValue = JsNumbers.roundEps(remaining * unitValue, 2);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("poId", po.id());
                row.put("poNumber", po.poNumber());
                row.put("date", PurchaseOrderOverdue.businessDay(po.date()));
                row.put("expectedDeliveryDate", po.expectedDeliveryDate() == null ? null
                        : PurchaseOrderOverdue.businessDay(po.expectedDeliveryDate()));
                row.put("supplierName", po.supplierName() != null ? po.supplierName() : "PO Umum");
                row.put("warehouseName", po.warehouseName());
                row.put("isOverdue", overdue);
                row.put("daysOverdue", late);
                row.put("itemId", it.id());
                row.put("itemName", it.itemName());
                row.put("unit", it.unit());
                row.put("ordered", JsNumbers.num(ordered));
                row.put("received", JsNumbers.num(received));
                row.put("remaining", JsNumbers.num(remaining));
                row.put("unitValue", JsNumbers.num(unitValue));
                row.put("remainingValue", JsNumbers.num(remainingValue));
                rows.add(row);
                poIds.add(po.id());
                if (overdue) {
                    overdueIds.add(po.id());
                }
                remainingTotal += remainingValue;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("poCount", poIds.size());
        summary.put("lineCount", rows.size());
        summary.put("remainingValue", JsNumbers.num(JsNumbers.roundEps(remainingTotal, 2)));
        summary.put("overduePoCount", overdueIds.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("summary", summary);
        return out;
    }

    // ───────────────────────────── Kinerja Pemasok ─────────────────────────────

    record PerfItem(String id, BigDecimal quantity, BigDecimal qtyReceived, BigDecimal estimatedRate, BigDecimal discount) {
    }

    record PerfInvoiceLine(String purchaseOrderItemId, BigDecimal quantity, BigDecimal rate, BigDecimal discount) {
    }

    /** receiptDates = tanggal STT non-batal; invoiceLines = baris PI non-batal yang menagih baris STT PO ini. */
    record PerfPo(String id, Instant date, Instant expectedDeliveryDate, String supplierId, String supplierName,
                  List<PerfItem> items, List<Instant> receiptDates, List<PerfInvoiceLine> invoiceLines) {
    }

    private static final class Acc {
        String supplierId;
        String supplierName;
        int poCount;
        int evaluated;
        int onTime;
        int firstOnTime;
        double leadSum;
        int leadCount;
        double ordered;
        double filled;
        double piValue;
        double poValue;
    }

    private static long dayNum(Instant d) {
        return LocalDate.parse(PurchaseOrderOverdue.businessDay(d)).toEpochDay();
    }

    private static Number pct(double num, double den, int dp) {
        return den > 0 ? JsNumbers.num(JsNumbers.roundEps((num / den) * 100, dp)) : null;
    }

    static List<Map<String, Object>> buildSupplierPerformance(List<PerfPo> pos) {
        Map<String, Acc> acc = new LinkedHashMap<>();
        for (PerfPo po : pos) {
            String key = po.supplierId() != null ? po.supplierId() : "__general__";
            Acc a = acc.get(key);
            if (a == null) {
                a = new Acc();
                a.supplierId = po.supplierId();
                a.supplierName = po.supplierId() != null ? po.supplierName() : "PO Umum";
                acc.put(key, a);
            }
            a.poCount += 1;

            List<Long> sttDays = po.receiptDates().stream().map(PurchaseReports::dayNum).sorted().toList();
            if (!sttDays.isEmpty()) {
                a.leadSum += Math.max(0, sttDays.get(0) - dayNum(po.date()));
                a.leadCount += 1;
                if (po.expectedDeliveryDate() != null) {
                    long due = dayNum(po.expectedDeliveryDate());
                    a.evaluated += 1;
                    if (sttDays.get(sttDays.size() - 1) <= due) {
                        a.onTime += 1;
                    }
                    if (sttDays.get(0) <= due) {
                        a.firstOnTime += 1;
                    }
                }
            }

            Map<String, Double> poNet = new HashMap<>();
            for (PerfItem it : po.items()) {
                double ordered = JsNumbers.n(it.quantity());
                a.ordered += ordered;
                a.filled += Math.min(JsNumbers.n(it.qtyReceived()), ordered);
                poNet.put(it.id(), JsNumbers.n(it.estimatedRate()) * (1 - JsNumbers.n(it.discount()) / 100));
            }
            for (PerfInvoiceLine l : po.invoiceLines()) {
                Double ref = poNet.get(l.purchaseOrderItemId());
                if (ref == null) {
                    continue;
                }
                double qty = JsNumbers.n(l.quantity());
                a.piValue += qty * JsNumbers.n(l.rate()) * (1 - JsNumbers.n(l.discount()) / 100);
                a.poValue += qty * ref;
            }
        }

        Collator collator = Collator.getInstance(Locale.forLanguageTag("id-ID"));
        List<Acc> sorted = new ArrayList<>(acc.values());
        sorted.sort(Comparator.comparingInt((Acc a) -> -a.poCount)
                .thenComparing((Acc a) -> a.supplierName == null ? "" : a.supplierName, collator));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Acc a : sorted) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("supplierId", a.supplierId);
            row.put("supplierName", a.supplierName);
            row.put("poCount", a.poCount);
            row.put("evaluatedCount", a.evaluated);
            row.put("onTimeRate", pct(a.onTime, a.evaluated, 1));
            row.put("firstOnTimeRate", pct(a.firstOnTime, a.evaluated, 1));
            row.put("avgLeadTimeDays", a.leadCount > 0 ? JsNumbers.num(JsNumbers.roundEps(a.leadSum / a.leadCount, 1)) : null);
            row.put("fillRate", pct(a.filled, a.ordered, 1));
            row.put("priceVariancePct", a.poValue > 0
                    ? JsNumbers.num(JsNumbers.roundEps(((a.piValue - a.poValue) / a.poValue) * 100, 2)) : null);
            row.put("priceVarianceAmount", JsNumbers.num(JsNumbers.roundEps(a.piValue - a.poValue, 2)));
            rows.add(row);
        }
        return rows;
    }
}
