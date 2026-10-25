package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaAssetQr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaAssetQr (tabel ga_asset_qrs). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaAssetQrRepository extends JpaRepository<GaAssetQr, String>, JpaSpecificationExecutor<GaAssetQr> {
}
