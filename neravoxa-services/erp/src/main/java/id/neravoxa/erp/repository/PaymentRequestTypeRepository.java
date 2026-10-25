package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.PaymentRequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PaymentRequestType (tabel payment_request_types). Dibangkitkan; query khusus taruh di paket domain. */
public interface PaymentRequestTypeRepository extends JpaRepository<PaymentRequestType, String>, JpaSpecificationExecutor<PaymentRequestType> {
}
