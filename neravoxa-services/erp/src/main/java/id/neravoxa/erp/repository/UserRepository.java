package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository User (tabel users). Dibangkitkan; query khusus taruh di paket domain. */
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
}
