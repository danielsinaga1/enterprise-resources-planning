package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository SalesOrder (tabel sales_orders). Dibangkitkan; query khusus taruh di paket domain. */
public interface SalesOrderRepository extends JpaRepository<SalesOrder, String>, JpaSpecificationExecutor<SalesOrder> {
}
