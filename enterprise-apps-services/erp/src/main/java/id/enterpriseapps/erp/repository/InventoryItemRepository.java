package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository InventoryItem (tabel inventory_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, String>, JpaSpecificationExecutor<InventoryItem> {
}
