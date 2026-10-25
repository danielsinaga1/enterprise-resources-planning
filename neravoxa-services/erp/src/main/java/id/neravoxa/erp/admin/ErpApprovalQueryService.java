package id.neravoxa.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.approval.ErpApprovalGateService;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.entity.ApprovalRequest;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.TransactionAttachment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bacaan halaman Approval — port handler GET di {@code routes/approvals.ts} + {@code services/approvalContextLookups.ts}.
 *
 * <p>Tanpa capability {@code approval.view_all} pengguna hanya melihat pengajuannya sendiri (pengajuan orang
 * lain dijawab 404 agar keberadaannya tidak bocor).
 */
@Service
@RequiredArgsConstructor
public class ErpApprovalQueryService {

    static final String VIEW_ALL = "approval.view_all";
    private static final String NOT_FOUND = "Pengajuan tidak ditemukan.";
    private static final List<InvoiceStatus> OPEN_INVOICE = List.of(InvoiceStatus.Submitted, InvoiceStatus.PartiallyPaid,
            InvoiceStatus.Overdue);

    private final ErpAccess access;
    private final ErpApprovalGateService gate;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** {@code GET /requests?status=} — Pending dulu (urut status), terbaru dulu, maks. 100. */
    @ErpTx(readOnly = true)
    public List<ObjectNode> list(String status) {
        boolean viewAll = access.hasCap(VIEW_ALL);
        boolean byStatus = status != null && !status.isEmpty();
        StringBuilder jpql = new StringBuilder("select r from ApprovalRequest r where 1 = 1");
        if (byStatus) {
            jpql.append(" and r.status = :st");
        }
        if (!viewAll) {
            jpql.append(" and r.requestedBy = :uid");
        }
        jpql.append(" order by r.status asc, r.createdAt desc");
        TypedQuery<ApprovalRequest> q = em.createQuery(jpql.toString(), ApprovalRequest.class);
        if (byStatus) {
            q.setParameter("st", status);
        }
        if (!viewAll) {
            q.setParameter("uid", access.userId());
        }
        List<ApprovalRequest> rows = q.setMaxResults(100).getResultList();
        return withPeople(rows);
    }

    /** {@code GET /requests/:id} — satu pengajuan (deep link notifikasi) + perbandingan untuk tipe edit. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        ApprovalRequest r = visible(id);
        ObjectNode out = withPeople(List.of(r)).get(0);
        out.set("comparison", ErpJson.tree(ApprovalComparison.build(r)));
        return out;
    }

    /**
     * {@code GET /requests/:id/context} — info pendukung dialog: baris STT sumber (invoice pembelian),
     * lampiran pengajuan & STT, posisi supplier, riwayat harga beli, nama party/item dari payload.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> context(String id) {
        ApprovalRequest request = visible(id);
        JsonNode payload = request.getPayload() == null || request.getPayload().isNull() ? ErpJson.object() : request.getPayload();
        List<JsonNode> payloadItems = ErpJson.elements(payload.get("items"));

        // Baris STT yang ditagih (goodsReceiptItemId per baris invoice).
        Set<String> griIds = new LinkedHashSet<>();
        for (JsonNode it : payloadItems) {
            JsonNode v = it == null ? null : it.get("goodsReceiptItemId");
            if (v != null && v.isTextual() && !v.textValue().isEmpty()) {
                griIds.add(v.textValue());
            }
        }
        Map<String, Object> sttLines = new LinkedHashMap<>();
        Map<String, Map<String, Object>> sttMap = new LinkedHashMap<>();
        if (!griIds.isEmpty()) {
            List<Object[]> gris = em.createQuery("select g.id, g.description, g.quantity, g.unit, g.lockedUnitCost, "
                            + "gr.id, gr.sttNumber, gr.date, gr.nomorMobil, gr.driverName, gr.status "
                            + "from GoodsReceiptItem g join g.goodsReceipt gr where g.id in :ids", Object[].class)
                    .setParameter("ids", griIds).getResultList();
            Map<String, Object[]> invoiced = new HashMap<>();
            for (Object[] row : em.createQuery("select pii.goodsReceiptItemId, pi.invoiceNumber, pi.status "
                            + "from PurchaseInvoiceItem pii join pii.purchaseInvoice pi where pii.goodsReceiptItemId in :ids", Object[].class)
                    .setParameter("ids", griIds).getResultList()) {
                invoiced.putIfAbsent((String) row[0], row);
            }
            for (Object[] g : gris) {
                String griId = (String) g[0];
                String grId = (String) g[5];
                Object[] inv = invoiced.get(griId);
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("sttNumber", g[6]);
                line.put("goodsReceiptId", grId);
                line.put("description", g[1]);
                line.put("sttDate", g[7]);
                line.put("sttStatus", g[10]);
                line.put("quantity", AdminJs.json(dbl(g[2])));
                line.put("unit", g[3]);
                line.put("lockedUnitCost", AdminJs.json(dbl(g[4])));
                // Baris STT ini sudah dipakai invoice lain (non-cancel) → potensi dobel tagih.
                line.put("alreadyInvoiced", inv != null && inv[2] != InvoiceStatus.Cancelled ? inv[1] : null);
                sttLines.put(griId, line);
                sttMap.computeIfAbsent(grId, k -> {
                    Map<String, Object> gr = new LinkedHashMap<>();
                    gr.put("id", grId);
                    gr.put("sttNumber", g[6]);
                    gr.put("date", g[7]);
                    gr.put("nomorMobil", g[8]);
                    gr.put("driverName", g[9]);
                    gr.put("status", g[10]);
                    return gr;
                });
            }
        }
        List<String> sttIds = new ArrayList<>(sttMap.keySet());
        List<ObjectNode> requestAtt = attachments("approval_request", List.of(request.getId()));
        List<ObjectNode> sttAtt = sttIds.isEmpty() ? List.of() : attachments("goods_receipt", sttIds);
        List<Map<String, Object>> stts = new ArrayList<>();
        for (String sttId : sttIds) {
            Map<String, Object> gr = new LinkedHashMap<>(sttMap.get(sttId));
            List<ObjectNode> own = new ArrayList<>();
            for (ObjectNode a : sttAtt) {
                if (sttId.equals(a.path("referenceId").asText())) {
                    own.add(a);
                }
            }
            gr.put("attachments", own);
            stts.add(gr);
        }
        stts.sort((a, b) -> String.valueOf(a.get("sttNumber")).compareTo(String.valueOf(b.get("sttNumber"))));

        // Posisi supplier: hutang berjalan & deposit (uang muka) tersisa.
        String partyId = textOrNull(payload.get("partyId"));
        Map<String, Object> supplier = null;
        if (partyId != null && !partyId.isEmpty()) {
            List<Object[]> party = em.createQuery("select p.id, p.name, p.depositBalance from Party p where p.id = :id", Object[].class)
                    .setParameter("id", partyId).getResultList();
            if (!party.isEmpty()) {
                Object[] agg = em.createQuery("select coalesce(sum(i.outstanding), 0), count(i) from PurchaseInvoice i "
                                + "where i.partyId = :pid and i.status in :st and i.outstanding > 0", Object[].class)
                        .setParameter("pid", partyId).setParameter("st", OPEN_INVOICE).getSingleResult();
                supplier = new LinkedHashMap<>();
                supplier.put("name", party.get(0)[1]);
                supplier.put("depositBalance", AdminJs.json(dbl(party.get(0)[2])));
                supplier.put("openInvoiceCount", ((Number) agg[1]).longValue());
                supplier.put("openOutstanding", AdminJs.json(dbl(agg[0])));
            }
        }

        // Riwayat harga beli per item (5 transaksi terakhir, semua supplier) untuk pembanding.
        Set<String> itemIds = new LinkedHashSet<>();
        for (JsonNode it : payloadItems) {
            JsonNode v = it == null ? null : it.get("inventoryItemId");
            if (v != null && v.isTextual() && !v.textValue().isEmpty()) {
                itemIds.add(v.textValue());
            }
        }
        Map<String, Object> priceHistory = new LinkedHashMap<>();
        for (String iid : itemIds) {
            List<Object[]> rows = em.createQuery("select pii.rate, pii.quantity, pi.invoiceNumber, pi.date, pi.partyId, s.name "
                            + "from PurchaseInvoiceItem pii join pii.purchaseInvoice pi left join pi.supplier s "
                            + "where pii.inventoryItemId = :iid and pi.status <> :cancelled order by pi.date desc", Object[].class)
                    .setParameter("iid", iid).setParameter("cancelled", InvoiceStatus.Cancelled)
                    .setMaxResults(5).getResultList();
            List<Map<String, Object>> hist = new ArrayList<>();
            for (Object[] r : rows) {
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("rate", AdminJs.json(dbl(r[0])));
                h.put("quantity", AdminJs.json(dbl(r[1])));
                h.put("invoiceNumber", r[2]);
                h.put("date", r[3]);
                h.put("supplier", r[5]);
                h.put("sameSupplier", r[4] != null && r[4].equals(partyId));
                hist.add(h);
            }
            priceHistory.put(iid, hist);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sttLines", sttLines);
        out.put("stts", stts);
        out.put("requestAttachments", requestAtt);
        out.put("supplier", supplier);
        out.put("priceHistory", priceHistory);
        Lookups lookups = lookups(payload);
        out.put("party", lookups.party());
        out.put("items", lookups.items());
        out.put("comparison", ApprovalComparison.build(request));
        return out;
    }

    /** {@code GET /pending} — apakah ada pengajuan Pending untuk dokumen ini (badge + kunci tombol edit). */
    @ErpTx(readOnly = true)
    public Map<String, Object> pending(String documentType, String documentId) {
        if (!ErpApprovalGateService.isEditDocumentType(documentType) || documentId == null || documentId.isEmpty()) {
            throw ErpException.business("documentType/documentId tidak valid.");
        }
        ApprovalRequest p = gate.findPendingForTarget(documentType, documentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pending", p == null ? null : ErpJson.map("id", p.getId(), "createdAt", p.getCreatedAt()));
        return out;
    }

    // ───────────────────────── approvalContextLookups ─────────────────────────

    record Lookups(Map<String, Object> party, Map<String, Object> items) {
    }

    /** Nama party (payload.partyId) + item (inputs/outputs[].itemId) — payload hanya menyimpan id. */
    Lookups lookups(JsonNode payload) {
        Map<String, Object> party = null;
        String partyId = payload != null && payload.isObject() ? textOrNull(payload.get("partyId")) : null;
        if (partyId != null && !partyId.isEmpty()) {
            List<Object[]> rows = em.createQuery("select p.id, p.name from Party p where p.id = :id", Object[].class)
                    .setParameter("id", partyId).getResultList();
            if (!rows.isEmpty()) {
                party = ErpJson.map("id", rows.get(0)[0], "name", rows.get(0)[1]);
            }
        }
        Set<String> ids = new LinkedHashSet<>();
        if (payload != null && payload.isObject()) {
            for (String key : List.of("inputs", "outputs")) {
                for (JsonNode r : ErpJson.elements(payload.get(key))) {
                    JsonNode v = r == null ? null : r.get("itemId");
                    if (v != null && v.isTextual() && !v.textValue().isEmpty()) {
                        ids.add(v.textValue());
                    }
                }
            }
        }
        Map<String, Object> items = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] r : em.createQuery("select i.id, i.name, i.unit from InventoryItem i where i.id in :ids", Object[].class)
                    .setParameter("ids", ids).getResultList()) {
                items.put((String) r[0], ErpJson.map("name", r[1], "unit", r[2]));
            }
        }
        return new Lookups(party, items);
    }

    // ───────────────────────── helper ─────────────────────────

    /** Pengajuan yang boleh dilihat aktor; 404 bila tidak ada atau milik orang lain tanpa view_all. */
    private ApprovalRequest visible(String id) {
        ApprovalRequest r = em.find(ApprovalRequest.class, id);
        if (r == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        if (!access.hasCap(VIEW_ALL) && !r.getRequestedBy().equals(access.userId())) {
            throw ErpException.notFound(NOT_FOUND);
        }
        return r;
    }

    /**
     * Baris + {@code requester {fullName, role}}, {@code approver {fullName}}, {@code rejecter {fullName}} —
     * padanan include dengan select parsial Prisma (null bila kolom FK kosong).
     */
    private List<ObjectNode> withPeople(List<ApprovalRequest> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (ApprovalRequest r : rows) {
            ids.add(r.getRequestedBy());
            if (r.getApprovedBy() != null) {
                ids.add(r.getApprovedBy());
            }
            if (r.getRejectedBy() != null) {
                ids.add(r.getRejectedBy());
            }
        }
        Map<String, Object[]> users = users(ids);
        List<ObjectNode> out = new ArrayList<>(rows.size());
        for (ApprovalRequest r : rows) {
            ObjectNode o = AdminShape.scalars(r);
            Object[] req = users.get(r.getRequestedBy());
            o.set("requester", ErpJson.tree(req == null ? null : ErpJson.map("fullName", req[1], "role", req[2])));
            Object[] app = r.getApprovedBy() == null ? null : users.get(r.getApprovedBy());
            o.set("approver", ErpJson.tree(app == null ? null : ErpJson.map("fullName", app[1])));
            Object[] rej = r.getRejectedBy() == null ? null : users.get(r.getRejectedBy());
            o.set("rejecter", ErpJson.tree(rej == null ? null : ErpJson.map("fullName", rej[1])));
            out.add(o);
        }
        return out;
    }

    private Map<String, Object[]> users(Collection<String> ids) {
        Map<String, Object[]> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Object[] u : em.createQuery("select u.id, u.fullName, u.role from User u where u.id in :ids", Object[].class)
                .setParameter("ids", ids).getResultList()) {
            out.put((String) u[0], u);
        }
        return out;
    }

    /** Lampiran (select id, fileName, mimeType, fileSize, referenceType, referenceId, createdAt, user.fullName). */
    private List<ObjectNode> attachments(String referenceType, Collection<String> referenceIds) {
        List<Object[]> rows = em.createQuery("select a, u.fullName from TransactionAttachment a left join a.user u "
                        + "where a.referenceType = :t and a.referenceId in :ids order by a.createdAt asc", Object[].class)
                .setParameter("t", referenceType).setParameter("ids", referenceIds).getResultList();
        List<ObjectNode> out = new ArrayList<>();
        for (Object[] row : rows) {
            TransactionAttachment a = (TransactionAttachment) row[0];
            ObjectNode o = ErpJson.object();
            o.put("id", a.getId());
            o.put("fileName", a.getFileName());
            o.put("mimeType", a.getMimeType());
            o.put("fileSize", a.getFileSize());
            o.put("referenceType", a.getReferenceType());
            o.put("referenceId", a.getReferenceId());
            o.set("createdAt", ErpJson.tree(a.getCreatedAt()));
            o.set("user", ErpJson.tree(row[1] == null ? null : ErpJson.map("fullName", row[1])));
            out.add(o);
        }
        return out;
    }

    private static String textOrNull(JsonNode v) {
        return v != null && v.isTextual() ? v.textValue() : null;
    }

    private static double dbl(Object v) {
        return v == null ? 0 : v instanceof BigDecimal b ? b.doubleValue() : ((Number) v).doubleValue();
    }
}
