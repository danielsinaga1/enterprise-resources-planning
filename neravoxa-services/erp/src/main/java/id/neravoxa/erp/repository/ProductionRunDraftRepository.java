package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.ProductionRunDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProductionRunDraft (tabel production_run_drafts). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProductionRunDraftRepository extends JpaRepository<ProductionRunDraft, String>, JpaSpecificationExecutor<ProductionRunDraft> {
}
