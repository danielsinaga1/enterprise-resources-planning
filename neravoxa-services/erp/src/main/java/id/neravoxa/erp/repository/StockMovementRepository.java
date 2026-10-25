package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository StockMovement (tabel stock_movements). Dibangkitkan; query khusus taruh di paket domain. */
public interface StockMovementRepository extends JpaRepository<StockMovement, String>, JpaSpecificationExecutor<StockMovement> {
}
