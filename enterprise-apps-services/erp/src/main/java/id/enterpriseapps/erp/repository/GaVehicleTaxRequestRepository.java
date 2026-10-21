package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaVehicleTaxRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleTaxRequest (tabel ga_vehicle_tax_requests). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleTaxRequestRepository extends JpaRepository<GaVehicleTaxRequest, String>, JpaSpecificationExecutor<GaVehicleTaxRequest> {
}
