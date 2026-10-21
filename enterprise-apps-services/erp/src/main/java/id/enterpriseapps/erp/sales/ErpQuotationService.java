package id.enterpriseapps.erp.sales;

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
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.Quotation;
import id.enterpriseapps.erp.entity.QuotationItem;
import id.enterpriseapps.erp.entity.QuotationStatus;
import id.enterpriseapps.erp.entity.SalesInvoice;
import id.enterpriseapps.erp.entity.SalesOrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Penawaran harga (quotation) — port {@code routes/quotations.ts} + {@code services/quotationList.ts}.
 *
 * <p>Siklus: Draft → Sent → Accepted/Rejected (atau Expired oleh sweeper) → dikonversi ke invoice
 * penjualan. Penawaran tidak berdampak GL; konversi memakai endpoint invoice penjualan (GL
 * single-source di sana) lalu {@code POST /:id/link} menautkan invoicenya.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpQuotationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Baris ter-resolve — padanan {@code ResolvedItem} di route sumber. */
    private record ResolvedItem(String itemType, String inventoryItemId, String serviceItemId, String accountId,
                                String itemName, BigDecimal quantity, String unit, BigDecimal rate,
                                BigDecimal discount, BigDecimal amount, BigDecimal taxPct, BigDecimal pphPct,
                                String description) {
    }

    private record Totals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal pphAmount, BigDecimal grandTotal) {
    }

    private static List<ResolvedItem> resolveItems(List<SalesItemRequest> items) {
        List<ResolvedItem> out = new ArrayList<>();
        for (SalesItemRequest it : items) {
            BigDecimal qty = it.quantity();
            BigDecimal rate = Money.nz(it.rate());
            BigDecimal discount = Money.nz(it.discount());
            BigDecimal gross = qty.multiply(rate);
            BigDecimal amount = gross.subtract(gross.multiply(discount).divide(HUNDRED)).setScale(2, RoundingMode.HALF_UP);
            out.add(new ResolvedItem(
                    it.itemType() != null ? it.itemType() : "product",
                    SalesSupport.orNull(it.inventoryItemId()),
                    SalesSupport.orNull(it.serviceItemId()),
                    SalesSupport.orNull(it.accountId()),
                    it.itemName(),
                    qty,
                    it.unit() != null ? it.unit() : "Kg",
                    rate,
                    discount,
                    amount,
                    Money.nz(it.taxPct()),
                    Money.nz(it.pphPct()),
                    it.description()));
        }
        return out;
    }

    private static Totals computeTotals(List<ResolvedItem> items, BigDecimal potongan, BigDecimal biayaLain) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal pph = BigDecimal.ZERO;
        for (ResolvedItem it : items) {
            subtotal = subtotal.add(it.amount());
            tax = tax.add(it.amount().multiply(it.taxPct().divide(HUNDRED)));
            pph = pph.add(it.amount().multiply(it.pphPct().divide(HUNDRED)));
        }
        BigDecimal taxAmount = tax.setScale(2, RoundingMode.HALF_UP);
        BigDecimal pphAmount = pph.setScale(2, RoundingMode.HALF_UP);
        BigDecimal grandTotal = subtotal.add(taxAmount).subtract(pphAmount).subtract(Money.nz(potongan))
                .add(Money.nz(biayaLain)).setScale(2, RoundingMode.HALF_UP);
        return new Totals(subtotal, taxAmount, pphAmount, grandTotal);
    }

    // ───────────────────────────── baca ─────────────────────────────

    /** GET /quotations — page, limit (≤500), status, partyId, startDate, endDate, search, sort. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 50, 500);
        JpqlFilter where = QuotationList.where(query);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), QuotationList.SORT_FIELDS, QuotationList.DEFAULT_SORT);
        ErpQuery<Quotation> q = where.applyTo(ErpQuery.of(em, Quotation.class, "q").fetch("left join fetch q.items"))
                .orderBy(QuotationList.orderBy(sort));
        long total = q.count();
        List<Quotation> rows = q.list(paging.skip(), paging.limit());

        Map<String, Map<String, Object>> customers = SalesJson.briefs(em,
                "select p.id, p.name, p.phone, p.email from Party p where p.id in :ids",
                rows.stream().map(Quotation::getPartyId).toList(), "id", "name", "phone", "email");
        Map<String, Map<String, Object>> invoices = SalesJson.briefs(em,
                "select s.id, s.invoiceNumber, s.date from SalesInvoice s where s.id in :ids",
                rows.stream().map(Quotation::getSalesInvoiceId).toList(), "id", "invoiceNumber", "date");
        List<ObjectNode> data = new ArrayList<>();
        for (Quotation qt : rows) {
            ObjectNode n = SalesJson.obj(qt);
            n.set("customer", SalesJson.relation(customers, qt.getPartyId()));
            n.set("items", SalesJson.array(qt.getItems()));
            n.set("salesInvoice", SalesJson.relation(invoices, qt.getSalesInvoiceId()));
            data.add(n);
        }
        return ListQuery.result(data, total, paging);
    }

    /** GET /quotations/summary — KPI untuk filter yang sama dengan daftar. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        JpqlFilter where = QuotationList.where(query);
        List<Object[]> groups = where.aggregate(em, "q.status, count(q), sum(q.grandTotal)", "group by q.status",
                Object[].class, null).getResultList();
        Long expiringSoon = where.aggregate(em, "count(q)", null, Long.class,
                QuotationList.EXPIRING_SOON, QuotationList.expiringSoonParams(Instant.now())).getSingleResult();
        Long awaiting = where.aggregate(em, "count(q)", null, Long.class,
                QuotationList.AWAITING_CONVERSION, "acAccepted", QuotationStatus.Accepted).getSingleResult();
        return QuotationList.summarize(groups, expiringSoon, awaiting);
    }

    /** GET /quotations/stats — ringkasan tingkat konversi ({@code from}/{@code to} opsional). */
    @ErpTx(readOnly = true)
    public Map<String, Object> stats(String from, String to) {
        JpqlFilter where = new JpqlFilter("Quotation", "q");
        if (SalesSupport.hasText(from)) {
            where.and("q.date >= :from", "from", ErpDates.toInstant(from));
        }
        if (SalesSupport.hasText(to)) {
            Instant lte = ErpDates.tryInstant(to + "T23:59:59.999Z");
            if (lte == null) {
                throw ErpException.business("Tanggal tidak valid.");
            }
            where.and("q.date <= :to", "to", lte);
        }
        List<Object[]> grouped = where.aggregate(em, "q.status, count(q), sum(q.grandTotal)", "group by q.status",
                Object[].class, null).getResultList();
        Map<String, Object> counts = new LinkedHashMap<>();
        Map<String, Object> values = new LinkedHashMap<>();
        for (QuotationStatus s : List.of(QuotationStatus.Draft, QuotationStatus.Sent, QuotationStatus.Accepted,
                QuotationStatus.Rejected, QuotationStatus.Expired, QuotationStatus.Cancelled)) {
            counts.put(s.name(), 0L);
            values.put(s.name(), BigDecimal.ZERO);
        }
        for (Object[] g : grouped) {
            counts.put(String.valueOf(g[0]), ((Number) g[1]).longValue());
            values.put(String.valueOf(g[0]), SalesSupport.num(Money.nz((BigDecimal) g[2])));
        }
        long issued = count(counts, "Sent") + count(counts, "Accepted") + count(counts, "Rejected") + count(counts, "Expired");
        long accepted = count(counts, "Accepted");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", counts);
        out.put("values", values);
        out.put("issued", issued);
        out.put("accepted", accepted);
        out.put("conversionRate", QuotationList.percent(accepted, issued));
        return out;
    }

    private static long count(Map<String, Object> counts, String key) {
        Object v = counts.get(key);
        return v == null ? 0 : ((Number) v).longValue();
    }

    /** GET /quotations/:id — detail + customer + items (barang/layanan) + user + invoice + SO aktif. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<Quotation> rows = em.createQuery(
                        "select q from Quotation q left join fetch q.customer left join fetch q.items where q.id = :id",
                        Quotation.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.of(404, "Penawaran tidak ditemukan.");
        }
        Quotation qt = rows.get(0);
        ObjectNode n = SalesJson.obj(qt);
        n.set("customer", SalesJson.scalars(qt.getCustomer()));

        List<QuotationItem> items = qt.getItems();
        Map<String, Map<String, Object>> inventory = SalesJson.briefs(em,
                "select v.id, v.code, v.name, v.unit from InventoryItem v where v.id in :ids",
                items.stream().map(QuotationItem::getInventoryItemId).toList(), "id", "code", "name", "unit");
        Map<String, Map<String, Object>> services = SalesJson.briefs(em,
                "select s.id, s.code, s.name from ServiceItem s where s.id in :ids",
                items.stream().map(QuotationItem::getServiceItemId).toList(), "id", "code", "name");
        ArrayNode itemArr = ErpJson.arrayNode();
        for (QuotationItem it : items) {
            ObjectNode in = SalesJson.obj(it);
            in.set("inventoryItem", SalesJson.relation(inventory, it.getInventoryItemId()));
            in.set("serviceItem", SalesJson.relation(services, it.getServiceItemId()));
            itemArr.add(in);
        }
        n.set("items", itemArr);
        n.set("user", SalesJson.relation(SalesJson.users(em, List.of(qt.getCreatedBy())), qt.getCreatedBy()));
        Map<String, Map<String, Object>> invoice = SalesJson.briefs(em,
                "select s.id, s.invoiceNumber, s.date, s.grandTotal from SalesInvoice s where s.id in :ids",
                qt.getSalesInvoiceId() == null ? List.of() : List.of(qt.getSalesInvoiceId()),
                "id", "invoiceNumber", "date", "grandTotal");
        n.set("salesInvoice", SalesJson.relation(invoice, qt.getSalesInvoiceId()));

        // SO aktif yang dibuat dari penawaran ini.
        List<Object[]> orders = em.createQuery(
                        "select s.id, s.soNumber, s.status from SalesOrder s where s.quotationId = :id "
                                + "and s.status <> :cancelled order by s.createdAt asc", Object[].class)
                .setParameter("id", qt.getId())
                .setParameter("cancelled", SalesOrderStatus.Cancelled)
                .getResultList();
        List<Map<String, Object>> so = new ArrayList<>();
        for (Object[] r : orders) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("soNumber", r[1]);
            m.put("status", r[2]);
            so.add(m);
        }
        n.set("salesOrders", ErpJson.tree(so));
        return n;
    }

    // ───────────────────────────── tulis ─────────────────────────────

    /** POST /quotations — buat Draft (nomor QO-YYYYMM-NNNN). */
    @ErpTx
    public ObjectNode create(QuotationRequests.Upsert body, String userId) {
        Instant date = SalesSupport.parseDate(body.date());
        Instant validUntil = SalesSupport.parseDate(body.validUntil());
        if (date == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        if (validUntil == null) {
            throw ErpException.business("Format tanggal berlaku-sampai tidak valid.");
        }
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(date);
        String quotationNumber = numbers.generate("QO", date, fiscalYear.getId());
        List<ResolvedItem> items = resolveItems(body.items());
        Totals totals = computeTotals(items, body.potongan(), body.biayaLain());

        Quotation q = new Quotation();
        q.setQuotationNumber(quotationNumber);
        q.setDate(date);
        q.setValidUntil(validUntil);
        q.setPartyId(body.partyId());
        q.setStatus(QuotationStatus.Draft);
        q.setSubtotal(totals.subtotal());
        q.setTaxPct(BigDecimal.ZERO);
        applyHeader(q, body, totals);
        q.setFiscalYearId(fiscalYear.getId());
        q.setCreatedBy(userId);
        em.persist(q);
        persistItems(q.getId(), items);
        SalesSupport.flush(em);
        return reloadWithItemsAndCustomer(q.getId());
    }

    /** PUT /quotations/:id — edit (hanya Draft); semua baris diganti. */
    @ErpTx
    public ObjectNode update(String id, QuotationRequests.Upsert body) {
        Instant date = ErpDates.toInstant(body.date());
        Instant validUntil = ErpDates.toInstant(body.validUntil());
        Quotation existing = em.find(Quotation.class, id);
        if (existing == null) {
            throw ErpException.business("Penawaran tidak ditemukan.");
        }
        if (existing.getStatus() != QuotationStatus.Draft) {
            throw ErpException.business("Hanya penawaran berstatus Draft yang dapat diedit. Status saat ini: "
                    + existing.getStatus().name() + ".");
        }
        List<ResolvedItem> items = resolveItems(body.items());
        Totals totals = computeTotals(items, body.potongan(), body.biayaLain());

        // Ganti semua baris: hapus lalu buat ulang.
        em.createQuery("delete from QuotationItem i where i.quotationId = :id").setParameter("id", id).executeUpdate();

        existing.setDate(date);
        existing.setValidUntil(validUntil);
        existing.setPartyId(body.partyId());
        existing.setSubtotal(totals.subtotal());
        applyHeader(existing, body, totals);
        existing.setUpdatedAt(Instant.now());
        persistItems(id, items);
        SalesSupport.flush(em);
        return reloadWithItemsAndCustomer(id);
    }

    private static void applyHeader(Quotation q, QuotationRequests.Upsert body, Totals totals) {
        q.setPotongan(Money.nz(body.potongan()));
        q.setBiayaLain(Money.nz(body.biayaLain()));
        q.setLabelPotongan(SalesSupport.orNull(body.labelPotongan()));
        q.setLabelBiaya(SalesSupport.orNull(body.labelBiaya()));
        q.setGrandTotal(totals.grandTotal());
        q.setTerms(SalesSupport.orNull(body.terms()));
        q.setNotes(SalesSupport.orNull(body.notes()));
    }

    private void persistItems(String quotationId, List<ResolvedItem> items) {
        for (ResolvedItem it : items) {
            QuotationItem qi = new QuotationItem();
            qi.setQuotationId(quotationId);
            qi.setItemType(it.itemType());
            qi.setInventoryItemId(it.inventoryItemId());
            qi.setServiceItemId(it.serviceItemId());
            qi.setAccountId(it.accountId());
            qi.setItemName(it.itemName());
            qi.setQuantity(it.quantity());
            qi.setUnit(it.unit());
            qi.setRate(it.rate());
            qi.setDiscount(it.discount());
            qi.setAmount(it.amount());
            qi.setTaxPct(it.taxPct());
            qi.setPphPct(it.pphPct());
            qi.setDescription(it.description());
            em.persist(qi);
        }
    }

    /** {@code include: { items: true, customer: true }} setelah tulis — muat ulang dari DB. */
    private ObjectNode reloadWithItemsAndCustomer(String id) {
        em.clear();
        Quotation q = em.createQuery(
                        "select q from Quotation q left join fetch q.customer left join fetch q.items where q.id = :id",
                        Quotation.class)
                .setParameter("id", id).getSingleResult();
        ObjectNode n = SalesJson.obj(q);
        n.set("items", SalesJson.array(q.getItems()));
        n.set("customer", SalesJson.scalars(q.getCustomer()));
        return n;
    }

    /** DELETE /quotations/:id — hanya Draft yang boleh dihapus permanen. */
    @ErpTx
    public Map<String, Object> delete(String id) {
        Quotation existing = em.find(Quotation.class, id);
        if (existing == null) {
            throw ErpException.of(404, "Penawaran tidak ditemukan.");
        }
        if (existing.getStatus() != QuotationStatus.Draft) {
            throw ErpException.business(
                    "Hanya penawaran Draft yang bisa dihapus. Gunakan tombol Cancel untuk yang sudah diterbitkan.");
        }
        // quotation_items ON DELETE CASCADE.
        em.remove(existing);
        SalesSupport.flush(em);
        return Map.of("success", true);
    }

    private Quotation findOrBusiness(String id) {
        Quotation q = em.find(Quotation.class, id);
        if (q == null) {
            throw ErpException.business("Penawaran tidak ditemukan.");
        }
        return q;
    }

    private JsonNode saved(Quotation q) {
        q.setUpdatedAt(Instant.now());
        SalesSupport.flush(em);
        return SalesJson.scalars(q);
    }

    /** PATCH /quotations/:id/send — Draft → Sent. */
    @ErpTx
    public JsonNode send(String id) {
        Quotation q = findOrBusiness(id);
        if (q.getStatus() != QuotationStatus.Draft) {
            throw ErpException.business("Hanya Draft yang bisa dikirim. Status saat ini: " + q.getStatus().name() + ".");
        }
        Long items = em.createQuery("select count(i) from QuotationItem i where i.quotationId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (items == 0) {
            throw ErpException.business("Penawaran harus memiliki minimal satu item.");
        }
        Instant now = Instant.now();
        if (q.getValidUntil().isBefore(now)) {
            throw ErpException.business("Tanggal berlaku-sampai sudah lewat. Edit dulu sebelum mengirim.");
        }
        q.setStatus(QuotationStatus.Sent);
        q.setSentAt(now);
        return saved(q);
    }

    /** PATCH /quotations/:id/accept — Sent → Accepted (masih berlaku). */
    @ErpTx
    public JsonNode accept(String id) {
        Quotation q = findOrBusiness(id);
        if (q.getStatus() != QuotationStatus.Sent) {
            throw ErpException.business("Hanya status Sent yang bisa di-Accept. Status saat ini: " + q.getStatus().name() + ".");
        }
        Instant now = Instant.now();
        // Sweeper bisa berbalapan dengan accept; validUntil dicek ulang di transaksi ini.
        if (q.getValidUntil().isBefore(now)) {
            throw ErpException.business("Penawaran sudah kadaluarsa. Buat penawaran baru.");
        }
        q.setStatus(QuotationStatus.Accepted);
        q.setAcceptedAt(now);
        return saved(q);
    }

    /** PATCH /quotations/:id/reject — Sent → Rejected dengan alasan. */
    @ErpTx
    public JsonNode reject(String id, String reason) {
        Quotation q = findOrBusiness(id);
        if (q.getStatus() != QuotationStatus.Sent) {
            throw ErpException.business("Hanya status Sent yang bisa di-Reject. Status saat ini: " + q.getStatus().name() + ".");
        }
        q.setStatus(QuotationStatus.Rejected);
        q.setRejectedAt(Instant.now());
        q.setRejectReason(reason);
        return saved(q);
    }

    /** PATCH /quotations/:id/cancel — status apa pun → Cancelled, kecuali sudah dikonversi. */
    @ErpTx
    public JsonNode cancel(String id) {
        Quotation q = findOrBusiness(id);
        if (q.getConvertedAt() != null) {
            throw ErpException.business("Penawaran sudah dikonversi ke Sales Invoice. Batalkan invoice-nya terlebih dulu.");
        }
        if (q.getStatus() == QuotationStatus.Cancelled) {
            throw ErpException.business("Penawaran sudah dibatalkan.");
        }
        q.setStatus(QuotationStatus.Cancelled);
        q.setCancelledAt(Instant.now());
        return saved(q);
    }

    /**
     * GET /quotations/:id/convert-payload — data prefill modal invoice penjualan. Frontend lalu POST
     * ke /sales/invoices (yang memposting GL) dan memanggil POST /:id/link.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> convertPayload(String id) {
        List<Quotation> rows = em.createQuery(
                        "select q from Quotation q left join fetch q.customer left join fetch q.items where q.id = :id",
                        Quotation.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.of(404, "Penawaran tidak ditemukan.");
        }
        Quotation q = rows.get(0);
        if (q.getStatus() != QuotationStatus.Accepted) {
            throw ErpException.business("Hanya penawaran Accepted yang bisa dikonversi. Status saat ini: "
                    + q.getStatus().name() + ".");
        }
        if (q.getSalesInvoiceId() != null) {
            throw ErpException.business("Penawaran sudah dikonversi.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("quotationId", q.getId());
        out.put("quotationNumber", q.getQuotationNumber());
        out.put("partyId", q.getPartyId());
        out.put("partyName", q.getCustomer().getName());
        out.put("potongan", SalesSupport.num(q.getPotongan()));
        out.put("biayaLain", SalesSupport.num(q.getBiayaLain()));
        out.put("labelPotongan", q.getLabelPotongan());
        out.put("labelBiaya", q.getLabelBiaya());
        out.put("terms", q.getTerms());
        out.put("notes", q.getNotes());
        List<Map<String, Object>> items = new ArrayList<>();
        for (QuotationItem it : q.getItems()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemType", it.getItemType());
            m.put("inventoryItemId", it.getInventoryItemId());
            m.put("serviceItemId", it.getServiceItemId());
            m.put("accountId", it.getAccountId());
            m.put("itemName", it.getItemName());
            m.put("quantity", SalesSupport.num(it.getQuantity()));
            m.put("unit", it.getUnit());
            m.put("rate", SalesSupport.num(it.getRate()));
            m.put("discount", SalesSupport.num(it.getDiscount()));
            m.put("taxPct", SalesSupport.num(it.getTaxPct()));
            m.put("pphPct", SalesSupport.num(it.getPphPct()));
            m.put("description", it.getDescription());
            items.add(m);
        }
        out.put("items", items);
        return out;
    }

    /**
     * POST /quotations/:id/link — tautkan invoice penjualan yang baru dibuat. Pembuatan invoice
     * (posting GL) dipisah dari penautan agar logika GL tetap satu sumber di modul invoice.
     */
    @ErpTx
    public ObjectNode link(String id, String salesInvoiceId) {
        Quotation q = findOrBusiness(id);
        if (q.getStatus() != QuotationStatus.Accepted) {
            throw ErpException.business("Hanya Accepted yang bisa di-link ke invoice. Status: " + q.getStatus().name() + ".");
        }
        if (q.getSalesInvoiceId() != null) {
            throw ErpException.business("Penawaran sudah ter-link ke invoice lain.");
        }
        SalesInvoice invoice = em.find(SalesInvoice.class, salesInvoiceId);
        if (invoice == null) {
            throw ErpException.business("Sales Invoice tidak ditemukan.");
        }
        if (!invoice.getPartyId().equals(q.getPartyId())) {
            throw ErpException.business("Customer pada invoice tidak sama dengan penawaran.");
        }
        q.setSalesInvoiceId(salesInvoiceId);
        q.setConvertedAt(Instant.now());
        q.setUpdatedAt(Instant.now());
        SalesSupport.flush(em);
        ObjectNode n = SalesJson.obj(q);
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("id", invoice.getId());
        inv.put("invoiceNumber", invoice.getInvoiceNumber());
        n.set("salesInvoice", ErpJson.tree(inv));
        log.info("Quotation linked to sales invoice quotationId={} salesInvoiceId={}", id, salesInvoiceId);
        return n;
    }
}
