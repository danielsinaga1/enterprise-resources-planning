package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.PaymentRequestSettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PaymentRequestSettlementLine (tabel payment_request_settlement_lines). Dibangkitkan; query khusus taruh di paket domain. */
public interface PaymentRequestSettlementLineRepository extends JpaRepository<PaymentRequestSettlementLine, String>, JpaSpecificationExecutor<PaymentRequestSettlementLine> {
}
