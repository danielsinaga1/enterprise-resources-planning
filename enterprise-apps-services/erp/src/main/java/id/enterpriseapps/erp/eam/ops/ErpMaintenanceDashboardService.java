package id.enterpriseapps.erp.eam.ops;

import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.GaDocumentStatus;
import id.enterpriseapps.erp.entity.GaVehicleInsuranceStatus;
import id.enterpriseapps.erp.entity.GaVehicleTaxStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dashboard pemeliharaan — port {@code loadMaintenanceDashboard}: hanya sumber yang boleh dilihat pemanggil
 * yang dimuat (horizon 400 hari, maks 500 baris per sumber), lalu dihitung {@link MaintenanceDashboardCalc}.
 */
@Service
public class ErpMaintenanceDashboardService {

    private static final int TAKE = 500;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx(readOnly = true)
    public Map<String, Object> load(MaintenanceDashboardCalc.Access access, Instant now) {
        Instant horizon = now.plus(Duration.ofDays(400));
        List<MaintenanceDashboardCalc.ScheduleRow> schedules = !access.maintenance() ? List.of()
                : em.createQuery("select s.id, s.assetId, a.name, s.nextDueAt, s.isActive from GaMaintenanceSchedule s "
                                + "join s.asset a where s.isActive = true and s.nextDueAt < :h order by s.nextDueAt asc",
                        Object[].class).setParameter("h", horizon).setMaxResults(TAKE).getResultList().stream()
                .map(r -> new MaintenanceDashboardCalc.ScheduleRow((String) r[0], (String) r[1], (String) r[2],
                        (Instant) r[3], Boolean.TRUE.equals(r[4]))).toList();
        List<MaintenanceDashboardCalc.WorkOrderRow> workOrders = !access.maintenance() ? List.of()
                : em.createQuery("select w.id, w.code, w.title, a.name, w.status, w.dueAt from GaWorkOrder w join w.asset a "
                                + "where w.status in :active and w.dueAt is not null order by w.dueAt asc", Object[].class)
                .setParameter("active", GaWorkOrderList.ACTIVE).setMaxResults(TAKE).getResultList().stream()
                .map(r -> new MaintenanceDashboardCalc.WorkOrderRow((String) r[0], (String) r[1], (String) r[2],
                        (String) r[3], String.valueOf(r[4]), (Instant) r[5])).toList();
        List<MaintenanceDashboardCalc.VehicleTaxRow> taxes = !access.vehicle() ? List.of()
                : em.createQuery("select t.id, t.assetId, a.name, t.type, t.dueDate, t.status from GaVehicleTax t "
                                + "join t.asset a where t.status = :st and t.dueDate < :h order by t.dueDate asc", Object[].class)
                .setParameter("st", GaVehicleTaxStatus.ACTIVE).setParameter("h", horizon).setMaxResults(TAKE)
                .getResultList().stream()
                .map(r -> new MaintenanceDashboardCalc.VehicleTaxRow((String) r[0], (String) r[1], (String) r[2],
                        String.valueOf(r[3]), (Instant) r[4], String.valueOf(r[5]))).toList();
        List<MaintenanceDashboardCalc.InsuranceRow> insurances = !access.vehicle() ? List.of()
                : em.createQuery("select i.id, i.assetId, a.name, i.provider, i.endDate, i.status from GaVehicleInsurance i "
                                + "join i.asset a where i.status = :st and i.endDate < :h order by i.endDate asc", Object[].class)
                .setParameter("st", GaVehicleInsuranceStatus.ACTIVE).setParameter("h", horizon).setMaxResults(TAKE)
                .getResultList().stream()
                .map(r -> new MaintenanceDashboardCalc.InsuranceRow((String) r[0], (String) r[1], (String) r[2],
                        (String) r[3], (Instant) r[4], String.valueOf(r[5]))).toList();
        List<MaintenanceDashboardCalc.DocumentRow> documents = !access.documents() ? List.of()
                : em.createQuery("select d.id, d.title, a.name, d.expiryDate, d.status from GaDocument d left join d.asset a "
                                + "where d.status = :expired or (d.status = :active and d.expiryDate is not null "
                                + "and d.expiryDate < :h) order by d.expiryDate asc nulls last", Object[].class)
                .setParameter("expired", GaDocumentStatus.EXPIRED).setParameter("active", GaDocumentStatus.ACTIVE)
                .setParameter("h", horizon).setMaxResults(TAKE).getResultList().stream()
                .map(r -> new MaintenanceDashboardCalc.DocumentRow((String) r[0], (String) r[1], (String) r[2],
                        (Instant) r[3], String.valueOf(r[4]))).toList();
        return MaintenanceDashboardCalc.compute(
                new MaintenanceDashboardCalc.Input(schedules, workOrders, taxes, insurances, documents), access, now);
    }
}
