package id.enterpriseapps.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps irms_users (read-only snapshot of auth data) in sync with auth_users.
 * Uses a native INSERT ... ON CONFLICT upsert to set explicit IDs (bypasses JPA
 * @GeneratedValue for snapshot inserts).
 *
 * NOTE on the catch blocks below: they keep a failure from propagating, but they do
 * NOT make it harmless. A failed statement marks the surrounding transaction
 * rollback-only, so the caller still fails at commit with UnexpectedRollbackException.
 * Treat any warning logged here as a real bug, not as a tolerated hiccup.
 */
@Service
@Slf4j
public class IrmsUserSyncService {

    @PersistenceContext(unitName = "primary")
    private EntityManager em;

    private static final String UPSERT_SQL =
        "INSERT INTO irms_users " +
        "  (id, npk, npp, name, email, mobile_phone, rf_id, parent_id, job_id, dept_id, division_id, status) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET " +
        "  npk = EXCLUDED.npk, npp = EXCLUDED.npp, name = EXCLUDED.name, email = EXCLUDED.email, " +
        "  mobile_phone = EXCLUDED.mobile_phone, rf_id = EXCLUDED.rf_id, parent_id = EXCLUDED.parent_id, " +
        "  job_id = EXCLUDED.job_id, dept_id = EXCLUDED.dept_id, division_id = EXCLUDED.division_id, " +
        "  status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP";

    @Transactional(transactionManager = "transactionManager")
    public void syncCreate(Long id, String npk, String npp, String name, String email,
                           String mobilePhone, String rfId, Long parentId,
                           Long jobId, Long deptId, Long divisionId, Short status) {
        try {
            em.createNativeQuery(UPSERT_SQL)
                .setParameter(1, id)
                .setParameter(2, npk)
                .setParameter(3, npp)
                .setParameter(4, name)
                .setParameter(5, email)
                .setParameter(6, mobilePhone)
                .setParameter(7, rfId)
                .setParameter(8, parentId)
                .setParameter(9, jobId)
                .setParameter(10, deptId)
                .setParameter(11, divisionId)
                .setParameter(12, status)
                .executeUpdate();
            log.info("irms_users: upserted snapshot for user id={}", id);
        } catch (Exception e) {
            log.warn("irms_users sync create failed for id={}: {}", id, e.getMessage());
        }
    }

    @Transactional(transactionManager = "transactionManager")
    public void syncUpdate(Long id, String npk, String npp, String name, String email,
                           String mobilePhone, String rfId, Long parentId,
                           Long jobId, Long deptId, Long divisionId, Short status) {
        // Reuse same upsert — ON DUPLICATE KEY handles update case
        syncCreate(id, npk, npp, name, email, mobilePhone, rfId, parentId, jobId, deptId, divisionId, status);
    }

    @Transactional(transactionManager = "transactionManager")
    public void syncDelete(Long id) {
        try {
            em.createNativeQuery("DELETE FROM irms_users WHERE id = ?")
                .setParameter(1, id)
                .executeUpdate();
            log.info("irms_users: deleted snapshot for user id={}", id);
        } catch (Exception e) {
            log.warn("irms_users sync delete failed for id={}: {}", id, e.getMessage());
        }
    }

    @Transactional(transactionManager = "transactionManager")
    public void syncStatus(Long id, Short status) {
        try {
            em.createNativeQuery("UPDATE irms_users SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
                .setParameter(1, status)
                .setParameter(2, id)
                .executeUpdate();
            log.info("irms_users: synced status={} for user id={}", status, id);
        } catch (Exception e) {
            log.warn("irms_users sync status failed for id={}: {}", id, e.getMessage());
        }
    }
}
