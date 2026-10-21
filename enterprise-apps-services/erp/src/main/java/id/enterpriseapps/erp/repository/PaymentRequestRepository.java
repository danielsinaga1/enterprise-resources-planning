package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PaymentRequest (tabel payment_requests). Dibangkitkan; query khusus taruh di paket domain. */
public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, String>, JpaSpecificationExecutor<PaymentRequest> {
}
