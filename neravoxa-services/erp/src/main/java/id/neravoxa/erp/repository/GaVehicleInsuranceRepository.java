package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaVehicleInsurance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleInsurance (tabel ga_vehicle_insurances). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleInsuranceRepository extends JpaRepository<GaVehicleInsurance, String>, JpaSpecificationExecutor<GaVehicleInsurance> {
}
