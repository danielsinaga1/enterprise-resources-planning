package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.CustomerDepositApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository CustomerDepositApplication (tabel customer_deposit_applications). Dibangkitkan; query khusus taruh di paket domain. */
public interface CustomerDepositApplicationRepository extends JpaRepository<CustomerDepositApplication, String>, JpaSpecificationExecutor<CustomerDepositApplication> {
}
