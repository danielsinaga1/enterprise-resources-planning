package id.enterpriseapps.erp.fulfillment;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.inventory.ErpWarehouseStockService;
import id.enterpriseapps.erp.entity.InventoryItem;
import id.enterpriseapps.erp.entity.Warehouse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Cek kecukupan stok per (item, gudang) sebelum Surat Jalan dibuat/diedit — blok bersama
 * {@code submitDeliveryOrderInTx} dan {@code editDeliveryOrderInTx}. Memakai {@code inventory_stocks}
 * (stok per gudang), bukan {@code currentStock} item yang merupakan total lintas gudang.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpDeliveryOrderStockGuard {

    private final ErpWarehouseStockService stock;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private static final class Need {
        final String itemId;
        final String warehouseId;
        BigDecimal qty;

        Need(String itemId, String warehouseId, BigDecimal qty) {
            this.itemId = itemId;
            this.warehouseId = warehouseId;
            this.qty = qty;
        }
    }

    /**
     * @param shortageHint kalimat tambahan setelah pesan stok kurang (submit: saran produksi/transfer;
     *                     edit: kosong) — pesan sumber berbeda untuk kedua alur
     * @param logContext   konteks log peringatan saat bypass stok minus
     */
    @ErpTx(readOnly = true)
    public void check(List<FulfillmentRequests.DoItem> items, boolean allowNegativeStock, String reason,
                      String shortageHint, String logContext) {
        Map<String, Need> needs = new LinkedHashMap<>();
        for (FulfillmentRequests.DoItem it : items) {
            if (it.inventoryItemId() == null) {
                continue;
            }
            String whId = stock.resolveWarehouseId(it.warehouseId());
            if (whId == null) {
                throw ErpException.business("Item '" + it.itemName() + "' perlu pilih gudang sumber.");
            }
            String key = ErpWarehouseStockService.key(it.inventoryItemId(), whId);
            Need prev = needs.get(key);
            if (prev != null) {
                prev.qty = prev.qty.add(it.quantity());
            } else {
                needs.put(key, new Need(it.inventoryItemId(), whId, it.quantity()));
            }
        }
        if (needs.isEmpty()) {
            return;
        }
        List<String> itemIds = new ArrayList<>(new LinkedHashSet<>(needs.values().stream().map(n -> n.itemId).toList()));
        Map<String, InventoryItem> itemById = new HashMap<>();
        em.createQuery("select i from InventoryItem i where i.id in :ids", InventoryItem.class)
                .setParameter("ids", itemIds).getResultList().forEach(i -> itemById.put(i.getId(), i));
        List<String> whIds = new ArrayList<>(new LinkedHashSet<>(needs.values().stream().map(n -> n.warehouseId).toList()));
        Map<String, String> whName = new HashMap<>();
        em.createQuery("select w from Warehouse w where w.id in :ids", Warehouse.class)
                .setParameter("ids", whIds).getResultList().forEach(w -> whName.put(w.getId(), w.getName()));
        Map<String, BigDecimal> stockMap = stock.getWarehouseStockMap(needs.values().stream()
                .map(n -> new ErpWarehouseStockService.Pair(n.itemId, n.warehouseId)).toList());

        for (Map.Entry<String, Need> e : needs.entrySet()) {
            Need n = e.getValue();
            InventoryItem inv = itemById.get(n.itemId);
            if (inv == null) {
                throw ErpException.business("Item tidak ditemukan.");
            }
            if (!Boolean.TRUE.equals(inv.getIsActive())) {
                throw ErpException.business("Item '" + inv.getName() + "' tidak aktif.");
            }
            BigDecimal available = stockMap.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (available.compareTo(n.qty) < 0) {
                String wh = whName.getOrDefault(n.warehouseId, "gudang");
                if (!allowNegativeStock) {
                    throw ErpException.business("Stok '" + inv.getName() + "' di gudang " + wh + " tidak cukup. Tersedia: "
                            + FulfillmentJson.idNum(available) + " " + inv.getUnit() + ", dibutuhkan: "
                            + FulfillmentJson.idNum(n.qty) + " " + inv.getUnit() + "." + shortageHint);
                }
                log.warn("{} with negative stock bypass itemId={} itemName={} warehouseId={} warehouseName={} available={} needed={} reason={}",
                        logContext, inv.getId(), inv.getName(), n.warehouseId, wh, available, n.qty, reason);
            }
        }
    }
}
