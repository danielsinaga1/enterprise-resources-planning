package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaWorkOrderPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaWorkOrderPart (tabel ga_work_order_parts). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaWorkOrderPartRepository extends JpaRepository<GaWorkOrderPart, String>, JpaSpecificationExecutor<GaWorkOrderPart> {
}
