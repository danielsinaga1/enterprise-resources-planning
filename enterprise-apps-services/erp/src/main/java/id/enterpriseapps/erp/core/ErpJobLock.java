package id.enterpriseapps.erp.core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Kunci job terjadwal lintas replika — port {@code withAdvisoryLock} di {@code jobs/scheduler.ts}.
 *
 * <p>Job berjalan di dalam satu transaksi db_erp dengan {@code pg_try_advisory_xact_lock}; replika
 * lain yang tidak mendapat kunci melewati tick tersebut. Lock lepas saat COMMIT/ROLLBACK.
 * Service {@code @ErpTx} yang dipanggil di dalam job ikut transaksi ini.
 */
@Slf4j
@Component
public class ErpJobLock {

    private final TransactionTemplate tx;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public ErpJobLock(@Qualifier(ErpTx.MANAGER) PlatformTransactionManager tm) {
        this.tx = new TransactionTemplate(tm);
        this.tx.setTimeout(5 * 60);
    }

    /** Jalankan {@code job} bila kunci {@code jobName} didapat; null bila dilewati/gagal. */
    public <T> T runExclusive(String jobName, Supplier<T> job) {
        try {
            return tx.execute(status -> {
                Object locked = em.createNativeQuery("SELECT pg_try_advisory_xact_lock(:k)")
                        .setParameter("k", (long) jobName.hashCode() + 7_000_000_000L)
                        .getSingleResult();
                if (!Boolean.TRUE.equals(locked)) {
                    log.debug("ERP job {} dilewati (lock dipegang instance lain)", jobName);
                    return null;
                }
                return job.get();
            });
        } catch (RuntimeException e) {
            log.error("ERP job {} gagal", jobName, e);
            return null;
        }
    }

    public void runExclusive(String jobName, Runnable job) {
        runExclusive(jobName, () -> {
            job.run();
            return Boolean.TRUE;
        });
    }
}
