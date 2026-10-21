package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.ProductionRunCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProductionRunCost (tabel production_run_costs). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProductionRunCostRepository extends JpaRepository<ProductionRunCost, String>, JpaSpecificationExecutor<ProductionRunCost> {
}
