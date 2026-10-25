package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.CogsBackfillQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository CogsBackfillQueue (tabel cogs_backfill_queue). Dibangkitkan; query khusus taruh di paket domain. */
public interface CogsBackfillQueueRepository extends JpaRepository<CogsBackfillQueue, String>, JpaSpecificationExecutor<CogsBackfillQueue> {
}
