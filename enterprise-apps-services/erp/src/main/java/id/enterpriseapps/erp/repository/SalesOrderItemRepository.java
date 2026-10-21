package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.SalesOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository SalesOrderItem (tabel sales_order_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, String>, JpaSpecificationExecutor<SalesOrderItem> {
}
