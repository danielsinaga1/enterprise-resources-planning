package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.PaymentSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PaymentSplit (tabel payment_splits). Dibangkitkan; query khusus taruh di paket domain. */
public interface PaymentSplitRepository extends JpaRepository<PaymentSplit, String>, JpaSpecificationExecutor<PaymentSplit> {
}
