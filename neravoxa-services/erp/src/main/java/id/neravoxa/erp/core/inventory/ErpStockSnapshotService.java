package id.neravoxa.erp.core.inventory;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.Warehouse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stok per gudang per akhir tanggal — port {@code services/stockSnapshot.ts}.
 *
 * <p>Berjangkar pada stok live {@code inventory_stocks} lalu mundur:
 * qty_asof(X) = live − Σ(gerakan bertanda setelah X). Nilai memakai averageCost terkini (estimasi).
 */
@Service
public class ErpStockSnapshotService {

    public static final String NOTE = "Kuantitas dihitung tepat dari riwayat pergerakan stok per tanggal. "
            + "Nilai memakai harga rata-rata (avg cost) terkini sebagai estimasi.";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private record ItemMeta(String id, String code, String name, String unit, String category, BigDecimal avg) {
    }

    /**
     * @param dateStr      tanggal target (YYYY-MM-DD, inklusif sampai akhir hari)
     * @param warehouseIds filter opsional; kosong = semua gudang aktif
     * @return {@code { asOfDate, warehouses: [{ warehouseId, code, name, location, isDefault, items: [...], totalValue, itemCount }], grandTotalValue, valuationNote }}
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> build(String dateStr, List<String> warehouseIds) {
        Instant dayEnd = ErpDates.utcEndOfDay(dateStr);
        List<Warehouse> warehouses = warehouseIds != null && !warehouseIds.isEmpty()
                ? em.createQuery("select w from Warehouse w where w.id in :ids order by w.sortOrder asc, w.name asc", Warehouse.class)
                .setParameter("ids", warehouseIds).getResultList()
                : em.createQuery("select w from Warehouse w where w.isActive = true order by w.sortOrder asc, w.name asc", Warehouse.class)
                .getResultList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOfDate", dateStr);
        if (warehouses.isEmpty()) {
            out.put("warehouses", List.of());
            out.put("grandTotalValue", 0);
            out.put("valuationNote", NOTE);
            return out;
        }
        List<String> whIds = warehouses.stream().map(Warehouse::getId).toList();

        Map<String, BigDecimal> qty = new HashMap<>();
        Map<String, ItemMeta> items = new HashMap<>();
        List<Object[]> live = em.createQuery(
                        "select s.warehouseId, s.itemId, s.currentStock, i.code, i.name, i.unit, i.category, i.averageCost "
                                + "from InventoryStock s join s.item i where s.warehouseId in :ids", Object[].class)
                .setParameter("ids", whIds).getResultList();
        for (Object[] r : live) {
            qty.put(r[0] + "|" + r[1], Money.nz((BigDecimal) r[2]));
            items.put((String) r[1], new ItemMeta((String) r[1], (String) r[3], (String) r[4], (String) r[5],
                    (String) r[6], Money.nz((BigDecimal) r[7])));
        }

        List<Object[]> after = em.createQuery(
                        "select m.warehouseId, m.itemId, "
                                + "sum(case when m.movementType in (id.neravoxa.erp.entity.StockMovementType.In, "
                                + "id.neravoxa.erp.entity.StockMovementType.AdjustmentIn, "
                                + "id.neravoxa.erp.entity.StockMovementType.TransferIn) then m.quantity else 0 end), "
                                + "sum(case when m.movementType in (id.neravoxa.erp.entity.StockMovementType.In, "
                                + "id.neravoxa.erp.entity.StockMovementType.AdjustmentIn, "
                                + "id.neravoxa.erp.entity.StockMovementType.TransferIn) then 0 else m.quantity end) "
                                + "from StockMovement m where m.warehouseId in :ids and m.isCancelled = false and m.date > :d "
                                + "group by m.warehouseId, m.itemId", Object[].class)
                .setParameter("ids", whIds).setParameter("d", dayEnd).getResultList();
        List<String> missing = new ArrayList<>();
        for (Object[] r : after) {
            if (r[0] == null) {
                continue;
            }
            String k = r[0] + "|" + r[1];
            BigDecimal in = Money.nz((BigDecimal) r[2]);
            BigDecimal outQ = Money.nz((BigDecimal) r[3]);
            qty.put(k, qty.getOrDefault(k, BigDecimal.ZERO).subtract(in).add(outQ));
            if (!items.containsKey((String) r[1])) {
                missing.add((String) r[1]);
            }
        }
        if (!missing.isEmpty()) {
            em.createQuery("select i.id, i.code, i.name, i.unit, i.category, i.averageCost from InventoryItem i where i.id in :ids",
                            Object[].class)
                    .setParameter("ids", missing).getResultList()
                    .forEach(r -> items.put((String) r[0], new ItemMeta((String) r[0], (String) r[1], (String) r[2],
                            (String) r[3], (String) r[4], Money.nz((BigDecimal) r[5]))));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        BigDecimal grand = BigDecimal.ZERO;
        for (Warehouse w : warehouses) {
            List<Map<String, Object>> whItems = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> e : qty.entrySet()) {
                String[] parts = e.getKey().split("\\|");
                if (!parts[0].equals(w.getId()) || e.getValue().signum() == 0) {
                    continue;
                }
                ItemMeta meta = items.get(parts[1]);
                if (meta == null) {
                    continue;
                }
                BigDecimal q = Money.round(e.getValue(), 3);
                BigDecimal value = Money.round2(e.getValue().multiply(meta.avg()));
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("itemId", parts[1]);
                it.put("code", meta.code());
                it.put("name", meta.name());
                it.put("unit", meta.unit());
                it.put("category", meta.category());
                it.put("qty", q);
                it.put("averageCost", Money.round2(meta.avg()));
                it.put("value", value);
                whItems.add(it);
                total = total.add(value);
            }
            whItems.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("qty")).reversed());
            Map<String, Object> wm = new LinkedHashMap<>();
            wm.put("warehouseId", w.getId());
            wm.put("code", w.getCode());
            wm.put("name", w.getName());
            wm.put("location", w.getLocation());
            wm.put("isDefault", w.getIsDefault());
            wm.put("items", whItems);
            wm.put("totalValue", total);
            wm.put("itemCount", whItems.size());
            result.add(wm);
            grand = grand.add(total);
        }
        out.put("warehouses", result);
        out.put("grandTotalValue", grand);
        out.put("valuationNote", NOTE);
        return out;
    }
}
