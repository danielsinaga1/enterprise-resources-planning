package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaInventoryCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaInventoryCategory (tabel ga_inventory_categories). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaInventoryCategoryRepository extends JpaRepository<GaInventoryCategory, String>, JpaSpecificationExecutor<GaInventoryCategory> {
}
