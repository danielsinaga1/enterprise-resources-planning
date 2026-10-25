package id.neravoxa.erp.core.inventory;

import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stok per gudang + stok total item — port {@code utils/warehouseStock.ts}.
 *
 * <p>Semua perubahan kuantitas stok WAJIB lewat helper ini (SQL increment atomik), bukan setter
 * entity, agar tidak terjadi lost update antar transaksi:
 * <ul>
 *   <li>{@link #adjustWarehouseStock} → {@code inventory_stocks.current_stock} (upsert)</li>
 *   <li>{@link #adjustItemStock} → {@code inventory_items.current_stock} (total lintas gudang)</li>
 * </ul>
 */
@Service
public class ErpWarehouseStockService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** {@code preferred} bila diisi, selain itu gudang default aktif; null bila tidak ada default. */
    @ErpTx(readOnly = true)
    public String resolveWarehouseId(String preferred) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        List<String> ids = em.createQuery(
                        "select w.id from Warehouse w where w.isDefault = true and w.isActive = true", String.class)
                .setMaxResults(1).getResultList();
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * Tambah delta stok item di gudang (positif = masuk, negatif = keluar). Upsert baris
     * inventory_stocks. No-op bila warehouseId null atau delta 0.
     */
    @ErpTx
    public void adjustWarehouseStock(String itemId, String warehouseId, BigDecimal delta) {
        if (warehouseId == null || delta == null || delta.signum() == 0) {
            return;
        }
        em.flush();
        em.createNativeQuery("INSERT INTO inventory_stocks (id, item_id, warehouse_id, current_stock, created_at, updated_at) "
                        + "VALUES (:id, :item, :wh, :d, now(), now()) "
                        + "ON CONFLICT (item_id, warehouse_id) DO UPDATE SET current_stock = inventory_stocks.current_stock + EXCLUDED.current_stock, "
                        + "updated_at = now()")
                .setParameter("id", UUID.randomUUID().toString())
                .setParameter("item", itemId)
                .setParameter("wh", warehouseId)
                .setParameter("d", delta)
                .executeUpdate();
    }

    /** Tambah delta stok total item ({@code inventory_items.current_stock}). */
    @ErpTx
    public void adjustItemStock(String itemId, BigDecimal delta) {
        if (delta == null || delta.signum() == 0) {
            return;
        }
        em.flush();
        em.createNativeQuery("UPDATE inventory_items SET current_stock = current_stock + :d, updated_at = now() WHERE id = :id")
                .setParameter("d", delta)
                .setParameter("id", itemId)
                .executeUpdate();
    }

    /** Set harga rata-rata item (setelah perhitungan WAC). */
    @ErpTx
    public void setAverageCost(String itemId, BigDecimal averageCost) {
        em.flush();
        em.createNativeQuery("UPDATE inventory_items SET average_cost = :c, updated_at = now() WHERE id = :id")
                .setParameter("c", Money.round2(averageCost))
                .setParameter("id", itemId)
                .executeUpdate();
    }

    /** Stok total & avg cost terkini langsung dari DB (bukan dari entity yang mungkin basi). */
    @ErpTx(readOnly = true)
    public ItemStock currentItemStock(String itemId) {
        em.flush();
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT current_stock, average_cost FROM inventory_items WHERE id = :id")
                .setParameter("id", itemId).getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] r = rows.get(0);
        return new ItemStock(Money.of(r[0]), Money.of(r[1]));
    }

    public record ItemStock(BigDecimal currentStock, BigDecimal averageCost) {
    }

    public record Pair(String itemId, String warehouseId) {
    }

    public static String key(String itemId, String warehouseId) {
        return itemId + "::" + warehouseId;
    }

    /**
     * Stok per gudang untuk pasangan (item, gudang); kunci {@link #key(String, String)}.
     * Baris yang belum ada dilaporkan 0. Pakai ini (bukan stok total item) untuk cek kecukupan stok.
     */
    @ErpTx(readOnly = true)
    public Map<String, BigDecimal> getWarehouseStockMap(Collection<Pair> pairs) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (pairs == null || pairs.isEmpty()) {
            return map;
        }
        em.flush();
        List<String> items = pairs.stream().map(Pair::itemId).distinct().toList();
        List<String> whs = pairs.stream().map(Pair::warehouseId).distinct().toList();
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT item_id, warehouse_id, current_stock FROM inventory_stocks "
                                + "WHERE item_id IN (:items) AND warehouse_id IN (:whs)")
                .setParameter("items", items).setParameter("whs", whs).getResultList();
        java.util.Set<String> wanted = new java.util.HashSet<>();
        pairs.forEach(p -> wanted.add(key(p.itemId(), p.warehouseId())));
        for (Object[] r : rows) {
            String k = key((String) r[0], (String) r[1]);
            if (wanted.contains(k)) {
                map.put(k, Money.of(r[2]));
            }
        }
        return map;
    }

    /** Stok satu item di satu gudang (0 bila belum pernah ada). */
    @ErpTx(readOnly = true)
    public BigDecimal warehouseStock(String itemId, String warehouseId) {
        if (warehouseId == null) {
            return BigDecimal.ZERO;
        }
        return getWarehouseStockMap(List.of(new Pair(itemId, warehouseId)))
                .getOrDefault(key(itemId, warehouseId), BigDecimal.ZERO);
    }
}
