package id.enterpriseapps.erp.purchase;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.entity.DocumentStatus;
import id.enterpriseapps.erp.entity.GoodsReceipt;
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
 * Daftar, STT pending, dan detail STT/BPB — port {@code goodsReceiptList.ts} + handler GET
 * {@code routes/goodsReceipts.ts}. Status tagih diturunkan dari link PI: Uninvoiced (stok sendiri,
 * perlu PI), Invoiced, CustomerOwned (tak pernah di-invoice), Submitted (belum ditagih, termasuk
 * titipan), Cancelled, Draft. STT backfill legacy hanya tampil dengan {@code includeLegacy=true}.
 */
@Service
public class ErpGoodsReceiptQueryService {

    static final Set<String> SORT_FIELDS = Set.of("date", "sttNumber", "supplier");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);
    static final List<String> STATUS_FILTERS = List.of("Uninvoiced", "Invoiced", "CustomerOwned", "Cancelled", "Draft", "Submitted");
    private static final String DATE_ERROR = "Format tanggal harus YYYY-MM-DD.";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    ErpPurchaseInvoiceQueryService.Filter buildWhere(Map<String, String> query) {
        List<String> conds = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();

        String status = ListQuery.str(query, "status");
        if (status != null) {
            if (!STATUS_FILTERS.contains(status)) {
                throw ErpException.business("Status STT tidak dikenal.");
            }
            switch (status) {
                case "Uninvoiced" -> {
                    conds.add("g.status = :gst and g.invoiceId is null and g.isCustomerOwned = false");
                    params.put("gst", DocumentStatus.Submitted);
                }
                case "Invoiced" -> {
                    conds.add("g.status = :gst and g.invoiceId is not null");
                    params.put("gst", DocumentStatus.Submitted);
                }
                case "CustomerOwned" -> {
                    conds.add("g.status = :gst and g.isCustomerOwned = true");
                    params.put("gst", DocumentStatus.Submitted);
                }
                case "Cancelled" -> {
                    conds.add("g.status = :gst");
                    params.put("gst", DocumentStatus.Cancelled);
                }
                case "Draft" -> {
                    conds.add("g.status = :gst");
                    params.put("gst", DocumentStatus.Draft);
                }
                default -> {
                    conds.add("g.status = :gst and g.invoiceId is null");
                    params.put("gst", DocumentStatus.Submitted);
                }
            }
        }
        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            conds.add("g.partyId = :pid");
            params.put("pid", partyId);
        }
        String start = ListQuery.dateParam(query, "startDate", DATE_ERROR);
        String end = ListQuery.dateParam(query, "endDate", DATE_ERROR);
        Instant[] range = ErpDates.localDayRange(start, end);
        if (range[0] != null) {
            conds.add("g.date >= :dGte");
            params.put("dGte", range[0]);
        }
        if (range[1] != null) {
            conds.add("g.date < :dLt");
            params.put("dLt", range[1]);
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            conds.add("(lower(g.sttNumber) like :q or lower(s.name) like :q or lower(g.nomorMobil) like :q "
                    + "or lower(po.poNumber) like :q)");
            params.put("q", ErpQuery.contains(search));
        }
        // STT backfill legacy mengotori daftar; hanya STT nyata kecuali diminta.
        if (!"true".equals(ListQuery.str(query, "includeLegacy"))) {
            conds.add("g.isLegacyBackfill = false");
        }
        return new ErpPurchaseInvoiceQueryService.Filter(conds, params, search != null);
    }

    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        String primary = "supplier".equals(sort.field()) ? "s.name " + dir : "g." + sort.field() + " " + dir;
        // Tie-breaker stabil agar baris tidak berpindah antar halaman.
        return "sttNumber".equals(sort.field()) ? primary : primary + ", g.sttNumber " + dir;
    }

    /** GET / — STT + supplier{id,name} + invoice{id,invoiceNumber} + purchaseOrder{id,poNumber} + _count.items. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        ErpPurchaseInvoiceQueryService.Filter f = buildWhere(query);
        ListQuery.Sort sort = ListQuery.sort(ListQuery.str(query, "sort"), SORT_FIELDS, DEFAULT_SORT);

        ErpQuery<GoodsReceipt> q = ErpQuery.of(em, GoodsReceipt.class, "g")
                .join("left join g.supplier s", "left join g.purchaseOrder po")
                .fetch("left join fetch g.supplier", "left join fetch g.invoice", "left join fetch g.purchaseOrder");
        f.applyTo(q);
        q.orderBy(orderBy(sort));
        long total = q.count();
        List<GoodsReceipt> rows = q.list(paging.skip(), paging.limit());

        Map<String, Long> itemCounts = new HashMap<>();
        if (!rows.isEmpty()) {
            em.createQuery("select gi.goodsReceiptId, count(gi) from GoodsReceiptItem gi where gi.goodsReceiptId in :ids "
                            + "group by gi.goodsReceiptId", Object[].class)
                    .setParameter("ids", rows.stream().map(GoodsReceipt::getId).toList())
                    .getResultList()
                    .forEach(r -> itemCounts.put((String) r[0], ((Number) r[1]).longValue()));
        }
        PurchaseShape shape = PurchaseShape.scalars()
                .with("supplier", PurchaseShape.select("id", "name"))
                .with("invoice", PurchaseShape.select("id", "invoiceNumber"))
                .with("purchaseOrder", PurchaseShape.select("id", "poNumber"));
        List<ObjectNode> data = new ArrayList<>();
        for (GoodsReceipt g : rows) {
            ObjectNode n = shape.of(g);
            n.set("_count", ErpJson.tree(ErpJson.map("items", itemCounts.getOrDefault(g.getId(), 0L))));
            data.add(n);
        }
        return ListQuery.result(data, total, paging);
    }

    /** GET /pending?partyId= — STT yang belum ditagih untuk modal PI (titipan & legacy dikecualikan). */
    @ErpTx(readOnly = true)
    public Map<String, Object> pending(String partyId) {
        if (partyId == null || partyId.isEmpty()) {
            throw ErpException.business("partyId wajib diisi.");
        }
        List<GoodsReceipt> rows = em.createQuery("select g from GoodsReceipt g left join fetch g.items it "
                        + "left join fetch it.inventoryItem left join fetch it.warehouse left join fetch g.supplier "
                        + "where g.partyId = :p and g.status = :st and g.invoiceId is null and g.isLegacyBackfill = false "
                        + "and g.isCustomerOwned = false order by g.date asc", GoodsReceipt.class)
                .setParameter("p", partyId).setParameter("st", DocumentStatus.Submitted).getResultList();
        PurchaseShape shape = PurchaseShape.scalars()
                .with("items", PurchaseShape.scalars().with("inventoryItem").with("warehouse"))
                .with("supplier", PurchaseShape.select("id", "name"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", shape.list(rows));
        return out;
    }

    /** GET /:id — STT + items (item, gudang, gerakan stok, baris PO) + supplier + invoice + PO + user. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<GoodsReceipt> found = em.createQuery("select g from GoodsReceipt g left join fetch g.items it "
                        + "left join fetch it.inventoryItem left join fetch it.warehouse left join fetch it.stockMovement "
                        + "left join fetch it.purchaseOrderItem left join fetch g.supplier left join fetch g.invoice "
                        + "left join fetch g.purchaseOrder left join fetch g.user where g.id = :id", GoodsReceipt.class)
                .setParameter("id", id).getResultList();
        if (found.isEmpty()) {
            throw ErpException.notFound("STT tidak ditemukan.");
        }
        return PurchaseShape.scalars()
                .with("items", PurchaseShape.scalars().with("inventoryItem").with("warehouse").with("stockMovement")
                        .with("purchaseOrderItem"))
                .with("supplier")
                .with("invoice", PurchaseShape.select("id", "invoiceNumber", "status", "grandTotal"))
                .with("purchaseOrder", PurchaseShape.select("id", "poNumber", "date"))
                .with("user", PurchaseShape.select("id", "fullName"))
                .of(found.get(0));
    }
}
