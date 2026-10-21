package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.ProductionProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProductionProcess (tabel production_processes). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProductionProcessRepository extends JpaRepository<ProductionProcess, String>, JpaSpecificationExecutor<ProductionProcess> {
}
