package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository RolePermission (tabel role_permissions). Dibangkitkan; query khusus taruh di paket domain. */
public interface RolePermissionRepository extends JpaRepository<RolePermission, String>, JpaSpecificationExecutor<RolePermission> {
}
