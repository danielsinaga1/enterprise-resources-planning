package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.SystemAccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository SystemAccountMapping (tabel system_account_mappings). Dibangkitkan; query khusus taruh di paket domain. */
public interface SystemAccountMappingRepository extends JpaRepository<SystemAccountMapping, String>, JpaSpecificationExecutor<SystemAccountMapping> {
}
