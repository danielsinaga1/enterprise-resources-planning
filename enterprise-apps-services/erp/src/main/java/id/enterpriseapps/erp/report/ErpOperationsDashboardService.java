package id.enterpriseapps.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.DocumentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Widget operasional dashboard — port {@code /dashboard/stock-alerts}, {@code /negative-stock}
 * (gerbang: izin reports ATAU inventory, di ErpRouteGuard) dan {@code /pending-do}.
 */
@Service
public class ErpOperationsDashboardService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET /dashboard/stock-alerts — item aktif dengan stok ≤ minimum (maks. 20, stok terendah dulu). */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> stockAlerts() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : ReportRows.rows(em.createNativeQuery(
                "SELECT id, code, name, unit, category, current_stock, minimum_stock FROM inventory_items "
                        + "WHERE is_active = true AND minimum_stock > 0 AND current_stock <= minimum_stock "
                        + "ORDER BY current_stock ASC LIMIT 20"))) {
            BigDecimal current = ReportRows.dec(r[5]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("code", r[1]);
            m.put("name", r[2]);
            m.put("unit", r[3]);
            m.put("currentStock", current);
            m.put("minimumStock", ReportRows.dec(r[6]));
            m.put("status", current.signum() == 0 ? "Habis" : "Rendah");
            out.add(m);
        }
        return out;
    }

    /**
     * GET /dashboard/pending-do — Surat Jalan Submitted yang belum diinvoice (agar DO lama tidak
     * terlupa), umur dalam hari kalender lokal.
     */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> pendingDeliveryOrders() {
        List<Object[]> rows = em.createQuery("select d.id, d.doNumber, d.date, c.id, c.name from DeliveryOrder d "
                        + "left join d.customer c where d.status = :sub and d.invoiceId is null order by d.date asc",
                        Object[].class)
                .setParameter("sub", DocumentStatus.Submitted).setMaxResults(50).getResultList();
        Map<String, List<Object[]>> items = new HashMap<>();
        if (!rows.isEmpty()) {
            em.createQuery("select i.deliveryOrderId, i.quantity, i.unit from DeliveryOrderItem i "
                            + "where i.deliveryOrderId in :ids order by i.createdAt asc, i.id asc", Object[].class)
                    .setParameter("ids", rows.stream().map(r -> (String) r[0]).toList()).getResultList()
                    .forEach(r -> items.computeIfAbsent((String) r[0], k -> new ArrayList<>()).add(r));
        }
        long today = ReportDates.startOfLocalDay(Instant.now()).toEpochMilli();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] d : rows) {
            long doDate = ReportDates.startOfLocalDay((Instant) d[2]).toEpochMilli();
            List<Object[]> its = items.getOrDefault((String) d[0], List.of());
            BigDecimal totalQty = BigDecimal.ZERO;
            for (Object[] it : its) {
                totalQty = totalQty.add(ReportRows.dec(it[1]));
            }
            Map<String, Object> customer = null;
            if (d[3] != null) {
                customer = new LinkedHashMap<>();
                customer.put("id", d[3]);
                customer.put("name", d[4]);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d[0]);
            m.put("doNumber", d[1]);
            m.put("date", d[2]);
            m.put("customer", customer);
            m.put("itemCount", its.size());
            m.put("totalQty", totalQty);
            m.put("unit", its.isEmpty() || its.get(0)[2] == null ? "" : its.get(0)[2]);
            m.put("ageDays", Math.max(0, ReportDates.floorDays(today - doDate)));
            out.add(m);
        }
        return out;
    }

    /**
     * GET /dashboard/negative-stock — item dengan stok &lt; 0 (total atau per gudang) + gerakan
     * terakhir, untuk memantau role yang boleh bypass validasi stok.
     */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> negativeStock() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : ReportRows.rows(em.createNativeQuery("""
                SELECT ii.id, ii.code, ii.name, ii.unit, ii.current_stock,
                       CAST((SELECT json_agg(json_build_object(
                                'warehouseCode', w.code,
                                'warehouseName', w.name,
                                'currentStock', invs.current_stock))
                               FROM inventory_stocks invs
                               JOIN warehouses w ON w.id = invs.warehouse_id
                              WHERE invs.item_id = ii.id AND invs.current_stock < 0) AS text),
                       (SELECT\s""" + ReportRows.epochMs("sm.created_at") + """
                 FROM stock_movements sm
                         WHERE sm.item_id = ii.id AND sm.is_cancelled = false
                         ORDER BY sm.created_at DESC LIMIT 1)
                  FROM inventory_items ii
                 WHERE ii.is_active = true
                   AND (ii.current_stock < 0
                        OR EXISTS (SELECT 1 FROM inventory_stocks invs
                                    WHERE invs.item_id = ii.id AND invs.current_stock < 0))
                 ORDER BY ii.current_stock ASC
                 LIMIT 50
                """))) {
            JsonNode warehouses = r[5] == null ? ErpJson.arrayNode() : ErpJson.parse((String) r[5]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("code", r[1]);
            m.put("name", r[2]);
            m.put("unit", r[3]);
            m.put("currentStock", ReportRows.dec(r[4]));
            m.put("negativeWarehouses", warehouses);
            m.put("lastMovementAt", ReportRows.instantMs(r[6]));
            out.add(m);
        }
        return out;
    }
}
