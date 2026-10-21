package id.enterpriseapps.erp.purchase;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpAllocationService;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.PaymentAllocation;
import id.enterpriseapps.erp.entity.PurchaseInvoice;
import id.enterpriseapps.erp.entity.PurchaseInvoiceItem;
import id.enterpriseapps.erp.entity.VendorDepositApplication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Daftar, ringkasan KPI, dan detail invoice pembelian — port {@code purchaseInvoiceList.ts} dan
 * handler GET di {@code routes/purchase.ts}. Daftar & ringkasan memakai filter yang sama sehingga
 * kartu KPI selalu menggambarkan baris yang sama dengan tabel.
 */
@Service
@RequiredArgsConstructor
public class ErpPurchaseInvoiceQueryService {

    static final Set<String> SORT_FIELDS = Set.of("date", "dueDate", "invoiceNumber", "supplier", "grandTotal", "outstanding");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);
    private static final String DATE_ERROR = "Format tanggal harus YYYY-MM-DD.";

    private final ErpAllocationService allocations;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Kondisi WHERE bersama (alias invoice {@code i}, supplier {@code s} bila {@code supplierJoin}). */
    record Filter(List<String> conditions, Map<String, Object> params, boolean supplierJoin) {
        String where() {
            return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        }

        void bind(jakarta.persistence.Query q) {
            params.forEach(q::setParameter);
        }

        /** Terapkan ke ErpQuery (semua kondisi sebagai satu AND berparameter). */
        <T> void applyTo(ErpQuery<T> q) {
            if (conditions.isEmpty()) {
                return;
            }
            Object[] nv = new Object[params.size() * 2];
            int k = 0;
            for (Map.Entry<String, Object> e : params.entrySet()) {
                nv[k++] = e.getKey();
                nv[k++] = e.getValue();
            }
            q.where(String.join(" and ", conditions), nv);
        }
    }

    /** Port {@code buildPurchaseInvoiceWhere}: status, partyId, startDate/endDate, search. */
    Filter buildWhere(Map<String, String> query) {
        List<String> conds = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        boolean supplierJoin = false;

        String status = ListQuery.str(query, "status");
        if (status != null) {
            InvoiceStatus st;
            try {
                st = InvoiceStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw ErpException.business("Status invoice tidak dikenal.");
            }
            conds.add("i.status = :st");
            params.put("st", st);
        }
        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            conds.add("i.partyId = :pid");
            params.put("pid", partyId);
        }
        String start = ListQuery.dateParam(query, "startDate", DATE_ERROR);
        String end = ListQuery.dateParam(query, "endDate", DATE_ERROR);
        Instant[] range = ErpDates.localDayRange(start, end);
        if (range[0] != null) {
            conds.add("i.date >= :dGte");
            params.put("dGte", range[0]);
        }
        if (range[1] != null) {
            conds.add("i.date < :dLt");
            params.put("dLt", range[1]);
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            conds.add("(lower(i.invoiceNumber) like :q or lower(s.name) like :q)");
            params.put("q", ErpQuery.contains(search));
            supplierJoin = true;
        }
        return new Filter(conds, params, supplierJoin);
    }

    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        String primary = switch (sort.field()) {
            case "supplier" -> "s.name " + dir;
            case "dueDate" -> "i.dueDate " + dir + " nulls last";
            default -> "i." + sort.field() + " " + dir;
        };
        // Tie-breaker stabil agar baris tidak berpindah antar halaman.
        return "invoiceNumber".equals(sort.field()) ? primary : primary + ", i.invoiceNumber " + dir;
    }

    /** GET / — {@code { data, total, page, limit }}; tiap baris + supplier + items. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        // Maks 500 dipertahankan untuk pemanggil lama yang masih memuat seluruh daftar.
        ListQuery.Paging paging = ListQuery.paging(query, 50, 500);
        Filter f = buildWhere(query);
        ListQuery.Sort sort = ListQuery.sort(ListQuery.str(query, "sort"), SORT_FIELDS, DEFAULT_SORT);

        ErpQuery<PurchaseInvoice> q = ErpQuery.of(em, PurchaseInvoice.class, "i")
                .join("left join i.supplier s")
                .fetch("left join fetch i.supplier", "left join fetch i.items");
        f.applyTo(q);
        q.orderBy(orderBy(sort));
        long total = q.count();
        List<PurchaseInvoice> rows = q.list(paging.skip(), paging.limit());
        PurchaseShape shape = PurchaseShape.scalars().with("supplier").with("items");
        List<ObjectNode> data = rows.stream().map(shape::of).toList();
        return ListQuery.result(data, total, paging);
    }

    /** GET /summary — total per status + jatuh tempo, untuk filter yang sama dengan daftar. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        Filter f = buildWhere(query);
        String from = " from PurchaseInvoice i" + (f.supplierJoin() ? " left join i.supplier s" : "");
        TypedQuery<Object[]> groups = em.createQuery(
                "select i.status, count(i), coalesce(sum(i.grandTotal), 0), coalesce(sum(i.outstanding), 0)"
                        + from + f.where() + " group by i.status", Object[].class);
        f.bind(groups);

        // Aturan sama dengan indikator baris: lewat jatuh tempo dan masih ada sisa hutang.
        String overdueWhere = (f.where().isEmpty() ? " where " : f.where() + " and ")
                + "i.dueDate < :odNow and i.outstanding > 0";
        TypedQuery<Object[]> overdue = em.createQuery(
                "select count(i), coalesce(sum(i.outstanding), 0)" + from + overdueWhere, Object[].class);
        f.bind(overdue);
        overdue.setParameter("odNow", Instant.now());

        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        long count = 0;
        Map<String, Object> byStatus = new LinkedHashMap<>();
        for (Object[] g : groups.getResultList()) {
            InvoiceStatus st = (InvoiceStatus) g[0];
            long n = ((Number) g[1]).longValue();
            BigDecimal gt = Money.of(g[2]);
            BigDecimal os = Money.of(g[3]);
            count += n;
            if (st != InvoiceStatus.Cancelled) {
                grandTotal = grandTotal.add(gt);
            }
            // Pembatalan meng-nol-kan outstanding, jadi semua status boleh dijumlah.
            outstanding = outstanding.add(os);
            byStatus.put(st.name(), ErpJson.map("count", n, "grandTotal", Money.round2(gt), "outstanding", Money.round2(os)));
        }
        Object[] od = overdue.getSingleResult();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", count);
        out.put("grandTotal", Money.round2(grandTotal));
        out.put("outstanding", Money.round2(outstanding));
        out.put("paidCount", statusCount(byStatus, InvoiceStatus.Paid));
        out.put("cancelledCount", statusCount(byStatus, InvoiceStatus.Cancelled));
        out.put("overdueCount", ((Number) od[0]).longValue());
        out.put("overdueOutstanding", Money.round2(Money.of(od[1])));
        out.put("byStatus", byStatus);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static long statusCount(Map<String, Object> byStatus, InvoiceStatus st) {
        Object v = byStatus.get(st.name());
        return v == null ? 0 : ((Number) ((Map<String, Object>) v).get("count")).longValue();
    }

    /** Detail + baris untuk three-way match (dihitung terpisah agar kegagalannya tidak merusak detail). */
    record Detail(ObjectNode node, List<ErpThreeWayMatchService.MatchLine> matchLines) {
    }

    /** GET /:id — invoice + supplier + items(+STT asal) + user + uang muka + alokasi pembayaran. */
    @ErpTx(readOnly = true)
    public Detail detail(String id) {
        List<PurchaseInvoice> found = em.createQuery("select i from PurchaseInvoice i left join fetch i.supplier "
                        + "left join fetch i.user where i.id = :id", PurchaseInvoice.class)
                .setParameter("id", id).getResultList();
        if (found.isEmpty()) {
            throw ErpException.notFound("Invoice tidak ditemukan.");
        }
        PurchaseInvoice inv = found.get(0);
        List<PurchaseInvoiceItem> items = em.createQuery("select it from PurchaseInvoiceItem it "
                        + "left join fetch it.goodsReceiptItem gi left join fetch gi.goodsReceipt "
                        + "where it.purchaseInvoiceId = :id", PurchaseInvoiceItem.class)
                .setParameter("id", id).getResultList();
        // Termasuk alokasi uang muka yang dibatalkan (read-only) agar kronologi pembayaran lengkap.
        List<VendorDepositApplication> deposits = em.createQuery("select d from VendorDepositApplication d "
                        + "left join fetch d.depositPayment where d.purchaseInvoiceId = :id order by d.appliedAt asc",
                VendorDepositApplication.class).setParameter("id", id).getResultList();
        List<PaymentAllocation> allocs = em.createQuery("select a from PaymentAllocation a join fetch a.payment p "
                        + "left join fetch p.account left join fetch p.splits sp left join fetch sp.account "
                        + "where a.invoiceType = 'PurchaseInvoice' and a.invoiceId = :id order by p.date asc",
                PaymentAllocation.class).setParameter("id", id).getResultList();

        ObjectNode node = PurchaseShape.scalars()
                .with("supplier")
                .with("user", PurchaseShape.select("id", "fullName"))
                .of(inv);
        // STT/BPB asal per baris, agar PI menunjukkan dari penerimaan mana tiap item berasal.
        node.set("items", PurchaseShape.scalars()
                .with("goodsReceiptItem", PurchaseShape.select("id")
                        .with("goodsReceipt", PurchaseShape.select("id", "sttNumber", "date")))
                .list(items));
        node.set("depositApplications", PurchaseShape.scalars()
                .with("depositPayment", PurchaseShape.select("id", "paymentNumber", "date", "amount"))
                .list(deposits));
        ArrayNode allocArr = ErpJson.arrayNode();
        PurchaseShape allocShape = PurchaseShape.scalars();
        for (PaymentAllocation a : allocs) {
            ObjectNode o = allocShape.of(a);
            o.set("payment", ErpJson.tree(allocations.paymentForAllocation(a.getPayment())));
            allocArr.add(o);
        }
        node.set("paymentAllocations", allocArr);

        List<ErpThreeWayMatchService.MatchLine> lines = items.stream()
                .map(it -> new ErpThreeWayMatchService.MatchLine(it.getId(), it.getItemName(), it.getUnit(),
                        it.getQuantity(), it.getTimbanganDiterima(), it.getRate(), it.getDiscount(),
                        it.getGoodsReceiptItemId()))
                .toList();
        return new Detail(node, lines);
    }
}
