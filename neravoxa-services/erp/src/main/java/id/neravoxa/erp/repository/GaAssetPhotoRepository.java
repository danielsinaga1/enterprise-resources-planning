package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaAssetPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaAssetPhoto (tabel ga_asset_photos). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaAssetPhotoRepository extends JpaRepository<GaAssetPhoto, String>, JpaSpecificationExecutor<GaAssetPhoto> {
}
