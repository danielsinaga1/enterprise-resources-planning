package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.StockTransferItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository StockTransferItem (tabel stock_transfer_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface StockTransferItemRepository extends JpaRepository<StockTransferItem, String>, JpaSpecificationExecutor<StockTransferItem> {
}
