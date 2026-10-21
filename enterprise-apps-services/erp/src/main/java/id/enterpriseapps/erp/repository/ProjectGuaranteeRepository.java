package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.ProjectGuarantee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ProjectGuarantee (tabel project_guarantees). Dibangkitkan; query khusus taruh di paket domain. */
public interface ProjectGuaranteeRepository extends JpaRepository<ProjectGuarantee, String>, JpaSpecificationExecutor<ProjectGuarantee> {
}
