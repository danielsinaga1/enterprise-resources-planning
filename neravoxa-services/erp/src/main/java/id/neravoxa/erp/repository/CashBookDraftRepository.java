package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.CashBookDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository CashBookDraft (tabel cash_book_drafts). Dibangkitkan; query khusus taruh di paket domain. */
public interface CashBookDraftRepository extends JpaRepository<CashBookDraft, String>, JpaSpecificationExecutor<CashBookDraft> {
}
