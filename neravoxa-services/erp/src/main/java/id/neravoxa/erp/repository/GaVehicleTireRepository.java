package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaVehicleTire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleTire (tabel ga_vehicle_tires). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleTireRepository extends JpaRepository<GaVehicleTire, String>, JpaSpecificationExecutor<GaVehicleTire> {
}
