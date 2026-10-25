package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.AccountingLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository AccountingLedgerEntry (tabel accounting_ledger_entries). Dibangkitkan; query khusus taruh di paket domain. */
public interface AccountingLedgerEntryRepository extends JpaRepository<AccountingLedgerEntry, String>, JpaSpecificationExecutor<AccountingLedgerEntry> {
}
