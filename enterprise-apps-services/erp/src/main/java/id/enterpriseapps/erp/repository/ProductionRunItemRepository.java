package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.ProductionRunItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProductionRunItem (tabel production_run_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProductionRunItemRepository extends JpaRepository<ProductionRunItem, String>, JpaSpecificationExecutor<ProductionRunItem> {
}
