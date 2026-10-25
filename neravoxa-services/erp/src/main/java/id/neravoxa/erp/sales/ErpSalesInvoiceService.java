package id.neravoxa.erp.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.accounting.ErpAllocationService;
import id.neravoxa.erp.entity.CustomerDepositApplication;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.PaymentAllocation;
import id.neravoxa.erp.entity.SalesInvoice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daftar, ringkasan, detail dan edit metadata invoice penjualan — handler baca/edit di
 * {@code routes/sales.ts} + {@code services/salesInvoiceList.ts}. Posting ada di
 * {@link ErpSalesInvoiceSubmitService}, pembatalan di {@link ErpSalesInvoiceCancelService}.
 */
@Service
@RequiredArgsConstructor
public class ErpSalesInvoiceService {

    private final ErpAllocationService allocations;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * GET /sales/invoices — page, limit (≤500), status, partyId, startDate, endDate, search, sort.
     * Tiap baris = invoice + customer + items + {@code deductionTotal}.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        // Maks 500 dipertahankan untuk pemanggil lama yang masih memuat seluruh daftar.
        ListQuery.Paging paging = ListQuery.paging(query, 50, 500);
        JpqlFilter where = SalesInvoiceList.where(query);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), SalesInvoiceList.SORT_FIELDS, SalesInvoiceList.DEFAULT_SORT);

        ErpQuery<SalesInvoice> q = where.applyTo(ErpQuery.of(em, SalesInvoice.class, "i")
                        .fetch("left join fetch i.customer", "left join fetch i.items"))
                .orderBy(SalesInvoiceList.orderBy(sort));
        long total = q.count();
        List<SalesInvoice> invoices = q.list(paging.skip(), paging.limit());

        Map<String, BigDecimal> deductions = deductionTotals(invoices.stream().map(SalesInvoice::getId).toList());
        List<ObjectNode> data = new ArrayList<>(invoices.size());
        for (SalesInvoice inv : invoices) {
            ObjectNode node = withCustomerAndItems(inv);
            node.set("deductionTotal", ErpJson.tree(deductions.getOrDefault(inv.getId(), BigDecimal.ZERO)));
            data.add(node);
        }
        return ListQuery.result(data, total, paging);
    }

    /**
     * deductionTotal: jumlah split pembayaran ke akun non-kas (komisi, diskon, dll) per invoice.
     * Satu query batch (port {@code $queryRaw} di sumber).
     */
    private Map<String, BigDecimal> deductionTotals(List<String> invoiceIds) {
        Map<String, BigDecimal> out = new HashMap<>();
        if (invoiceIds.isEmpty()) {
            return out;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT pa.invoice_id AS invoice_id, COALESCE(SUM(ps.amount), 0) AS total
                        FROM payment_allocations pa
                        JOIN payments p ON p.id = pa.payment_id
                        JOIN payment_splits ps ON ps.payment_id = p.id
                        JOIN accounts a ON a.id = ps.account_id
                        WHERE pa.invoice_type = 'SalesInvoice'
                          AND pa.invoice_id IN (:ids)
                          AND p.status != 'Cancelled'
                          AND a.account_number NOT LIKE '1.1%'
                        GROUP BY pa.invoice_id""")
                .setParameter("ids", invoiceIds)
                .getResultList();
        for (Object[] r : rows) {
            out.put(String.valueOf(r[0]), SalesSupport.num(new BigDecimal(r[1].toString())));
        }
        return out;
    }

    /** GET /sales/invoices/summary — KPI dengan filter yang sama dengan daftar. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        JpqlFilter where = SalesInvoiceList.where(query);
        List<Object[]> groups = where.aggregate(em,
                "i.status, count(i), sum(i.grandTotal), sum(i.outstanding)", "group by i.status",
                Object[].class, null).getResultList();
        Object[] overdue = where.aggregate(em, "count(i), sum(i.outstanding)", null, Object[].class,
                SalesInvoiceList.OVERDUE, "overdueNow", Instant.now()).getSingleResult();
        long overdueCount = overdue[0] == null ? 0 : ((Number) overdue[0]).longValue();
        BigDecimal overdueOutstanding = overdue[1] == null ? BigDecimal.ZERO : (BigDecimal) overdue[1];
        return SalesInvoiceList.summarize(groups, overdueCount, overdueOutstanding);
    }

    /**
     * GET /sales/invoices/:id — invoice + customer + items (layanan, barang dgn averageCost untuk
     * analisis margin "Material WAC", batch sumber, baris Surat Jalan) + user + alokasi pembayaran
     * (dengan split, agar PDF bisa memisah kas vs potongan) + aplikasi uang muka (termasuk yang batal).
     */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<SalesInvoice> rows = em.createQuery(
                        "select i from SalesInvoice i left join fetch i.customer left join fetch i.items where i.id = :id",
                        SalesInvoice.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.of(404, "Invoice tidak ditemukan.");
        }
        SalesInvoice invoice = rows.get(0);
        ObjectNode node = SalesJson.obj(invoice);
        node.set("customer", SalesJson.scalars(invoice.getCustomer()));
        node.set("items", detailItems(invoice));
        Map<String, Map<String, Object>> users = SalesJson.users(em, List.of(invoice.getCreatedBy()));
        node.set("user", SalesJson.relation(users, invoice.getCreatedBy()));
        node.set("paymentAllocations", paymentAllocations(invoice.getId()));
        node.set("depositApplications", depositApplications(invoice.getId()));
        return node;
    }

    private ArrayNode detailItems(SalesInvoice invoice) {
        var items = invoice.getItems();
        Map<String, Map<String, Object>> services = SalesJson.briefs(em,
                "select s.id, s.code, s.name from ServiceItem s where s.id in :ids",
                items.stream().map(i -> i.getServiceItemId()).toList(), "id", "code", "name");
        // inventoryItem.averageCost = basis "Material WAC" analisis margin; unitCostLoaded (sudah
        // ada di baris) = basis "Loaded HPP".
        Map<String, Map<String, Object>> inventory = SalesJson.briefs(em,
                "select v.id, v.code, v.name, v.averageCost from InventoryItem v where v.id in :ids",
                items.stream().map(i -> i.getInventoryItemId()).toList(), "id", "code", "name", "averageCost");
        Map<String, Map<String, Object>> movements = SalesJson.briefs(em,
                "select m.id, m.movementNumber, m.date, m.referenceNumber from StockMovement m where m.id in :ids",
                items.stream().map(i -> i.getSourceMovementId()).toList(), "id", "movementNumber", "date", "referenceNumber");
        Map<String, Map<String, Object>> doItems = new HashMap<>();
        List<String> doItemIds = items.stream().map(i -> i.getDeliveryOrderItemId()).filter(x -> x != null).toList();
        if (!doItemIds.isEmpty()) {
            List<Object[]> rows = em.createQuery(
                            "select d.id, o.id, o.doNumber, o.date from DeliveryOrderItem d join d.deliveryOrder o where d.id in :ids",
                            Object[].class)
                    .setParameter("ids", doItemIds).getResultList();
            for (Object[] r : rows) {
                Map<String, Object> order = new LinkedHashMap<>();
                order.put("id", r[1]);
                order.put("doNumber", r[2]);
                order.put("date", r[3]);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r[0]);
                m.put("deliveryOrder", order);
                doItems.put((String) r[0], m);
            }
        }
        ArrayNode arr = ErpJson.arrayNode();
        for (var item : items) {
            ObjectNode n = SalesJson.obj(item);
            n.set("serviceItem", SalesJson.relation(services, item.getServiceItemId()));
            n.set("inventoryItem", SalesJson.relation(inventory, item.getInventoryItemId()));
            n.set("sourceMovement", SalesJson.relation(movements, item.getSourceMovementId()));
            n.set("deliveryOrderItem", SalesJson.relation(doItems, item.getDeliveryOrderItemId()));
            arr.add(n);
        }
        return arr;
    }

    /** Alokasi pembayaran invoice + pembayaran ({@code PAYMENT_ALLOCATION_PAYMENT_SELECT}), urut tanggal bayar. */
    private ArrayNode paymentAllocations(String invoiceId) {
        List<PaymentAllocation> rows = em.createQuery(
                        "select a from PaymentAllocation a join fetch a.payment p left join fetch p.account "
                                + "left join fetch p.splits s left join fetch s.account "
                                + "where a.invoiceType = 'SalesInvoice' and a.invoiceId = :id order by p.date asc",
                        PaymentAllocation.class)
                .setParameter("id", invoiceId).getResultList();
        ArrayNode arr = ErpJson.arrayNode();
        for (PaymentAllocation a : rows) {
            ObjectNode n = SalesJson.obj(a);
            n.set("payment", ErpJson.tree(allocations.paymentForAllocation(a.getPayment())));
            arr.add(n);
        }
        return arr;
    }

    /** Aplikasi uang muka customer — termasuk yang dibatalkan (read-only) agar kronologi lengkap di UI. */
    private ArrayNode depositApplications(String invoiceId) {
        List<Object[]> rows = em.createQuery(
                        "select d, p.id, p.paymentNumber, p.date, p.amount from CustomerDepositApplication d "
                                + "join d.depositPayment p where d.salesInvoiceId = :id order by d.appliedAt asc",
                        Object[].class)
                .setParameter("id", invoiceId).getResultList();
        ArrayNode arr = ErpJson.arrayNode();
        for (Object[] r : rows) {
            ObjectNode n = SalesJson.obj((CustomerDepositApplication) r[0]);
            Map<String, Object> payment = new LinkedHashMap<>();
            payment.put("id", r[1]);
            payment.put("paymentNumber", r[2]);
            payment.put("date", r[3]);
            payment.put("amount", r[4]);
            n.set("depositPayment", ErpJson.tree(payment));
            arr.add(n);
        }
        return arr;
    }

    /** PUT /sales/invoices/:id — edit notes, dueDate, terms (tidak dikirim = tetap, null/kosong = hapus). */
    @ErpTx
    public JsonNode updateMeta(String id, SalesInvoiceRequests.MetaUpdate body) {
        SalesInvoice invoice = em.find(SalesInvoice.class, id);
        if (invoice == null) {
            throw ErpException.of(404, "Invoice tidak ditemukan.");
        }
        if (invoice.getStatus() == InvoiceStatus.Cancelled) {
            throw ErpException.business("Invoice sudah dibatalkan, tidak bisa diedit.");
        }
        if (body.has("notes")) {
            invoice.setNotes(SalesSupport.clearable(body.notes()));
        }
        if (body.has("dueDate")) {
            invoice.setDueDate(SalesSupport.hasText(body.dueDate()) ? SalesSupport.parseDate(body.dueDate()) : null);
        }
        if (body.has("terms")) {
            invoice.setTerms(SalesSupport.clearable(body.terms()));
        }
        // Prisma update menyentuh updatedAt; flush agar @PreUpdate mengisinya sebelum respons dibentuk.
        invoice.setUpdatedAt(Instant.now());
        SalesSupport.flush(em);
        return SalesJson.scalars(invoice);
    }

    /** Invoice + customer + items (padanan {@code include: { customer: true, items: true }}). */
    static ObjectNode withCustomerAndItems(SalesInvoice inv) {
        ObjectNode node = SalesJson.obj(inv);
        node.set("customer", SalesJson.scalars(inv.getCustomer()));
        node.set("items", SalesJson.array(inv.getItems()));
        return node;
    }
}
