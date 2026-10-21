package id.enterpriseapps.erp.purchase;

import jakarta.persistence.EntityManager;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;

/**
 * Helper kecil akses DB untuk paket pembelian.
 *
 * <p>{@link #flush(EntityManager)}: flush eksplisit dengan translasi exception Hibernate ke
 * hierarki Spring, sehingga pelanggaran unik/FK saat menyimpan dokumen dipetakan
 * {@code ErpExceptionHandler} ke pesan ENTARIS (P2002/P2003 → 400), bukan 500 generik
 * (service bukan {@code @Repository}, jadi tidak ditranslasi otomatis).
 */
final class PurchaseDb {

    private static final HibernateJpaDialect DIALECT = new HibernateJpaDialect();

    private PurchaseDb() {
    }

    static void flush(EntityManager em) {
        try {
            em.flush();
        } catch (RuntimeException e) {
            DataAccessException translated = DIALECT.translateExceptionIfPossible(e);
            throw translated != null ? translated : e;
        }
    }

    /** True bila string terisi (padanan truthy JS untuk id/teks opsional). */
    static boolean has(String s) {
        return s != null && !s.isEmpty();
    }
}
