package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaAssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaAssetCategory (tabel ga_asset_categories). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaAssetCategoryRepository extends JpaRepository<GaAssetCategory, String>, JpaSpecificationExecutor<GaAssetCategory> {
}
