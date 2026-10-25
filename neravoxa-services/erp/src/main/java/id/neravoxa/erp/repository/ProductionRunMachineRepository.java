package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.ProductionRunMachine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProductionRunMachine (tabel production_run_machines). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProductionRunMachineRepository extends JpaRepository<ProductionRunMachine, String>, JpaSpecificationExecutor<ProductionRunMachine> {
}
