package id.neravoxa.erp.accounting;

import id.neravoxa.erp.core.ErpException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import java.sql.SQLException;

/**
 * Pemetaan pelanggaran constraint saat flush ke pesan ENTARIS.
 *
 * <p>Exception dari {@code em.flush()} di dalam service tidak diterjemahkan Spring menjadi
 * {@code DataIntegrityViolationException} (hanya terjadi saat commit), sehingga tanpa helper ini
 * pelanggaran unik/FK berakhir sebagai 500 generik. Padanan kode Prisma P2002/P2003 di
 * {@code handleRouteError}.
 */
final class AccountingDb {

    static final String UNIQUE_VIOLATION = "23505";
    static final String FK_VIOLATION = "23503";
    static final String DUPLICATE_MESSAGE = "Data duplikat terdeteksi. Silakan coba lagi.";
    static final String INVALID_REFERENCE_MESSAGE = "Referensi data tidak valid. Periksa data yang dipilih.";

    private AccountingDb() {
    }

    /** SQLSTATE pertama di rantai cause, atau null. */
    static String sqlState(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException s && s.getSQLState() != null) {
                return s.getSQLState();
            }
            if (t instanceof org.hibernate.JDBCException j && j.getSQLState() != null) {
                return j.getSQLState();
            }
            t = t.getCause();
        }
        return null;
    }

    static boolean isUniqueViolation(Throwable e) {
        return UNIQUE_VIOLATION.equals(sqlState(e));
    }

    static boolean isForeignKeyViolation(Throwable e) {
        return FK_VIOLATION.equals(sqlState(e));
    }

    /** {@code em.flush()} dengan P2002/P2003 → 400 seperti {@code handleRouteError}. */
    static void flush(EntityManager em) {
        try {
            em.flush();
        } catch (PersistenceException e) {
            throw translate(e);
        }
    }

    static RuntimeException translate(RuntimeException e) {
        if (isUniqueViolation(e)) {
            return ErpException.business(DUPLICATE_MESSAGE);
        }
        if (isForeignKeyViolation(e)) {
            return ErpException.business(INVALID_REFERENCE_MESSAGE);
        }
        return e;
    }

    /** Pesan akar (baris pertama) untuk dilaporkan ke klien seperti {@code error.message}. */
    static String rootMessage(Throwable e) {
        if (e instanceof ErpException) {
            return e.getMessage();
        }
        Throwable t = e;
        String msg = e.getMessage();
        while (t != null) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                msg = t.getMessage();
            }
            t = t.getCause();
        }
        if (msg == null) {
            return null;
        }
        int nl = msg.indexOf('\n');
        return (nl < 0 ? msg : msg.substring(0, nl)).trim();
    }
}
