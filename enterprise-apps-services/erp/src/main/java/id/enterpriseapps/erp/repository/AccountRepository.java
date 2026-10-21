package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository Account (tabel accounts). Dibangkitkan; query khusus taruh di paket domain. */
public interface AccountRepository extends JpaRepository<Account, String>, JpaSpecificationExecutor<Account> {
}
