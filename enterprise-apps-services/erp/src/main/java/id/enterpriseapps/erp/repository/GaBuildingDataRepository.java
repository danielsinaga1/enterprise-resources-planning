package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaBuildingData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaBuildingData (tabel ga_building_data). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaBuildingDataRepository extends JpaRepository<GaBuildingData, String>, JpaSpecificationExecutor<GaBuildingData> {
}
