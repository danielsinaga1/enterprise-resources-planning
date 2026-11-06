package id.neravoxa.erp.production;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.JournalItem;
import id.neravoxa.erp.entity.ProductionDowntime;
import id.neravoxa.erp.entity.ProductionRun;
import id.neravoxa.erp.entity.ProductionRunCost;
import id.neravoxa.erp.entity.ProductionRunItem;
import id.neravoxa.erp.entity.ProductionRunMachine;
import id.neravoxa.erp.entity.ProductionRunProcess;
import id.neravoxa.erp.entity.StockMovementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Daftar, ringkasan KPI dan detail Data Produksi — port GET di {@code routes/inventory/productionRuns.ts}
 * + {@code services/productionRunList.ts}. Daftar & ringkasan memakai filter yang sama
 * ({@link #buildWhere}) agar kartu KPI menggambarkan baris yang tampil.
 */
@Service
public class ErpProductionRunQueryService {

    static final List<String> SORT_FIELDS = List.of("date", "runNumber", "yieldPct");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);
    static final List<String> STATUS_FILTERS = List.of("active", "cancelled");

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Kondisi JPQL (alias {@code r} = ProductionRun) + parameternya. */
    record RunFilter(List<String> conds, Map<String, Object> params) {
        String where(String... extra) {
            List<String> all = new ArrayList<>(conds);
            all.addAll(List.of(extra));
            return all.isEmpty() ? "" : " where " + String.join(" and ", all);
        }

        void bind(jakarta.persistence.Query q) {
            params.forEach(q::setParameter);
        }
    }

    /**
     * Filter: status (active | cancelled), machineId (mesin tunggal), startDate/endDate (YYYY-MM-DD,
     * rentang hari bisnis), search (nomor run, nomor referensi, catatan, nama/kode item input/output).
     */
    RunFilter buildWhere(Map<String, String> query) {
        List<String> conds = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        String status = ListQuery.str(query, "status");
        if (status != null) {
            if (!STATUS_FILTERS.contains(status)) {
                throw ErpException.business("Status proses produksi tidak dikenal.");
            }
            conds.add("r.isCancelled = :fCancelled");
            params.put("fCancelled", "cancelled".equals(status));
        }
        String machineId = ListQuery.str(query, "machineId");
        if (machineId != null) {
            conds.add("r.machineId = :fMachine");
            params.put("fMachine", machineId);
        }
        String startDate = ListQuery.dateParam(query, "startDate", "Format tanggal harus YYYY-MM-DD.");
        String endDate = ListQuery.dateParam(query, "endDate", "Format tanggal harus YYYY-MM-DD.");
        if (startDate != null || endDate != null) {
            Instant[] range = ErpDates.localDayRange(startDate, endDate);
            if (range[0] != null) {
                conds.add("r.date >= :fGte");
                params.put("fGte", range[0]);
            }
            if (range[1] != null) {
                conds.add("r.date < :fLt");
                params.put("fLt", range[1]);
            }
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            conds.add("(lower(r.runNumber) like :fS or lower(r.referenceNumber) like :fS or lower(r.notes) like :fS "
                    + "or exists (select 1 from ProductionRunItem sx join sx.item si where sx.productionRunId = r.id "
                    + "and (lower(si.name) like :fS or lower(si.code) like :fS)))");
            params.put("fS", ErpQuery.contains(search));
        }
        return new RunFilter(conds, params);
    }

    /** Urutan sort: runNumber saja; yield (null terakhir) / tanggal + runNumber sebagai pemecah seri. */
    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        if ("runNumber".equals(sort.field())) {
            return "r.runNumber " + dir;
        }
        if ("yieldPct".equals(sort.field())) {
            return "r.yieldPct " + dir + " nulls last, r.runNumber " + dir;
        }
        return "r.date " + dir + ", r.runNumber " + dir;
    }

    /** GET / — daftar berpaginasi (limit ≤ 200); tanpa {@code sort} urut tanggal terbaru. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        RunFilter f = buildWhere(query);
        String order = query.containsKey("sort")
                ? orderBy(ListQuery.sort(query.get("sort"), SORT_FIELDS, DEFAULT_SORT))
                : "r.date desc, r.createdAt desc";
        ErpQuery<ProductionRun> q = ErpQuery.of(em, ProductionRun.class, "r")
                .fetch("left join fetch r.machine", "left join fetch r.createdBy");
        if (!f.conds().isEmpty()) {
            Object[] nameValues = new Object[f.params().size() * 2];
            int i = 0;
            for (Map.Entry<String, Object> e : f.params().entrySet()) {
                nameValues[i++] = e.getKey();
                nameValues[i++] = e.getValue();
            }
            q.where(String.join(" and ", f.conds()), nameValues);
        }
        q.orderBy(order);
        long total = q.count();
        List<ProductionRun> runs = q.list(paging.skip(), paging.limit());

        Map<String, List<Map<String, Object>>> itemsByRun = new HashMap<>();
        if (!runs.isEmpty()) {
            for (ProductionRunItem x : em.createQuery("select x from ProductionRunItem x join fetch x.item "
                            + "where x.productionRunId in :ids order by x.createdAt asc, x.id asc", ProductionRunItem.class)
                    .setParameter("ids", runs.stream().map(ProductionRun::getId).toList()).getResultList()) {
                Map<String, Object> j = ProdJson.scalars(x);
                j.put("item", ProdJson.pick(x.getItem(), "id", "name", "unit", "code"));
                itemsByRun.computeIfAbsent(x.getProductionRunId(), k -> new ArrayList<>()).add(j);
            }
        }
        Map<String, Map<String, Object>> tollById = tollOrders(runs.stream().map(ProductionRun::getTollOrderId).toList());
        List<Map<String, Object>> data = new ArrayList<>();
        for (ProductionRun r : runs) {
            Map<String, Object> m = ProdJson.scalars(r);
            m.put("tollOrder", r.getTollOrderId() == null ? null : tollById.get(r.getTollOrderId()));
            m.put("items", itemsByRun.getOrDefault(r.getId(), List.of()));
            m.put("machine", ProdJson.pick(r.getMachine(), "id", "code", "name"));
            m.put("createdBy", ProdJson.pick(r.getCreatedBy(), "id", "fullName"));
            data.add(m);
        }
        return ListQuery.result(data, total, paging);
    }

    /** GET /summary — total KPI untuk filter daftar (run aktif saja untuk kuantitas & yield). */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        RunFilter f = buildWhere(query);
        TypedQuery<Object[]> countsQ = em.createQuery("select r.isCancelled, count(r) from ProductionRun r" + f.where()
                + " group by r.isCancelled", Object[].class);
        f.bind(countsQ);
        long count = 0;
        long cancelledCount = 0;
        for (Object[] r : countsQ.getResultList()) {
            long n = ((Number) r[1]).longValue();
            count += n;
            if (Boolean.TRUE.equals(r[0])) {
                cancelledCount += n;
            }
        }

        String activeSub = "select r.id from ProductionRun r" + f.where("r.isCancelled = false");
        TypedQuery<Object[]> linesQ = em.createQuery("select x.lineType, x.isByProduct, sum(x.quantity) from ProductionRunItem x "
                + "where x.productionRunId in (" + activeSub + ") group by x.lineType, x.isByProduct", Object[].class);
        f.bind(linesQ);
        BigDecimal input = BigDecimal.ZERO;
        BigDecimal output = BigDecimal.ZERO;
        BigDecimal byproduct = BigDecimal.ZERO;
        for (Object[] l : linesQ.getResultList()) {
            BigDecimal q = Money.nz((BigDecimal) l[2]);
            if ("Input".equals(l[0])) {
                input = input.add(q);
                continue;
            }
            output = output.add(q);
            if ("ByProduct".equals(l[0]) || Boolean.TRUE.equals(l[1])) {
                byproduct = byproduct.add(q);
            }
        }

        TypedQuery<Object[]> runsQ = em.createQuery("select r.id, r.yieldPct from ProductionRun r"
                + f.where("r.isCancelled = false"), Object[].class);
        f.bind(runsQ);
        List<Object[]> runRows = runsQ.getResultList();
        TypedQuery<Object[]> inputsQ = em.createQuery("select x.productionRunId, sum(x.quantity) from ProductionRunItem x "
                + "where x.lineType = 'Input' and x.productionRunId in (" + activeSub + ") group by x.productionRunId", Object[].class);
        f.bind(inputsQ);
        Map<String, BigDecimal> inputByRun = new HashMap<>();
        for (Object[] r : inputsQ.getResultList()) {
            inputByRun.put((String) r[0], (BigDecimal) r[1]);
        }
        // Rata-rata yield tertimbang input (Σ yield × input ÷ Σ input, hanya run ber-yield).
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (Object[] r : runRows) {
            BigDecimal y = (BigDecimal) r[1];
            if (y == null) {
                continue;
            }
            BigDecimal w = Money.nz(inputByRun.get((String) r[0]));
            if (w.signum() <= 0) {
                continue;
            }
            weighted = weighted.add(y.multiply(w));
            weight = weight.add(w);
        }

        TypedQuery<String> unitsQ = em.createQuery("select distinct i.unit from InventoryItem i where exists ("
                + "select 1 from ProductionRunItem x where x.itemId = i.id and x.productionRunId in (" + activeSub + "))", String.class);
        f.bind(unitsQ);
        List<String> units = unitsQ.getResultList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", count);
        out.put("activeCount", count - cancelledCount);
        out.put("cancelledCount", cancelledCount);
        out.put("inputQty", ProdJson.plain(input.setScale(3, RoundingMode.HALF_UP)));
        out.put("outputQty", ProdJson.plain(output.setScale(3, RoundingMode.HALF_UP)));
        out.put("byproductQty", ProdJson.plain(byproduct.setScale(3, RoundingMode.HALF_UP)));
        out.put("yieldPct", weight.signum() > 0
                ? ProdJson.plain(weighted.divide(weight, ProductionOee.MC).setScale(2, RoundingMode.HALF_UP)) : null);
        out.put("unit", new HashSet<>(units).size() == 1 ? units.get(0) : null);
        return out;
    }

    /**
     * GET /:id — detail lengkap untuk drawer + jurnal aktif terbaru (edit membuat revisi
     * {@code JV-<runNumber>-R<n>} dan membatalkan yang lama) + ringkasan nilai GL nyata dari
     * stock_movements (bukan WAC saat ini).
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> detail(String id) {
        List<ProductionRun> found = em.createQuery("select r from ProductionRun r left join fetch r.machine "
                + "left join fetch r.createdBy where r.id = :id", ProductionRun.class).setParameter("id", id).getResultList();
        if (found.isEmpty()) {
            throw ErpException.notFound(ErpProductionRunPostingSupport.RUN_NOT_FOUND);
        }
        ProductionRun run = found.get(0);

        List<ProductionRunItem> items = em.createQuery("select x from ProductionRunItem x join fetch x.item "
                        + "left join fetch x.warehouse left join fetch x.sourceMovement where x.productionRunId = :id "
                        + "order by x.createdAt asc, x.id asc", ProductionRunItem.class)
                .setParameter("id", id).getResultList();
        List<Map<String, Object>> itemRows = new ArrayList<>();
        Set<String> mainItemIds = new HashSet<>();
        for (ProductionRunItem x : items) {
            Map<String, Object> j = ProdJson.scalars(x);
            j.put("item", ProdJson.pick(x.getItem(), "id", "name", "unit", "code", "averageCost"));
            j.put("warehouse", ProdJson.pick(x.getWarehouse(), "id", "code", "name"));
            j.put("sourceMovement", ProdJson.pick(x.getSourceMovement(), "id", "movementNumber", "date", "quantity",
                    "unitCost", "referenceType", "referenceNumber"));
            itemRows.add(j);
            if ("Output".equals(x.getLineType()) && !Boolean.TRUE.equals(x.getIsByProduct())) {
                mainItemIds.add(x.getItemId());
            }
        }

        List<Map<String, Object>> costRows = new ArrayList<>();
        for (ProductionRunCost c : em.createQuery("select c from ProductionRunCost c left join fetch c.costAccount "
                        + "left join fetch c.standard where c.productionRunId = :id order by c.createdAt asc, c.id asc",
                ProductionRunCost.class).setParameter("id", id).getResultList()) {
            Map<String, Object> j = ProdJson.scalars(c);
            j.put("costAccount", ProdJson.pick(c.getCostAccount(), "id", "accountNumber", "name"));
            j.put("standard", ProdJson.pick(c.getStandard(), "id", "name"));
            costRows.add(j);
        }

        List<Map<String, Object>> processRows = new ArrayList<>();
        for (ProductionRunProcess p : em.createQuery("select p from ProductionRunProcess p join fetch p.process pr "
                        + "where p.productionRunId = :id order by pr.sortOrder asc", ProductionRunProcess.class)
                .setParameter("id", id).getResultList()) {
            Map<String, Object> j = ProdJson.scalars(p);
            j.put("process", ProdJson.pick(p.getProcess(), "id", "name"));
            processRows.add(j);
        }

        List<Map<String, Object>> machineRows = new ArrayList<>();
        for (ProductionRunMachine m : em.createQuery("select m from ProductionRunMachine m join fetch m.machine "
                        + "where m.productionRunId = :id order by m.createdAt asc", ProductionRunMachine.class)
                .setParameter("id", id).getResultList()) {
            Map<String, Object> j = ProdJson.scalars(m);
            j.put("machine", ProdJson.pick(m.getMachine(), "id", "code", "name", "unit", "idealRatePerHour"));
            machineRows.add(j);
        }

        List<Map<String, Object>> downtimeRows = new ArrayList<>();
        for (ProductionDowntime d : em.createQuery("select d from ProductionDowntime d left join fetch d.machine "
                        + "where d.productionRunId = :id order by d.createdAt asc", ProductionDowntime.class)
                .setParameter("id", id).getResultList()) {
            Map<String, Object> j = ProdJson.scalars(d);
            j.put("machine", ProdJson.pick(d.getMachine(), "id", "code", "name"));
            downtimeRows.add(j);
        }

        Map<String, Object> out = ProdJson.scalars(run);
        out.put("tollOrder", run.getTollOrderId() == null ? null
                : tollOrders(List.of(run.getTollOrderId())).get(run.getTollOrderId()));
        out.put("items", itemRows);
        out.put("runCosts", costRows);
        out.put("runProcesses", processRows);
        out.put("machine", ProdJson.pick(run.getMachine(), "id", "code", "name", "unit", "idealRatePerHour"));
        out.put("runMachines", machineRows);
        out.put("downtimes", downtimeRows);
        out.put("createdBy", ProdJson.pick(run.getCreatedBy(), "id", "fullName"));
        out.put("journal", activeJournal("JV-" + run.getRunNumber()));
        out.put("glSummary", glSummary(id, mainItemIds));
        return out;
    }

    /** Ringkasan Order Jasa Maklon {id, orderNumber, partyId, partyName, byProductOwner, status} per id. */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> tollOrders(List<String> ids) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        List<String> wanted = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return out;
        }
        List<Object[]> rows = em.createNativeQuery("SELECT o.id, o.order_number, o.party_id, p.name, o.by_product_owner, o.status "
                + "FROM toll_orders o JOIN parties p ON p.id = o.party_id WHERE o.id IN (:ids)").setParameter("ids", wanted)
                .getResultList();
        for (Object[] r : rows) {
            out.put((String) r[0], ErpProductionRunPostingSupport.map("id", r[0], "orderNumber", r[1], "partyId", r[2],
                    "partyName", r[3], "byProductOwner", r[4], "status", r[5]));
        }
        return out;
    }

    /** Jurnal aktif terbaru berawalan {@code prefix} + baris (+ akun ringkas); null bila tidak ada. */
    private Map<String, Object> activeJournal(String prefix) {
        List<JournalEntry> rows = em.createQuery("select j from JournalEntry j where j.entryNumber like :p "
                        + "and j.status <> :c order by j.entryNumber desc", JournalEntry.class)
                .setParameter("p", ErpProductionRunPostingSupport.likePrefix(prefix))
                .setParameter("c", DocumentStatus.Cancelled).setMaxResults(1).getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        JournalEntry je = rows.get(0);
        List<Map<String, Object>> items = new ArrayList<>();
        for (JournalItem ji : em.createQuery("select ji from JournalItem ji join fetch ji.account "
                + "where ji.journalEntryId = :id", JournalItem.class).setParameter("id", je.getId()).getResultList()) {
            Map<String, Object> j = ProdJson.scalars(ji);
            j.put("account", ProdJson.pick(ji.getAccount(), "id", "accountNumber", "name"));
            items.add(j);
        }
        Map<String, Object> m = ProdJson.scalars(je);
        m.put("items", items);
        return m;
    }

    /**
     * Ringkasan nilai GL nyata dari stock_movements: bahan keluar (Out) vs produk masuk (In) per output
     * utama / produk samping. rawMaterialPerKg = modal bahan baku murni = nilai bahan ÷ kg output utama.
     * Aritmetika angka JS (double) seperti sumber.
     */
    private Map<String, Object> glSummary(String runId, Set<String> mainItemIds) {
        List<Object[]> mvs = em.createQuery("select m.movementType, m.itemId, m.quantity, m.totalValue from StockMovement m "
                + "where m.referenceId = :id and m.isCancelled = false", Object[].class).setParameter("id", runId).getResultList();
        if (mvs.isEmpty()) {
            return null;
        }
        double inputQty = 0;
        double inputValue = 0;
        double mainQty = 0;
        double mainValue = 0;
        double bpQty = 0;
        double bpValue = 0;
        for (Object[] m : mvs) {
            double q = Money.nz((BigDecimal) m[2]).doubleValue();
            double v = Money.nz((BigDecimal) m[3]).doubleValue();
            if (m[0] == StockMovementType.Out) {
                inputQty += q;
                inputValue += v;
            } else if (mainItemIds.contains((String) m[1])) {
                mainQty += q;
                mainValue += v;
            } else {
                bpQty += q;
                bpValue += v;
            }
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("inputQty", ProdJson.jsonNumber(inputQty));
        s.put("inputValue", ProdJson.jsonNumber(inputValue));
        s.put("mainQty", ProdJson.jsonNumber(mainQty));
        s.put("mainValue", ProdJson.jsonNumber(mainValue));
        s.put("byproductQty", ProdJson.jsonNumber(bpQty));
        s.put("byproductValue", ProdJson.jsonNumber(bpValue));
        // + = kredit ke 5.1 (biaya konversi dibebankan), − = debit (rugi produksi)
        s.put("conversionDiff", ProdJson.jsonNumber(mainValue + bpValue - inputValue));
        s.put("rawMaterialPerKg", mainQty > 0 ? ProdJson.jsonNumber(inputValue / mainQty) : null);
        s.put("glPerKg", mainQty > 0 ? ProdJson.jsonNumber(mainValue / mainQty) : null);
        s.put("yieldPct", inputQty > 0 ? ProdJson.jsonNumber((mainQty / inputQty) * 100) : null);
        return s;
    }
}
