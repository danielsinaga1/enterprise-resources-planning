package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaBranch (tabel ga_branches). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaBranchRepository extends JpaRepository<GaBranch, String>, JpaSpecificationExecutor<GaBranch> {
}
