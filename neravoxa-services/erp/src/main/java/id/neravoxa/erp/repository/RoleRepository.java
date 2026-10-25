package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository Role (tabel roles). Dibangkitkan; query khusus taruh di paket domain. */
public interface RoleRepository extends JpaRepository<Role, String>, JpaSpecificationExecutor<Role> {
}
