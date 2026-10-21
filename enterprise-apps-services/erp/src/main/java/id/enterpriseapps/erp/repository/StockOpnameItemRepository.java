package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.StockOpnameItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository StockOpnameItem (tabel stock_opname_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface StockOpnameItemRepository extends JpaRepository<StockOpnameItem, String>, JpaSpecificationExecutor<StockOpnameItem> {
}
