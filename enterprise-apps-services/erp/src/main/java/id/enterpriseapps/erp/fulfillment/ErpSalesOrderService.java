package id.enterpriseapps.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpDocumentNumberService;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.entity.CustomerDepositApplication;
import id.enterpriseapps.erp.entity.DeliveryOrder;
import id.enterpriseapps.erp.entity.DeliveryOrderItem;
import id.enterpriseapps.erp.entity.DocumentStatus;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.InventoryItem;
import id.enterpriseapps.erp.entity.Party;
import id.enterpriseapps.erp.entity.Payment;
import id.enterpriseapps.erp.entity.PaymentType;
import id.enterpriseapps.erp.entity.SalesOrder;
import id.enterpriseapps.erp.entity.SalesOrderItem;
import id.enterpriseapps.erp.entity.SalesOrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pesanan Penjualan (SO) — port {@code routes/salesOrders.ts} + {@code services/salesOrderList.ts}.
 *
 * <p>SO adalah komitmen jual sebelum DO + invoice (tanpa dampak GL/stok). Qty terkirim + status SO
 * diturunkan dari baris DO tertaut lewat {@link ErpSalesOrderDeliveryService} (satu sumber kebenaran
 * untuk generate-do, edit DO, dan batal DO). Bypass stok minus = capability {@code inventory.negative_stock}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpSalesOrderService {

    static final List<String> SORT_FIELDS = List.of("date", "soNumber", "customer", "expectedDelivery", "grandTotal");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpSalesDocumentLinkService links;
    private final ErpSalesOrderDeliveryService soDelivery;
    private final ErpDeliveryOrderSubmitService doSubmitter;
    private final ErpDeliveryOrderService deliveryOrders;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── daftar & ringkasan ─────────────────────────────

    /** Satu kondisi JPQL + parameternya. */
    private record Cond(String jpql, String param, Object value) {
    }

    /** Filter bersama daftar & ringkasan agar kartu KPI selalu menggambarkan baris yang sama. */
    private record Filter(List<Cond> conditions) {
        String where() {
            return conditions.isEmpty() ? ""
                    : " where " + String.join(" and ", conditions.stream().map(c -> "(" + c.jpql() + ")").toList());
        }

        void apply(ErpQuery<?> q) {
            conditions.forEach(c -> q.where(c.jpql(), c.param(), c.value()));
        }

        void bind(jakarta.persistence.Query q) {
            conditions.forEach(c -> q.setParameter(c.param(), c.value()));
        }
    }

    private Filter filter(Map<String, String> query) {
        List<Cond> conds = new ArrayList<>();
        String status = ListQuery.str(query, "status");
        if (status != null) {
            SalesOrderStatus st = Arrays.stream(SalesOrderStatus.values()).filter(s -> s.name().equals(status))
                    .findFirst().orElseThrow(() -> ErpException.business("Status pesanan tidak dikenal."));
            conds.add(new Cond("s.status = :status", "status", st));
        }
        String partyId = ListQuery.str(query, "partyId");
        if (partyId != null) {
            conds.add(new Cond("s.partyId = :partyId", "partyId", partyId));
        }
        String startDate = ListQuery.dateParam(query, "startDate", "Format tanggal harus YYYY-MM-DD.");
        String endDate = ListQuery.dateParam(query, "endDate", "Format tanggal harus YYYY-MM-DD.");
        Instant[] day = ErpDates.localDayRange(startDate, endDate);
        if (day[0] != null) {
            conds.add(new Cond("s.date >= :gte", "gte", day[0]));
        }
        if (day[1] != null) {
            conds.add(new Cond("s.date < :lt", "lt", day[1]));
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            conds.add(new Cond("lower(s.soNumber) like :search or lower(c.name) like :search "
                    + "or lower(s.customerRefNo) like :search", "search", ErpQuery.contains(search)));
        }
        return new Filter(conds);
    }

    /**
     * GET / — query: page, limit (≤200), status, partyId, startDate, endDate (YYYY-MM-DD),
     * search (No. SO / nama customer / No. PO customer), sort (mis. "-date") → {@code { data, total, page, limit }}.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        // Maks 200 dipertahankan untuk pemanggil yang memuat seluruh daftar (modal DO "Tarik dari SO").
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        Filter f = filter(query);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), SORT_FIELDS, DEFAULT_SORT);
        String dir = sort.dir();
        String primary = switch (sort.field()) {
            case "customer" -> "c.name " + dir;
            case "expectedDelivery" -> "s.expectedDelivery " + dir + " nulls last";
            default -> "s." + sort.field() + " " + dir;
        };
        ErpQuery<SalesOrder> q = ErpQuery.of(em, SalesOrder.class, "s").join("left join s.customer c");
        f.apply(q);
        q.orderBy("soNumber".equals(sort.field()) ? primary : primary + ", s.soNumber " + dir);

        long total = q.count();
        List<SalesOrder> rows = q.list(paging.skip(), paging.limit());
        List<String> ids = rows.stream().map(SalesOrder::getId).toList();
        var customers = FulfillmentJson.parties(em, rows.stream().map(SalesOrder::getPartyId).toList());
        Map<String, Long> itemCounts = FulfillmentJson.counts(em,
                "select i.salesOrderId, count(i) from SalesOrderItem i where i.salesOrderId in :ids group by i.salesOrderId", ids);
        Map<String, Long> doCounts = FulfillmentJson.counts(em,
                "select d.salesOrderId, count(d) from DeliveryOrder d where d.salesOrderId in :ids group by d.salesOrderId", ids);
        List<JsonNode> data = new ArrayList<>();
        for (SalesOrder so : rows) {
            ObjectNode n = FulfillmentJson.obj(so);
            n.set("customer", FulfillmentJson.relation(customers, so.getPartyId()));
            n.set("_count", ErpJson.tree(ErpJson.map(
                    "items", itemCounts.getOrDefault(so.getId(), 0L),
                    "deliveryOrders", doCounts.getOrDefault(so.getId(), 0L))));
            data.add(n);
        }
        return ListQuery.result(data, total, paging);
    }

    /** GET /summary — KPI untuk filter yang sama dengan daftar (dideklarasikan sebelum /:id). */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        Filter f = filter(query);
        var q = em.createQuery("select s.status, count(s), sum(s.grandTotal) from SalesOrder s left join s.customer c"
                + f.where() + " group by s.status", Object[].class);
        f.bind(q);
        long count = 0;
        long activeCount = 0;
        BigDecimal activeValue = BigDecimal.ZERO;
        Map<String, Object> byStatus = new LinkedHashMap<>();
        Map<SalesOrderStatus, Long> counts = new HashMap<>();
        for (Object[] g : q.getResultList()) {
            SalesOrderStatus st = (SalesOrderStatus) g[0];
            long n = ((Number) g[1]).longValue();
            BigDecimal value = Money.nz((BigDecimal) g[2]);
            count += n;
            if (st != SalesOrderStatus.Cancelled) {
                activeCount += n;
                activeValue = activeValue.add(value);
            }
            counts.put(st, n);
            byStatus.put(st.name(), ErpJson.map("count", n, "grandTotal", Money.round2(value)));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", count);
        out.put("activeCount", activeCount);
        out.put("activeValue", Money.round2(activeValue));
        out.put("draftCount", counts.getOrDefault(SalesOrderStatus.Draft, 0L));
        out.put("submittedCount", counts.getOrDefault(SalesOrderStatus.Submitted, 0L));
        out.put("partiallyDeliveredCount", counts.getOrDefault(SalesOrderStatus.PartiallyDelivered, 0L));
        out.put("fullyDeliveredCount", counts.getOrDefault(SalesOrderStatus.FullyDelivered, 0L));
        out.put("cancelledCount", counts.getOrDefault(SalesOrderStatus.Cancelled, 0L));
        out.put("byStatus", byStatus);
        return out;
    }

    // ───────────────────────────── detail ─────────────────────────────

    /** GET /:id — detail + DO terkait + uang muka + ringkasan uang muka (panel progress UI). */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        SalesOrder so = em.find(SalesOrder.class, id);
        if (so == null) {
            throw ErpException.business("Sales Order tidak ditemukan.");
        }
        ObjectNode n = FulfillmentJson.obj(so);
        n.set("customer", FulfillmentJson.scalars(em.find(Party.class, so.getPartyId())));

        List<SalesOrderItem> items = em.createQuery(
                        "select i from SalesOrderItem i left join fetch i.inventoryItem where i.salesOrderId = :id "
                                + "order by i.createdAt, i.id", SalesOrderItem.class)
                .setParameter("id", id).getResultList();
        ArrayNode itemNodes = ErpJson.arrayNode();
        for (SalesOrderItem it : items) {
            ObjectNode line = FulfillmentJson.obj(it);
            InventoryItem inv = it.getInventoryItemId() == null ? null : it.getInventoryItem();
            line.set("inventoryItem", inv == null ? ErpJson.tree(null) : ErpJson.tree(ErpJson.map(
                    "id", inv.getId(), "code", inv.getCode(), "name", inv.getName(), "unit", inv.getUnit(),
                    "currentStock", inv.getCurrentStock())));
            itemNodes.add(line);
        }
        n.set("items", itemNodes);

        List<DeliveryOrder> dos = em.createQuery(
                        "select d from DeliveryOrder d where d.salesOrderId = :id order by d.date asc, d.doNumber asc",
                        DeliveryOrder.class)
                .setParameter("id", id).getResultList();
        List<String> doIds = dos.stream().map(DeliveryOrder::getId).toList();
        List<DeliveryOrderItem> doItems = doIds.isEmpty() ? List.of() : em.createQuery(
                        "select i from DeliveryOrderItem i where i.deliveryOrderId in :ids order by i.createdAt, i.id",
                        DeliveryOrderItem.class)
                .setParameter("ids", doIds).getResultList();
        var invoices = FulfillmentJson.briefs(em,
                "select i.id, i.invoiceNumber, i.date, i.grandTotal, i.outstanding, i.status from SalesInvoice i where i.id in :ids",
                dos.stream().map(DeliveryOrder::getInvoiceId).toList(),
                "id", "invoiceNumber", "date", "grandTotal", "outstanding", "status");
        ArrayNode doNodes = ErpJson.arrayNode();
        for (DeliveryOrder d : dos) {
            List<Map<String, Object>> lines = new ArrayList<>();
            for (DeliveryOrderItem it : doItems) {
                if (d.getId().equals(it.getDeliveryOrderId())) {
                    lines.add(ErpJson.map("itemName", it.getItemName(), "quantity", it.getQuantity(), "unit", it.getUnit()));
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("doNumber", d.getDoNumber());
            m.put("date", d.getDate());
            m.put("status", d.getStatus());
            m.put("items", lines);
            m.put("invoice", d.getInvoiceId() == null ? null : invoices.get(d.getInvoiceId()));
            doNodes.add(ErpJson.tree(m));
        }
        n.set("deliveryOrders", doNodes);

        var quotation = FulfillmentJson.briefs(em, "select q.id, q.quotationNumber from Quotation q where q.id in :ids",
                so.getQuotationId() == null ? List.of() : List.of(so.getQuotationId()), "id", "quotationNumber");
        n.set("quotation", FulfillmentJson.relation(quotation, so.getQuotationId()));
        n.set("user", FulfillmentJson.relation(FulfillmentJson.users(em, List.of(so.getCreatedBy())), so.getCreatedBy()));

        List<Payment> deposits = em.createQuery(
                        "select p from Payment p where p.salesOrderId = :id and p.status <> :cancelled and p.paymentType = :type "
                                + "order by p.date desc, p.paymentNumber desc", Payment.class)
                .setParameter("id", id)
                .setParameter("cancelled", DocumentStatus.Cancelled)
                .setParameter("type", PaymentType.CustomerDeposit)
                .getResultList();
        List<String> depositIds = deposits.stream().map(Payment::getId).toList();
        List<CustomerDepositApplication> apps = depositIds.isEmpty() ? List.of() : em.createQuery(
                        "select a from CustomerDepositApplication a where a.depositPaymentId in :ids and a.isCancelled = false",
                        CustomerDepositApplication.class)
                .setParameter("ids", depositIds).getResultList();
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal applied = BigDecimal.ZERO;
        ArrayNode depNodes = ErpJson.arrayNode();
        for (Payment p : deposits) {
            List<Map<String, Object>> pApps = new ArrayList<>();
            for (CustomerDepositApplication a : apps) {
                if (p.getId().equals(a.getDepositPaymentId())) {
                    pApps.add(ErpJson.map("appliedAmount", a.getAppliedAmount()));
                    applied = applied.add(Money.nz(a.getAppliedAmount()));
                }
            }
            received = received.add(Money.nz(p.getAmount()).subtract(Money.nz(p.getRefundedAmount())));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("paymentNumber", p.getPaymentNumber());
            m.put("date", p.getDate());
            m.put("amount", p.getAmount());
            m.put("refundedAmount", p.getRefundedAmount());
            m.put("customerDepositApplications", pApps);
            depNodes.add(ErpJson.tree(m));
        }
        n.set("depositPayments", depNodes);

        // Ringkasan uang muka: DP diterima (neto refund), DP terpakai di invoice, sisa vs grand total.
        BigDecimal grandTotal = Money.nz(so.getGrandTotal());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("received", FulfillmentJson.num(received));
        summary.put("applied", FulfillmentJson.num(applied));
        summary.put("depositRemaining", FulfillmentJson.num(received.subtract(applied).max(BigDecimal.ZERO)));
        summary.put("grandTotal", FulfillmentJson.num(grandTotal));
        summary.put("remaining", FulfillmentJson.num(grandTotal.subtract(received).max(BigDecimal.ZERO)));
        summary.put("pct", grandTotal.signum() > 0
                ? received.multiply(BigDecimal.valueOf(100)).divide(grandTotal, 0, RoundingMode.HALF_UP).intValue() : 0);
        n.set("depositSummary", ErpJson.tree(summary));
        return n;
    }

    // ───────────────────────────── tulis ─────────────────────────────

    /** Subtotal = Σ amount baris; grand total = max(0, subtotal − potongan) + PPN + biaya lain (2 desimal). */
    private static BigDecimal[] totals(FulfillmentRequests.SoInput body) {
        BigDecimal subtotal = body.items().stream().map(FulfillmentRequests.SoItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal afterDisc = subtotal.subtract(body.potongan()).max(BigDecimal.ZERO);
        BigDecimal tax = afterDisc.multiply(body.taxPct()).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        BigDecimal grandTotal = afterDisc.add(tax).add(body.biayaLain());
        return new BigDecimal[]{Money.round2(subtotal), Money.round2(grandTotal)};
    }

    private void createItems(String soId, List<FulfillmentRequests.SoItem> items) {
        // created_at bertambah 1 ms agar urutan input terjaga (kolom presisi milidetik).
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        int seq = 0;
        for (FulfillmentRequests.SoItem i : items) {
            SalesOrderItem it = new SalesOrderItem();
            it.setSalesOrderId(soId);
            it.setInventoryItemId(i.inventoryItemId());
            it.setItemName(i.itemName());
            it.setQuantity(i.quantity());
            it.setUnit(i.unit());
            it.setRate(i.rate());
            it.setDiscount(i.discount());
            it.setAmount(i.amount());
            it.setDescription(i.description());
            it.setDeliveredQty(BigDecimal.ZERO);
            it.setCreatedAt(base.plusMillis(seq++));
            em.persist(it);
        }
    }

    /** SO + items + customer lengkap ({@code include: { items: true, customer: true }}), dibaca ulang dari DB. */
    private ObjectNode reloadWithItemsAndCustomer(String id) {
        em.flush();
        em.clear();
        SalesOrder so = em.find(SalesOrder.class, id);
        List<SalesOrderItem> items = em.createQuery(
                        "select i from SalesOrderItem i where i.salesOrderId = :id order by i.createdAt, i.id", SalesOrderItem.class)
                .setParameter("id", id).getResultList();
        ObjectNode n = FulfillmentJson.obj(so);
        n.set("items", FulfillmentJson.array(items));
        n.set("customer", FulfillmentJson.scalars(em.find(Party.class, so.getPartyId())));
        return n;
    }

    /** POST / — SO baru berstatus Draft (nomor PSN-YYYYMM-NNNN). */
    @ErpTx
    public ObjectNode create(JsonNode raw, String userId) {
        FulfillmentRequests.SoInput body = FulfillmentRequests.salesOrder(raw);
        Instant date = ErpDates.toInstant(body.date());
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
        String quotationId = Zod.truthy(body.quotationId());
        if (quotationId != null) {
            links.assertQuotationLinkable(quotationId, body.partyId());
        }
        String soNumber = numbers.generate("PSN", date, fy.getId());
        BigDecimal[] t = totals(body);

        SalesOrder so = new SalesOrder();
        so.setSoNumber(soNumber);
        so.setDate(date);
        so.setExpectedDelivery(Zod.truthy(body.expectedDelivery()) == null ? null : ErpDates.toInstant(body.expectedDelivery()));
        so.setPartyId(body.partyId());
        so.setCustomerRefNo(body.customerRefNo());
        so.setQuotationId(quotationId);
        so.setStatus(SalesOrderStatus.Draft);
        so.setNotes(body.notes());
        so.setSubtotal(t[0]);
        so.setTaxPct(body.taxPct());
        so.setPotongan(body.potongan());
        so.setBiayaLain(body.biayaLain());
        so.setGrandTotal(t[1]);
        so.setFiscalYearId(fy.getId());
        so.setCreatedBy(userId);
        em.persist(so);
        em.flush();
        createItems(so.getId(), body.items());
        return reloadWithItemsAndCustomer(so.getId());
    }

    /** PUT /:id — hanya SO Draft; item diganti seluruhnya, nomor & tahun fiskal tetap. */
    @ErpTx
    public ObjectNode update(String id, JsonNode raw) {
        FulfillmentRequests.SoInput body = FulfillmentRequests.salesOrder(raw);
        SalesOrder so = em.find(SalesOrder.class, id);
        if (so == null) {
            throw ErpException.business("Sales Order tidak ditemukan.");
        }
        if (so.getStatus() != SalesOrderStatus.Draft) {
            throw ErpException.business("Sales Order yang sudah Submitted tidak bisa diedit. Cancel dulu kalau perlu.");
        }
        BigDecimal[] t = totals(body);
        String quotationId = links.resolveSalesOrderQuotationId(body.quotationIdPresent(), body.quotationId(),
                so.getQuotationId(), body.partyId());
        em.createQuery("delete from SalesOrderItem i where i.salesOrderId = :id").setParameter("id", so.getId()).executeUpdate();
        so.setDate(ErpDates.toInstant(body.date()));
        so.setExpectedDelivery(Zod.truthy(body.expectedDelivery()) == null ? null : ErpDates.toInstant(body.expectedDelivery()));
        so.setPartyId(body.partyId());
        so.setCustomerRefNo(body.customerRefNo());
        so.setQuotationId(quotationId);
        so.setNotes(body.notes());
        so.setSubtotal(t[0]);
        so.setTaxPct(body.taxPct());
        so.setPotongan(body.potongan());
        so.setBiayaLain(body.biayaLain());
        so.setGrandTotal(t[1]);
        so.setUpdatedAt(Instant.now());
        em.flush();
        createItems(so.getId(), body.items());
        return reloadWithItemsAndCustomer(so.getId());
    }

    /** POST /:id/submit — Draft → Submitted. */
    @ErpTx
    public ObjectNode submit(String id) {
        SalesOrder so = em.find(SalesOrder.class, id);
        if (so == null) {
            throw ErpException.business("Sales Order tidak ditemukan.");
        }
        if (so.getStatus() != SalesOrderStatus.Draft) {
            throw ErpException.business("Hanya SO status Draft yang bisa di-submit.");
        }
        so.setStatus(SalesOrderStatus.Submitted);
        so.setSubmittedAt(Instant.now());
        return reloadWithItemsAndCustomer(so.getId());
    }

    /** POST /:id/cancel — ditolak selama masih ada Surat Jalan aktif. Respons = header SO saja. */
    @ErpTx
    public ObjectNode cancel(String id) {
        SalesOrder so = em.find(SalesOrder.class, id);
        if (so == null) {
            throw ErpException.business("Sales Order tidak ditemukan.");
        }
        if (so.getStatus() == SalesOrderStatus.Cancelled) {
            throw ErpException.business("Sales Order sudah dibatalkan.");
        }
        Long activeDos = em.createQuery(
                        "select count(d) from DeliveryOrder d where d.salesOrderId = :id and d.status <> :cancelled", Long.class)
                .setParameter("id", so.getId())
                .setParameter("cancelled", DocumentStatus.Cancelled)
                .getSingleResult();
        if (activeDos > 0) {
            throw ErpException.business("Sales Order punya " + activeDos
                    + " Surat Jalan aktif. Batalkan SJ dulu sebelum cancel SO.");
        }
        so.setStatus(SalesOrderStatus.Cancelled);
        so.setCancelledAt(Instant.now());
        em.flush();
        return FulfillmentJson.obj(so);
    }

    /**
     * POST /:id/generate-do — buat Surat Jalan dari SO lewat submit DO biasa (stok keluar WAC terkunci),
     * tautkan DO & barisnya ke SO, lalu turunkan ulang qty terkirim + status SO. Lebih kirim (qty &gt; sisa)
     * DIPERBOLEHKAN — SO menampilkannya sebagai "+X lebih"; hanya dicatat di log sebagai jejak audit.
     *
     * @param canBypassNegativeStock aktor punya capability {@code inventory.negative_stock}
     */
    @ErpTx(timeout = 30)
    public ObjectNode generateDeliveryOrder(String id, JsonNode raw, String userId, boolean canBypassNegativeStock) {
        FulfillmentRequests.GenerateDoInput body = FulfillmentRequests.generateDo(raw);
        if (body.allowNegativeStock() && !canBypassNegativeStock) {
            throw ErpException.of(HttpStatus.FORBIDDEN, "Hanya Admin yang dapat bypass validasi stok.");
        }
        SalesOrder so = em.find(SalesOrder.class, id);
        if (so == null) {
            throw ErpException.business("Sales Order tidak ditemukan.");
        }
        if (so.getStatus() != SalesOrderStatus.Submitted && so.getStatus() != SalesOrderStatus.PartiallyDelivered) {
            throw ErpException.business("Hanya SO status Submitted atau PartiallyDelivered yang bisa generate Surat Jalan.");
        }
        Map<String, SalesOrderItem> soItems = new LinkedHashMap<>();
        em.createQuery("select i from SalesOrderItem i where i.salesOrderId = :id order by i.createdAt, i.id", SalesOrderItem.class)
                .setParameter("id", so.getId()).getResultList().forEach(i -> soItems.put(i.getId(), i));

        for (FulfillmentRequests.GenerateDoItem b : body.items()) {
            SalesOrderItem soItem = soItems.get(b.salesOrderItemId());
            if (soItem == null) {
                throw ErpException.business("SO item " + b.salesOrderItemId() + " tidak ditemukan di SO ini.");
            }
            BigDecimal remaining = Money.nz(soItem.getQuantity()).subtract(Money.nz(soItem.getDeliveredQty()));
            if (b.quantity().compareTo(remaining) > 0) {
                log.info("Generate DO with over-delivery (qty exceeds remaining SO qty) soId={} soNumber={} soItemId={} itemName={} qty={} remaining={}",
                        so.getId(), so.getSoNumber(), soItem.getId(), soItem.getItemName(), b.quantity(), remaining);
            }
        }

        List<FulfillmentRequests.DoItem> doItems = new ArrayList<>();
        for (FulfillmentRequests.GenerateDoItem b : body.items()) {
            SalesOrderItem soItem = soItems.get(b.salesOrderItemId());
            String description = b.description() != null ? b.description() : soItem.getDescription();
            doItems.add(new FulfillmentRequests.DoItem(Zod.truthy(soItem.getInventoryItemId()), Zod.truthy(b.warehouseId()),
                    soItem.getItemName(), b.quantity(), soItem.getUnit(), description));
        }
        FulfillmentRequests.DoInput doBody = new FulfillmentRequests.DoInput(body.date(), so.getPartyId(),
                body.nomorMobil(), body.driverName(), body.recipientName(),
                body.notes() != null ? body.notes() : "Auto dari SO " + so.getSoNumber(),
                doItems, body.allowNegativeStock(), body.negativeStockReason(), body.temporaryNoteIds());
        ErpDeliveryOrderSubmitService.Submitted s = doSubmitter.submitInTx(doBody, userId);

        // Tautkan DO ke SO dan tiap baris DO ke item SO (urutan baris = urutan body).
        s.deliveryOrder().setSalesOrderId(so.getId());
        for (int i = 0; i < body.items().size() && i < s.items().size(); i++) {
            s.items().get(i).setSalesOrderItemId(body.items().get(i).salesOrderItemId());
        }
        em.flush();

        // delivered_qty + status diturunkan dari baris DO yang benar-benar tertaut.
        soDelivery.recomputeDeliveredQty(so.getId());

        String doId = s.deliveryOrder().getId();
        log.info("DO generated from SO doId={} soId={}", doId, id);
        return deliveryOrders.reloadWithItemsAndCustomer(doId, true);
    }
}
