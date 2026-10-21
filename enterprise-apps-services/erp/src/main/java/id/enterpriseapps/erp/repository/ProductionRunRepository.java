package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.ProductionRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProductionRun (tabel production_runs). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProductionRunRepository extends JpaRepository<ProductionRun, String>, JpaSpecificationExecutor<ProductionRun> {
}
