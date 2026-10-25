package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaVehicleServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleServiceRequest (tabel ga_vehicle_service_requests). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleServiceRequestRepository extends JpaRepository<GaVehicleServiceRequest, String>, JpaSpecificationExecutor<GaVehicleServiceRequest> {
}
