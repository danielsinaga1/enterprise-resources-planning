package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaMaterialRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaMaterialRequest (tabel ga_material_requests). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaMaterialRequestRepository extends JpaRepository<GaMaterialRequest, String>, JpaSpecificationExecutor<GaMaterialRequest> {
}
