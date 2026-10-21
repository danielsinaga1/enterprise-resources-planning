package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaWorkOrderPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaWorkOrderPhoto (tabel ga_work_order_photos). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaWorkOrderPhotoRepository extends JpaRepository<GaWorkOrderPhoto, String>, JpaSpecificationExecutor<GaWorkOrderPhoto> {
}
