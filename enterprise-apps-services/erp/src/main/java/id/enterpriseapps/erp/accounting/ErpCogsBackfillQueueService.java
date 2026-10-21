package id.enterpriseapps.erp.accounting;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.entity.CogsBackfillQueue;
import id.enterpriseapps.erp.entity.CogsBackfillSettlement;
import id.enterpriseapps.erp.entity.InventoryItem;
import id.enterpriseapps.erp.entity.SalesInvoice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Antrian COGS backfill (HPP yang tertunda karena stok/biaya belum tersedia saat penjualan) —
 * port {@code routes/cogsBackfill.ts}: daftar antrian + ringkasan untuk widget dasbor.
 */
@Service
@RequiredArgsConstructor
public class ErpCogsBackfillQueueService {

    private static final List<String> OPEN = List.of("Pending", "PartiallySettled");

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET /cogs-backfill — {@code { data }}; default status Pending/PartiallySettled, limit 50 (maks 200). */
    @ErpTx(readOnly = true)
    public ObjectNode list(Map<String, String> query) {
        String status = query.get("status");
        String itemId = query.get("itemId");
        String limitRaw = query.getOrDefault("limit", "50");
        double n = JsNumbers.number(limitRaw);
        if (Double.isNaN(n) || n == 0) {
            n = 50;
        }
        double take = Math.min(n, 200);
        if (take != Math.rint(take)) {
            // Prisma menolak take pecahan → fallback 500 rute.
            throw ErpException.of(500, "Gagal mengambil daftar COGS backfill.");
        }
        int t = (int) Math.max(take, -1_000_000);

        boolean hasStatus = status != null && !status.isEmpty();
        boolean hasItem = itemId != null && !itemId.isEmpty();
        // take negatif = ambil dari ujung daftar (semantik Prisma), hasil tetap urut createdAt desc.
        String jpql = "select q from CogsBackfillQueue q join fetch q.inventoryItem join fetch q.salesInvoice s "
                + "join fetch s.customer where " + (hasStatus ? "q.status = :status" : "q.status in :open")
                + (hasItem ? " and q.inventoryItemId = :item" : "")
                + " order by q.createdAt " + (t >= 0 ? "desc" : "asc");
        var q = em.createQuery(jpql, CogsBackfillQueue.class);
        if (hasStatus) {
            q.setParameter("status", status);
        } else {
            q.setParameter("open", OPEN);
        }
        if (hasItem) {
            q.setParameter("item", itemId);
        }
        List<CogsBackfillQueue> rows = new ArrayList<>(q.setMaxResults(Math.abs(t)).getResultList());
        if (t < 0) {
            Collections.reverse(rows);
        }

        Map<String, List<CogsBackfillSettlement>> settlements = new HashMap<>();
        if (!rows.isEmpty()) {
            for (CogsBackfillSettlement s : em.createQuery("select s from CogsBackfillSettlement s "
                            + "where s.queueId in :ids order by s.createdAt desc", CogsBackfillSettlement.class)
                    .setParameter("ids", rows.stream().map(CogsBackfillQueue::getId).toList()).getResultList()) {
                settlements.computeIfAbsent(s.getQueueId(), k -> new ArrayList<>()).add(s);
            }
        }

        ArrayNode data = ErpJson.arrayNode();
        for (CogsBackfillQueue row : rows) {
            ObjectNode o = AccountingJson.pick(row, AccountingJson.COGS_QUEUE);
            InventoryItem item = row.getInventoryItem();
            o.set("inventoryItem", AccountingJson.pick(item, "id", "code", "name", "unit", "currentStock", "averageCost"));
            SalesInvoice inv = row.getSalesInvoice();
            ObjectNode si = AccountingJson.pick(inv, "id", "invoiceNumber", "date");
            si.set("customer", AccountingJson.pick(inv.getCustomer(), "name"));
            o.set("salesInvoice", si);
            ArrayNode st = o.putArray("settlements");
            for (CogsBackfillSettlement s : settlements.getOrDefault(row.getId(), List.of())) {
                st.add(AccountingJson.pick(s, "id", "qtySettled", "costAtSettle", "differential", "triggerSource",
                        "triggerRefNo", "journalEntryId", "createdAt"));
            }
            data.add(o);
        }
        ObjectNode out = ErpJson.object();
        out.set("data", data);
        return out;
    }

    /** GET /cogs-backfill/summary — jumlah antrian terbuka + estimasi nilai per item (qty × HPP rata-rata). */
    @ErpTx(readOnly = true)
    public ObjectNode summary() {
        long pendingCount = em.createQuery("select count(q) from CogsBackfillQueue q where q.status in :open", Long.class)
                .setParameter("open", OPEN).getSingleResult();
        List<Object[]> byItem = em.createQuery("select q.inventoryItemId, sum(q.qtyPending) from CogsBackfillQueue q "
                        + "where q.status in :open group by q.inventoryItemId", Object[].class)
                .setParameter("open", OPEN).getResultList();
        Map<String, InventoryItem> items = new HashMap<>();
        if (!byItem.isEmpty()) {
            for (InventoryItem i : em.createQuery("select i from InventoryItem i where i.id in :ids", InventoryItem.class)
                    .setParameter("ids", byItem.stream().map(r -> (String) r[0]).toList()).getResultList()) {
                items.put(i.getId(), i);
            }
        }
        ArrayNode breakdown = ErpJson.arrayNode();
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] r : byItem) {
            String itemId = (String) r[0];
            InventoryItem item = items.get(itemId);
            BigDecimal qty = Money.nz((BigDecimal) r[1]);
            BigDecimal avgCost = item == null ? BigDecimal.ZERO : Money.nz(item.getAverageCost());
            BigDecimal value = qty.multiply(avgCost);
            total = total.add(value);
            ObjectNode o = breakdown.addObject();
            o.put("itemId", itemId);
            o.put("code", item == null ? "" : item.getCode());
            o.put("name", item == null ? "(deleted)" : item.getName());
            o.put("unit", item == null ? "" : item.getUnit());
            o.set("qtyPending", JsNumbers.node(qty));
            o.set("estimatedValue", JsNumbers.node(value));
        }
        ObjectNode out = ErpJson.object();
        out.put("pendingCount", pendingCount);
        out.set("totalPendingValue", JsNumbers.node(total));
        out.set("breakdown", breakdown);
        return out;
    }
}
