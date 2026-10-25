package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.SalesReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository SalesReturnItem (tabel sales_return_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface SalesReturnItemRepository extends JpaRepository<SalesReturnItem, String>, JpaSpecificationExecutor<SalesReturnItem> {
}
