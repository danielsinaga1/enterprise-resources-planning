package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.ProductionRunProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProductionRunProcess (tabel production_run_processes). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProductionRunProcessRepository extends JpaRepository<ProductionRunProcess, String>, JpaSpecificationExecutor<ProductionRunProcess> {
}
