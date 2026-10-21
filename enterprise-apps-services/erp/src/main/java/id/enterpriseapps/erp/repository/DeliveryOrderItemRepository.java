package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.DeliveryOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository DeliveryOrderItem (tabel delivery_order_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface DeliveryOrderItemRepository extends JpaRepository<DeliveryOrderItem, String>, JpaSpecificationExecutor<DeliveryOrderItem> {
}
