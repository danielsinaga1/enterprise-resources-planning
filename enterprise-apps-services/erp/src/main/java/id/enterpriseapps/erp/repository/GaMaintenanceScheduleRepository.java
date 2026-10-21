package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaMaintenanceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaMaintenanceSchedule (tabel ga_maintenance_schedules). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaMaintenanceScheduleRepository extends JpaRepository<GaMaintenanceSchedule, String>, JpaSpecificationExecutor<GaMaintenanceSchedule> {
}
