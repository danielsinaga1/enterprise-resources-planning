package id.enterpriseapps.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps auth_job_titles (db_auth) in sync with irms_job_titles (db_irms), the
 * table actually managed by the Master Data "Job Titles" screen.
 *
 * auth_users.job_id carries a FOREIGN KEY to auth_job_titles (fk_auth_users_job),
 * but job titles are only ever created/edited against irms_job_titles. Without
 * this sync, any job title added after the two tables were seeded is unusable
 * when creating/editing a user — Add User 500s with a FK violation.
 *
 * Uses a native INSERT ... ON CONFLICT upsert to set explicit IDs (bypasses JPA
 * @GeneratedValue), mirroring IrmsUserSyncService's pattern for the same reason:
 * the mirrored row must share the source row's id.
 */
@Service
@Slf4j
public class AuthJobTitleSyncService {

    @PersistenceContext(unitName = "auth")
    private EntityManager em;

    private static final String UPSERT_SQL =
        "INSERT INTO auth_job_titles (id, name, created_at, updated_at) " +
        "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
        "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = CURRENT_TIMESTAMP";

    @Transactional(transactionManager = "authTransactionManager")
    public void sync(Long id, String name) {
        try {
            em.createNativeQuery(UPSERT_SQL)
                .setParameter(1, id)
                .setParameter(2, name)
                .executeUpdate();
        } catch (Exception e) {
            log.warn("auth_job_titles sync failed for id={} name={}: {}", id, name, e.getMessage());
        }
    }

    @Transactional(transactionManager = "authTransactionManager")
    public void delete(Long id) {
        try {
            // ON DELETE SET NULL on fk_auth_users_job — safe even if referenced.
            em.createNativeQuery("DELETE FROM auth_job_titles WHERE id = ?")
                .setParameter(1, id)
                .executeUpdate();
        } catch (Exception e) {
            log.warn("auth_job_titles delete failed for id={}: {}", id, e.getMessage());
        }
    }
}
