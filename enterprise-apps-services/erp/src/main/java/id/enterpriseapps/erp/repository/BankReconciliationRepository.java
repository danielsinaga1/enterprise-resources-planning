package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.BankReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository BankReconciliation (tabel bank_reconciliations). Dibangkitkan; query khusus taruh di paket domain. */
public interface BankReconciliationRepository extends JpaRepository<BankReconciliation, String>, JpaSpecificationExecutor<BankReconciliation> {
}
