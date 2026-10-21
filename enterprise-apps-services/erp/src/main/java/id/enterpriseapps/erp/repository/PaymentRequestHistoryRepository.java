package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.PaymentRequestHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PaymentRequestHistory (tabel payment_request_history). Dibangkitkan; query khusus taruh di paket domain. */
public interface PaymentRequestHistoryRepository extends JpaRepository<PaymentRequestHistory, String>, JpaSpecificationExecutor<PaymentRequestHistory> {
}
