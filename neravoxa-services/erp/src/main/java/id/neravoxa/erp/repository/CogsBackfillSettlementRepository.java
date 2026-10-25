package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.CogsBackfillSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository CogsBackfillSettlement (tabel cogs_backfill_settlements). Dibangkitkan; query khusus taruh di paket domain. */
public interface CogsBackfillSettlementRepository extends JpaRepository<CogsBackfillSettlement, String>, JpaSpecificationExecutor<CogsBackfillSettlement> {
}
