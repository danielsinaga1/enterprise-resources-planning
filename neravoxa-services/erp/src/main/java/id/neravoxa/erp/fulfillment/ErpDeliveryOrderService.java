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
import id.neravoxa.erp.core.approval.ErpApprovalGateService;
import id.neravoxa.erp.core.notification.ErpNotificationService;
import id.neravoxa.erp.core.security.ErpActor;
import id.neravoxa.erp.entity.ApprovalRequest;
import id.neravoxa.erp.entity.DeliveryOrder;
import id.neravoxa.erp.entity.DeliveryOrderItem;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.Party;
import id.neravoxa.erp.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orkestrasi handler {@code routes/deliveryOrders.ts} + query daftar {@code services/deliveryOrderList.ts}.
 *
 * <p>Status "Invoiced" diturunkan (Submitted + invoiceId terisi), jadi filter {@code status} menerima
 * Submitted (belum diinvoice), Invoiced, Cancelled dan Draft.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpDeliveryOrderService {

    public static final String DOCUMENT_TYPE = "DeliveryOrder";
    static final List<String> SORT_FIELDS = List.of("date", "doNumber", "customer");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);

    private final ErpDeliveryOrderSubmitService submitter;
    private final ErpDeliveryOrderEditService editor;
    private final ErpDeliveryOrderCancelService canceller;
    private final ErpDeliveryOrderFromInvoiceService fromInvoice;
    private final ErpApprovalGateService approvalGate;
    private final ErpNotificationService notifications;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Hasil aksi yang bisa masuk antrian approval: pending = 202. */
    public record Outcome(boolean pending, Object body) {
    }

    // ───────────────────────────── query ─────────────────────────────

    /**
     * GET / — query: page, limit (≤200), status (Submitted | Invoiced | Cancelled | Draft), partyId,
     * startDate, endDate (YYYY-MM-DD), search (No. DO / customer / no. mobil / No. SO / No. PO pelanggan),
     * sort (date | doNumber | customer, "-" = desc) → {@code { data, total, page, limit }}.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        ErpQuery<DeliveryOrder> q = ErpQuery.of(em, DeliveryOrder.class, "d")
                .join("left join d.customer c", "left join d.salesOrder so");
        String status = ListQuery.str(query, "status");
        if (status != null) {
            switch (status) {
                case "Submitted" -> q.where("d.status = :st and d.invoiceId is null", "st", DocumentStatus.Submitted);
                case "Invoiced" -> q.where("d.status = :st and d.invoiceId is not null", "st", DocumentStatus.Submitted);
                case "Cancelled" -> q.where("d.status = :st", "st", DocumentStatus.Cancelled);
                case "Draft" -> q.where("d.status = :st", "st", DocumentStatus.Draft);
                default -> throw ErpException.business("Status surat jalan tidak dikenal.");
            }
        }
        String partyId = ListQuery.str(query, "partyId");
        q.whereIf(partyId != null, "d.partyId = :partyId", "partyId", partyId);
        String startDate = ListQuery.dateParam(query, "startDate", "Format tanggal harus YYYY-MM-DD.");
        String endDate = ListQuery.dateParam(query, "endDate", "Format tanggal harus YYYY-MM-DD.");
        Instant[] day = ErpDates.localDayRange(startDate, endDate);
        q.whereIf(day[0] != null, "d.date >= :gte", "gte", day[0]);
        q.whereIf(day[1] != null, "d.date < :lt", "lt", day[1]);
        String search = ListQuery.str(query, "search");
        q.whereIf(search != null, "(lower(d.doNumber) like :s or lower(c.name) like :s or lower(d.nomorMobil) like :s "
                + "or lower(so.soNumber) like :s or lower(so.customerRefNo) like :s)", "s",
                search == null ? null : ErpQuery.contains(search));

        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), SORT_FIELDS, DEFAULT_SORT);
        String dir = sort.dir();
        String primary = "customer".equals(sort.field()) ? "c.name " + dir : "d." + sort.field() + " " + dir;
        // Tie-breaker stabil agar baris tidak berpindah halaman.
        q.orderBy("doNumber".equals(sort.field()) ? primary : primary + ", d.doNumber " + dir);

        long total = q.count();
        List<DeliveryOrder> rows = q.list(paging.skip(), paging.limit());
        List<String> ids = rows.stream().map(DeliveryOrder::getId).toList();
        var customers = FulfillmentJson.parties(em, rows.stream().map(DeliveryOrder::getPartyId).toList());
        var invoices = FulfillmentJson.briefs(em, "select i.id, i.invoiceNumber from SalesInvoice i where i.id in :ids",
                rows.stream().map(DeliveryOrder::getInvoiceId).toList(), "id", "invoiceNumber");
        var orders = FulfillmentJson.briefs(em, "select s.id, s.soNumber, s.customerRefNo from SalesOrder s where s.id in :ids",
                rows.stream().map(DeliveryOrder::getSalesOrderId).toList(), "id", "soNumber", "customerRefNo");
        Map<String, Long> itemCounts = FulfillmentJson.counts(em,
                "select i.deliveryOrderId, count(i) from DeliveryOrderItem i where i.deliveryOrderId in :ids "
                        + "group by i.deliveryOrderId", ids);
        List<JsonNode> data = new ArrayList<>();
        for (DeliveryOrder d : rows) {
            ObjectNode n = FulfillmentJson.obj(d);
            n.set("customer", FulfillmentJson.relation(customers, d.getPartyId()));
            n.set("invoice", FulfillmentJson.relation(invoices, d.getInvoiceId()));
            n.set("salesOrder", FulfillmentJson.relation(orders, d.getSalesOrderId()));
            n.set("_count", ErpJson.tree(ErpJson.map("items", itemCounts.getOrDefault(d.getId(), 0L))));
            data.add(n);
        }
        return ListQuery.result(data, total, paging);
    }

    /** GET /pending?partyId= — DO yang belum ditagih (dipakai modal invoice). */
    @ErpTx(readOnly = true)
    public List<JsonNode> pending(String partyId) {
        if (partyId == null || partyId.isEmpty()) {
            throw ErpException.business("partyId wajib diisi.");
        }
        List<DeliveryOrder> rows = em.createQuery(
                        "select d from DeliveryOrder d where d.partyId = :p and d.status = :st and d.invoiceId is null "
                                + "order by d.date asc, d.doNumber asc", DeliveryOrder.class)
                .setParameter("p", partyId)
                .setParameter("st", DocumentStatus.Submitted)
                .getResultList();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> ids = rows.stream().map(DeliveryOrder::getId).toList();
        List<DeliveryOrderItem> items = em.createQuery(
                        "select i from DeliveryOrderItem i left join fetch i.inventoryItem where i.deliveryOrderId in :ids "
                                + "order by i.createdAt, i.id", DeliveryOrderItem.class)
                .setParameter("ids", ids).getResultList();
        // Harga jual & diskon dari SO supaya baris invoice dari Surat Jalan terisi otomatis (bukan rate 0).
        var soItems = FulfillmentJson.keyedBriefs(em,
                "select s.id, s.rate, s.discount from SalesOrderItem s where s.id in :ids",
                items.stream().map(DeliveryOrderItem::getSalesOrderItemId).toList(), "rate", "discount");
        var customers = FulfillmentJson.parties(em, rows.stream().map(DeliveryOrder::getPartyId).toList());
        var orders = FulfillmentJson.keyedBriefs(em,
                "select s.id, s.soNumber, s.customerRefNo from SalesOrder s where s.id in :ids",
                rows.stream().map(DeliveryOrder::getSalesOrderId).toList(), "soNumber", "customerRefNo");
        List<JsonNode> out = new ArrayList<>();
        for (DeliveryOrder d : rows) {
            ObjectNode n = FulfillmentJson.obj(d);
            ArrayNode lines = ErpJson.arrayNode();
            for (DeliveryOrderItem it : items) {
                if (!d.getId().equals(it.getDeliveryOrderId())) {
                    continue;
                }
                ObjectNode line = FulfillmentJson.obj(it);
                line.set("inventoryItem", FulfillmentJson.scalars(it.getInventoryItemId() == null ? null : it.getInventoryItem()));
                line.set("salesOrderItem", FulfillmentJson.relation(soItems, it.getSalesOrderItemId()));
                lines.add(line);
            }
            n.set("items", lines);
            n.set("customer", FulfillmentJson.relation(customers, d.getPartyId()));
            n.set("salesOrder", FulfillmentJson.relation(orders, d.getSalesOrderId()));
            out.add(n);
        }
        return out;
    }

    /** GET /:id — detail lengkap; 404 bila tidak ada. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        DeliveryOrder d = em.find(DeliveryOrder.class, id);
        if (d == null) {
            throw ErpException.notFound("Surat jalan tidak ditemukan.");
        }
        List<DeliveryOrderItem> items = em.createQuery(
                        "select i from DeliveryOrderItem i left join fetch i.inventoryItem left join fetch i.warehouse "
                                + "left join fetch i.stockMovement where i.deliveryOrderId = :id order by i.createdAt, i.id",
                        DeliveryOrderItem.class)
                .setParameter("id", id).getResultList();
        ObjectNode n = FulfillmentJson.obj(d);
        ArrayNode lines = ErpJson.arrayNode();
        for (DeliveryOrderItem it : items) {
            ObjectNode line = FulfillmentJson.obj(it);
            line.set("inventoryItem", FulfillmentJson.scalars(it.getInventoryItemId() == null ? null : it.getInventoryItem()));
            line.set("warehouse", FulfillmentJson.scalars(it.getWarehouseId() == null ? null : it.getWarehouse()));
            line.set("stockMovement", FulfillmentJson.scalars(it.getStockMovementId() == null ? null : it.getStockMovement()));
            lines.add(line);
        }
        n.set("items", lines);
        n.set("customer", FulfillmentJson.scalars(em.find(Party.class, d.getPartyId())));
        var invoice = FulfillmentJson.briefs(em,
                "select i.id, i.invoiceNumber, i.status, i.grandTotal from SalesInvoice i where i.id in :ids",
                d.getInvoiceId() == null ? List.of() : List.of(d.getInvoiceId()), "id", "invoiceNumber", "status", "grandTotal");
        n.set("invoice", FulfillmentJson.relation(invoice, d.getInvoiceId()));
        var order = FulfillmentJson.briefs(em,
                "select s.id, s.soNumber, s.customerRefNo from SalesOrder s where s.id in :ids",
                d.getSalesOrderId() == null ? List.of() : List.of(d.getSalesOrderId()), "id", "soNumber", "customerRefNo");
        n.set("salesOrder", FulfillmentJson.relation(order, d.getSalesOrderId()));
        n.set("user", FulfillmentJson.relation(FulfillmentJson.users(em, List.of(d.getCreatedBy())), d.getCreatedBy()));
        return n;
    }

    /**
     * DO + baris + customer lengkap (padanan {@code include: { items: true, customer: true }}), dibaca ulang
     * dari DB setelah flush. HANYA untuk pemilik transaksi (persistence context dikosongkan).
     *
     * @param withSalesOrder tambahkan {@code salesOrder: { id, soNumber }} (respons generate-do)
     */
    @ErpTx(readOnly = true)
    public ObjectNode reloadWithItemsAndCustomer(String id, boolean withSalesOrder) {
        em.flush();
        em.clear();
        DeliveryOrder d = em.find(DeliveryOrder.class, id);
        List<DeliveryOrderItem> items = em.createQuery(
                        "select i from DeliveryOrderItem i where i.deliveryOrderId = :id order by i.createdAt, i.id",
                        DeliveryOrderItem.class)
                .setParameter("id", id).getResultList();
        ObjectNode n = FulfillmentJson.obj(d);
        n.set("items", FulfillmentJson.array(items));
        n.set("customer", FulfillmentJson.scalars(em.find(Party.class, d.getPartyId())));
        if (withSalesOrder) {
            var order = FulfillmentJson.briefs(em, "select s.id, s.soNumber from SalesOrder s where s.id in :ids",
                    d.getSalesOrderId() == null ? List.of() : List.of(d.getSalesOrderId()), "id", "soNumber");
            n.set("salesOrder", FulfillmentJson.relation(order, d.getSalesOrderId()));
        }
        return n;
    }

    // ───────────────────────────── tulis ─────────────────────────────

    /**
     * POST / — submit DO. Stok minus: pemegang capability {@code inventory.negative_stock} bypass langsung;
     * selain itu TIDAK diblok tetapi MASUK ANTRIAN APPROVAL (dokumen dibuat saat approve, dengan bypass).
     *
     * @param canBypassNegativeStock aktor punya capability {@code inventory.negative_stock}
     */
    @ErpTx(timeout = 15)
    public Outcome create(JsonNode raw, ErpActor actor, boolean canBypassNegativeStock) {
        FulfillmentRequests.DoInput body = FulfillmentRequests.deliveryOrder(raw);
        if (body.allowNegativeStock() && !canBypassNegativeStock) {
            ApprovalRequest request = approvalGate.queueApproval(DOCUMENT_TYPE, raw == null ? ErpJson.object() : raw,
                    BigDecimal.ZERO, actor.userId(), "Surat Jalan (menunggu approval, stok minus)");
            User requester = em.find(User.class, actor.userId());
            String link = approvalGate.approvalsLink(request.getId());
            String reason = Zod.truthy(body.negativeStockReason());
            notifications.notifyAdmins(new ErpNotificationService.Notice(
                    "approval_pending",
                    "🔔 Persetujuan: Surat Jalan (stok minus)",
                    "Surat Jalan dari " + (requester != null && requester.getFullName() != null ? requester.getFullName() : "staf")
                            + " butuh persetujuan karena stok minus."
                            + (reason != null ? "\nAlasan: " + reason : "")
                            + (link != null ? "\n\n🔗 Buka & proses: " + link : ""),
                    ErpJson.map("kind", "approval", "requestId", request.getId(), "documentType", DOCUMENT_TYPE)),
                    actor.userId());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("pendingApproval", true);
            out.put("request", FulfillmentJson.scalars(request));
            out.put("message", "Surat jalan menunggu persetujuan manajer (stok minus).");
            return new Outcome(true, out);
        }
        ErpDeliveryOrderSubmitService.Submitted s = submitter.submitInTx(body, actor.userId());
        log.info("Delivery order submitted doId={} userId={} negativeBypass={}", s.deliveryOrder().getId(), actor.userId(),
                body.allowNegativeStock());
        return new Outcome(false, reloadWithItemsAndCustomer(s.deliveryOrder().getId(), false));
    }

    /**
     * PUT /:id — edit penuh (balik gerakan lama, terapkan baris baru). Bypass stok minus butuh capability
     * {@code inventory.negative_stock} (403 tanpa antrian approval).
     */
    @ErpTx(timeout = 15)
    public ObjectNode edit(String id, JsonNode raw, String userId, boolean canBypassNegativeStock) {
        FulfillmentRequests.DoInput body = FulfillmentRequests.deliveryOrder(raw);
        if (body.allowNegativeStock() && !canBypassNegativeStock) {
            throw ErpException.of(HttpStatus.FORBIDDEN, "Hanya Admin yang dapat bypass validasi stok.");
        }
        editor.editInTx(id, body, userId);
        log.info("Delivery order edited doId={} userId={} negativeBypass={}", id, userId, body.allowNegativeStock());
        return reloadWithItemsAndCustomer(id, false);
    }

    /**
     * PATCH /:id — hanya header (notes, nomorMobil, driverName, recipientName); qty & item tidak bisa
     * berubah setelah stok bergerak. Status harus masih Submitted. Field yang tidak dikirim = tetap.
     */
    @ErpTx
    public ObjectNode patchHeader(String id, JsonNode raw) {
        DeliveryOrder d = em.find(DeliveryOrder.class, id);
        if (d == null) {
            throw ErpException.notFound("Surat jalan tidak ditemukan.");
        }
        if (d.getStatus() != DocumentStatus.Submitted) {
            throw ErpException.business("Hanya surat jalan berstatus Submitted yang bisa diedit.");
        }
        JsonNode body = raw != null && raw.isObject() ? raw : ErpJson.object();
        if (body.has("notes")) {
            d.setNotes(text(body.get("notes")));
        }
        if (body.has("nomorMobil")) {
            d.setNomorMobil(text(body.get("nomorMobil")));
        }
        if (body.has("driverName")) {
            d.setDriverName(text(body.get("driverName")));
        }
        if (body.has("recipientName")) {
            d.setRecipientName(text(body.get("recipientName")));
        }
        // Prisma update selalu menyentuh updatedAt.
        d.setUpdatedAt(Instant.now());
        em.flush();
        return FulfillmentJson.obj(d);
    }

    /** Nilai kolom teks dari body bebas; tipe lain ditolak Prisma (500 di sumber). */
    private static String text(JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            throw ErpException.of(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal memperbarui surat jalan.");
        }
        return v.asText();
    }

    /** POST /:id/cancel — kembalikan stok, lepas SJS, hitung ulang SO. */
    @ErpTx(timeout = 15)
    public Map<String, Object> cancel(String id, String userId) {
        Map<String, Object> out = canceller.cancelInTx(id);
        log.info("Delivery order cancelled doId={} userId={}", id, userId);
        return out;
    }

    /** POST /from-invoice/:invoiceId — DO retroaktif tanpa gerakan stok; respons = header DO saja. */
    @ErpTx(timeout = 15)
    public ObjectNode createFromInvoice(String invoiceId, JsonNode raw, String userId) {
        FulfillmentRequests.DoFromInvoiceInput body = FulfillmentRequests.deliveryOrderFromInvoice(raw);
        DeliveryOrder d = fromInvoice.createInTx(invoiceId, body, userId);
        log.info("Delivery order created from invoice doId={} invoiceId={} userId={}", d.getId(), invoiceId, userId);
        return FulfillmentJson.obj(d);
    }
}
