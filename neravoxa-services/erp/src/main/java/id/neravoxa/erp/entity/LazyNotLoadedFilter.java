package id.neravoxa.erp.entity;

import org.hibernate.Hibernate;

/**
 * Filter {@code @JsonInclude(CUSTOM)} untuk field relasi entity ERP.
 *
 * <p>Meniru semantik {@code include} Prisma: relasi yang tidak di-fetch (proxy/koleksi
 * lazy yang belum terinisialisasi) TIDAK ditulis ke JSON, sedangkan relasi yang
 * di-fetch selalu ditulis — termasuk daftar kosong dan nilai null. Jackson memanggil
 * {@link #equals(Object)} dengan nilai properti; {@code true} berarti "lewati".
 */
public class LazyNotLoadedFilter {

    @Override
    public boolean equals(Object value) {
        return value != null && !Hibernate.isInitialized(value);
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
