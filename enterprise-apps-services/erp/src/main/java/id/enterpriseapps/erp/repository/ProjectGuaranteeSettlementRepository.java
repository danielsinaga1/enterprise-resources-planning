package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.ProjectGuaranteeSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProjectGuaranteeSettlement (tabel project_guarantee_settlements). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProjectGuaranteeSettlementRepository extends JpaRepository<ProjectGuaranteeSettlement, String>, JpaSpecificationExecutor<ProjectGuaranteeSettlement> {
}
