package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaVehicleInspectionPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaVehicleInspectionPhoto (tabel ga_vehicle_inspection_photos). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaVehicleInspectionPhotoRepository extends JpaRepository<GaVehicleInspectionPhoto, String>, JpaSpecificationExecutor<GaVehicleInspectionPhoto> {
}
