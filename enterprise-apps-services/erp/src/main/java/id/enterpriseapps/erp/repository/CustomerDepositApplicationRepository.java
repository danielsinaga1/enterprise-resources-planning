package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.CustomerDepositApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository CustomerDepositApplication (tabel customer_deposit_applications). Dibangkitkan; query khusus taruh di paket domain. */
public interface CustomerDepositApplicationRepository extends JpaRepository<CustomerDepositApplication, String>, JpaSpecificationExecutor<CustomerDepositApplication> {
}
