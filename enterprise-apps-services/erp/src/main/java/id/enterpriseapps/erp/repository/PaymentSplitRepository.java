package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.PaymentSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PaymentSplit (tabel payment_splits). Dibangkitkan; query khusus taruh di paket domain. */
public interface PaymentSplitRepository extends JpaRepository<PaymentSplit, String>, JpaSpecificationExecutor<PaymentSplit> {
}
