package id.enterpriseapps.erp.production;

import id.enterpriseapps.erp.core.ErpException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import java.sql.SQLException;

/** Helper akses data kecil untuk paket produksi. */
final class ProdDb {

    private ProdDb() {
    }

    /**
     * Flush sekarang; pelanggaran unik (P2002 Prisma) → 409 dengan pesan rute ENTARIS. Pelanggaran
     * lain diteruskan (FK → 400 lewat ErpExceptionHandler).
     */
    static void flushOrConflict(EntityManager em, String conflictMessage) {
        try {
            em.flush();
        } catch (PersistenceException e) {
            if ("23505".equals(sqlState(e))) {
                throw ErpException.conflict(conflictMessage);
            }
            throw e;
        }
    }

    static String sqlState(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException s && s.getSQLState() != null) {
                return s.getSQLState();
            }
            t = t.getCause();
        }
        return null;
    }
}
