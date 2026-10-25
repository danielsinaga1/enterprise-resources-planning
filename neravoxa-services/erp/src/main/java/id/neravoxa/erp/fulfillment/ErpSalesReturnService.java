package id.neravoxa.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.approval.ErpApprovalGateService;
import id.neravoxa.erp.core.notification.ErpNotificationService;
import id.neravoxa.erp.core.security.ErpActor;
import id.neravoxa.erp.entity.ApprovalRequest;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.JournalItem;
import id.neravoxa.erp.entity.Party;
import id.neravoxa.erp.entity.SalesInvoice;
import id.neravoxa.erp.entity.SalesInvoiceItem;
import id.neravoxa.erp.entity.SalesReturn;
import id.neravoxa.erp.entity.SalesReturnItem;
import id.neravoxa.erp.entity.User;
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
 * Orkestrasi handler {@code routes/salesReturns.ts} + query daftar {@code services/salesReturnList.ts}.
 * Pembuatan retur di-gate approval untuk role tanpa {@code approval.bypass}.
 */
@Service
@RequiredArgsConstructor
public class ErpSalesReturnService {

    public static final String DOCUMENT_TYPE = "SalesReturn";
    /** SalesReturn.status berupa string; nilai yang ditulis service. */
    static final List<String> STATUSES = List.of("Draft", "Submitted", "Cancelled");
    static final List<String> SORT_FIELDS = List.of("date", "returnNumber", "customer", "totalReturn");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);

    private final ErpSalesReturnSubmitService submitter;
    private final ErpSalesReturnCancelService canceller;
    private final ErpApprovalGateService approvalGate;
    private final ErpNotificationService notifications;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Hasil aksi yang bisa masuk antrian approval: pending = 202. */
    public record Outcome(boolean pending, Object body) {
    }

    /**
     * GET / — query: page, limit (default 50, ≤200), status, partyId, salesInvoiceId, startDate/endDate
     * (YYYY-MM-DD), search (No. retur / customer / No. invoice), sort (date | returnNumber | customer |
     * totalReturn, "-" = desc; default -date) → {@code { data, total, page, limit }}.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        ErpQuery<SalesReturn> q = ErpQuery.of(em, SalesReturn.class, "r")
                .join("left join r.customer c", "left join r.salesInvoice si");
        String status = ListQuery.str(query, "status");
        if (status != null) {
            if (!STATUSES.contains(status)) {
                throw ErpException.business("Status retur tidak dikenal.");
            }
            q.where("r.status = :status", "status", status);
        }
        String partyId = ListQuery.str(query, "partyId");
        q.whereIf(partyId != null, "r.partyId = :partyId", "partyId", partyId);
        String invoiceId = ListQuery.str(query, "salesInvoiceId");
        q.whereIf(invoiceId != null, "r.salesInvoiceId = :invoiceId", "invoiceId", invoiceId);
        String startDate = ListQuery.dateParam(query, "startDate", "Format tanggal harus YYYY-MM-DD.");
        String endDate = ListQuery.dateParam(query, "endDate", "Format tanggal harus YYYY-MM-DD.");
        Instant[] day = ErpDates.localDayRange(startDate, endDate);
        q.whereIf(day[0] != null, "r.date >= :gte", "gte", day[0]);
        q.whereIf(day[1] != null, "r.date < :lt", "lt", day[1]);
        String search = ListQuery.str(query, "search");
        q.whereIf(search != null, "(lower(r.returnNumber) like :s or lower(c.name) like :s or lower(si.invoiceNumber) like :s)",
                "s", search == null ? null : ErpQuery.contains(search));

        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), SORT_FIELDS, DEFAULT_SORT);
        String dir = sort.dir();
        String primary = "customer".equals(sort.field()) ? "c.name " + dir : "r." + sort.field() + " " + dir;
        // Tie-breaker stabil agar baris tidak berpindah halaman.
        q.orderBy("returnNumber".equals(sort.field()) ? primary : primary + ", r.returnNumber " + dir);

        long total = q.count();
        List<SalesReturn> rows = q.list(paging.skip(), paging.limit());
        List<String> ids = rows.stream().map(SalesReturn::getId).toList();
        var customers = FulfillmentJson.parties(em, rows.stream().map(SalesReturn::getPartyId).toList());
        var invoices = FulfillmentJson.briefs(em, "select i.id, i.invoiceNumber from SalesInvoice i where i.id in :ids",
                rows.stream().map(SalesReturn::getSalesInvoiceId).toList(), "id", "invoiceNumber");
        Map<String, List<SalesReturnItem>> itemsByReturn = items(ids);
        List<JsonNode> data = new ArrayList<>();
        for (SalesReturn r : rows) {
            ObjectNode n = FulfillmentJson.obj(r);
            n.set("customer", FulfillmentJson.relation(customers, r.getPartyId()));
            n.set("salesInvoice", FulfillmentJson.relation(invoices, r.getSalesInvoiceId()));
            n.set("items", FulfillmentJson.array(itemsByReturn.getOrDefault(r.getId(), List.of())));
            data.add(n);
        }
        return ListQuery.result(data, total, paging);
    }

    private Map<String, List<SalesReturnItem>> items(List<String> returnIds) {
        Map<String, List<SalesReturnItem>> out = new HashMap<>();
        if (returnIds.isEmpty()) {
            return out;
        }
        // Tabel tanpa created_at: urutan simpan = urutan fisik baris (seperti include Prisma).
        @SuppressWarnings("unchecked")
        List<String> ordered = em.createNativeQuery(
                        "SELECT id FROM sales_return_items WHERE sales_return_id IN (:ids) ORDER BY ctid")
                .setParameter("ids", returnIds).getResultList();
        Map<String, SalesReturnItem> byId = new HashMap<>();
        em.createQuery("select i from SalesReturnItem i where i.salesReturnId in :ids", SalesReturnItem.class)
                .setParameter("ids", returnIds).getResultList().forEach(i -> byId.put(i.getId(), i));
        for (String id : ordered) {
            SalesReturnItem it = byId.get(id);
            if (it != null) {
                out.computeIfAbsent(it.getSalesReturnId(), k -> new ArrayList<>()).add(it);
            }
        }
        return out;
    }

    /** GET /eligible/:invoiceId — baris invoice + sisa qty yang masih bisa diretur. */
    @ErpTx(readOnly = true)
    public Map<String, Object> eligible(String invoiceId) {
        SalesInvoice invoice = em.find(SalesInvoice.class, invoiceId);
        if (invoice == null) {
            throw ErpException.notFound("Invoice tidak ditemukan.");
        }
        @SuppressWarnings("unchecked")
        List<String> lineIds = em.createNativeQuery("SELECT id FROM sales_invoice_items WHERE sales_invoice_id = :id ORDER BY ctid")
                .setParameter("id", invoiceId).getResultList();
        Map<String, SalesInvoiceItem> byId = new HashMap<>();
        em.createQuery("select i from SalesInvoiceItem i left join fetch i.warehouse where i.salesInvoiceId = :id",
                        SalesInvoiceItem.class)
                .setParameter("id", invoiceId).getResultList().forEach(i -> byId.put(i.getId(), i));
        Map<String, BigDecimal> returned = new HashMap<>();
        if (!lineIds.isEmpty()) {
            em.createQuery("select r.salesInvoiceItemId, sum(r.quantity) from SalesReturnItem r join r.salesReturn s "
                            + "where r.salesInvoiceItemId in :ids and s.status <> 'Cancelled' group by r.salesInvoiceItemId",
                            Object[].class)
                    .setParameter("ids", lineIds)
                    .getResultList().forEach(r -> returned.put((String) r[0], Money.nz((BigDecimal) r[1])));
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        for (String lineId : lineIds) {
            SalesInvoiceItem it = byId.get(lineId);
            if (it == null) {
                continue;
            }
            BigDecimal already = returned.getOrDefault(it.getId(), BigDecimal.ZERO);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("salesInvoiceItemId", it.getId());
            m.put("itemName", it.getItemName());
            m.put("inventoryItemId", it.getInventoryItemId());
            m.put("isInventory", it.getInventoryItemId() != null);
            m.put("warehouseId", it.getWarehouseId());
            m.put("warehouseName", it.getWarehouseId() != null && it.getWarehouse() != null ? it.getWarehouse().getName() : null);
            m.put("unit", it.getUnit());
            m.put("rate", FulfillmentJson.num(Money.nz(it.getRate())));
            m.put("discount", FulfillmentJson.num(Money.nz(it.getDiscount())));
            m.put("taxPct", FulfillmentJson.num(Money.nz(it.getTaxPct())));
            m.put("quantity", FulfillmentJson.num(Money.nz(it.getQuantity())));
            m.put("alreadyReturned", FulfillmentJson.num(already));
            m.put("remaining", FulfillmentJson.num(Money.nz(it.getQuantity()).subtract(already)));
            lines.add(m);
        }
        Party customer = em.find(Party.class, invoice.getPartyId());
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("id", invoice.getId());
        inv.put("invoiceNumber", invoice.getInvoiceNumber());
        inv.put("status", invoice.getStatus());
        inv.put("outstanding", FulfillmentJson.num(Money.nz(invoice.getOutstanding())));
        inv.put("grandTotal", FulfillmentJson.num(Money.nz(invoice.getGrandTotal())));
        inv.put("customer", customer == null ? null : ErpJson.map("id", customer.getId(), "name", customer.getName()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("invoice", inv);
        out.put("lines", lines);
        return out;
    }

    /** GET /:id — detail + jurnal {@code JV-<returnNumber>} (beserta akun) atau null. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        SalesReturn r = em.find(SalesReturn.class, id);
        if (r == null) {
            throw ErpException.notFound("Retur tidak ditemukan.");
        }
        return detailNode(r);
    }

    private ObjectNode detailNode(SalesReturn r) {
        ObjectNode n = FulfillmentJson.obj(r);
        n.set("customer", FulfillmentJson.scalars(em.find(Party.class, r.getPartyId())));
        var invoice = FulfillmentJson.briefs(em,
                "select i.id, i.invoiceNumber, i.grandTotal, i.outstanding from SalesInvoice i where i.id in :ids",
                List.of(r.getSalesInvoiceId()), "id", "invoiceNumber", "grandTotal", "outstanding");
        n.set("salesInvoice", FulfillmentJson.relation(invoice, r.getSalesInvoiceId()));
        n.set("items", FulfillmentJson.array(items(List.of(r.getId())).getOrDefault(r.getId(), List.of())));

        List<JournalEntry> je = em.createQuery("select j from JournalEntry j where j.entryNumber = :n", JournalEntry.class)
                .setParameter("n", "JV-" + r.getReturnNumber()).getResultList();
        if (je.isEmpty()) {
            n.set("journal", ErpJson.tree(null));
        } else {
            ObjectNode j = FulfillmentJson.obj(je.get(0));
            List<JournalItem> lines = em.createQuery(
                            "select i from JournalItem i left join fetch i.account where i.journalEntryId = :id", JournalItem.class)
                    .setParameter("id", je.get(0).getId()).getResultList();
            ArrayNode arr = ErpJson.arrayNode();
            for (JournalItem it : lines) {
                ObjectNode line = FulfillmentJson.obj(it);
                line.set("account", it.getAccount() == null ? ErpJson.tree(null) : ErpJson.tree(ErpJson.map(
                        "id", it.getAccount().getId(),
                        "accountNumber", it.getAccount().getAccountNumber(),
                        "name", it.getAccount().getName())));
                arr.add(line);
            }
            j.set("items", arr);
            n.set("journal", j);
        }
        return n;
    }

    /**
     * POST / — buat retur. Role tanpa {@code approval.bypass} → antrian approval (202); nilai yang
     * ditampilkan ke approver dihitung ulang dari invoice saat approve.
     */
    @ErpTx(timeout = 30)
    public Outcome create(JsonNode raw, ErpActor actor, boolean needsApproval) {
        FulfillmentRequests.ReturnInput body = FulfillmentRequests.salesReturn(raw);
        if (needsApproval) {
            ApprovalRequest request = approvalGate.queueApproval(DOCUMENT_TYPE, raw == null ? ErpJson.object() : raw,
                    submitter.estimateAmount(body), actor.userId(), "Retur Penjualan (menunggu approval)");
            User requester = em.find(User.class, actor.userId());
            String link = approvalGate.approvalsLink(request.getId());
            notifications.notifyAdmins(new ErpNotificationService.Notice(
                    "approval_pending",
                    "🔔 Persetujuan: Retur Penjualan",
                    "Retur Penjualan dari " + (requester != null && requester.getFullName() != null ? requester.getFullName() : "staf")
                            + " menunggu persetujuan Anda." + (link != null ? "\n\n🔗 Buka & proses: " + link : ""),
                    ErpJson.map("kind", "approval", "requestId", request.getId(), "documentType", DOCUMENT_TYPE)),
                    actor.userId());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("pendingApproval", true);
            out.put("request", FulfillmentJson.scalars(request));
            out.put("message", "Retur menunggu persetujuan manajer sebelum diproses.");
            return new Outcome(true, out);
        }
        SalesReturn ret = submitter.submitInTx(body, actor.userId());
        String id = ret.getId();
        em.flush();
        em.clear();
        SalesReturn reloaded = em.find(SalesReturn.class, id);
        ObjectNode n = FulfillmentJson.obj(reloaded);
        n.set("customer", FulfillmentJson.scalars(em.find(Party.class, reloaded.getPartyId())));
        n.set("items", FulfillmentJson.array(items(List.of(id)).getOrDefault(id, List.of())));
        var invoice = FulfillmentJson.keyedBriefs(em, "select i.id, i.invoiceNumber from SalesInvoice i where i.id in :ids",
                List.of(reloaded.getSalesInvoiceId()), "invoiceNumber");
        n.set("salesInvoice", FulfillmentJson.relation(invoice, reloaded.getSalesInvoiceId()));
        return new Outcome(false, n);
    }

    /** POST /:id/cancel. */
    @ErpTx(timeout = 15)
    public Map<String, Object> cancel(String id) {
        return canceller.cancelInTx(id);
    }
}
