package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PurchaseOrderItem (tabel purchase_order_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, String>, JpaSpecificationExecutor<PurchaseOrderItem> {
}
