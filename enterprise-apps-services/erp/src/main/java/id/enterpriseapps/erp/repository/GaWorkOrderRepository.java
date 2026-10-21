package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaWorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaWorkOrder (tabel ga_work_orders). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaWorkOrderRepository extends JpaRepository<GaWorkOrder, String>, JpaSpecificationExecutor<GaWorkOrder> {
}
