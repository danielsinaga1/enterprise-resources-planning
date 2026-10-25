package id.neravoxa.erp.eam.ops;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stok GA per lokasi ({@code ga_stock_by_location}) — terpisah dari persediaan akuntansi: tanpa jurnal,
 * tanpa WAC. Semua perubahan lewat SQL atomik (upsert ON CONFLICT) agar dua mutasi paralel tidak saling
 * menimpa; total stok item = jumlah baris per lokasi.
 */
final class GaStock {

    private GaStock() {
    }

    /**
     * Tambah/kurangi stok satu lokasi secara atomik dan kembalikan kuantitas barunya. Baris dibuat bila
     * belum ada (bernilai {@code delta}). Penjaga "tidak boleh minus" menjadi tanggung jawab pemanggil.
     */
    static BigDecimal adjust(EntityManager em, String itemId, String locationId, BigDecimal delta) {
        Object v = em.createNativeQuery("INSERT INTO ga_stock_by_location (id, item_id, location_id, quantity, updated_at) "
                        + "VALUES (gen_random_uuid()::text, :item, :loc, CAST(:d AS numeric), now()) "
                        + "ON CONFLICT (item_id, location_id) DO UPDATE SET "
                        + "quantity = ga_stock_by_location.quantity + CAST(:d AS numeric), updated_at = now() "
                        + "RETURNING quantity")
                .setParameter("item", itemId)
                .setParameter("loc", locationId)
                .setParameter("d", delta)
                .getSingleResult();
        return toDecimal(v);
    }

    /** Kuantitas saat ini (dikunci FOR UPDATE di dalam transaksi); null bila baris belum ada. */
    static BigDecimal lockCurrent(EntityManager em, String itemId, String locationId) {
        List<?> rows = em.createNativeQuery("SELECT quantity FROM ga_stock_by_location "
                        + "WHERE item_id = :item AND location_id = :loc FOR UPDATE")
                .setParameter("item", itemId)
                .setParameter("loc", locationId)
                .getResultList();
        return rows.isEmpty() ? null : toDecimal(rows.get(0));
    }

    /** Set stok absolut (opname) — upsert atomik. */
    static void set(EntityManager em, String itemId, String locationId, BigDecimal quantity) {
        em.createNativeQuery("INSERT INTO ga_stock_by_location (id, item_id, location_id, quantity, updated_at) "
                        + "VALUES (gen_random_uuid()::text, :item, :loc, CAST(:q AS numeric), now()) "
                        + "ON CONFLICT (item_id, location_id) DO UPDATE SET quantity = CAST(:q AS numeric), updated_at = now()")
                .setParameter("item", itemId)
                .setParameter("loc", locationId)
                .setParameter("q", quantity)
                .executeUpdate();
    }

    static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(v.toString());
    }
}
