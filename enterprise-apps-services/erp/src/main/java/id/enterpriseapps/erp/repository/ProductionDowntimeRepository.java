package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.ProductionDowntime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProductionDowntime (tabel production_downtimes). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProductionDowntimeRepository extends JpaRepository<ProductionDowntime, String>, JpaSpecificationExecutor<ProductionDowntime> {
}
