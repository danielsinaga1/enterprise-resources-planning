package id.neravoxa.erp.report;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.InvoiceStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Laporan operasional — port {@code routes/reports/operational.ts}: HPP, profitabilitas per SKU,
 * ringkasan modal produksi, jadwal hutang & piutang. (Laporan harian: {@link ErpDailyReportService}.)
 */
@Service
public class ErpOperationalReportService {

    private static final List<InvoiceStatus> NOT_ACTIVE = List.of(InvoiceStatus.Draft, InvoiceStatus.Cancelled);

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** {@code new Date(param)} untuk parameter yang tidak divalidasi sumber: tanggal tak valid membuat query gagal (500). */
    private static Instant rawDate(String raw, String failMessage) {
        Instant d = ReportDates.parse(raw);
        if (d == null) {
            throw ErpException.of(500, failMessage);
        }
        return d;
    }

    private static boolean present(String s) {
        return s != null && !s.isEmpty();
    }

    private static Map<String, Object> period(Instant start, Instant end) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("start", start);
        m.put("end", end);
        return m;
    }

    // ─────────────────────────────── HPP ───────────────────────────────

    /** GET /reports/hpp — penjualan barang per item: pendapatan vs HPP (qty × avg cost saat ini). */
    @ErpTx(readOnly = true)
    public Map<String, Object> hpp(Map<String, String> query) {
        String fail = "Gagal mengambil laporan HPP.";
        Instant now = Instant.now();
        Instant start = present(query.get("startDate")) ? rawDate(query.get("startDate"), fail) : ReportDates.startOfLocalYear(now);
        Instant end = present(query.get("endDate")) ? rawDate(query.get("endDate"), fail) : now;
        Query q = em.createNativeQuery("""
                SELECT ii.id, ii.code, ii.name, COALESCE(ii.category, 'Lainnya'),
                       SUM(si.quantity), SUM(si.amount), SUM(si.quantity * ii.average_cost)
                  FROM sales_invoice_items si
                  JOIN sales_invoices s ON s.id = si.sales_invoice_id
                  LEFT JOIN inventory_items ii ON ii.id = si.inventory_item_id
                 WHERE s.status NOT IN ('Draft', 'Cancelled')
                   AND s.date >= CAST(:start AS timestamp)
                   AND s.date <= CAST(:end AS timestamp)
                   AND si.inventory_item_id IS NOT NULL
                 GROUP BY ii.id, ii.code, ii.name, ii.category
                 ORDER BY SUM(si.amount) DESC
                """)
                .setParameter("start", ReportDates.sqlTs(start))
                .setParameter("end", ReportDates.sqlTs(end));
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;
        for (Object[] r : ReportRows.rows(q)) {
            BigDecimal revenue = ReportRows.dec(r[5]);
            BigDecimal cogs = ReportRows.dec(r[6]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", r[0]);
            m.put("itemCode", r[1]);
            m.put("itemName", r[2]);
            m.put("category", r[3]);
            m.put("totalQty", ReportRows.dec(r[4]));
            m.put("totalRevenue", revenue);
            m.put("totalCogs", cogs);
            m.put("grossMargin", revenue.subtract(cogs));
            m.put("marginPct", revenue.signum() > 0 ? ReportRows.pct(revenue.subtract(cogs), revenue, 1) : BigDecimal.ZERO);
            items.add(m);
            totalRevenue = totalRevenue.add(revenue);
            totalCogs = totalCogs.add(cogs);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalCogs", totalCogs);
        summary.put("grossProfit", totalRevenue.subtract(totalCogs));
        summary.put("grossMarginPct", totalRevenue.signum() > 0
                ? ReportRows.pct(totalRevenue.subtract(totalCogs), totalRevenue, 1) : BigDecimal.ZERO);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period(start, end));
        out.put("items", items);
        out.put("summary", summary);
        return out;
    }

    // ─────────────────────────── profitabilitas per SKU ───────────────────────────

    /**
     * GET /reports/profitability-per-sku — pendapatan vs HPP material (WAC saat query) vs HPP
     * ter-load per SKU; loaded = qty × COALESCE(unit_cost_loaded, average_cost).
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> profitabilityPerSku(Map<String, String> query) {
        String fail = "Gagal mengambil laporan profitability per SKU.";
        Instant now = Instant.now();
        Instant start = present(query.get("startDate")) ? rawDate(query.get("startDate"), fail) : ReportDates.startOfLocalYear(now);
        Instant end = ReportDates.endOfLocalDay(present(query.get("endDate")) ? rawDate(query.get("endDate"), fail) : now);
        Query q = em.createNativeQuery("""
                SELECT ii.id, ii.code, ii.name, ii.unit, COALESCE(ii.category, 'Lainnya'),
                       SUM(si.quantity), SUM(si.amount),
                       SUM(si.quantity * ii.average_cost),
                       SUM(si.quantity * COALESCE(si.unit_cost_loaded, ii.average_cost)),
                       SUM(CASE WHEN si.unit_cost_loaded IS NOT NULL THEN 1 ELSE 0 END),
                       COUNT(*)
                  FROM sales_invoice_items si
                  JOIN sales_invoices s ON s.id = si.sales_invoice_id
                  LEFT JOIN inventory_items ii ON ii.id = si.inventory_item_id
                 WHERE s.status NOT IN ('Draft', 'Cancelled')
                   AND s.date >= CAST(:start AS timestamp)
                   AND s.date <= CAST(:end AS timestamp)
                   AND si.inventory_item_id IS NOT NULL
                 GROUP BY ii.id, ii.code, ii.name, ii.unit, ii.category
                 ORDER BY SUM(si.amount) DESC
                """)
                .setParameter("start", ReportDates.sqlTs(start))
                .setParameter("end", ReportDates.sqlTs(end));
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalMaterial = BigDecimal.ZERO;
        BigDecimal totalLoaded = BigDecimal.ZERO;
        for (Object[] r : ReportRows.rows(q)) {
            BigDecimal revenue = ReportRows.dec(r[6]);
            BigDecimal material = ReportRows.dec(r[7]);
            BigDecimal loaded = ReportRows.dec(r[8]);
            long withBatch = ReportRows.lng(r[9]);
            long lines = ReportRows.lng(r[10]);
            boolean hasRevenue = revenue.signum() > 0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", r[0]);
            m.put("itemCode", r[1]);
            m.put("itemName", r[2]);
            m.put("unit", r[3]);
            m.put("category", r[4]);
            m.put("totalQty", ReportRows.dec(r[5]));
            m.put("totalRevenue", revenue);
            m.put("materialCogs", material);
            m.put("loadedCogs", loaded);
            m.put("materialMargin", revenue.subtract(material));
            m.put("loadedMargin", revenue.subtract(loaded));
            m.put("materialMarginPct", hasRevenue ? ReportRows.pct(revenue.subtract(material), revenue, 1) : BigDecimal.ZERO);
            m.put("loadedMarginPct", hasRevenue ? ReportRows.pct(revenue.subtract(loaded), revenue, 1) : BigDecimal.ZERO);
            m.put("linesWithBatch", withBatch);
            m.put("totalLines", lines);
            m.put("coveragePct", lines > 0
                    ? ReportRows.pct(BigDecimal.valueOf(withBatch), BigDecimal.valueOf(lines), 0) : BigDecimal.ZERO);
            items.add(m);
            totalRevenue = totalRevenue.add(revenue);
            totalMaterial = totalMaterial.add(material);
            totalLoaded = totalLoaded.add(loaded);
        }
        boolean hasRevenue = totalRevenue.signum() > 0;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalMaterialCogs", totalMaterial);
        summary.put("totalLoadedCogs", totalLoaded);
        summary.put("materialGrossProfit", totalRevenue.subtract(totalMaterial));
        summary.put("loadedGrossProfit", totalRevenue.subtract(totalLoaded));
        summary.put("materialMarginPct", hasRevenue ? ReportRows.pct(totalRevenue.subtract(totalMaterial), totalRevenue, 1) : BigDecimal.ZERO);
        summary.put("loadedMarginPct", hasRevenue ? ReportRows.pct(totalRevenue.subtract(totalLoaded), totalRevenue, 1) : BigDecimal.ZERO);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period(start, end));
        out.put("items", items);
        out.put("summary", summary);
        return out;
    }

    // ─────────────────────────── modal produksi ───────────────────────────

    private record RunRow(String runId, String runNumber, Instant date, String outputName, String warehouseName,
                          BigDecimal inputKg, BigDecimal inputRp, BigDecimal mainKg, BigDecimal mainRp,
                          BigDecimal byproductKg, BigDecimal byproductRp, BigDecimal conversionCostRp) {
    }

    /** Akumulator per (output × gudang) dan TOTAL. */
    private static final class Agg {
        final String outputName;
        final String warehouseName;
        long runCount;
        BigDecimal inputKg = BigDecimal.ZERO;
        BigDecimal inputRp = BigDecimal.ZERO;
        BigDecimal mainKg = BigDecimal.ZERO;
        BigDecimal mainRp = BigDecimal.ZERO;
        BigDecimal byproductKg = BigDecimal.ZERO;
        BigDecimal byproductRp = BigDecimal.ZERO;
        BigDecimal conversionCostRp = BigDecimal.ZERO;

        Agg(String outputName, String warehouseName) {
            this.outputName = outputName;
            this.warehouseName = warehouseName;
        }

        void add(RunRow r) {
            runCount++;
            inputKg = inputKg.add(r.inputKg());
            inputRp = inputRp.add(r.inputRp());
            mainKg = mainKg.add(r.mainKg());
            mainRp = mainRp.add(r.mainRp());
            byproductKg = byproductKg.add(r.byproductKg());
            byproductRp = byproductRp.add(r.byproductRp());
            conversionCostRp = conversionCostRp.add(r.conversionCostRp());
        }

        Map<String, Object> derive() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("outputName", outputName);
            m.put("warehouseName", warehouseName);
            m.put("runCount", runCount);
            m.put("inputKg", inputKg);
            m.put("inputRp", inputRp);
            m.put("mainKg", mainKg);
            m.put("mainRp", mainRp);
            m.put("byproductKg", byproductKg);
            m.put("byproductRp", byproductRp);
            m.put("conversionCostRp", conversionCostRp);
            putRatios(m, inputKg, inputRp, mainKg, mainRp);
            return m;
        }
    }

    /** yieldPct, inputPerKg, rawMaterialPerKg (bahan / kg output utama), glPerKg (HPP terposting / kg). */
    private static void putRatios(Map<String, Object> m, BigDecimal inputKg, BigDecimal inputRp, BigDecimal mainKg,
                                  BigDecimal mainRp) {
        boolean hasInput = inputKg.signum() > 0;
        boolean hasMain = mainKg.signum() > 0;
        m.put("yieldPct", hasInput ? ReportRows.div(mainKg.multiply(BigDecimal.valueOf(100)), inputKg) : BigDecimal.ZERO);
        m.put("inputPerKg", hasInput ? ReportRows.div(inputRp, inputKg) : BigDecimal.ZERO);
        m.put("rawMaterialPerKg", hasMain ? ReportRows.div(inputRp, mainKg) : BigDecimal.ZERO);
        m.put("glPerKg", hasMain ? ReportRows.div(mainRp, mainKg) : BigDecimal.ZERO);
    }

    /**
     * GET /reports/production-cost-summary — per output utama × gudang: biaya bahan/kg vs HPP
     * terposting/kg (bahan − NRV by-product + biaya konversi). Hanya baca, tanpa jurnal.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> productionCostSummary(Map<String, String> query) {
        Instant now = Instant.now();
        Instant start = ErpFinancialReportService.queryDate(query.get("startDate"), false);
        Instant end = ErpFinancialReportService.queryDate(query.get("endDate"), true);
        if (start == null) {
            start = ReportDates.startOfLocalMonth(now, 0);
        }
        if (end == null) {
            end = now;
        }
        String warehouseId = present(query.get("warehouseId")) ? query.get("warehouseId") : null;

        Query q = em.createNativeQuery("""
                WITH runs AS (
                  SELECT pr.id, pr.run_number, pr.date FROM production_runs pr
                   WHERE NOT pr.is_cancelled AND pr.date >= CAST(:start AS timestamp) AND pr.date <= CAST(:end AS timestamp)
                ),
                main_items AS (
                  SELECT DISTINCT i.production_run_id run_id, i.item_id FROM production_run_items i
                    JOIN runs r ON r.id = i.production_run_id
                   WHERE i.line_type = 'Output' AND NOT i.is_by_product
                ),
                mv AS (
                  SELECT sm.reference_id run_id, sm.movement_type mt, sm.item_id, sm.warehouse_id,
                         sm.quantity q, sm.total_value tv,
                         EXISTS (SELECT 1 FROM main_items m WHERE m.run_id = sm.reference_id AND m.item_id = sm.item_id) is_main
                    FROM stock_movements sm JOIN runs r ON r.id = sm.reference_id
                   WHERE NOT sm.is_cancelled
                )
                SELECT r.id, r.run_number,\s""" + ReportRows.epochMs("r.date") + """
                ,
                  (SELECT string_agg(DISTINCT ii.name, ' + ') FROM main_items m JOIN inventory_items ii ON ii.id = m.item_id WHERE m.run_id = r.id),
                  (SELECT string_agg(DISTINCT w.name, ', ') FROM mv JOIN warehouses w ON w.id = mv.warehouse_id WHERE mv.run_id = r.id AND mv.mt = 'In' AND mv.is_main),
                  (SELECT min(mv.warehouse_id) FROM mv WHERE mv.run_id = r.id AND mv.mt = 'In' AND mv.is_main),
                  (SELECT sum(mv.q) FROM mv WHERE mv.run_id = r.id AND mv.mt = 'Out'),
                  (SELECT sum(mv.tv) FROM mv WHERE mv.run_id = r.id AND mv.mt = 'Out'),
                  (SELECT sum(mv.q) FROM mv WHERE mv.run_id = r.id AND mv.mt = 'In' AND mv.is_main),
                  (SELECT sum(mv.tv) FROM mv WHERE mv.run_id = r.id AND mv.mt = 'In' AND mv.is_main),
                  (SELECT sum(mv.q) FROM mv WHERE mv.run_id = r.id AND mv.mt = 'In' AND NOT mv.is_main),
                  (SELECT sum(mv.tv) FROM mv WHERE mv.run_id = r.id AND mv.mt = 'In' AND NOT mv.is_main),
                  (SELECT sum(c.total_amount) FROM production_run_costs c WHERE c.production_run_id = r.id)
                FROM runs r ORDER BY r.date, r.run_number
                """)
                .setParameter("start", ReportDates.sqlTs(start))
                .setParameter("end", ReportDates.sqlTs(end));

        List<RunRow> runs = new ArrayList<>();
        for (Object[] r : ReportRows.rows(q)) {
            if (warehouseId != null && !warehouseId.equals(r[5])) {
                continue;
            }
            BigDecimal mainKg = ReportRows.dec(r[8]);
            if (mainKg.signum() <= 0) {
                continue;
            }
            runs.add(new RunRow((String) r[0], (String) r[1], ReportRows.instantMs(r[2]),
                    r[3] == null ? "-" : (String) r[3], r[4] == null ? "-" : (String) r[4],
                    ReportRows.dec(r[6]), ReportRows.dec(r[7]), mainKg, ReportRows.dec(r[9]),
                    ReportRows.dec(r[10]), ReportRows.dec(r[11]), ReportRows.dec(r[12])));
        }

        List<Map<String, Object>> runRows = new ArrayList<>();
        Map<String, Agg> groups = new LinkedHashMap<>();
        Agg total = new Agg("TOTAL", "");
        for (RunRow r : runs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("runId", r.runId());
            m.put("runNumber", r.runNumber());
            m.put("date", r.date());
            m.put("outputName", r.outputName());
            m.put("warehouseName", r.warehouseName());
            m.put("inputKg", r.inputKg());
            m.put("inputRp", r.inputRp());
            m.put("mainKg", r.mainKg());
            m.put("mainRp", r.mainRp());
            m.put("byproductKg", r.byproductKg());
            m.put("byproductRp", r.byproductRp());
            m.put("conversionCostRp", r.conversionCostRp());
            putRatios(m, r.inputKg(), r.inputRp(), r.mainKg(), r.mainRp());
            runRows.add(m);
            groups.computeIfAbsent(r.outputName() + "|" + r.warehouseName(),
                    k -> new Agg(r.outputName(), r.warehouseName())).add(r);
            total.add(r);
        }
        // localeCompare: urut gudang lalu nama output.
        Collator collator = Collator.getInstance(Locale.US);
        List<Agg> sorted = new ArrayList<>(groups.values());
        sorted.sort(Comparator.<Agg, String>comparing(a -> a.warehouseName, collator)
                .thenComparing(a -> a.outputName, collator));
        List<Map<String, Object>> summary = new ArrayList<>();
        sorted.forEach(a -> summary.add(a.derive()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period(start, end));
        out.put("summary", summary);
        out.put("total", total.derive());
        out.put("runs", runRows);
        return out;
    }

    // ─────────────────────────── jadwal hutang / piutang ───────────────────────────

    /** GET /reports/payable-schedule — faktur pembelian outstanding, status jatuh tempo per hari ini. */
    @ErpTx(readOnly = true)
    public Map<String, Object> payableSchedule() {
        return schedule(true);
    }

    /** GET /reports/receivable-schedule — faktur penjualan outstanding. */
    @ErpTx(readOnly = true)
    public Map<String, Object> receivableSchedule() {
        return schedule(false);
    }

    private Map<String, Object> schedule(boolean payable) {
        String jpql = payable
                ? "select i.id, i.invoiceNumber, p.name, p.id, i.date, i.dueDate, i.grandTotal, i.outstanding "
                + "from PurchaseInvoice i join i.supplier p where i.outstanding > 0 and i.status not in :st order by i.dueDate asc"
                : "select i.id, i.invoiceNumber, p.name, p.id, i.date, i.dueDate, i.grandTotal, i.outstanding "
                + "from SalesInvoice i join i.customer p where i.outstanding > 0 and i.status not in :st order by i.dueDate asc";
        long now = Instant.now().toEpochMilli();
        List<Map<String, Object>> data = new ArrayList<>();
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        BigDecimal dueSoon = BigDecimal.ZERO;
        for (Object[] r : em.createQuery(jpql, Object[].class).setParameter("st", NOT_ACTIVE).getResultList()) {
            Instant due = (Instant) r[5];
            Long daysUntilDue = due == null ? null : ReportDates.ceilDays(due.toEpochMilli() - now);
            String status = due == null ? "no_due_date"
                    : daysUntilDue < 0 ? "overdue"
                    : daysUntilDue <= 7 ? "due_soon"
                    : "on_track";
            BigDecimal outstanding = Money.nz((BigDecimal) r[7]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("invoiceNumber", r[1]);
            m.put(payable ? "supplierName" : "customerName", r[2]);
            m.put(payable ? "supplierId" : "customerId", r[3]);
            m.put("date", r[4]);
            m.put("dueDate", due);
            m.put("grandTotal", Money.nz((BigDecimal) r[6]));
            m.put("outstanding", outstanding);
            m.put("daysUntilDue", daysUntilDue);
            m.put("status", status);
            data.add(m);
            totalOutstanding = totalOutstanding.add(outstanding);
            if ("overdue".equals(status)) {
                overdue = overdue.add(outstanding);
            } else if ("due_soon".equals(status)) {
                dueSoon = dueSoon.add(outstanding);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOutstanding", totalOutstanding);
        summary.put("overdueAmount", overdue);
        if (payable) {
            summary.put("dueSoonAmount", dueSoon);
        }
        summary.put("totalInvoices", data.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("invoices", data);
        out.put("summary", summary);
        return out;
    }
}
