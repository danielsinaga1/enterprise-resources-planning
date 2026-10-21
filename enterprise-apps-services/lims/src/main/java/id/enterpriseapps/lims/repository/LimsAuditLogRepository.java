package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Jejak audit bersifat append-only, jadi repositori ini sengaja hanya memakai
 * {@code save} (insert) dan pembacaan. Jangan menambahkan metode penghapusan —
 * database menolaknya lewat trigger {@code trg_lims_audit_logs_immutable}.
 */
@Repository
public interface LimsAuditLogRepository extends JpaRepository<LimsAuditLog, Long> {

    /**
     * Seluruh jejak satu laporan, termasuk perubahan pada detail analisisnya —
     * baris laporan pun menyimpan dirinya sendiri sebagai induk, sehingga satu
     * query cukup.
     */
    List<LimsAuditLog> findByParentTypeAndParentIdOrderByCreatedAtDesc(String parentType, Long parentId);

    Page<LimsAuditLog> findByParentTypeAndParentIdOrderByCreatedAtDesc(String parentType, Long parentId, Pageable pageable);

    List<LimsAuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);
}
