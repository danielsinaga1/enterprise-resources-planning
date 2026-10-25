package id.neravoxa.erp.inventory;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.InventoryItem;
import id.neravoxa.erp.entity.StockMovement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dashboard gudang & produksi — port {@code routes/inventory/dashboard.ts} (+ inventoryDashboard.ts).
 *
 * <p>Batas bulan memakai kalender UTC, sama dengan proses Node ENTARIS di container (TZ UTC).
 * Nilai stok = qty fisik × harga rata-rata (operasional), bukan saldo GL — dashboard utama sengaja
 * memakai GL.
 */
@Service
public class ErpInventoryDashboardService {

    private static final String[] MONTH_NAMES = {"Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"};
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final long DAY_MS = 86_400_000L;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /** Awal bulan (UTC) dengan pergeseran {@code monthsDelta} dari bulan berjalan. */
    private static LocalDate monthStart(int monthsDelta) {
        return todayUtc().withDayOfMonth(1).plusMonths(monthsDelta);
    }

    private static Instant utc(LocalDate d) {
        return d.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static LocalDateTime ldt(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static BigDecimal bd(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof Double d) {
            return BigDecimal.valueOf(d);
        }
        return Money.of(v);
    }

    // ───────────────────────────── stok ─────────────────────────────

    /** GET /metrics — ringkasan KPI gudang. */
    @ErpTx(readOnly = true)
    public Map<String, Object> metrics() {
        long totalItems = em.createQuery("select count(i) from InventoryItem i", Long.class).getSingleResult();
        long activeItems = em.createQuery("select count(i) from InventoryItem i where i.isActive = true "
                + "and i.currentStock > 0", Long.class).getSingleResult();
        Number low = (Number) em.createNativeQuery("SELECT count(*) FROM inventory_items WHERE is_active = true "
                + "AND minimum_stock > 0 AND current_stock <= minimum_stock").getSingleResult();
        long movements = em.createQuery("select count(m) from StockMovement m where m.isCancelled = false "
                        + "and m.date >= :d", Long.class)
                .setParameter("d", utc(monthStart(0))).getSingleResult();
        // Nilai operasional (qty fisik × avg cost), bukan saldo GL.
        Object value = em.createNativeQuery("SELECT COALESCE(SUM(current_stock * average_cost), 0) "
                + "FROM inventory_items WHERE is_active = true").getSingleResult();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalItems", totalItems);
        m.put("activeItems", activeItems);
        m.put("lowStockCount", low == null ? 0L : low.longValue());
        m.put("movementsThisMonth", movements);
        m.put("inventoryValue", bd(value));
        return m;
    }

    /** GET /by-category — distribusi stok item aktif per kategori. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> byCategory() {
        Map<String, Object[]> map = new LinkedHashMap<>();
        for (Object[] r : em.createQuery("select i.category, i.currentStock from InventoryItem i where i.isActive = true",
                Object[].class).getResultList()) {
            String cat = r[0] == null || ((String) r[0]).isEmpty() ? "Lainnya" : (String) r[0];
            Object[] e = map.computeIfAbsent(cat, k -> new Object[]{0L, BigDecimal.ZERO});
            e[0] = (Long) e[0] + 1;
            e[1] = ((BigDecimal) e[1]).add(Money.nz((BigDecimal) r[1]));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        map.forEach((cat, e) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", cat);
            m.put("itemCount", e[0]);
            m.put("totalQuantity", e[1]);
            out.add(m);
        });
        out.sort((a, b) -> ((BigDecimal) b.get("totalQuantity")).compareTo((BigDecimal) a.get("totalQuantity")));
        return out;
    }

    /** GET /stock-value?groupBy=category|warehouse — nilai stok item aktif; per gudang memakai saldo inventory_stocks. */
    @ErpTx(readOnly = true)
    public Map<String, Object> stockValue(String groupBy) {
        List<InventoryDashboardCalc.Row> rows = new ArrayList<>();
        if ("category".equals(groupBy)) {
            for (Object[] r : em.createQuery("select i.category, i.currentStock, i.averageCost from InventoryItem i "
                    + "where i.isActive = true", Object[].class).getResultList()) {
                rows.add(new InventoryDashboardCalc.Row((String) r[0], null, (BigDecimal) r[1], (BigDecimal) r[2]));
            }
        } else {
            for (Object[] r : em.createQuery("select s.warehouseId, w.name, s.currentStock, i.averageCost "
                    + "from InventoryStock s join s.item i left join s.warehouse w where i.isActive = true", Object[].class)
                    .getResultList()) {
                rows.add(new InventoryDashboardCalc.Row((String) r[0], (String) r[1], (BigDecimal) r[2], (BigDecimal) r[3]));
            }
        }
        return InventoryDashboardCalc.summarize(rows, groupBy, InventoryDashboardCalc.GROUP_LIMIT);
    }

    /** GET /movement-trend — tren 6 bulan (masuk/keluar/penyesuaian; transfer tidak dihitung). */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> movementTrend() {
        int months = 6;
        LocalDate start = monthStart(-(months - 1));
        Map<String, BigDecimal[]> monthMap = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            monthMap.put(start.plusMonths(i).format(YM),
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT to_char(date, 'YYYY-MM'), movement_type, SUM(quantity) "
                        + "FROM stock_movements WHERE is_cancelled = false AND date >= :d GROUP BY 1, 2")
                .setParameter("d", ldt(utc(start))).getResultList();
        for (Object[] r : rows) {
            BigDecimal[] e = monthMap.get((String) r[0]);
            if (e == null) {
                continue;
            }
            BigDecimal q = bd(r[2]);
            switch ((String) r[1]) {
                case "In" -> e[0] = e[0].add(q);
                case "Out" -> e[1] = e[1].add(q);
                case "AdjustmentIn" -> e[2] = e[2].add(q);
                case "AdjustmentOut" -> e[3] = e[3].add(q);
                default -> {
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        monthMap.forEach((month, e) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", month);
            m.put("inQty", e[0]);
            m.put("outQty", e[1]);
            m.put("adjInQty", e[2]);
            m.put("adjOutQty", e[3]);
            m.put("netChange", e[0].add(e[2]).subtract(e[1]).subtract(e[3]));
            out.add(m);
        });
        return out;
    }

    /** GET /top-items — semua item aktif urut nilai stok (qty × avg cost) menurun. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> topItems() {
        List<InventoryItem> items = em.createQuery("select i from InventoryItem i where i.isActive = true",
                InventoryItem.class).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (InventoryItem i : items) {
            BigDecimal cur = Money.nz(i.getCurrentStock());
            BigDecimal min = Money.nz(i.getMinimumStock());
            BigDecimal avg = Money.nz(i.getAverageCost());
            Map<String, Object> m = InvJson.pick(i, "id", "code", "name", "unit", "category", "currentStock",
                    "minimumStock", "averageCost");
            m.put("stockValue", cur.multiply(avg));
            m.put("stockPct", min.signum() > 0
                    ? InvJson.jsRound(cur.multiply(Money.HUNDRED).divide(min, 10, RoundingMode.HALF_UP), 0) : null);
            out.add(m);
        }
        out.sort((a, b) -> ((BigDecimal) b.get("stockValue")).compareTo((BigDecimal) a.get("stockValue")));
        return out;
    }

    /** GET /recent-movements — 10 gerakan aktif terakhir. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> recentMovements() {
        List<StockMovement> rows = em.createQuery("select m from StockMovement m join fetch m.item "
                        + "where m.isCancelled = false order by m.date desc", StockMovement.class)
                .setMaxResults(10).getResultList();
        return rows.stream().map(m -> {
            Map<String, Object> x = InvJson.pick(m, "id", "movementNumber", "date", "movementType", "quantity",
                    "totalValue", "notes");
            x.put("item", InvJson.pick(m.getItem(), "name", "unit", "code"));
            return x;
        }).toList();
    }

    // ───────────────────────────── produksi ─────────────────────────────

    /** GET /production-stats — statistik run produksi. */
    @ErpTx(readOnly = true)
    public Map<String, Object> productionStats() {
        long totalRuns = em.createQuery("select count(r) from ProductionRun r where r.isCancelled = false", Long.class)
                .getSingleResult();
        long thisMonth = em.createQuery("select count(r) from ProductionRun r where r.isCancelled = false "
                + "and r.date >= :d", Long.class).setParameter("d", utc(monthStart(0))).getSingleResult();
        List<BigDecimal> yields = em.createQuery("select r.yieldPct from ProductionRun r where r.isCancelled = false "
                        + "and r.yieldPct is not null order by r.date desc", BigDecimal.class)
                .setMaxResults(50).getResultList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalRuns", totalRuns);
        m.put("thisMonthRuns", thisMonth);
        m.put("avgYield", yields.isEmpty() ? BigDecimal.ZERO : InvJson.jsRound(InvJson.avg(yields), 2));
        return m;
    }

    private record Run(String id, String runNumber, Instant date, BigDecimal yieldPct) {
    }

    private record RunLine(String lineType, BigDecimal quantity, boolean isByProduct, String itemId) {
    }

    /** GET /production — dashboard produksi lengkap (periode + periode sebelumnya, yield, HPP loaded). */
    @ErpTx(readOnly = true)
    public Map<String, Object> production(String itemId, String startDate, String endDate) {
        final String fail = "Gagal mengambil dashboard produksi.";
        Instant filterStart = null;
        Instant filterEnd = null;
        if (startDate != null) {
            filterStart = ErpDates.tryInstant(startDate);
            if (filterStart == null) {
                // new Date(invalid) di sumber → query Prisma gagal → 500.
                throw ErpException.of(500, fail);
            }
        }
        if (endDate != null) {
            filterEnd = ErpDates.tryInstant(endDate + "T23:59:59");
            if (filterEnd == null) {
                throw ErpException.of(500, fail);
            }
        }
        Instant periodStart = filterStart != null ? filterStart : utc(monthStart(0));
        Instant periodEnd = filterEnd != null ? filterEnd : utc(monthStart(1)).minusSeconds(1);
        long periodDays = Math.max(1, (long) Math.ceil((periodEnd.toEpochMilli() - periodStart.toEpochMilli()) / (double) DAY_MS));
        // Periode sebelumnya: durasi sama, tepat sebelum periode ini.
        Instant prevEnd = periodStart.minusMillis(1);
        Instant prevStart = prevEnd.minusMillis(periodDays * DAY_MS);

        List<String> runIdFilter = null;
        if (itemId != null) {
            runIdFilter = em.createQuery("select distinct x.productionRunId from ProductionRunItem x where x.itemId = :i",
                    String.class).setParameter("i", itemId).getResultList();
        }
        String base = "r.isCancelled = false" + (runIdFilter != null ? " and r.id in :runIds" : "");
        List<String> runIds = runIdFilter == null ? null : (List<String>) ErpQuery.nonEmpty(runIdFilter, "__none__");

        long totalRuns = countRuns(base, runIds, null, null);
        long periodRuns = countRuns(base, runIds, periodStart, periodEnd);
        long prevPeriodRuns = countRuns(base, runIds, prevStart, prevEnd);
        TypedQuery<Object[]> rq = em.createQuery("select r.id, r.runNumber, r.date, r.yieldPct from ProductionRun r where "
                + base + " order by r.date desc", Object[].class);
        if (runIds != null) {
            rq.setParameter("runIds", runIds);
        }
        List<Run> allRuns = rq.getResultList().stream()
                .map(r -> new Run((String) r[0], (String) r[1], (Instant) r[2], (BigDecimal) r[3])).toList();
        final Instant ps = periodStart;
        final Instant pe = periodEnd;
        java.util.function.Predicate<Run> inPeriod = r -> !r.date().isBefore(ps) && !r.date().isAfter(pe);

        List<Run> withYield = allRuns.stream().filter(r -> r.yieldPct() != null).toList();
        BigDecimal avgYield = InvJson.avg(withYield.stream().map(Run::yieldPct).toList());
        List<Run> periodYield = new ArrayList<>(withYield.stream().filter(inPeriod).toList());
        BigDecimal avgYieldPeriod = InvJson.avg(periodYield.stream().map(Run::yieldPct).toList());
        List<String> periodRunIds = allRuns.stream().filter(inPeriod).map(Run::id).toList();

        // Total memakai semua baris run yang cocok (tidak difilter itemId).
        List<RunLine> lines = new ArrayList<>();
        if (!periodRunIds.isEmpty()) {
            for (Object[] r : em.createQuery("select x.lineType, x.quantity, x.isByProduct, x.itemId from ProductionRunItem x "
                    + "where x.productionRunId in :ids", Object[].class).setParameter("ids", periodRunIds).getResultList()) {
                lines.add(new RunLine((String) r[0], Money.nz((BigDecimal) r[1]), Boolean.TRUE.equals(r[2]), (String) r[3]));
            }
        }
        BigDecimal totalInput = BigDecimal.ZERO;
        BigDecimal totalOutput = BigDecimal.ZERO;
        BigDecimal totalByProduct = BigDecimal.ZERO;
        for (RunLine l : lines) {
            if ("Input".equals(l.lineType())) {
                totalInput = totalInput.add(l.quantity());
            }
            if ("Output".equals(l.lineType())) {
                totalOutput = totalOutput.add(l.quantity());
            }
            if ("ByProduct".equals(l.lineType()) || l.isByProduct()) {
                totalByProduct = totalByProduct.add(l.quantity());
            }
        }

        Map<String, List<BigDecimal[]>> yieldByDay = new TreeMap<>();
        for (Run r : periodYield) {
            yieldByDay.computeIfAbsent(ErpDates.isoDay(r.date()), k -> new ArrayList<>()).add(new BigDecimal[]{r.yieldPct()});
        }
        List<Map<String, Object>> yieldTrend = new ArrayList<>();
        yieldByDay.forEach((day, vals) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day);
            m.put("avgYield", InvJson.jsRound(InvJson.avg(vals.stream().map(v -> v[0]).toList()), 2));
            m.put("count", vals.size());
            yieldTrend.add(m);
        });

        List<Map<String, Object>> topInputs = topLines(lines, "Input");
        List<Map<String, Object>> topOutputs = topLines(lines, "Output");

        periodYield.sort((a, b) -> b.yieldPct().compareTo(a.yieldPct()));
        List<Map<String, Object>> best = new ArrayList<>();
        for (Run r : periodYield.subList(0, Math.min(5, periodYield.size()))) {
            best.add(yieldRow(r));
        }
        List<Map<String, Object>> worst = new ArrayList<>();
        List<Run> tail = periodYield.subList(Math.max(0, periodYield.size() - 5), periodYield.size());
        for (int i = tail.size() - 1; i >= 0; i--) {
            worst.add(yieldRow(tail.get(i)));
        }

        List<Map<String, Object>> recent = recentProduction(base, runIds, periodStart, periodEnd);
        Map<String, Object> loadedHpp = loadedHpp(periodRunIds);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", totalRuns);
        summary.put("periodRuns", periodRuns);
        summary.put("prevPeriodRuns", prevPeriodRuns);
        summary.put("avgYield", InvJson.jsRound(avgYield, 2));
        summary.put("avgYieldPeriod", InvJson.jsRound(avgYieldPeriod, 2));
        summary.put("totalInputQtyPeriod", totalInput);
        summary.put("totalOutputQtyPeriod", totalOutput);
        summary.put("totalByProductQtyPeriod", totalByProduct);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("yieldTrend", yieldTrend);
        out.put("topInputs", topInputs);
        out.put("topOutputs", topOutputs);
        out.put("bestYield", best);
        out.put("worstYield", worst);
        out.put("recentProduction", recent);
        out.put("loadedHpp", loadedHpp);
        return out;
    }

    private long countRuns(String base, List<String> runIds, Instant from, Instant to) {
        TypedQuery<Long> q = em.createQuery("select count(r) from ProductionRun r where " + base
                + (from != null ? " and r.date >= :from and r.date <= :to" : ""), Long.class);
        if (runIds != null) {
            q.setParameter("runIds", runIds);
        }
        if (from != null) {
            q.setParameter("from", from).setParameter("to", to);
        }
        return q.getSingleResult();
    }

    private static Map<String, Object> yieldRow(Run r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runNumber", r.runNumber());
        m.put("date", r.date());
        m.put("yieldPct", r.yieldPct());
        return m;
    }

    /** 5 item teratas per qty untuk lineType tertentu: { id, name, unit, quantity }. */
    private List<Map<String, Object>> topLines(List<RunLine> lines, String lineType) {
        Map<String, BigDecimal> qty = new LinkedHashMap<>();
        for (RunLine l : lines) {
            if (lineType.equals(l.lineType())) {
                qty.merge(l.itemId(), l.quantity(), BigDecimal::add);
            }
        }
        List<Map.Entry<String, BigDecimal>> sorted = new ArrayList<>(qty.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        List<Map.Entry<String, BigDecimal>> top = sorted.subList(0, Math.min(5, sorted.size()));
        Map<String, Object[]> details = new HashMap<>();
        if (!top.isEmpty()) {
            em.createQuery("select i.id, i.name, i.unit from InventoryItem i where i.id in :ids", Object[].class)
                    .setParameter("ids", top.stream().map(Map.Entry::getKey).toList())
                    .getResultList().forEach(r -> details.put((String) r[0], r));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : top) {
            Object[] d = details.get(e.getKey());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getKey());
            m.put("name", d != null && d[1] != null ? d[1] : "");
            m.put("unit", d != null && d[2] != null ? d[2] : "");
            m.put("quantity", e.getValue());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> recentProduction(String base, List<String> runIds, Instant from, Instant to) {
        TypedQuery<Object[]> q = em.createQuery("select r.id, r.runNumber, r.date, r.yieldPct, r.referenceNumber "
                + "from ProductionRun r where " + base + " and r.date >= :from and r.date <= :to order by r.date desc",
                Object[].class).setParameter("from", from).setParameter("to", to).setMaxResults(10);
        if (runIds != null) {
            q.setParameter("runIds", runIds);
        }
        List<Object[]> runs = q.getResultList();
        Map<String, List<Object[]>> itemsByRun = new HashMap<>();
        if (!runs.isEmpty()) {
            for (Object[] r : em.createQuery("select x.productionRunId, x.lineType, x.quantity, it.name, it.unit "
                            + "from ProductionRunItem x join x.item it where x.productionRunId in :ids "
                            + "order by x.createdAt asc, x.id asc", Object[].class)
                    .setParameter("ids", runs.stream().map(r -> (String) r[0]).toList()).getResultList()) {
                itemsByRun.computeIfAbsent((String) r[0], k -> new ArrayList<>()).add(r);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : runs) {
            List<Object[]> items = itemsByRun.getOrDefault((String) r[0], List.of());
            List<Object[]> inputs = items.stream().filter(i -> "Input".equals(i[1])).toList();
            List<Object[]> outputs = items.stream().filter(i -> "Output".equals(i[1])).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("runNumber", r[1]);
            m.put("date", r[2]);
            m.put("yieldPct", r[3]);
            m.put("referenceNumber", r[4]);
            m.put("totalInput", Money.sum(inputs, i -> (BigDecimal) i[2]));
            m.put("totalOutput", Money.sum(outputs, i -> (BigDecimal) i[2]));
            m.put("inputSummary", lineSummary(inputs));
            m.put("outputSummary", lineSummary(outputs));
            out.add(m);
        }
        return out;
    }

    private static String lineSummary(List<Object[]> lines) {
        List<String> parts = new ArrayList<>();
        for (Object[] i : lines) {
            parts.add(i[3] + " " + InvJson.idNum(Money.nz((BigDecimal) i[2]), 3) + " " + i[4]);
        }
        return String.join(", ", parts);
    }

    /** Analitik HPP loaded: hanya run periode dengan computedHpp terisi. */
    private Map<String, Object> loadedHpp(List<String> periodRunIds) {
        List<Object[]> runs = periodRunIds.isEmpty() ? List.of() : em.createQuery("select r.id, r.computedHpp, "
                        + "r.totalMaterialCost, r.totalCostInputSide, r.totalCostOutputSide, r.totalByproductValue "
                        + "from ProductionRun r where r.id in :ids and r.computedHpp is not null", Object[].class)
                .setParameter("ids", periodRunIds).getResultList();
        Map<String, List<Object[]>> outputsByRun = new HashMap<>();
        if (!runs.isEmpty()) {
            for (Object[] o : em.createQuery("select x.productionRunId, x.itemId, x.quantity, it.code, it.name, it.unit "
                            + "from ProductionRunItem x join x.item it where x.productionRunId in :ids and x.lineType = 'Output' "
                            + "order by x.createdAt asc, x.id asc", Object[].class)
                    .setParameter("ids", runs.stream().map(r -> (String) r[0]).toList()).getResultList()) {
                outputsByRun.computeIfAbsent((String) o[0], k -> new ArrayList<>()).add(o);
            }
        }
        BigDecimal material = BigDecimal.ZERO;
        BigDecimal inputSide = BigDecimal.ZERO;
        BigDecimal outputSide = BigDecimal.ZERO;
        BigDecimal byproduct = BigDecimal.ZERO;
        for (Object[] r : runs) {
            material = material.add(Money.nz((BigDecimal) r[2]));
            inputSide = inputSide.add(Money.nz((BigDecimal) r[3]));
            outputSide = outputSide.add(Money.nz((BigDecimal) r[4]));
            byproduct = byproduct.add(Money.nz((BigDecimal) r[5]));
        }
        BigDecimal numerator = material.add(inputSide).add(outputSide).subtract(byproduct);

        // Rata-rata tertimbang computedHpp per produk (bobot qty output utama).
        Map<String, Object[]> perProduct = new LinkedHashMap<>();
        for (Object[] r : runs) {
            BigDecimal hpp = Money.nz((BigDecimal) r[1]);
            for (Object[] o : outputsByRun.getOrDefault((String) r[0], List.of())) {
                BigDecimal mainQty = Money.nz((BigDecimal) o[2]);
                if (mainQty.signum() <= 0) {
                    continue;
                }
                // [itemId, code, name, unit, runs, totalQty, sumQtyTimesHpp, latestHpp]
                Object[] e = perProduct.computeIfAbsent((String) o[1], k -> new Object[]{k, o[3], o[4], o[5], 0,
                        BigDecimal.ZERO, BigDecimal.ZERO, hpp});
                e[4] = (Integer) e[4] + 1;
                e[5] = ((BigDecimal) e[5]).add(mainQty);
                e[6] = ((BigDecimal) e[6]).add(mainQty.multiply(hpp));
                e[7] = hpp;
            }
        }
        List<Object[]> sorted = new ArrayList<>(perProduct.values());
        sorted.sort((a, b) -> ((BigDecimal) b[5]).compareTo((BigDecimal) a[5]));
        List<Map<String, Object>> top = new ArrayList<>();
        BigDecimal totalMainQty = BigDecimal.ZERO;
        for (Object[] e : sorted.subList(0, Math.min(5, sorted.size()))) {
            BigDecimal totalQty = (BigDecimal) e[5];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", e[0]);
            m.put("itemCode", e[1]);
            m.put("itemName", e[2]);
            m.put("unit", e[3]);
            m.put("runs", e[4]);
            m.put("totalQty", totalQty);
            m.put("avgComputedHpp", totalQty.signum() > 0
                    ? InvJson.toFixed(((BigDecimal) e[6]).divide(totalQty, 10, RoundingMode.HALF_UP), 2) : BigDecimal.ZERO);
            m.put("latestComputedHpp", InvJson.toFixed((BigDecimal) e[7], 2));
            top.add(m);
            totalMainQty = totalMainQty.add(totalQty);
        }
        BigDecimal perKg = totalMainQty.signum() > 0 ? numerator.divide(totalMainQty, 10, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("perProduct", top);
        m.put("totalMaterialCost", InvJson.toFixed(material, 2));
        m.put("totalCostInputSide", InvJson.toFixed(inputSide, 2));
        m.put("totalCostOutputSide", InvJson.toFixed(outputSide, 2));
        m.put("totalByproductValue", InvJson.toFixed(byproduct, 2));
        m.put("totalLoadedHpp", InvJson.toFixed(numerator, 2));
        m.put("avgLoadedHppPerKg", InvJson.toFixed(perKg, 2));
        m.put("runsWithHppCount", runs.size());
        return m;
    }

    /** GET /yield-trend — rata-rata yield per bulan (6 bulan terakhir). */
    @ErpTx(readOnly = true)
    public Map<String, Object> yieldTrend() {
        LocalDate sixMonthsAgo = monthStart(-5);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT to_char(date_trunc('month', date), 'YYYY-MM') AS month, "
                        + "AVG(yield_pct)::float AS avg_yield, COUNT(*) AS run_count FROM production_runs "
                        + "WHERE is_cancelled = false AND yield_pct IS NOT NULL AND date >= :d GROUP BY 1 ORDER BY 1 ASC")
                .setParameter("d", ldt(utc(sixMonthsAgo))).getResultList();
        Map<String, Object[]> byKey = new HashMap<>();
        rows.forEach(r -> byKey.put((String) r[0], r));
        List<Map<String, Object>> months = new ArrayList<>();
        List<BigDecimal> avgs = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate d = monthStart(-i);
            Object[] e = byKey.get(d.format(YM));
            BigDecimal avg = e != null ? InvJson.jsRound(bd(e[1]), 2) : BigDecimal.ZERO;
            long count = e != null ? ((Number) e[2]).longValue() : 0L;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", MONTH_NAMES[d.getMonthValue() - 1]);
            m.put("avgYield", avg);
            m.put("runCount", count);
            months.add(m);
            avgs.add(avg);
            counts.add(count);
        }
        List<BigDecimal> withRuns = new ArrayList<>();
        for (int i = 0; i < avgs.size(); i++) {
            if (counts.get(i) > 0) {
                withRuns.add(avgs.get(i));
            }
        }
        BigDecimal overall = withRuns.isEmpty() ? BigDecimal.ZERO : InvJson.jsRound(InvJson.avg(withRuns), 2);
        BigDecimal last = avgs.get(avgs.size() - 1);
        BigDecimal prev = avgs.get(avgs.size() - 2);
        BigDecimal delta = prev.signum() > 0
                ? InvJson.jsRound(last.subtract(prev).multiply(Money.HUNDRED).divide(prev, 10, RoundingMode.HALF_UP), 2)
                : BigDecimal.ZERO;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("months", months);
        out.put("overallAvg", overall);
        out.put("latest", last);
        out.put("deltaPct", delta);
        return out;
    }

    /**
     * GET /oee — laporan periode (default bulan berjalan). Summary/trend/perShift merata-ratakan persen
     * level run yang dihitung saat submit; perMachine & downtime dari tabelnya sendiri.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> oee(String startDate, String endDate) {
        Instant fs = startDate != null && !startDate.isEmpty() ? ErpDates.tryInstant(startDate) : null;
        Instant fe = endDate != null && !endDate.isEmpty() ? ErpDates.tryInstant(endDate + "T23:59:59.999") : null;
        Instant periodStart = fs != null ? fs : utc(monthStart(0));
        Instant periodEnd = fe != null ? fe : utc(monthStart(1)).minusMillis(1);

        List<Object[]> runs = em.createQuery("select r.id, r.date, r.shift, r.downtimeMin, r.availabilityPct, "
                        + "r.performancePct, r.qualityPct, r.oeePct from ProductionRun r where r.isCancelled = false "
                        + "and r.date >= :from and r.date <= :to", Object[].class)
                .setParameter("from", periodStart).setParameter("to", periodEnd).getResultList();
        List<String> runIds = runs.stream().map(r -> (String) r[0]).toList();
        List<Object[]> machineRows = runIds.isEmpty() ? List.of() : em.createQuery("select m.machineId, m.runMin, "
                        + "m.downtimeMin, m.allocatedQty, m.availabilityPct, m.performancePct, m.qualityPct, m.oeePct, "
                        + "mc.code, mc.name from ProductionRunMachine m left join m.machine mc "
                        + "where m.productionRunId in :ids", Object[].class)
                .setParameter("ids", runIds).getResultList();
        List<Object[]> downtimeRows = runIds.isEmpty() ? List.of() : em.createQuery("select d.category, d.durationMin "
                        + "from ProductionDowntime d where d.productionRunId in :ids", Object[].class)
                .setParameter("ids", runIds).getResultList();

        Pcts total = new Pcts();
        BigDecimal totalDowntime = BigDecimal.ZERO;
        Map<String, Pcts> byDay = new TreeMap<>();
        Map<String, Pcts> byShift = new HashMap<>();
        for (Object[] r : runs) {
            total.add(r, 7, 4, 5, 6);
            totalDowntime = totalDowntime.add(Money.nz((BigDecimal) r[3]));
            byDay.computeIfAbsent(ErpDates.isoDay((Instant) r[1]), k -> new Pcts()).add(r, 7, 4, 5, 6);
            String shift = r[2] != null && !((String) r[2]).trim().isEmpty() ? (String) r[2] : "(Tanpa Shift)";
            byShift.computeIfAbsent(shift, k -> new Pcts()).add(r, 7, 4, 5, 6);
        }
        Set<String> machinesUsed = new HashSet<>();
        Map<String, Object[]> machineMeta = new LinkedHashMap<>();
        Map<String, Pcts> byMachine = new LinkedHashMap<>();
        for (Object[] m : machineRows) {
            machinesUsed.add((String) m[0]);
            // [code, name, totalRunMin, totalDowntimeMin, totalAllocatedQty]
            Object[] meta = machineMeta.computeIfAbsent((String) m[0], k -> new Object[]{
                    m[8] != null ? m[8] : "", m[9] != null ? m[9] : "", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            meta[2] = ((BigDecimal) meta[2]).add(Money.nz((BigDecimal) m[1]));
            meta[3] = ((BigDecimal) meta[3]).add(Money.nz((BigDecimal) m[2]));
            meta[4] = ((BigDecimal) meta[4]).add(Money.nz((BigDecimal) m[3]));
            byMachine.computeIfAbsent((String) m[0], k -> new Pcts()).add(m, 7, 4, 5, 6);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", runs.size());
        summary.put("runsWithOee", total.oee.size());
        total.putAvgs(summary);
        summary.put("totalDowntimeMin", InvJson.jsRound(totalDowntime, 1));
        summary.put("machinesUsed", machinesUsed.size());

        List<Map<String, Object>> trend = new ArrayList<>();
        byDay.forEach((day, p) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day);
            m.put("runs", p.runs);
            p.putAvgs(m);
            trend.add(m);
        });

        List<Map<String, Object>> perMachine = new ArrayList<>();
        byMachine.forEach((id, p) -> {
            Object[] meta = machineMeta.get(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("machineId", id);
            m.put("code", meta[0]);
            m.put("name", meta[1]);
            m.put("runs", p.runs);
            p.putAvgs(m);
            m.put("totalRunMin", InvJson.jsRound((BigDecimal) meta[2], 1));
            m.put("totalDowntimeMin", InvJson.jsRound((BigDecimal) meta[3], 1));
            m.put("totalAllocatedQty", InvJson.jsRound((BigDecimal) meta[4], 1));
            perMachine.add(m);
        });
        perMachine.sort((a, b) -> ((BigDecimal) b.get("avgOee")).compareTo((BigDecimal) a.get("avgOee")));

        Collator coll = Collator.getInstance(java.util.Locale.forLanguageTag("id"));
        List<Map<String, Object>> perShift = new ArrayList<>();
        byShift.forEach((shift, p) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("shift", shift);
            m.put("runs", p.runs);
            p.putAvgs(m);
            perShift.add(m);
        });
        perShift.sort((a, b) -> coll.compare((String) a.get("shift"), (String) b.get("shift")));

        // Downtime per kategori (Pareto, urut total menit menurun).
        Map<String, Object[]> dt = new LinkedHashMap<>();
        for (Object[] d : downtimeRows) {
            String cat = d[0] != null && !((String) d[0]).trim().isEmpty() ? (String) d[0] : "Lain";
            Object[] e = dt.computeIfAbsent(cat, k -> new Object[]{BigDecimal.ZERO, 0});
            e[0] = ((BigDecimal) e[0]).add(Money.nz((BigDecimal) d[1]));
            e[1] = (Integer) e[1] + 1;
        }
        List<Map<String, Object>> downtime = new ArrayList<>();
        dt.forEach((cat, e) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", cat);
            m.put("totalMin", InvJson.jsRound((BigDecimal) e[0], 1));
            m.put("count", e[1]);
            downtime.add(m);
        });
        downtime.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("totalMin")).reversed());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("trend", trend);
        out.put("perMachine", perMachine);
        out.put("perShift", perShift);
        out.put("downtimeByCategory", downtime);
        return out;
    }

    /** Akumulator persen OEE/availability/performance/quality (nilai null diabaikan). */
    private static final class Pcts {
        final List<BigDecimal> oee = new ArrayList<>();
        final List<BigDecimal> avail = new ArrayList<>();
        final List<BigDecimal> perf = new ArrayList<>();
        final List<BigDecimal> qual = new ArrayList<>();
        int runs;

        void add(Object[] row, int oeeIdx, int availIdx, int perfIdx, int qualIdx) {
            runs++;
            addIf(oee, row[oeeIdx]);
            addIf(avail, row[availIdx]);
            addIf(perf, row[perfIdx]);
            addIf(qual, row[qualIdx]);
        }

        private static void addIf(List<BigDecimal> list, Object v) {
            if (v != null) {
                list.add((BigDecimal) v);
            }
        }

        void putAvgs(Map<String, Object> m) {
            m.put("avgOee", InvJson.jsRound(InvJson.avg(oee), 1));
            m.put("avgAvailability", InvJson.jsRound(InvJson.avg(avail), 1));
            m.put("avgPerformance", InvJson.jsRound(InvJson.avg(perf), 1));
            m.put("avgQuality", InvJson.jsRound(InvJson.avg(qual), 1));
        }
    }
}
