package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaVehicleFuelLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleFuelLog (tabel ga_vehicle_fuel_logs). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleFuelLogRepository extends JpaRepository<GaVehicleFuelLog, String>, JpaSpecificationExecutor<GaVehicleFuelLog> {
}
