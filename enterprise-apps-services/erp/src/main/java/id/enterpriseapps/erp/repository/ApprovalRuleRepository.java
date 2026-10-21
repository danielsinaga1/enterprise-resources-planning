package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.ApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ApprovalRule (tabel approval_rules). Dibangkitkan; query khusus taruh di paket domain. */
public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, String>, JpaSpecificationExecutor<ApprovalRule> {
}
