package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaInventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaInventoryMovement (tabel ga_inventory_movements). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaInventoryMovementRepository extends JpaRepository<GaInventoryMovement, String>, JpaSpecificationExecutor<GaInventoryMovement> {
}
