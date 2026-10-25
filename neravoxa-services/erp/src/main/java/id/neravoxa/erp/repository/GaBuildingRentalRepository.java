package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaBuildingRental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaBuildingRental (tabel ga_building_rentals). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaBuildingRentalRepository extends JpaRepository<GaBuildingRental, String>, JpaSpecificationExecutor<GaBuildingRental> {
}
