package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository AuditLog (tabel audit_trail). Dibangkitkan; query khusus taruh di paket domain. */
public interface AuditLogRepository extends JpaRepository<AuditLog, String>, JpaSpecificationExecutor<AuditLog> {
}
