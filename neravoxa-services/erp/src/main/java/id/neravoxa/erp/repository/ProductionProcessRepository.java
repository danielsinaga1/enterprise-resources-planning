package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.ProductionProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProductionProcess (tabel production_processes). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProductionProcessRepository extends JpaRepository<ProductionProcess, String>, JpaSpecificationExecutor<ProductionProcess> {
}
