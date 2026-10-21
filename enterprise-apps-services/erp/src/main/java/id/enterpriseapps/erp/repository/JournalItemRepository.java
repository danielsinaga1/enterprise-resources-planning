package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.JournalItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository JournalItem (tabel journal_entry_accounts). Dibangkitkan; query khusus taruh di paket domain. */
public interface JournalItemRepository extends JpaRepository<JournalItem, String>, JpaSpecificationExecutor<JournalItem> {
}
