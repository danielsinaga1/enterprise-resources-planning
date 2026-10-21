package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaVehicleServiceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleServiceLog (tabel ga_vehicle_service_logs). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleServiceLogRepository extends JpaRepository<GaVehicleServiceLog, String>, JpaSpecificationExecutor<GaVehicleServiceLog> {
}
