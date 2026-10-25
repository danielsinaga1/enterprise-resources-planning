package id.neravoxa.erp.sales;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Helper kecil bersama paket penjualan: semantik JS/Prisma yang sering muncul di sumber ENTARIS
 * ({@code x || null}, {@code clearable}, {@code Number(decimal)}, {@code new Date(s)}) dan flush
 * yang menerjemahkan pelanggaran constraint agar dipetakan seperti P2002/P2003.
 */
final class SalesSupport {

    private SalesSupport() {
    }

    /** {@code x || null} untuk teks: null/kosong → null. */
    static String orNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /** True bila teks berisi (padanan truthiness string di JS). */
    static boolean hasText(String s) {
        return s != null && !s.isEmpty();
    }

    /**
     * Port {@code utils/clearable.ts} — dipanggil hanya bila field dikirim:
     * null/blank → null (hapus), selain itu teks yang di-trim.
     */
    static String clearable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** {@code Number(decimal)} di JSON: nol di belakang koma dibuang, tanpa notasi eksponen. */
    static BigDecimal num(BigDecimal v) {
        if (v == null) {
            return null;
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    /** {@code d.toDecimalPlaces(2).toNumber()} — nilai uang ringkasan (null → 0). */
    static BigDecimal money(BigDecimal v) {
        return num(Money.round2(Money.nz(v)));
    }

    /** {@code new Date(s)} yang valid; null bila kosong atau tidak bisa diparse (padanan cek isNaN). */
    static Instant parseDate(String s) {
        return hasText(s) ? ErpDates.tryInstant(s) : null;
    }

    /**
     * {@code em.flush()} dengan pelanggaran constraint (SQLSTATE 23xxx) diterjemahkan ke
     * {@link DataIntegrityViolationException}. Tanpa ini, flush di tengah transaksi melempar
     * PersistenceException mentah sehingga kode unik/FK berakhir 500 — padahal Prisma (P2002/P2003)
     * menjawab 400 "Data duplikat terdeteksi…" / "Referensi data tidak valid…" (dipetakan ErpExceptionHandler).
     */
    static void flush(EntityManager em) {
        try {
            em.flush();
        } catch (PersistenceException e) {
            String state = sqlState(e);
            if (state != null && state.startsWith("23")) {
                throw new DataIntegrityViolationException(e.getMessage(), e);
            }
            throw e;
        }
    }

    private static String sqlState(Throwable e) {
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
