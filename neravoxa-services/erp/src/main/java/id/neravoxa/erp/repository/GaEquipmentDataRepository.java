package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaEquipmentData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaEquipmentData (tabel ga_equipment_data). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaEquipmentDataRepository extends JpaRepository<GaEquipmentData, String>, JpaSpecificationExecutor<GaEquipmentData> {
}
