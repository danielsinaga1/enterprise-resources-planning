package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.DeliveryOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository DeliveryOrder (tabel delivery_orders). Dibangkitkan; query khusus taruh di paket domain. */
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, String>, JpaSpecificationExecutor<DeliveryOrder> {
}
