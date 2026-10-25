package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaVehicleOilLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleOilLog (tabel ga_vehicle_oil_logs). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleOilLogRepository extends JpaRepository<GaVehicleOilLog, String>, JpaSpecificationExecutor<GaVehicleOilLog> {
}
