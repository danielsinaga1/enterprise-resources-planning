package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.FixedAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository FixedAsset (tabel fixed_assets). Dibangkitkan; query khusus taruh di paket domain. */
public interface FixedAssetRepository extends JpaRepository<FixedAsset, String>, JpaSpecificationExecutor<FixedAsset> {
}
