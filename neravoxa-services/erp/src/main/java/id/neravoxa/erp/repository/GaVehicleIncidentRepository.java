package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaVehicleIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleIncident (tabel ga_vehicle_incidents). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleIncidentRepository extends JpaRepository<GaVehicleIncident, String>, JpaSpecificationExecutor<GaVehicleIncident> {
}
