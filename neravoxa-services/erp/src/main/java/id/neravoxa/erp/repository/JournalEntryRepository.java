package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository JournalEntry (tabel journal_entries). Dibangkitkan; query khusus taruh di paket domain. */
public interface JournalEntryRepository extends JpaRepository<JournalEntry, String>, JpaSpecificationExecutor<JournalEntry> {
}
