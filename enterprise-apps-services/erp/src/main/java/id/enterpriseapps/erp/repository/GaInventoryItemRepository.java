package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaInventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaInventoryItem (tabel ga_inventory_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaInventoryItemRepository extends JpaRepository<GaInventoryItem, String>, JpaSpecificationExecutor<GaInventoryItem> {
}
