package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaStockByLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaStockByLocation (tabel ga_stock_by_location). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaStockByLocationRepository extends JpaRepository<GaStockByLocation, String>, JpaSpecificationExecutor<GaStockByLocation> {
}
