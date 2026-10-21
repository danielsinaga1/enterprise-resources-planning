package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.PaymentRequestLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PaymentRequestLine (tabel payment_request_lines). Dibangkitkan; query khusus taruh di paket domain. */
public interface PaymentRequestLineRepository extends JpaRepository<PaymentRequestLine, String>, JpaSpecificationExecutor<PaymentRequestLine> {
}
