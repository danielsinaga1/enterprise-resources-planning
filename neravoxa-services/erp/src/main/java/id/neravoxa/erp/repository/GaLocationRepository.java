package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaLocation (tabel ga_locations). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaLocationRepository extends JpaRepository<GaLocation, String>, JpaSpecificationExecutor<GaLocation> {
}
