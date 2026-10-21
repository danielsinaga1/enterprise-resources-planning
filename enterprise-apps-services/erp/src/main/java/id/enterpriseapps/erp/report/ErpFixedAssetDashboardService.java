package id.enterpriseapps.erp.report;

import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /dashboard/fixed-assets — satu request untuk keempat widget aset tetap (port
 * {@code routes/dashboard/fixedAssets.ts}): ringkasan, per kategori, aset terbesar, penyusutan jatuh tempo.
 */
@Service
public class ErpFixedAssetDashboardService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private record Asset(String id, String assetNumber, String name, String category, Instant acquisitionDate,
                         BigDecimal cost, BigDecimal accumulated, BigDecimal bookValue) {
    }

    @ErpTx(readOnly = true)
    public Map<String, Object> dashboard() {
        LocalDate today = ReportDates.localDay(Instant.now());
        int y = today.getYear();
        int m0 = today.getMonthValue() - 1;
        Instant monthStart = ReportDates.localDate(y, m0, 1);
        // new Date(y, m + 1, 0, 23, 59, 59) = hari terakhir bulan ini pukul 23:59:59 lokal.
        Instant monthEnd = ReportDates.localDateTime(y, m0 + 1, 0, 23, 59, 59);
        Instant yearStart = ReportDates.localDate(y, 0, 1);

        List<Asset> assets = new ArrayList<>();
        for (Object[] r : em.createQuery("select a.id, a.assetNumber, a.name, a.category, a.acquisitionDate, "
                + "a.acquisitionCost, a.accumulatedDepreciation, a.bookValue from FixedAsset a where a.status = 'Active'",
                Object[].class).getResultList()) {
            assets.add(new Asset((String) r[0], (String) r[1], (String) r[2], (String) r[3], (Instant) r[4],
                    Money.nz((BigDecimal) r[5]), Money.nz((BigDecimal) r[6]), Money.nz((BigDecimal) r[7])));
        }
        BigDecimal totalCost = Money.sum(assets, Asset::cost);
        BigDecimal totalAccumulated = Money.sum(assets, Asset::accumulated);
        BigDecimal totalBookValue = Money.sum(assets, Asset::bookValue);

        Object[] thisMonth = em.createQuery("select coalesce(sum(d.amount), 0), count(d) from DepreciationEntry d "
                        + "where d.periodDate >= :s and d.periodDate <= :e", Object[].class)
                .setParameter("s", monthStart).setParameter("e", monthEnd).getSingleResult();
        BigDecimal ytd = Money.nz(em.createQuery("select sum(d.amount) from DepreciationEntry d "
                        + "where d.periodDate >= :s and d.periodDate <= :e and d.isPosted = true", BigDecimal.class)
                .setParameter("s", yearStart).setParameter("e", monthEnd).getSingleResult());
        List<Object[]> upcoming = em.createQuery("select d.id, fa.assetNumber, fa.name, fa.category, d.amount, d.periodDate "
                        + "from DepreciationEntry d join d.fixedAsset fa where d.periodDate >= :s and d.isPosted = false "
                        + "order by d.periodDate asc", Object[].class)
                .setParameter("s", monthStart).setMaxResults(5).getResultList();

        BigDecimal depThisMonth = ReportRows.dec(thisMonth[0]);
        long depThisMonthEntries = ReportRows.lng(thisMonth[1]);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", assets.size());
        summary.put("totalCost", totalCost);
        summary.put("totalAccumulated", totalAccumulated);
        summary.put("totalBookValue", totalBookValue);
        summary.put("depreciationThisMonth", depThisMonth);
        summary.put("depreciationThisMonthEntries", depThisMonthEntries);
        summary.put("depreciationYTD", ytd);

        Map<String, BigDecimal[]> byCat = new LinkedHashMap<>();
        for (Asset a : assets) {
            BigDecimal[] e = byCat.computeIfAbsent(a.category(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO});
            e[0] = e[0].add(BigDecimal.ONE);
            e[1] = e[1].add(a.cost());
            e[2] = e[2].add(a.bookValue());
        }
        List<Map<String, Object>> byCategory = new ArrayList<>();
        byCat.forEach((category, e) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", category);
            m.put("count", e[0].intValue());
            m.put("totalCost", e[1]);
            m.put("totalBookValue", e[2]);
            m.put("pct", totalBookValue.signum() > 0 ? ReportRows.pct(e[2], totalBookValue, 1) : BigDecimal.ZERO);
            byCategory.add(m);
        });
        byCategory.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("totalBookValue")).reversed());

        List<Asset> top = new ArrayList<>(assets);
        top.sort(Comparator.comparing(Asset::bookValue).reversed());
        List<Map<String, Object>> topAssets = new ArrayList<>();
        for (Asset a : top.subList(0, Math.min(10, top.size()))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id());
            m.put("assetNumber", a.assetNumber());
            m.put("name", a.name());
            m.put("category", a.category());
            m.put("acquisitionDate", a.acquisitionDate());
            m.put("acquisitionCost", a.cost());
            m.put("bookValue", a.bookValue());
            m.put("accumulatedDepreciation", a.accumulated());
            m.put("accumPct", a.cost().signum() > 0 ? ReportRows.pct(a.accumulated(), a.cost(), 1) : BigDecimal.ZERO);
            topAssets.add(m);
        }

        List<Map<String, Object>> upcomingRows = new ArrayList<>();
        for (Object[] r : upcoming) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("assetNumber", r[1]);
            m.put("assetName", r[2]);
            m.put("category", r[3]);
            m.put("amount", Money.nz((BigDecimal) r[4]));
            m.put("dueDate", r[5]);
            upcomingRows.add(m);
        }
        Map<String, Object> depreciationDue = new LinkedHashMap<>();
        depreciationDue.put("thisMonthAmount", depThisMonth);
        depreciationDue.put("thisMonthEntries", depThisMonthEntries);
        depreciationDue.put("upcoming", upcomingRows);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("byCategory", byCategory);
        out.put("topAssets", topAssets);
        out.put("depreciationDue", depreciationDue);
        return out;
    }
}
