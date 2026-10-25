package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.ProductionCostStandard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProductionCostStandard (tabel production_cost_standards). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProductionCostStandardRepository extends JpaRepository<ProductionCostStandard, String>, JpaSpecificationExecutor<ProductionCostStandard> {
}
