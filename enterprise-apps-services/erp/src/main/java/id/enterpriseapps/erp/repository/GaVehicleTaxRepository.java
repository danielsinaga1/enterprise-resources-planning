package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaVehicleTax;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleTax (tabel ga_vehicle_taxes). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleTaxRepository extends JpaRepository<GaVehicleTax, String>, JpaSpecificationExecutor<GaVehicleTax> {
}
