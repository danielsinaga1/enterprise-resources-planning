package id.neravoxa.erp.purchase;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.ApprovalRequest;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.GoodsReceipt;
import id.neravoxa.erp.entity.PurchaseOrder;
import id.neravoxa.erp.entity.PurchaseOrderItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Daftar & detail Purchase Order — port {@code purchaseOrderList.ts} + handler GET
 * {@code routes/purchaseOrders.ts}. Baris membawa turunan {@code receiptStatus},
 * {@code displayStatus}, {@code isOverdue}.
 */
@Service
public class ErpPurchaseOrderQueryService {

    static final Set<String> SORT_FIELDS = Set.of("date", "poNumber", "supplier", "expectedDeliveryDate", "totalEstimated");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);
    static final List<String> RECEIPT_FILTERS = List.of("unreceived", "partial", "full", "closed");
    private static final String DATE_ERROR = "Format tanggal harus YYYY-MM-DD.";
    private static final String OPEN_PO = "po.status = :openSubmitted and po.closedAt is null";
    private static final String ITEM_EXISTS = "exists (select x.id from PurchaseOrderItem x where x.purchaseOrderId = po.id";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * Port {@code buildPurchaseOrderWhere}: status (DocumentStatus | PendingApproval), partyId,
     * startDate/endDate, search (nomor PO / nama supplier), receipt (unreceived|partial|full|closed,
     * selalu atas PO terbuka kecuali "closed"), open=1 (Submitted & belum ditutup — pemilih PO di
     * STT), due=overdue.
     */
    ErpPurchaseInvoiceQueryService.Filter buildWhere(Map<String, String> query, Instant now) {
        List<String> conds = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();

        String status = ListQuery.str(query, "status");
        if ("PendingApproval".equals(status)) {
            // "Menunggu Persetujuan" = Draf + pengajuan Pending.
            conds.add("po.status = :st");
            params.put("st", DocumentStatus.Draft);
            conds.add("po.approvalStatus = 'Pending'");
        } else if (status != null) {
            DocumentStatus st;
            try {
                st = DocumentStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw ErpException.business("Status PO tidak dikenal.");
            }
            conds.add("po.status = :st");
            params.put("st", st);
            if (st == DocumentStatus.Draft) {
                conds.add("(po.approvalStatus is null or po.approvalStatus <> 'Pending')");
            }
        }

        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            conds.add("po.partyId = :pid");
            params.put("pid", partyId);
        }
        String start = ListQuery.dateParam(query, "startDate", DATE_ERROR);
        String end = ListQuery.dateParam(query, "endDate", DATE_ERROR);
        Instant[] range = ErpDates.localDayRange(start, end);
        if (range[0] != null) {
            conds.add("po.date >= :dGte");
            params.put("dGte", range[0]);
        }
        if (range[1] != null) {
            conds.add("po.date < :dLt");
            params.put("dLt", range[1]);
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            conds.add("(lower(po.poNumber) like :q or lower(s.name) like :q)");
            params.put("q", ErpQuery.contains(search));
        }

        String receipt = ListQuery.str(query, "receipt");
        if (receipt != null) {
            if (!RECEIPT_FILTERS.contains(receipt)) {
                throw ErpException.business("Filter penerimaan PO tidak dikenal.");
            }
            switch (receipt) {
                case "closed" -> {
                    conds.add("po.status = :openSubmitted and po.closedAt is not null");
                }
                case "unreceived" -> {
                    conds.add(OPEN_PO);
                    conds.add("not " + ITEM_EXISTS + " and x.qtyReceived > 0)");
                }
                case "full" -> {
                    conds.add(OPEN_PO);
                    conds.add(ITEM_EXISTS + ")");
                    conds.add("not " + ITEM_EXISTS + " and x.qtyReceived < x.quantity)");
                }
                default -> {
                    conds.add(OPEN_PO);
                    conds.add(ITEM_EXISTS + " and x.qtyReceived > 0)");
                    conds.add(ITEM_EXISTS + " and x.qtyReceived < x.quantity)");
                }
            }
            params.put("openSubmitted", DocumentStatus.Submitted);
        }

        String open = ListQuery.str(query, "open");
        if ("1".equals(open) || "true".equals(open)) {
            conds.add(OPEN_PO);
            params.put("openSubmitted", DocumentStatus.Submitted);
        }

        String due = ListQuery.str(query, "due");
        if (due != null) {
            if (!"overdue".equals(due)) {
                throw ErpException.business("Filter jatuh tempo PO tidak dikenal.");
            }
            conds.add(PurchaseOrderOverdue.OVERDUE_JPQL);
            Object[] p = PurchaseOrderOverdue.overdueParams(now);
            for (int i = 0; i < p.length; i += 2) {
                params.put((String) p[i], p[i + 1]);
            }
        }
        return new ErpPurchaseInvoiceQueryService.Filter(conds, params, search != null);
    }

    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        String primary = switch (sort.field()) {
            case "supplier" -> "s.name " + dir;
            case "expectedDeliveryDate" -> "po.expectedDeliveryDate " + dir + " nulls last";
            default -> "po." + sort.field() + " " + dir;
        };
        // Tie-breaker stabil agar baris tidak berpindah antar halaman.
        return "poNumber".equals(sort.field()) ? primary : primary + ", po.poNumber " + dir;
    }

    static List<PurchaseOrderRules.ReceiptLine> receiptLines(List<PurchaseOrderItem> items) {
        return items.stream().map(i -> new PurchaseOrderRules.ReceiptLine(i.getQuantity(), i.getQtyReceived())).toList();
    }

    /** GET / — {@code { data, total, page, limit }}; baris = PO + supplier{id,name} + _count + turunan. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        // Maks 200 untuk pemanggil yang memuat seluruh daftar (modal STT memuat PO terbuka).
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        Instant now = Instant.now();
        ErpPurchaseInvoiceQueryService.Filter f = buildWhere(query, now);
        ListQuery.Sort sort = ListQuery.sort(ListQuery.str(query, "sort"), SORT_FIELDS, DEFAULT_SORT);

        ErpQuery<PurchaseOrder> q = ErpQuery.of(em, PurchaseOrder.class, "po")
                .join("left join po.supplier s")
                .fetch("left join fetch po.supplier", "left join fetch po.items");
        f.applyTo(q);
        q.orderBy(orderBy(sort));
        long total = q.count();
        List<PurchaseOrder> rows = q.list(paging.skip(), paging.limit());

        Map<String, Long> grCounts = new HashMap<>();
        if (!rows.isEmpty()) {
            em.createQuery("select g.purchaseOrderId, count(g) from GoodsReceipt g where g.purchaseOrderId in :ids "
                            + "group by g.purchaseOrderId", Object[].class)
                    .setParameter("ids", rows.stream().map(PurchaseOrder::getId).toList())
                    .getResultList()
                    .forEach(r -> grCounts.put((String) r[0], ((Number) r[1]).longValue()));
        }

        // Item hanya dimuat untuk menurunkan status penerimaan; bentuk baris tetap (tanpa items).
        String today = PurchaseOrderOverdue.businessToday(now);
        PurchaseShape shape = PurchaseShape.scalars().with("supplier", PurchaseShape.select("id", "name"));
        List<ObjectNode> data = new ArrayList<>();
        for (PurchaseOrder po : rows) {
            List<PurchaseOrderRules.ReceiptLine> lines = receiptLines(po.getItems());
            ObjectNode n = shape.of(po);
            n.set("_count", ErpJson.tree(ErpJson.map("items", po.getItems().size(),
                    "goodsReceipts", grCounts.getOrDefault(po.getId(), 0L))));
            n.put("receiptStatus", PurchaseOrderRules.receiptStatus(po.getStatus(), po.getClosedAt(), lines));
            n.put("displayStatus", PurchaseOrderRules.displayStatus(po));
            n.put("isOverdue", PurchaseOrderOverdue.isOverdue(po.getStatus(), po.getClosedAt(),
                    po.getExpectedDeliveryDate(), lines, today));
            data.add(n);
        }
        return ListQuery.result(data, total, paging);
    }

    /** GET /:id — PO lengkap + STT non-batal + riwayat approval + total dihitung ulang dari baris. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<PurchaseOrder> found = em.createQuery("select po from PurchaseOrder po left join fetch po.supplier "
                        + "left join fetch po.warehouse left join fetch po.user where po.id = :id", PurchaseOrder.class)
                .setParameter("id", id).getResultList();
        if (found.isEmpty()) {
            throw ErpException.notFound("PO tidak ditemukan.");
        }
        PurchaseOrder po = found.get(0);
        List<PurchaseOrderItem> items = em.createQuery("select it from PurchaseOrderItem it left join fetch it.inventoryItem "
                        + "where it.purchaseOrderId = :id", PurchaseOrderItem.class)
                .setParameter("id", id).getResultList();
        List<GoodsReceipt> receipts = em.createQuery("select g from GoodsReceipt g where g.purchaseOrderId = :id "
                        + "and g.status <> :cancelled order by g.date desc", GoodsReceipt.class)
                .setParameter("id", id).setParameter("cancelled", DocumentStatus.Cancelled).getResultList();
        List<ApprovalRequest> approvals = em.createQuery("select r from ApprovalRequest r left join fetch r.requester "
                        + "left join fetch r.approver left join fetch r.rejecter "
                        + "where r.documentType = 'PurchaseOrder' and r.documentId = :id order by r.createdAt desc",
                ApprovalRequest.class).setParameter("id", id).getResultList();

        // Total dihitung ulang dari baris agar PO lama (kolom harga = 0) tetap konsisten.
        PurchaseCalc.PoTotals t = PurchaseCalc.poTotals(items.stream()
                .map(i -> new PurchaseCalc.PoLineInput(i.getQuantity(), i.getEstimatedRate(), i.getDiscount(), i.getTaxPct()))
                .toList(), po.getOtherCosts());

        ObjectNode n = PurchaseShape.scalars()
                .with("supplier")
                .with("warehouse", PurchaseShape.select("id", "code", "name"))
                .with("user", PurchaseShape.select("id", "fullName"))
                .of(po);
        n.set("items", PurchaseShape.scalars().with("inventoryItem").list(items));
        n.set("goodsReceipts", PurchaseShape.select("id", "sttNumber", "date", "status", "invoiceId").list(receipts));
        List<PurchaseOrderRules.ReceiptLine> lines = receiptLines(items);
        n.put("receiptStatus", PurchaseOrderRules.receiptStatus(po.getStatus(), po.getClosedAt(), lines));
        n.put("displayStatus", PurchaseOrderRules.displayStatus(po));
        n.put("isOverdue", PurchaseOrderOverdue.isOverdue(po.getStatus(), po.getClosedAt(), po.getExpectedDeliveryDate(),
                lines, PurchaseOrderOverdue.businessToday(Instant.now())));
        n.set("totals", ErpJson.tree(ErpJson.map(
                "subtotal", t.subtotal(),
                "discountTotal", t.discountTotal(),
                "taxTotal", t.taxTotal(),
                "otherCosts", t.otherCosts(),
                "grandTotal", t.grandTotal())));
        n.set("approvals", PurchaseShape.select("id", "status", "amount", "notes", "createdAt", "decidedAt")
                .with("requester", PurchaseShape.select("fullName"))
                .with("approver", PurchaseShape.select("fullName"))
                .with("rejecter", PurchaseShape.select("fullName"))
                .list(approvals));
        return n;
    }
}
