package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaVehicleInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleInspection (tabel ga_vehicle_inspections). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleInspectionRepository extends JpaRepository<GaVehicleInspection, String>, JpaSpecificationExecutor<GaVehicleInspection> {
}
