package id.neravoxa.erp.purchase;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.PurchaseOrder;
import id.neravoxa.erp.entity.PurchaseOrderItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laporan pembelian di bawah {@code /reports} — port {@code routes/reports/purchasing.ts}:
 * {@code /po-outstanding} dan {@code /supplier-performance}. Agregasi di {@link PurchaseReports}.
 */
@Service
public class ErpPurchaseReportService {

    private static final String DATE_ERROR = "Format tanggal harus YYYY-MM-DD.";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private record Where(List<String> conds, Map<String, Object> params) {
        String sql() {
            return conds.isEmpty() ? "" : " where " + String.join(" and ", conds);
        }

        <T> TypedQuery<T> bind(TypedQuery<T> q) {
            params.forEach(q::setParameter);
            return q;
        }
    }

    private static void dateRange(Map<String, String> query, Where w) {
        String start = ListQuery.dateParam(query, "startDate", DATE_ERROR);
        String end = ListQuery.dateParam(query, "endDate", DATE_ERROR);
        Instant[] range = ErpDates.localDayRange(start, end);
        if (range[0] != null) {
            w.conds().add("po.date >= :dGte");
            w.params().put("dGte", range[0]);
        }
        if (range[1] != null) {
            w.conds().add("po.date < :dLt");
            w.params().put("dLt", range[1]);
        }
    }

    private Map<String, List<PurchaseOrderItem>> itemsByPo(List<String> poIds) {
        Map<String, List<PurchaseOrderItem>> out = new HashMap<>();
        if (poIds.isEmpty()) {
            return out;
        }
        em.createQuery("select it from PurchaseOrderItem it where it.purchaseOrderId in :ids", PurchaseOrderItem.class)
                .setParameter("ids", poIds).getResultList()
                .forEach(it -> out.computeIfAbsent(it.getPurchaseOrderId(), k -> new ArrayList<>()).add(it));
        return out;
    }

    /** GET /reports/po-outstanding — PO terbuka (Submitted, belum ditutup) per baris sisa qty + nilai. */
    @ErpTx(readOnly = true)
    public Map<String, Object> poOutstanding(Map<String, String> query) {
        Where w = new Where(new ArrayList<>(), new LinkedHashMap<>());
        dateRange(query, w);
        Instant now = Instant.now();
        w.conds().add(0, "po.status = :submitted and po.closedAt is null");
        w.params().put("submitted", DocumentStatus.Submitted);
        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            w.conds().add("po.partyId = :pid");
            w.params().put("pid", partyId);
        }
        String warehouseId = ListQuery.str(query, "warehouseId");
        if (warehouseId != null) {
            w.conds().add("po.warehouseId = :wid");
            w.params().put("wid", warehouseId);
        }
        String overdue = ListQuery.str(query, "overdue");
        if ("1".equals(overdue) || "true".equals(overdue)) {
            w.conds().add(PurchaseOrderOverdue.OVERDUE_JPQL);
            Object[] p = PurchaseOrderOverdue.overdueParams(now);
            for (int i = 0; i < p.length; i += 2) {
                w.params().put((String) p[i], p[i + 1]);
            }
        }
        List<PurchaseOrder> pos = w.bind(em.createQuery("select po from PurchaseOrder po left join fetch po.supplier "
                        + "left join fetch po.warehouse" + w.sql()
                        + " order by po.expectedDeliveryDate asc nulls last, po.poNumber asc", PurchaseOrder.class))
                .setMaxResults(2000).getResultList();
        Map<String, List<PurchaseOrderItem>> items = itemsByPo(pos.stream().map(PurchaseOrder::getId).toList());

        List<PurchaseReports.OutstandingPo> input = new ArrayList<>();
        for (PurchaseOrder po : pos) {
            List<PurchaseReports.OutstandingItem> its = items.getOrDefault(po.getId(), List.of()).stream()
                    .map(i -> new PurchaseReports.OutstandingItem(i.getId(), i.getItemName(), i.getUnit(), i.getQuantity(),
                            i.getQtyReceived(), i.getEstimatedRate(), i.getDiscount(), i.getTaxPct()))
                    .toList();
            input.add(new PurchaseReports.OutstandingPo(po.getId(), po.getPoNumber(), po.getDate(), po.getStatus(),
                    po.getClosedAt(), po.getExpectedDeliveryDate(),
                    po.getSupplier() != null ? po.getSupplier().getName() : null,
                    po.getWarehouse() != null ? po.getWarehouse().getName() : null, its));
        }
        return PurchaseReports.buildPoOutstanding(input, PurchaseOrderOverdue.businessToday(now));
    }

    /** GET /reports/supplier-performance — per supplier: tepat waktu, lead time, fill rate, selisih harga. */
    @ErpTx(readOnly = true)
    public Map<String, Object> supplierPerformance(Map<String, String> query) {
        Where w = new Where(new ArrayList<>(), new LinkedHashMap<>());
        dateRange(query, w);
        w.conds().add(0, "po.status = :submitted");
        w.params().put("submitted", DocumentStatus.Submitted);
        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            w.conds().add("po.partyId = :pid");
            w.params().put("pid", partyId);
        }
        List<PurchaseOrder> pos = w.bind(em.createQuery("select po from PurchaseOrder po left join fetch po.supplier"
                        + w.sql(), PurchaseOrder.class))
                .setMaxResults(5000).getResultList();
        List<String> poIds = pos.stream().map(PurchaseOrder::getId).toList();
        Map<String, List<PurchaseOrderItem>> items = itemsByPo(poIds);

        // STT non-batal per PO (tanggal) + baris PI yang menagih baris STT tersebut.
        Map<String, List<Instant>> receipts = new HashMap<>();
        Map<String, String> receiptPo = new HashMap<>();
        if (!poIds.isEmpty()) {
            em.createQuery("select g.id, g.purchaseOrderId, g.date from GoodsReceipt g where g.purchaseOrderId in :ids "
                            + "and g.status <> :cancelled", Object[].class)
                    .setParameter("ids", poIds).setParameter("cancelled", DocumentStatus.Cancelled).getResultList()
                    .forEach(r -> {
                        receiptPo.put((String) r[0], (String) r[1]);
                        receipts.computeIfAbsent((String) r[1], k -> new ArrayList<>()).add((Instant) r[2]);
                    });
        }
        Map<String, List<PurchaseReports.PerfInvoiceLine>> invoiceLines = new HashMap<>();
        if (!receiptPo.isEmpty()) {
            em.createQuery("select gi.goodsReceiptId, gi.purchaseOrderItemId, pii.quantity, pii.rate, pii.discount, pi.status "
                            + "from GoodsReceiptItem gi join PurchaseInvoiceItem pii on pii.goodsReceiptItemId = gi.id "
                            + "join pii.purchaseInvoice pi where gi.goodsReceiptId in :ids and gi.purchaseOrderItemId is not null",
                    Object[].class).setParameter("ids", receiptPo.keySet()).getResultList()
                    .forEach(r -> {
                        InvoiceStatus st = (InvoiceStatus) r[5];
                        // Hanya PI yang benar-benar ditagih (bukan batal / draf).
                        if (st == InvoiceStatus.Cancelled || st == InvoiceStatus.Draft) {
                            return;
                        }
                        String poId = receiptPo.get((String) r[0]);
                        invoiceLines.computeIfAbsent(poId, k -> new ArrayList<>()).add(new PurchaseReports.PerfInvoiceLine(
                                (String) r[1], (BigDecimal) r[2], (BigDecimal) r[3], (BigDecimal) r[4]));
                    });
        }

        List<PurchaseReports.PerfPo> input = new ArrayList<>();
        for (PurchaseOrder po : pos) {
            List<PurchaseReports.PerfItem> its = items.getOrDefault(po.getId(), List.of()).stream()
                    .map(i -> new PurchaseReports.PerfItem(i.getId(), i.getQuantity(), i.getQtyReceived(),
                            i.getEstimatedRate(), i.getDiscount()))
                    .toList();
            input.add(new PurchaseReports.PerfPo(po.getId(), po.getDate(), po.getExpectedDeliveryDate(),
                    po.getSupplier() != null ? po.getSupplier().getId() : null,
                    po.getSupplier() != null ? po.getSupplier().getName() : null,
                    its, receipts.getOrDefault(po.getId(), List.of()), invoiceLines.getOrDefault(po.getId(), List.of())));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", PurchaseReports.buildSupplierPerformance(input));
        return out;
    }
}
