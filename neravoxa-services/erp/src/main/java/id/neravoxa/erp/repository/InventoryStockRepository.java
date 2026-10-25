package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.InventoryStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository InventoryStock (tabel inventory_stocks). Dibangkitkan; query khusus taruh di paket domain. */
public interface InventoryStockRepository extends JpaRepository<InventoryStock, String>, JpaSpecificationExecutor<InventoryStock> {
}
