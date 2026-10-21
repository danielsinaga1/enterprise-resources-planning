package id.enterpriseapps.erp.eam.vehicle;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.GaAsset;
import id.enterpriseapps.erp.entity.GaMaterialRequestStatus;
import id.enterpriseapps.erp.entity.GaVehicleData;
import id.enterpriseapps.erp.entity.GaVehicleInsuranceStatus;
import id.enterpriseapps.erp.entity.GaVehicleTaxStatus;
import id.enterpriseapps.erp.entity.GaVehicleTireStatus;
import id.enterpriseapps.erp.entity.GaWorkOrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laporan kendaraan (per aset) dan laporan armada — agregat biaya (TCO), kepatuhan dokumen dan kondisi,
 * read-only. Laporan armada mengagregasi di memori dari satu query batch per tabel sehingga jumlah query
 * tetap berapa pun ukuran armadanya. Angka dihitung double seperti {@code Number(...)} di sumber.
 */
@Service
public class ErpFleetReportService {

    static final List<String> COST_CATEGORIES = List.of("service", "fuel", "tax", "insurance", "incident", "workorder");
    private static final double MS_PER_MONTH = 30.44 * 24 * 3600 * 1000;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    record Period(Instant from, Instant to, long months) {
        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", from);
            m.put("to", to);
            m.put("months", months);
            return m;
        }

        boolean contains(Instant d) {
            return d != null && !d.isBefore(from) && !d.isAfter(to);
        }
    }

    /** {@code ?from=&to=} (YYYY-MM-DD). Default: 12 bulan terakhir s.d. akhir bulan berjalan (UTC). */
    static Period parsePeriod(String fromRaw, String toRaw) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant toParsed = fromNonEmpty(toRaw);
        LocalDate toDay = toParsed != null ? toParsed.atZone(ZoneOffset.UTC).toLocalDate()
                : today.withDayOfMonth(today.lengthOfMonth());
        Instant to = toDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
        Instant fromParsed = fromNonEmpty(fromRaw);
        LocalDate fromDay = fromParsed != null ? fromParsed.atZone(ZoneOffset.UTC).toLocalDate()
                : toDay.withDayOfMonth(1).minusMonths(11);
        Instant from = fromDay.atStartOfDay(ZoneOffset.UTC).toInstant();
        long months = Math.max(1, Math.round((to.toEpochMilli() - from.toEpochMilli()) / MS_PER_MONTH));
        return new Period(from, to, months);
    }

    private static Instant fromNonEmpty(String raw) {
        return raw == null || raw.isEmpty() ? null : ErpDates.tryInstant(raw);
    }

    static Map<String, Double> emptyBreakdown() {
        Map<String, Double> m = new LinkedHashMap<>();
        COST_CATEGORIES.forEach(k -> m.put(k, 0d));
        return m;
    }

    static Map<String, Object> breakdownJson(Map<String, Double> b) {
        Map<String, Object> m = new LinkedHashMap<>();
        b.forEach((k, v) -> m.put(k, VehicleJs.num(v)));
        return m;
    }

    record TrendRow(String month, String category, double amount) {
    }

    /** Trend bulanan kontinu (bulan tanpa data tetap muncul dengan 0). */
    static List<Map<String, Object>> monthlyTrend(Period period, List<TrendRow> rows) {
        Map<String, Map<String, Double>> buckets = new LinkedHashMap<>();
        LocalDate cursor = period.from().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        LocalDate last = period.to().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        while (!cursor.isAfter(last)) {
            Map<String, Double> b = emptyBreakdown();
            b.put("total", 0d);
            buckets.put(cursor.getYear() + "-" + String.format("%02d", cursor.getMonthValue()), b);
            cursor = cursor.plusMonths(1);
        }
        for (TrendRow r : rows) {
            Map<String, Double> b = buckets.get(r.month());
            if (b == null) {
                continue;
            }
            b.merge(r.category(), r.amount(), Double::sum);
            b.merge("total", r.amount(), Double::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        buckets.forEach((month, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", month);
            v.forEach((k, x) -> m.put(k, VehicleJs.num(x)));
            out.add(m);
        });
        return out;
    }

    /** Status kepatuhan dokumen: EXPIRED / EXPIRING (&lt;=30 hari) / OK / NONE. */
    static String complianceState(Instant due, Instant now) {
        if (due == null) {
            return "NONE";
        }
        long days = VehicleJs.daysUntil(due, now);
        if (days < 0) {
            return "EXPIRED";
        }
        if (days <= 30) {
            return "EXPIRING";
        }
        return "OK";
    }

    private static Long daysUntil(Instant d, Instant now) {
        return d == null ? null : VehicleJs.daysUntil(d, now);
    }

    private static double sum(Map<String, Double> b) {
        double s = 0;
        for (String k : COST_CATEGORIES) {
            s += b.get(k);
        }
        return s;
    }

    // Laporan per kendaraan

    @ErpTx(readOnly = true)
    public Map<String, Object> assetReport(String assetId, String fromRaw, String toRaw) {
        Period period = parsePeriod(fromRaw, toRaw);
        Instant now = Instant.now();
        List<GaAsset> found = em.createQuery("select a from GaAsset a left join fetch a.category where a.id = :id",
                GaAsset.class).setParameter("id", assetId).getResultList();
        if (found.isEmpty()) {
            throw ErpException.notFound("Aset tidak ditemukan.");
        }
        GaAsset asset = found.get(0);
        List<GaVehicleData> vds = em.createQuery("select v from GaVehicleData v where v.assetId = :a", GaVehicleData.class)
                .setParameter("a", assetId).getResultList();
        GaVehicleData vd = vds.isEmpty() ? null : vds.get(0);

        List<Object[]> services = em.createQuery("select l.tanggal, l.biaya, l.jenisService, l.odometer "
                        + "from GaVehicleServiceLog l where l.assetId = :a and l.tanggal >= :f and l.tanggal <= :t "
                        + "order by l.tanggal asc", Object[].class)
                .setParameter("a", assetId).setParameter("f", period.from()).setParameter("t", period.to()).getResultList();
        List<Object[]> fuels = em.createQuery("select f.tanggal, f.totalCost, f.liters, f.odometer "
                        + "from GaVehicleFuelLog f where f.assetId = :a and f.tanggal >= :f and f.tanggal <= :t "
                        + "order by f.tanggal asc", Object[].class)
                .setParameter("a", assetId).setParameter("f", period.from()).setParameter("t", period.to()).getResultList();
        List<Object[]> taxes = em.createQuery("select t.type, t.amount, t.paidAmount, t.paidAt, t.dueDate, t.status, "
                        + "t.periodYear from GaVehicleTax t where t.assetId = :a order by t.dueDate desc", Object[].class)
                .setParameter("a", assetId).getResultList();
        List<Object[]> insurances = em.createQuery("select i.type, i.provider, i.premium, i.startDate, i.endDate, i.status "
                        + "from GaVehicleInsurance i where i.assetId = :a order by i.endDate desc", Object[].class)
                .setParameter("a", assetId).getResultList();
        List<Object[]> incidents = em.createQuery("select i.tanggal, i.severity, i.status, i.actualCost, i.estimatedCost, "
                        + "i.description from GaVehicleIncident i where i.assetId = :a order by i.tanggal desc", Object[].class)
                .setParameter("a", assetId).getResultList();
        List<Object[]> lastInspection = em.createQuery("select i.tanggal, i.overallResult, i.odometer, i.notes "
                        + "from GaVehicleInspection i where i.assetId = :a order by i.tanggal desc", Object[].class)
                .setParameter("a", assetId).setMaxResults(1).getResultList();
        List<Object[]> activeTires = em.createQuery("select t.position, t.brand, t.serialNumber, t.installedAt, "
                        + "t.installedOdometer from GaVehicleTire t where t.assetId = :a and t.status = :st", Object[].class)
                .setParameter("a", assetId).setParameter("st", GaVehicleTireStatus.ACTIVE).getResultList()
                .stream().sorted(Comparator.comparing(r -> ((Enum<?>) r[0]).ordinal())).toList();
        List<Object[]> lastOil = em.createQuery("select o.tanggal, o.odometer, o.nextOdometerDue, o.brand, o.volume "
                        + "from GaVehicleOilLog o where o.assetId = :a order by o.tanggal desc", Object[].class)
                .setParameter("a", assetId).setMaxResults(1).getResultList();

        // TCO periode
        Map<String, Double> breakdown = emptyBreakdown();
        List<TrendRow> trend = new ArrayList<>();
        for (Object[] s : services) {
            double amount = VehicleJs.dec((BigDecimal) s[1]);
            breakdown.merge("service", amount, Double::sum);
            if (amount != 0) {
                trend.add(new TrendRow(VehicleJs.monthKey((Instant) s[0]), "service", amount));
            }
        }
        for (Object[] f : fuels) {
            double amount = VehicleJs.dec((BigDecimal) f[1]);
            breakdown.merge("fuel", amount, Double::sum);
            if (amount != 0) {
                trend.add(new TrendRow(VehicleJs.monthKey((Instant) f[0]), "fuel", amount));
            }
        }
        // Pajak: paidAt = tanggal biaya (hanya yang benar-benar dibayar di periode).
        int taxCount = 0;
        for (Object[] t : taxes) {
            Instant paidAt = (Instant) t[3];
            if (!period.contains(paidAt)) {
                continue;
            }
            taxCount++;
            double amount = VehicleTaxCost.effective((BigDecimal) t[1], (BigDecimal) t[2], paidAt, name(t[5]));
            breakdown.merge("tax", amount, Double::sum);
            if (amount != 0) {
                trend.add(new TrendRow(VehicleJs.monthKey(paidAt), "tax", amount));
            }
        }
        // Asuransi: premi diakui di bulan startDate polis yang mulai dalam periode.
        int insuranceCount = 0;
        for (Object[] i : insurances) {
            Instant start = (Instant) i[3];
            if (!period.contains(start)) {
                continue;
            }
            insuranceCount++;
            double amount = VehicleJs.dec((BigDecimal) i[2]);
            breakdown.merge("insurance", amount, Double::sum);
            if (amount != 0) {
                trend.add(new TrendRow(VehicleJs.monthKey(start), "insurance", amount));
            }
        }
        // Insiden: actualCost bila ada, fallback estimatedCost.
        int incidentCount = 0;
        for (Object[] inc : incidents) {
            Instant tgl = (Instant) inc[0];
            if (!period.contains(tgl)) {
                continue;
            }
            incidentCount++;
            double amount = incidentCost(inc[3], inc[4]);
            breakdown.merge("incident", amount, Double::sum);
            if (amount != 0) {
                trend.add(new TrendRow(VehicleJs.monthKey(tgl), "incident", amount));
            }
        }
        double totalCost = sum(breakdown);

        // Biaya per KM: rentang odometer dari log servis/BBM dalam periode.
        List<Integer> odometers = new ArrayList<>();
        services.forEach(s -> addOdo(odometers, s[3]));
        fuels.forEach(f -> addOdo(odometers, f[3]));
        Integer odoMin = odometers.stream().min(Integer::compare).orElse(null);
        Integer odoMax = odometers.stream().max(Integer::compare).orElse(null);
        Integer distanceKm = odoMin != null && odoMax != null && odoMax > odoMin ? odoMax - odoMin : null;
        double totalLiters = 0;
        for (Object[] f : fuels) {
            totalLiters += VehicleJs.dec((BigDecimal) f[2]);
        }

        // Kepatuhan
        List<Map<String, Object>> taxJson = new ArrayList<>();
        int issueCount = 0;
        for (Object[] t : taxes) {
            if (!"ACTIVE".equals(name(t[5]))) {
                continue;
            }
            Instant due = (Instant) t[4];
            String state = complianceState(due, now);
            if (!"OK".equals(state)) {
                issueCount++;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", name(t[0]));
            m.put("periodYear", t[6]);
            m.put("dueDate", due);
            m.put("paidAt", t[3]);
            m.put("amount", VehicleJs.num(VehicleJs.dec((BigDecimal) t[1])));
            m.put("state", state);
            m.put("daysRemaining", daysUntil(due, now));
            taxJson.add(m);
        }
        List<Map<String, Object>> insJson = new ArrayList<>();
        for (Object[] i : insurances) {
            if (!"ACTIVE".equals(name(i[5]))) {
                continue;
            }
            Instant end = (Instant) i[4];
            String state = complianceState(end, now);
            if (!"OK".equals(state)) {
                issueCount++;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", name(i[0]));
            m.put("provider", i[1]);
            m.put("endDate", end);
            m.put("premium", VehicleJs.num(VehicleJs.dec((BigDecimal) i[2])));
            m.put("state", state);
            m.put("daysRemaining", daysUntil(end, now));
            insJson.add(m);
        }
        List<Map<String, Object>> openJson = new ArrayList<>();
        for (Object[] inc : incidents) {
            String st = name(inc[2]);
            if (!"DILAPORKAN".equals(st) && !"DALAM_PROSES".equals(st)) {
                continue;
            }
            issueCount++;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tanggal", inc[0]);
            m.put("severity", name(inc[1]));
            m.put("status", st);
            m.put("description", inc[5]);
            m.put("cost", VehicleJs.num(incidentCost(inc[3], inc[4])));
            openJson.add(m);
        }
        Map<String, Object> compliance = new LinkedHashMap<>();
        compliance.put("taxes", taxJson);
        compliance.put("insurances", insJson);
        compliance.put("openIncidents", openJson);
        compliance.put("issueCount", issueCount);

        // Kondisi
        Integer odometerTerakhir = vd == null ? null : vd.getOdometerTerakhir();
        Object[] oil = lastOil.isEmpty() ? null : lastOil.get(0);
        Integer oilRemainingKm = oil != null && odometerTerakhir != null ? (Integer) oil[2] - odometerTerakhir : null;

        Map<String, Object> assetJson = new LinkedHashMap<>();
        assetJson.put("id", asset.getId());
        assetJson.put("code", asset.getCode());
        assetJson.put("name", asset.getName());
        assetJson.put("categoryName", asset.getCategory() == null ? null : asset.getCategory().getName());
        assetJson.put("status", name(asset.getStatus()));
        assetJson.put("condition", name(asset.getCondition()));
        assetJson.put("noPlat", vd == null ? null : vd.getNoPlat());
        assetJson.put("brand", vd == null ? null : vd.getBrand());
        assetJson.put("tipe", vd == null ? null : vd.getTipe());
        assetJson.put("tahunProduksi", vd == null ? null : vd.getTahunProduksi());
        assetJson.put("odometerTerakhir", odometerTerakhir);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("service", services.size());
        counts.put("fuel", fuels.size());
        counts.put("tax", taxCount);
        counts.put("insurance", insuranceCount);
        counts.put("incident", incidentCount);

        Map<String, Object> tco = new LinkedHashMap<>();
        tco.put("breakdown", breakdownJson(breakdown));
        tco.put("totalCost", VehicleJs.num(totalCost));
        tco.put("costPerMonth", VehicleJs.num(totalCost / period.months()));
        tco.put("distanceKm", distanceKm);
        tco.put("costPerKm", distanceKm != null ? VehicleJs.num(totalCost / distanceKm) : null);
        tco.put("fuelCostPerKm", distanceKm != null ? VehicleJs.num(breakdown.get("fuel") / distanceKm) : null);
        tco.put("totalLiters", VehicleJs.num(totalLiters));
        tco.put("kmPerLiter", distanceKm != null && totalLiters > 0 ? VehicleJs.num(distanceKm / totalLiters) : null);
        tco.put("counts", counts);

        Map<String, Object> condition = new LinkedHashMap<>();
        if (lastInspection.isEmpty()) {
            condition.put("lastInspection", null);
        } else {
            Object[] li = lastInspection.get(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tanggal", li[0]);
            m.put("overallResult", name(li[1]));
            m.put("odometer", li[2]);
            m.put("notes", li[3]);
            condition.put("lastInspection", m);
        }
        List<Map<String, Object>> tiresJson = new ArrayList<>();
        for (Object[] t : activeTires) {
            Integer installedOdo = (Integer) t[4];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("position", name(t[0]));
            m.put("brand", t[1]);
            m.put("serialNumber", t[2]);
            m.put("installedAt", t[3]);
            m.put("installedOdometer", installedOdo);
            m.put("usageKm", installedOdo != null && odometerTerakhir != null ? Math.max(0, odometerTerakhir - installedOdo) : null);
            tiresJson.add(m);
        }
        condition.put("activeTires", tiresJson);
        condition.put("activeTireCount", activeTires.size());
        if (oil == null) {
            condition.put("lastOil", null);
        } else {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tanggal", oil[0]);
            m.put("odometer", oil[1]);
            m.put("nextOdometerDue", oil[2]);
            m.put("brand", oil[3]);
            m.put("volume", VehicleJs.num(VehicleJs.dec((BigDecimal) oil[4])));
            m.put("remainingKm", oilRemainingKm);
            m.put("isDue", oilRemainingKm != null ? oilRemainingKm <= 0 : null);
            condition.put("lastOil", m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period.json());
        out.put("asset", assetJson);
        out.put("tco", tco);
        out.put("monthlyTrend", monthlyTrend(period, trend));
        out.put("compliance", compliance);
        out.put("condition", condition);
        return out;
    }

    private static void addOdo(List<Integer> out, Object v) {
        if (v instanceof Integer i && i > 0) {
            out.add(i);
        }
    }

    private static double incidentCost(Object actual, Object estimated) {
        return actual != null ? VehicleJs.dec((BigDecimal) actual) : VehicleJs.dec((BigDecimal) estimated);
    }

    private static String name(Object v) {
        return v == null ? null : (v instanceof Enum<?> e ? e.name() : v.toString());
    }

    // Laporan armada

    private record FleetAsset(String id, String code, String name, String status, String condition,
                              String categoryName, String noPlat, String brand, String tipe, Integer odometerTerakhir) {
    }

    @ErpTx(readOnly = true)
    public Map<String, Object> fleetReport(String fromRaw, String toRaw, String categoryId) {
        Period period = parsePeriod(fromRaw, toRaw);
        Instant now = Instant.now();
        String cat = categoryId == null || categoryId.isEmpty() ? null : categoryId;

        var assetQuery = em.createQuery("select a.id, a.code, a.name, a.status, a.condition, c.name, v.noPlat, v.brand, "
                + "v.tipe, v.odometerTerakhir from GaAsset a left join a.category c join GaVehicleData v on v.assetId = a.id "
                + (cat != null ? "where a.categoryId = :cat " : "") + "order by a.code asc", Object[].class);
        if (cat != null) {
            assetQuery.setParameter("cat", cat);
        }
        List<FleetAsset> assets = assetQuery.getResultList().stream()
                .map(r -> new FleetAsset((String) r[0], (String) r[1], (String) r[2], name(r[3]), name(r[4]),
                        (String) r[5], (String) r[6], (String) r[7], (String) r[8], (Integer) r[9]))
                .toList();
        List<String> ids = assets.stream().map(FleetAsset::id).toList();

        if (ids.isEmpty()) {
            Map<String, Object> totals = breakdownJson(emptyBreakdown());
            totals.put("totalCost", 0);
            totals.put("vehicleCount", 0);
            totals.put("costPerVehicle", 0);
            Map<String, Object> compliance = new LinkedHashMap<>();
            compliance.put("taxIssues", 0);
            compliance.put("insuranceIssues", 0);
            compliance.put("openIncidents", 0);
            compliance.put("vehiclesWithIssues", 0);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("period", period.json());
            out.put("vehicles", List.of());
            out.put("totals", totals);
            out.put("distribution", List.of());
            out.put("topSpenders", List.of());
            out.put("monthlyTrend", monthlyTrend(period, List.of()));
            out.put("compliance", compliance);
            return out;
        }

        Map<String, Map<String, Double>> perAsset = new HashMap<>();
        Map<String, Double> litersByAsset = new HashMap<>();
        List<TrendRow> trend = new ArrayList<>();
        Bumper bump = (assetId, category, amount, tanggal) -> {
            perAsset.computeIfAbsent(assetId, x -> emptyBreakdown()).merge(category, amount, Double::sum);
            if (amount != 0) {
                trend.add(new TrendRow(VehicleJs.monthKey(tanggal), category, amount));
            }
        };

        for (Object[] r : rangeRows("select l.assetId, l.tanggal, l.biaya from GaVehicleServiceLog l "
                + "where l.assetId in :ids and l.tanggal >= :f and l.tanggal <= :t", ids, period)) {
            bump.add((String) r[0], "service", VehicleJs.dec((BigDecimal) r[2]), (Instant) r[1]);
        }
        for (Object[] r : rangeRows("select f.assetId, f.tanggal, f.totalCost, f.liters from GaVehicleFuelLog f "
                + "where f.assetId in :ids and f.tanggal >= :f and f.tanggal <= :t", ids, period)) {
            bump.add((String) r[0], "fuel", VehicleJs.dec((BigDecimal) r[2]), (Instant) r[1]);
            litersByAsset.merge((String) r[0], VehicleJs.dec((BigDecimal) r[3]), Double::sum);
        }
        for (Object[] r : rangeRows("select t.assetId, t.paidAt, t.amount, t.paidAmount from GaVehicleTax t "
                + "where t.assetId in :ids and t.paidAt >= :f and t.paidAt <= :t", ids, period)) {
            Instant paidAt = (Instant) r[1];
            // Status tidak diambil di sumber (select tanpa status) — paidAt pasti terisi di sini.
            bump.add((String) r[0], "tax", VehicleTaxCost.effective((BigDecimal) r[2], (BigDecimal) r[3], paidAt, null), paidAt);
        }
        for (Object[] r : rangeRows("select i.assetId, i.startDate, i.premium from GaVehicleInsurance i "
                + "where i.assetId in :ids and i.startDate >= :f and i.startDate <= :t", ids, period)) {
            bump.add((String) r[0], "insurance", VehicleJs.dec((BigDecimal) r[2]), (Instant) r[1]);
        }
        for (Object[] r : rangeRows("select i.assetId, i.tanggal, i.actualCost, i.estimatedCost from GaVehicleIncident i "
                + "where i.assetId in :ids and i.tanggal >= :f and i.tanggal <= :t", ids, period)) {
            bump.add((String) r[0], "incident", incidentCost(r[2], r[3]), (Instant) r[1]);
        }

        List<Object[]> activeTaxes = em.createQuery("select t.assetId, t.dueDate from GaVehicleTax t "
                        + "where t.assetId in :ids and t.status = :st", Object[].class)
                .setParameter("ids", ids).setParameter("st", GaVehicleTaxStatus.ACTIVE)
                .getResultList();
        List<Object[]> activeInsurances = em.createQuery("select i.assetId, i.endDate from GaVehicleInsurance i "
                        + "where i.assetId in :ids and i.status = :st", Object[].class)
                .setParameter("ids", ids).setParameter("st", GaVehicleInsuranceStatus.ACTIVE)
                .getResultList();
        List<String> openIncidents = em.createQuery("select i.assetId from GaVehicleIncident i "
                        + "where i.assetId in :ids and i.status in :st", String.class)
                .setParameter("ids", ids).setParameter("st", ErpFleetService.OPEN_INCIDENT).getResultList();

        // Work Order: jasa (labor + vendor eksternal) + sparepart terpakai.
        List<Object[]> workOrders = em.createQuery("select w.id, w.assetId, w.status, w.reportedAt, w.completedAt, "
                        + "w.closedAt, w.laborCost, w.externalCost from GaWorkOrder w "
                        + "where w.assetId in :ids and w.status <> :cancelled", Object[].class)
                .setParameter("ids", ids).setParameter("cancelled", GaWorkOrderStatus.CANCELLED).getResultList();
        Map<String, Double> partCostByWo = new HashMap<>();
        em.createQuery("select p.workOrderId, p.quantity, p.unitCost from GaWorkOrderPart p join p.workOrder w "
                        + "where w.assetId in :ids and w.status <> :cancelled", Object[].class)
                .setParameter("ids", ids).setParameter("cancelled", GaWorkOrderStatus.CANCELLED).getResultList()
                .forEach(p -> partCostByWo.merge((String) p[0],
                        VehicleJs.dec((BigDecimal) p[1]) * VehicleJs.dec((BigDecimal) p[2]), Double::sum));
        // Material Request yang sudah DIBAYAR (pengadaan sparepart lewat jalur MR).
        List<Object[]> materials = em.createQuery("select m.assetId, m.paidAmount, m.paidAt, m.requestedAt, w.assetId "
                        + "from GaMaterialRequest m left join m.workOrder w where m.status = :paid "
                        + "and (m.assetId in :ids or w.assetId in :ids)", Object[].class)
                .setParameter("paid", GaMaterialRequestStatus.PAID).setParameter("ids", ids).getResultList();

        // CATATAN sumber: biaya sparepart/jasa WO dibayar lewat MR PAID, jadi SATU kategori 'workorder'
        // = labor + vendor eksternal + parts gudang + MR PAID, supaya tidak dobel hitung.
        Map<String, Integer> woCount = new HashMap<>();
        Map<String, Integer> woOpen = new HashMap<>();
        for (Object[] w : workOrders) {
            String assetId = (String) w[1];
            if (w[2] != GaWorkOrderStatus.CLOSED) {
                woOpen.merge(assetId, 1, Integer::sum);
            }
            Instant at = w[5] != null ? (Instant) w[5] : w[4] != null ? (Instant) w[4] : (Instant) w[3];
            if (!period.contains(at)) {
                continue;
            }
            double amount = VehicleJs.dec((BigDecimal) w[6]) + VehicleJs.dec((BigDecimal) w[7])
                    + partCostByWo.getOrDefault((String) w[0], 0d);
            woCount.merge(assetId, 1, Integer::sum);
            if (amount != 0) {
                bump.add(assetId, "workorder", amount, at);
            }
        }
        Set<String> idSet = new HashSet<>(ids);
        Map<String, Integer> mrCount = new HashMap<>();
        for (Object[] m : materials) {
            String target = m[0] != null ? (String) m[0] : (String) m[4];
            if (target == null || !idSet.contains(target)) {
                continue;
            }
            Instant at = m[2] != null ? (Instant) m[2] : (Instant) m[3];
            if (!period.contains(at)) {
                continue;
            }
            double amount = VehicleJs.dec((BigDecimal) m[1]);
            mrCount.merge(target, 1, Integer::sum);
            if (amount != 0) {
                bump.add(target, "workorder", amount, at);
            }
        }

        // Kepatuhan per aset + jatuh tempo terdekat (badge tabel).
        Map<String, Integer> taxIssue = new HashMap<>();
        Map<String, Instant> nearestTax = new HashMap<>();
        for (Object[] t : activeTaxes) {
            String a = (String) t[0];
            Instant due = (Instant) t[1];
            if (!"OK".equals(complianceState(due, now))) {
                taxIssue.merge(a, 1, Integer::sum);
            }
            Instant cur = nearestTax.get(a);
            if (cur == null || due.isBefore(cur)) {
                nearestTax.put(a, due);
            }
        }
        Map<String, Integer> insIssue = new HashMap<>();
        Map<String, Instant> nearestIns = new HashMap<>();
        for (Object[] i : activeInsurances) {
            String a = (String) i[0];
            Instant end = (Instant) i[1];
            if (!"OK".equals(complianceState(end, now))) {
                insIssue.merge(a, 1, Integer::sum);
            }
            Instant cur = nearestIns.get(a);
            if (cur == null || end.isBefore(cur)) {
                nearestIns.put(a, end);
            }
        }
        Map<String, Integer> incidentByAsset = new HashMap<>();
        openIncidents.forEach(a -> incidentByAsset.merge(a, 1, Integer::sum));

        List<Map<String, Object>> vehicles = new ArrayList<>();
        List<Double> totalsByVehicle = new ArrayList<>();
        Map<String, Double> totals = emptyBreakdown();
        double grandTotal = 0;
        int vehiclesWithIssues = 0;
        for (FleetAsset a : assets) {
            Map<String, Double> b = perAsset.getOrDefault(a.id(), emptyBreakdown());
            double totalCost = sum(b);
            Instant taxDue = nearestTax.get(a.id());
            Instant insDue = nearestIns.get(a.id());
            int issues = taxIssue.getOrDefault(a.id(), 0) + insIssue.getOrDefault(a.id(), 0)
                    + incidentByAsset.getOrDefault(a.id(), 0) + woOpen.getOrDefault(a.id(), 0);
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("assetId", a.id());
            v.put("code", a.code());
            v.put("name", a.name());
            v.put("noPlat", a.noPlat());
            v.put("brand", a.brand());
            v.put("tipe", a.tipe());
            v.put("categoryName", a.categoryName());
            v.put("status", a.status());
            v.put("condition", a.condition());
            v.put("odometerTerakhir", a.odometerTerakhir());
            v.put("breakdown", breakdownJson(b));
            v.put("totalCost", VehicleJs.num(totalCost));
            v.put("costPerMonth", VehicleJs.num(totalCost / period.months()));
            v.put("totalLiters", VehicleJs.num(litersByAsset.getOrDefault(a.id(), 0d)));
            v.put("taxState", complianceState(taxDue, now));
            v.put("taxDueDate", taxDue);
            v.put("insuranceState", complianceState(insDue, now));
            v.put("insuranceEndDate", insDue);
            v.put("openIncidentCount", incidentByAsset.getOrDefault(a.id(), 0));
            v.put("workOrderCount", woCount.getOrDefault(a.id(), 0));
            v.put("openWorkOrderCount", woOpen.getOrDefault(a.id(), 0));
            v.put("materialRequestCount", mrCount.getOrDefault(a.id(), 0));
            v.put("issueCount", issues);
            vehicles.add(v);
            totalsByVehicle.add(totalCost);
            for (String k : COST_CATEGORIES) {
                totals.merge(k, b.get(k), Double::sum);
            }
            grandTotal += totalCost;
            if (issues > 0) {
                vehiclesWithIssues++;
            }
        }

        Map<String, Object> totalsJson = breakdownJson(totals);
        totalsJson.put("totalCost", VehicleJs.num(grandTotal));
        totalsJson.put("vehicleCount", vehicles.size());
        totalsJson.put("costPerVehicle", VehicleJs.num(vehicles.isEmpty() ? 0 : grandTotal / vehicles.size()));

        List<Map<String, Object>> distribution = new ArrayList<>();
        for (String k : COST_CATEGORIES) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("category", k);
            d.put("amount", VehicleJs.num(totals.get(k)));
            d.put("percentage", VehicleJs.num(grandTotal > 0 ? (totals.get(k) / grandTotal) * 100 : 0));
            distribution.add(d);
        }

        // Top 10 pengeluaran (sort stabil seperti Array#sort).
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < vehicles.size(); i++) {
            if (totalsByVehicle.get(i) > 0) {
                order.add(i);
            }
        }
        order.sort((x, y) -> Double.compare(totalsByVehicle.get(y), totalsByVehicle.get(x)));
        List<Map<String, Object>> topSpenders = new ArrayList<>();
        for (int idx : order.subList(0, Math.min(10, order.size()))) {
            Map<String, Object> v = vehicles.get(idx);
            Map<String, Object> t = new LinkedHashMap<>();
            for (String k : List.of("assetId", "code", "name", "noPlat", "totalCost", "costPerMonth", "breakdown")) {
                t.put(k, v.get(k));
            }
            topSpenders.add(t);
        }

        Map<String, Object> compliance = new LinkedHashMap<>();
        compliance.put("taxIssues", taxIssue.values().stream().mapToInt(Integer::intValue).sum());
        compliance.put("insuranceIssues", insIssue.values().stream().mapToInt(Integer::intValue).sum());
        compliance.put("openIncidents", openIncidents.size());
        compliance.put("openWorkOrders", woOpen.values().stream().mapToInt(Integer::intValue).sum());
        compliance.put("vehiclesWithIssues", vehiclesWithIssues);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period.json());
        out.put("vehicles", vehicles);
        out.put("totals", totalsJson);
        out.put("distribution", distribution);
        out.put("topSpenders", topSpenders);
        out.put("monthlyTrend", monthlyTrend(period, trend));
        out.put("compliance", compliance);
        return out;
    }

    @FunctionalInterface
    private interface Bumper {
        void add(String assetId, String category, double amount, Instant tanggal);
    }

    private List<Object[]> rangeRows(String jpql, List<String> ids, Period period) {
        return em.createQuery(jpql, Object[].class).setParameter("ids", ids)
                .setParameter("f", period.from()).setParameter("t", period.to()).getResultList();
    }
}
