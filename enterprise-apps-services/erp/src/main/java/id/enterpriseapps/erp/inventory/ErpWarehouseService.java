package id.enterpriseapps.erp.inventory;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.inventory.ErpStockSnapshotService;
import id.enterpriseapps.erp.entity.InventoryStock;
import id.enterpriseapps.erp.entity.Warehouse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Master gudang + stok per gudang — port {@code routes/warehouses.ts}. */
@Service
@RequiredArgsConstructor
public class ErpWarehouseService {

    private final ErpStockSnapshotService snapshots;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET /warehouses?active=true|false — tiap gudang + itemCount (baris stok) & totalStock. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(String active) {
        String jpql = "select w from Warehouse w"
                + ("true".equals(active) ? " where w.isActive = true" : "false".equals(active) ? " where w.isActive = false" : "")
                + " order by w.sortOrder asc, w.name asc";
        List<Warehouse> warehouses = em.createQuery(jpql, Warehouse.class).getResultList();
        Map<String, Object[]> agg = new HashMap<>();
        if (!warehouses.isEmpty()) {
            em.createQuery("select s.warehouseId, count(s), sum(s.currentStock) from InventoryStock s "
                            + "where s.warehouseId in :ids group by s.warehouseId", Object[].class)
                    .setParameter("ids", warehouses.stream().map(Warehouse::getId).toList())
                    .getResultList()
                    .forEach(r -> agg.put((String) r[0], r));
        }
        List<Map<String, Object>> data = warehouses.stream().map(w -> {
            Map<String, Object> m = InvJson.scalars(w);
            Object[] a = agg.get(w.getId());
            m.put("itemCount", a == null ? 0L : ((Number) a[1]).longValue());
            m.put("totalStock", a == null ? BigDecimal.ZERO : Money.nz((BigDecimal) a[2]));
            return m;
        }).toList();
        return Map.of("data", data);
    }

    /**
     * GET /warehouses/stock-snapshot — stok per gudang per akhir tanggal (rekonstruksi mundur dari
     * riwayat gerakan, lihat kernel). Default tanggal = hari ini (UTC, seperti toISOString()).
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> snapshot(String date, String warehouseIds) {
        String dateStr = date != null && !date.isEmpty() ? date : ErpDates.isoDay(Instant.now());
        Instant parsed = ErpDates.tryInstant(dateStr);
        if (parsed == null) {
            throw ErpException.business("Parameter date tidak valid.");
        }
        List<String> ids = warehouseIds != null && !warehouseIds.isEmpty()
                ? Arrays.stream(warehouseIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : null;
        // Kernel butuh YYYY-MM-DD; masukan tanggal-waktu lain dinormalkan ke hari UTC-nya.
        String isoDay = ErpDates.isIsoDate(dateStr) ? dateStr : ErpDates.isoDay(parsed);
        Map<String, Object> out = snapshots.build(isoDay, ids);
        out.put("asOfDate", dateStr);
        return out;
    }

    /** GET /warehouses/:id — detail + stok tidak nol (item ringkas), urut stok terbanyak. */
    @ErpTx(readOnly = true)
    public Map<String, Object> detail(String id) {
        Warehouse wh = em.find(Warehouse.class, id);
        if (wh == null) {
            throw ErpException.notFound("Gudang tidak ditemukan.");
        }
        List<InventoryStock> stocks = em.createQuery("select s from InventoryStock s join fetch s.item "
                        + "where s.warehouseId = :id and s.currentStock <> 0 order by s.currentStock desc", InventoryStock.class)
                .setParameter("id", id).getResultList();
        Map<String, Object> m = InvJson.scalars(wh);
        m.put("stocks", stocks.stream().map(s -> {
            Map<String, Object> sm = InvJson.scalars(s);
            sm.put("item", InvJson.pick(s.getItem(), "id", "code", "name", "unit", "averageCost", "category"));
            return sm;
        }).toList());
        return m;
    }

    /** POST /warehouses — gudang default baru melepas status default gudang lain. */
    @ErpTx
    public Map<String, Object> create(WarehouseRequest body) {
        String code = body.getCode().toUpperCase(Locale.ROOT);
        assertCodeFree(code, null);
        boolean isDefault = Boolean.TRUE.equals(body.getIsDefault());
        if (isDefault) {
            clearDefaults();
        }
        Warehouse w = new Warehouse();
        w.setCode(code);
        w.setName(body.getName());
        w.setLocation(emptyToNull(body.getLocation()));
        w.setDescription(emptyToNull(body.getDescription()));
        w.setIsActive(body.getIsActive() != null ? body.getIsActive() : true);
        w.setIsDefault(isDefault);
        w.setSortOrder(body.getSortOrder() != null ? body.getSortOrder() : 0);
        em.persist(w);
        em.flush();
        return InvJson.scalars(w);
    }

    /** PUT /warehouses/:id — update parsial (field yang tidak dikirim tidak diubah). */
    @ErpTx
    public Map<String, Object> update(String id, WarehouseRequest body) {
        Warehouse w = em.find(Warehouse.class, id);
        if (w == null) {
            throw ErpException.business("Gudang tidak ditemukan.");
        }
        if (Boolean.TRUE.equals(body.getIsDefault()) && !Boolean.TRUE.equals(w.getIsDefault())) {
            clearDefaults();
        }
        if (body.getCode() != null && !body.getCode().isEmpty()) {
            String code = body.getCode().toUpperCase(Locale.ROOT);
            assertCodeFree(code, id);
            w.setCode(code);
        }
        if (body.getName() != null) {
            w.setName(body.getName());
        }
        if (body.has("location")) {
            w.setLocation(emptyToNull(body.getLocation()));
        }
        if (body.has("description")) {
            w.setDescription(emptyToNull(body.getDescription()));
        }
        if (body.getIsActive() != null) {
            w.setIsActive(body.getIsActive());
        }
        if (body.getIsDefault() != null) {
            w.setIsDefault(body.getIsDefault());
        }
        if (body.getSortOrder() != null) {
            w.setSortOrder(body.getSortOrder());
        }
        em.flush();
        return InvJson.scalars(w);
    }

    /** DELETE /warehouses/:id — hanya bila tanpa saldo & tanpa riwayat transaksi. */
    @ErpTx
    public Map<String, Object> delete(String id) {
        Warehouse w = em.find(Warehouse.class, id);
        if (w == null) {
            throw ErpException.notFound("Gudang tidak ditemukan.");
        }
        if (Boolean.TRUE.equals(w.getIsDefault())) {
            throw ErpException.business("Gudang default tidak bisa dihapus. Set gudang lain sebagai default dulu.");
        }
        long stockCount = em.createQuery("select count(s) from InventoryStock s where s.warehouseId = :id "
                + "and s.currentStock <> 0", Long.class).setParameter("id", id).getSingleResult();
        long movementCount = em.createQuery("select count(m) from StockMovement m where m.warehouseId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        long transferCount = em.createQuery("select count(t) from StockTransfer t "
                        + "where t.fromWarehouseId = :id or t.toWarehouseId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (stockCount > 0) {
            throw ErpException.business("Gudang masih punya " + stockCount
                    + " item dengan saldo. Pindahkan stok ke gudang lain dulu.");
        }
        if (movementCount > 0 || transferCount > 0) {
            throw ErpException.business(
                    "Gudang punya riwayat transaksi. Nonaktifkan saja (jangan hapus) supaya riwayat tetap terbaca.");
        }
        // Aman dihapus permanen: bersihkan baris stok nol lalu gudangnya.
        em.createQuery("delete from InventoryStock s where s.warehouseId = :id").setParameter("id", id).executeUpdate();
        em.remove(w);
        em.flush();
        return Map.of("success", true);
    }

    private void clearDefaults() {
        em.flush();
        em.createQuery("update Warehouse w set w.isDefault = false, w.updatedAt = :now where w.isDefault = true")
                .setParameter("now", Instant.now()).executeUpdate();
    }

    /** Kode unik (P2002 ENTARIS → 400 "Data duplikat terdeteksi"). */
    private void assertCodeFree(String code, String exceptId) {
        long n = em.createQuery("select count(w) from Warehouse w where w.code = :c and w.id <> :id", Long.class)
                .setParameter("c", code)
                .setParameter("id", exceptId == null ? "" : exceptId)
                .getSingleResult();
        if (n > 0) {
            throw ErpException.business("Data duplikat terdeteksi. Silakan coba lagi.");
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
