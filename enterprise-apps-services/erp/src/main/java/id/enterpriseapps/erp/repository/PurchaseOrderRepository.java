package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PurchaseOrder (tabel purchase_orders). Dibangkitkan; query khusus taruh di paket domain. */
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String>, JpaSpecificationExecutor<PurchaseOrder> {
}
