package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaMaterialRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaMaterialRequestItem (tabel ga_material_request_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaMaterialRequestItemRepository extends JpaRepository<GaMaterialRequestItem, String>, JpaSpecificationExecutor<GaMaterialRequestItem> {
}
