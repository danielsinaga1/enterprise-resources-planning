package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ApprovalRequest (tabel approval_requests). Dibangkitkan; query khusus taruh di paket domain. */
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, String>, JpaSpecificationExecutor<ApprovalRequest> {
}
