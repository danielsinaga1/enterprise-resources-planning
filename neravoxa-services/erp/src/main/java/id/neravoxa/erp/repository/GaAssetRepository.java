package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaAsset (tabel ga_assets). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaAssetRepository extends JpaRepository<GaAsset, String>, JpaSpecificationExecutor<GaAsset> {
}
