package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.Machine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository Machine (tabel machines). Dibangkitkan; query khusus taruh di paket domain. */
public interface MachineRepository extends JpaRepository<Machine, String>, JpaSpecificationExecutor<Machine> {
}
