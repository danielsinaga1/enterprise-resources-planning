package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaVehicleData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleData (tabel ga_vehicle_data). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleDataRepository extends JpaRepository<GaVehicleData, String>, JpaSpecificationExecutor<GaVehicleData> {
}
