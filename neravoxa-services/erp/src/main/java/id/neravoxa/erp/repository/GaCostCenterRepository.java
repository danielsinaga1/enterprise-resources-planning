package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaCostCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaCostCenter (tabel ga_cost_centers). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaCostCenterRepository extends JpaRepository<GaCostCenter, String>, JpaSpecificationExecutor<GaCostCenter> {
}
