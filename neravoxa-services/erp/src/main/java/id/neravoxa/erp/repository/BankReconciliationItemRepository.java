package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.BankReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository BankReconciliationItem (tabel bank_reconciliation_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface BankReconciliationItemRepository extends JpaRepository<BankReconciliationItem, String>, JpaSpecificationExecutor<BankReconciliationItem> {
}
