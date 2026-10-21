package id.enterpriseapps.smartdocs.dto;

import java.math.BigDecimal;

/**
 * Projection untuk query agregat laporan.
 *
 * <p>Sengaja dipisah per bentuk (jumlah uang vs cacah) alih-alih satu antarmuka
 * serba bisa: projection native query gagal saat getter menunjuk kolom yang tidak
 * ada di SELECT, jadi antarmuka gabungan akan meledak di separuh query.
 */
public final class SmartDocsBuckets {

    private SmartDocsBuckets() {
    }

    /** Satu kelompok beserta jumlah uangnya, mis. belanja per bulan. */
    public interface Amount {
        String getLabel();

        BigDecimal getTotal();
    }

    /** Satu kelompok beserta cacah dokumennya, mis. jumlah dokumen per kategori. */
    public interface Count {
        String getLabel();

        Long getTotalCount();
    }

    /** Subtotal item milik satu dokumen; dipakai agar daftar tidak N+1 query. */
    public interface DocumentSubtotal {
        Integer getDocumentId();

        BigDecimal getTotal();
    }
}
