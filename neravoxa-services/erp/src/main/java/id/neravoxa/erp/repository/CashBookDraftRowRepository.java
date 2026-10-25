package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.CashBookDraftRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository CashBookDraftRow (tabel cash_book_draft_rows). Dibangkitkan; query khusus taruh di paket domain. */
public interface CashBookDraftRowRepository extends JpaRepository<CashBookDraftRow, String>, JpaSpecificationExecutor<CashBookDraftRow> {
}
