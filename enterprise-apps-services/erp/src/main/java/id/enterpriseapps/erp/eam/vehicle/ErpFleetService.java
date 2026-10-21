package id.enterpriseapps.erp.eam.vehicle;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.entity.GaVehicleIncidentStatus;
import id.enterpriseapps.erp.entity.GaVehicleInsuranceStatus;
import id.enterpriseapps.erp.entity.GaVehicleTaxStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daftar armada (halaman Kendaraan) + KPI ringkasan. Semua sumber dimuat per tabel sekali jalan
 * (tanpa N+1), lalu dirakit/difilter di memori lewat {@link FleetList}.
 */
@Service
public class ErpFleetService {

    static final List<GaVehicleIncidentStatus> OPEN_INCIDENT = List.of(
            GaVehicleIncidentStatus.DILAPORKAN, GaVehicleIncidentStatus.DALAM_PROSES);

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx(readOnly = true)
    public Map<String, Object> fleet(Map<String, String> query) {
        // `req.query.categoryKind ?? 'vehicle'`: kosong ("") tetap ditolak.
        String rawKind = query.containsKey("categoryKind") ? query.get("categoryKind") : "vehicle";
        VehicleCategoryKind.Parsed kind = VehicleCategoryKind.parse(rawKind);
        if (kind.kind() == null) {
            throw ErpException.business("categoryKind tidak valid.");
        }
        FleetList.Filters filters = FleetList.parseFilters(query);
        ListQuery.Paging paging = ListQuery.paging(query, 25, 200);
        ListQuery.Sort sort = FleetList.parseSort(query.get("sort"));

        List<String> categoryIds = VehicleCategoryKind.resolveCategoryIds(em, kind.kind());
        List<FleetList.AssetSource> assets = categoryIds.isEmpty() ? List.of() : em.createQuery(
                        "select a.id, a.code, a.name, a.status, a.condition, a.picUserId, c.name, v.noPlat, v.brand, "
                                + "v.tipe, v.tahunProduksi, v.jenisBBM, v.odometerTerakhir from GaAsset a "
                                + "left join a.category c left join GaVehicleData v on v.assetId = a.id "
                                + "where a.categoryId in :cats order by a.code asc", Object[].class)
                .setParameter("cats", categoryIds).setMaxResults(5000).getResultList().stream()
                .map(r -> new FleetList.AssetSource((String) r[0], (String) r[1], (String) r[2], str(r[3]), str(r[4]),
                        (String) r[5], (String) r[6], (String) r[7], (String) r[8], (String) r[9], (Integer) r[10],
                        str(r[11]), (Integer) r[12]))
                .toList();
        List<String> ids = assets.stream().map(FleetList.AssetSource::id).toList();

        List<FleetList.TaxSource> taxes = List.of();
        List<FleetList.DatedSource> insurances = List.of();
        List<FleetList.DatedSource> inspections = List.of();
        List<String> openIncidents = List.of();
        Map<String, String> drivers = new HashMap<>();
        if (!ids.isEmpty()) {
            taxes = em.createQuery("select t.assetId, t.type, t.dueDate from GaVehicleTax t "
                            + "where t.assetId in :ids and t.status = :st", Object[].class)
                    .setParameter("ids", ids).setParameter("st", GaVehicleTaxStatus.ACTIVE).getResultList().stream()
                    .map(r -> new FleetList.TaxSource((String) r[0], str(r[1]), (Instant) r[2])).toList();
            insurances = em.createQuery("select i.assetId, i.endDate from GaVehicleInsurance i "
                            + "where i.assetId in :ids and i.status = :st", Object[].class)
                    .setParameter("ids", ids).setParameter("st", GaVehicleInsuranceStatus.ACTIVE).getResultList().stream()
                    .map(r -> new FleetList.DatedSource((String) r[0], (Instant) r[1])).toList();
            inspections = em.createQuery("select i.assetId, i.nextDueDate from GaVehicleInspection i "
                            + "where i.assetId in :ids and i.nextDueDate is not null order by i.tanggal desc", Object[].class)
                    .setParameter("ids", ids).getResultList().stream()
                    .map(r -> new FleetList.DatedSource((String) r[0], (Instant) r[1])).toList();
            openIncidents = em.createQuery("select i.assetId from GaVehicleIncident i "
                            + "where i.assetId in :ids and i.status in :st", String.class)
                    .setParameter("ids", ids).setParameter("st", OPEN_INCIDENT).getResultList();
            List<String> driverIds = assets.stream().map(FleetList.AssetSource::picUserId)
                    .filter(x -> x != null && !x.isEmpty()).distinct().toList();
            if (!driverIds.isEmpty()) {
                em.createQuery("select u.id, u.fullName from User u where u.id in :ids", Object[].class)
                        .setParameter("ids", driverIds).getResultList()
                        .forEach(r -> drivers.put((String) r[0], (String) r[1]));
            }
        }

        List<FleetList.Row> rows = FleetList.build(new FleetList.Sources(assets, taxes, insurances, inspections,
                openIncidents, drivers), ErpDates.dueBoundaries(Instant.now()));
        List<FleetList.Row> filtered = FleetList.sort(FleetList.filter(rows, filters), sort);
        return ListQuery.result(FleetList.page(filtered, paging).stream().map(FleetList.Row::json).toList(),
                filtered.size(), paging);
    }

    private static String str(Object v) {
        return v == null ? null : (v instanceof Enum<?> e ? e.name() : v.toString());
    }

    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        VehicleCategoryKind.Parsed kind = VehicleCategoryKind.parse(query.get("categoryKind"));
        if (kind.invalid()) {
            throw ErpException.business("categoryKind tidak valid.");
        }
        String categoryId = query.get("categoryId") != null && !query.get("categoryId").isEmpty() ? query.get("categoryId") : null;
        // assetWhere: kind → kategori hasil resolve; selain itu categoryId; selain itu semua aset.
        String assetCond;
        List<String> kindIds = null;
        if (kind.kind() != null) {
            kindIds = VehicleCategoryKind.resolveCategoryIds(em, kind.kind());
            assetCond = kindIds.isEmpty() ? "1=0" : "a.categoryId in :cats";
        } else if (categoryId != null) {
            assetCond = "a.categoryId = :cat";
        } else {
            assetCond = "1=1";
        }
        Instant now = Instant.now();
        Instant soon = now.plus(Duration.ofDays(30)); // 30 hari ke depan
        ZonedDateTime z = now.atZone(ZoneOffset.UTC);
        Instant monthStart = z.toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long totalVehicles = bind(em.createQuery("select count(a) from GaAsset a where " + assetCond, Long.class),
                kindIds, categoryId).getSingleResult();
        long taxExpiringSoon = em.createQuery("select count(t) from GaVehicleTax t where t.status = :st "
                        + "and t.dueDate >= :now and t.dueDate <= :soon", Long.class)
                .setParameter("st", GaVehicleTaxStatus.ACTIVE).setParameter("now", now).setParameter("soon", soon)
                .getSingleResult();
        long taxExpired = em.createQuery("select count(t) from GaVehicleTax t where t.status = :st and t.dueDate < :now",
                        Long.class)
                .setParameter("st", GaVehicleTaxStatus.ACTIVE).setParameter("now", now).getSingleResult();
        long insuranceExpiringSoon = em.createQuery("select count(i) from GaVehicleInsurance i where i.status = :st "
                        + "and i.endDate >= :now and i.endDate <= :soon", Long.class)
                .setParameter("st", GaVehicleInsuranceStatus.ACTIVE).setParameter("now", now).setParameter("soon", soon)
                .getSingleResult();
        long insuranceExpired = em.createQuery("select count(i) from GaVehicleInsurance i where i.status = :st "
                        + "and i.endDate < :now", Long.class)
                .setParameter("st", GaVehicleInsuranceStatus.ACTIVE).setParameter("now", now).getSingleResult();
        long openIncidents = em.createQuery("select count(i) from GaVehicleIncident i where i.status in :st", Long.class)
                .setParameter("st", OPEN_INCIDENT).getSingleResult();
        BigDecimal serviceThisMonth = em.createQuery("select sum(l.biaya) from GaVehicleServiceLog l "
                        + "where l.tanggal >= :m", BigDecimal.class)
                .setParameter("m", monthStart).getSingleResult();
        BigDecimal fuelThisMonth = em.createQuery("select sum(f.totalCost) from GaVehicleFuelLog f "
                        + "where f.tanggal >= :m", BigDecimal.class)
                .setParameter("m", monthStart).getSingleResult();

        // Pengingat inspeksi berkala: hanya inspeksi TERAKHIR per kendaraan yang dihitung supaya riwayat lama
        // tidak ikut menaikkan angka jatuh tempo.
        List<Object[]> inspections = bind(em.createQuery("select i.assetId, i.nextDueDate from GaVehicleInspection i "
                        + "join i.asset a where i.nextDueDate is not null and " + assetCond + " order by i.tanggal desc",
                Object[].class), kindIds, categoryId).getResultList();
        Map<String, Instant> latestPerAsset = new LinkedHashMap<>();
        for (Object[] r : inspections) {
            latestPerAsset.putIfAbsent((String) r[0], (Instant) r[1]);
        }
        long inspectionOverdue = 0;
        long inspectionDueSoon = 0;
        for (Instant due : latestPerAsset.values()) {
            if (due.isBefore(now)) {
                inspectionOverdue++;
            } else if (!due.isAfter(soon)) {
                inspectionDueSoon++;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalVehicles", totalVehicles);
        out.put("taxExpiringSoon", taxExpiringSoon);
        out.put("taxExpired", taxExpired);
        out.put("insuranceExpiringSoon", insuranceExpiringSoon);
        out.put("insuranceExpired", insuranceExpired);
        out.put("openIncidents", openIncidents);
        out.put("inspectionOverdue", inspectionOverdue);
        out.put("inspectionDueSoon", inspectionDueSoon);
        out.put("inspectionNeverDone", Math.max(totalVehicles - latestPerAsset.size(), 0));
        out.put("serviceCostThisMonth", VehicleJs.num(VehicleJs.dec(serviceThisMonth)));
        out.put("fuelCostThisMonth", VehicleJs.num(VehicleJs.dec(fuelThisMonth)));
        return out;
    }

    private static <T> TypedQuery<T> bind(TypedQuery<T> q, List<String> kindIds, String categoryId) {
        if (kindIds != null) {
            if (!kindIds.isEmpty()) {
                q.setParameter("cats", kindIds);
            }
        } else if (categoryId != null) {
            q.setParameter("cat", categoryId);
        }
        return q;
    }
}
