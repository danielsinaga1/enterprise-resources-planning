package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.VendorDepositApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository VendorDepositApplication (tabel vendor_deposit_applications). Dibangkitkan; query khusus taruh di paket domain. */
public interface VendorDepositApplicationRepository extends JpaRepository<VendorDepositApplication, String>, JpaSpecificationExecutor<VendorDepositApplication> {
}
