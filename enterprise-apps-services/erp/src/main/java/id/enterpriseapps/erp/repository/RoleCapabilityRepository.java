package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.RoleCapability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository RoleCapability (tabel role_capabilities). Dibangkitkan; query khusus taruh di paket domain. */
public interface RoleCapabilityRepository extends JpaRepository<RoleCapability, String>, JpaSpecificationExecutor<RoleCapability> {
}
