package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.GaAsset;
import id.neravoxa.erp.entity.GaInventoryCategory;
import id.neravoxa.erp.entity.GaInventoryItem;
import id.neravoxa.erp.entity.GaInventoryMovement;
import id.neravoxa.erp.entity.GaLocation;
import id.neravoxa.erp.entity.GaMaterialRequest;
import id.neravoxa.erp.entity.GaMaterialRequestItem;
import id.neravoxa.erp.entity.GaMaterialRequestStatus;
import id.neravoxa.erp.entity.GaMovementType;
import id.neravoxa.erp.entity.GaProcurementStatus;
import id.neravoxa.erp.entity.GaWorkOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Permintaan barang (Material Request) GA — port {@code routes/ga/materialRequest.ts}: pengadaan sparepart
 * internal untuk EAM. Alur DRAFT → SUBMITTED → APPROVED → pengadaan per item → RECEIVE (stok GA masuk
 * atomik) → FULFILLED. Tidak ada posting GL maupun PurchaseOrder di sini (pembayaran MR lewat antrian
 * Tagihan EAM di Pengajuan Pembayaran). RECEIVE tidak pernah membuat item inventaris dari nama saja
 * (categoryId NOT NULL): body wajib memberi inventoryItemId atau newItem { sku, categoryId }.
 */
@Slf4j
@Service
public class ErpGaMaterialRequestService {

    static final List<String> PROCUREMENT_STATUS = List.of("REQUESTED", "QUOTED", "ORDERED", "RECEIVED", "CANCELLED");

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ─────────────────────────── daftar & detail ───────────────────────────

    private Map<String, List<GaMaterialRequestItem>> itemsOf(Collection<String> mrIds) {
        Map<String, List<GaMaterialRequestItem>> out = new HashMap<>();
        List<String> all = new ArrayList<>(mrIds);
        for (int i = 0; i < all.size(); i += 1000) {
            List<String> chunk = all.subList(i, Math.min(all.size(), i + 1000));
            for (GaMaterialRequestItem it : em.createQuery("select i from GaMaterialRequestItem i "
                    + "where i.materialRequestId in :ids order by i.createdAt asc, i.id asc", GaMaterialRequestItem.class)
                    .setParameter("ids", chunk).getResultList()) {
                out.computeIfAbsent(it.getMaterialRequestId(), k -> new ArrayList<>()).add(it);
            }
        }
        return out;
    }

    /** {@code { ...mr, asset, workOrder, items (select), _count: { items } }}. */
    private List<ObjectNode> listRows(List<GaMaterialRequest> rows) {
        Map<String, List<GaMaterialRequestItem>> items = itemsOf(rows.stream().map(GaMaterialRequest::getId).toList());
        List<ObjectNode> out = new ArrayList<>();
        for (GaMaterialRequest m : rows) {
            ObjectNode o = GaOpsJson.scalars(m);
            o.set("asset", ErpJson.tree(GaOpsJson.assetBrief(m.getAsset())));
            GaWorkOrder wo = m.getWorkOrder();
            o.set("workOrder", ErpJson.tree(wo == null ? null : GaOpsJson.map("id", wo.getId(), "code", wo.getCode())));
            List<GaMaterialRequestItem> its = items.getOrDefault(m.getId(), List.of());
            ArrayNode arr = ErpJson.arrayNode();
            for (GaMaterialRequestItem it : its) {
                arr.add(ErpJson.tree(GaOpsJson.map("id", it.getId(), "itemName", it.getItemName(),
                        "quantity", it.getQuantity(), "uom", it.getUom(), "procurementStatus", it.getProcurementStatus())));
            }
            o.set("items", arr);
            o.set("_count", ErpJson.tree(GaOpsJson.map("items", its.size())));
            out.add(o);
        }
        return out;
    }

    /** GET / tanpa {@code page}: array (status/workOrderId/mine), terbaru dulu. Status tak dikenal → 500. */
    @ErpTx(readOnly = true)
    public List<ObjectNode> list(Map<String, String> query, String userId) {
        GaWhere w = new GaWhere();
        String status = query.get("status");
        if (status != null && !status.isEmpty()) {
            if (!GaMaterialRequestList.STATUSES.contains(status)) {
                throw ErpException.of(500, "Gagal mengambil daftar permintaan barang.");
            }
            w.and("m.status = :status", "status", GaMaterialRequestStatus.valueOf(status));
        }
        String workOrderId = query.get("workOrderId");
        w.andIf(workOrderId != null && !workOrderId.isEmpty(), "m.workOrderId = :woId", "woId", workOrderId);
        w.andIf("true".equals(query.get("mine")) && userId != null, "m.requestedById = :mineId", "mineId", userId);
        List<GaMaterialRequest> rows = w.bind(em.createQuery("select m from GaMaterialRequest m left join fetch m.asset "
                + "left join fetch m.workOrder" + w.clause() + " order by m.createdAt desc", GaMaterialRequest.class))
                .getResultList();
        return listRows(rows);
    }

    private Map<String, String> userNames(Collection<String> ids) {
        Set<String> unique = new LinkedHashSet<>();
        ids.forEach(id -> {
            if (id != null) {
                unique.add(id);
            }
        });
        Map<String, String> out = new HashMap<>();
        if (!unique.isEmpty()) {
            for (Object[] r : em.createQuery("select u.id, u.fullName from User u where u.id in :ids", Object[].class)
                    .setParameter("ids", unique).getResultList()) {
                out.put((String) r[0], (String) r[1]);
            }
        }
        return out;
    }

    /** GET /?page= → {@code { data, total, page, limit }} (limit ≤ 200) dengan {@code requester}. */
    @ErpTx(readOnly = true)
    public Map<String, Object> listPaged(Map<String, String> query, String userId) {
        ListQuery.Paging paging = ListQuery.paging(query, 25, 200);
        GaWhere where = GaMaterialRequestList.where(query, userId, false);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), GaMaterialRequestList.SORT_FIELDS,
                GaMaterialRequestList.DEFAULT_SORT);
        ErpQuery<GaMaterialRequest> q = ErpQuery.of(em, GaMaterialRequest.class, "m")
                .join("left join m.asset a", "left join m.workOrder wo")
                .fetch("left join fetch m.asset", "left join fetch m.workOrder")
                .orderBy(GaMaterialRequestList.orderBy(sort));
        where.applyTo(q);
        long total = q.count();
        List<GaMaterialRequest> rows = q.list(paging.skip(), paging.limit());
        Map<String, String> names = userNames(rows.stream().map(GaMaterialRequest::getRequestedById).toList());
        List<ObjectNode> data = listRows(rows);
        for (int i = 0; i < rows.size(); i++) {
            String rid = rows.get(i).getRequestedById();
            data.get(i).set("requester", ErpJson.tree(GaOpsJson.map("id", rid, "fullName", names.get(rid))));
        }
        return ListQuery.result(data, total, paging);
    }

    /** GET /summary — jumlah per status untuk filter daftar (filter status sendiri diabaikan). */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query, String userId) {
        GaWhere where = GaMaterialRequestList.where(query, userId, true);
        List<Object[]> groups = where.bind(em.createQuery("select m.status, count(m) from GaMaterialRequest m "
                + "left join m.asset a left join m.workOrder wo" + where.clause() + " group by m.status", Object[].class))
                .getResultList();
        Map<String, Long> byStatus = GaMaterialRequestList.countByStatus(groups);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return GaOpsJson.map("total", total, "byStatus", byStatus);
    }

    /** GET /:id — detail + item + WO + aset + requester (bentuk sama dengan daftar). */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<GaMaterialRequest> rows = em.createQuery("select m from GaMaterialRequest m left join fetch m.asset "
                + "left join fetch m.workOrder where m.id = :id", GaMaterialRequest.class).setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("Permintaan barang tidak ditemukan.");
        }
        GaMaterialRequest m = rows.get(0);
        List<GaMaterialRequestItem> items = em.createQuery("select i from GaMaterialRequestItem i "
                + "left join fetch i.inventoryItem left join fetch i.receivedIntoLocation "
                + "where i.materialRequestId = :id order by i.createdAt asc, i.id asc", GaMaterialRequestItem.class)
                .setParameter("id", id).getResultList();
        ObjectNode o = GaOpsJson.scalars(m);
        o.set("asset", ErpJson.tree(GaOpsJson.assetBrief(m.getAsset())));
        GaWorkOrder wo = m.getWorkOrder();
        o.set("workOrder", ErpJson.tree(wo == null ? null : GaOpsJson.map("id", wo.getId(), "code", wo.getCode(),
                "title", wo.getTitle(), "status", wo.getStatus())));
        ArrayNode arr = ErpJson.arrayNode();
        for (GaMaterialRequestItem it : items) {
            ObjectNode io = GaOpsJson.scalars(it);
            GaInventoryItem inv = it.getInventoryItem();
            io.set("inventoryItem", ErpJson.tree(inv == null ? null : GaOpsJson.map("id", inv.getId(), "sku", inv.getSku(),
                    "name", inv.getName(), "uom", inv.getUom())));
            io.set("receivedIntoLocation", ErpJson.tree(GaOpsJson.locFull(it.getReceivedIntoLocation())));
            arr.add(io);
        }
        o.set("items", arr);
        String fullName = userNames(List.of(m.getRequestedById())).get(m.getRequestedById());
        o.set("requester", ErpJson.tree(GaOpsJson.map("id", m.getRequestedById(), "fullName", fullName)));
        return o;
    }

    // ─────────────────────────── create / edit ───────────────────────────

    record ItemInput(String inventoryItemId, String itemName, String sku, String uom, BigDecimal quantity, String notes) {
    }

    record HeaderInput(String workOrderId, String assetId, String machineTarget, String notes, List<ItemInput> items) {
    }

    /** {@code MrItemInputSchema}. */
    private static ItemInput parseItem(JsonNode raw) {
        JsonNode e = GaZod.reqObject(raw);
        String inv = GaZod.optString(GaZod.field(e, "inventoryItemId"), 1, null, 0);
        String name = GaZod.reqString(GaZod.field(e, "itemName"), 1, "Nama barang wajib diisi.", 200);
        String sku = GaZod.optString(GaZod.field(e, "sku"), 0, null, 100);
        String uom = GaZod.reqString(GaZod.field(e, "uom"), 1, "Satuan (UOM) wajib diisi.", 50);
        BigDecimal qty = GaZod.reqNumber(GaZod.field(e, "quantity"), GaZod.Sign.POSITIVE, "Qty harus lebih dari 0.");
        String notes = GaZod.optString(GaZod.field(e, "notes"), 0, null, 500);
        return new ItemInput(inv, name, sku, uom, qty, notes);
    }

    private static List<ItemInput> parseItems(JsonNode raw) {
        List<ItemInput> out = new ArrayList<>();
        for (JsonNode el : GaZod.reqArray(raw, 1, "Minimal satu item.")) {
            out.add(parseItem(el));
        }
        return out;
    }

    /** {@code CreateMrSchema}. */
    static HeaderInput parseCreate(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String woId = GaZod.optString(GaZod.field(b, "workOrderId"), 1, null, 0);
        String assetId = GaZod.optString(GaZod.field(b, "assetId"), 1, null, 0);
        String machine = GaZod.optString(GaZod.field(b, "machineTarget"), 0, null, 200);
        String notes = GaZod.optString(GaZod.field(b, "notes"), 0, null, 1000);
        return new HeaderInput(woId, assetId, machine, notes, parseItems(GaZod.field(b, "items")));
    }

    /** {@code UpdateMrSchema} (tanpa workOrderId). */
    static HeaderInput parseUpdate(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String assetId = GaZod.optString(GaZod.field(b, "assetId"), 1, null, 0);
        String machine = GaZod.optString(GaZod.field(b, "machineTarget"), 0, null, 200);
        String notes = GaZod.optString(GaZod.field(b, "notes"), 0, null, 1000);
        return new HeaderInput(null, assetId, machine, notes, parseItems(GaZod.field(b, "items")));
    }

    /** {@code FromWoSchema} (workOrderId & aset diisi dari WO). */
    static HeaderInput parseFromWo(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String machine = GaZod.optString(GaZod.field(b, "machineTarget"), 0, null, 200);
        String notes = GaZod.optString(GaZod.field(b, "notes"), 0, null, 1000);
        return new HeaderInput(null, null, machine, notes, parseItems(GaZod.field(b, "items")));
    }

    private void checkLinkedItems(List<ItemInput> items) {
        Set<String> linked = new LinkedHashSet<>();
        items.forEach(it -> {
            if (it.inventoryItemId() != null && !it.inventoryItemId().isEmpty()) {
                linked.add(it.inventoryItemId());
            }
        });
        if (!linked.isEmpty()) {
            long found = em.createQuery("select count(i) from GaInventoryItem i where i.id in :ids", Long.class)
                    .setParameter("ids", linked).getSingleResult();
            if (found != linked.size()) {
                throw ErpException.business("Sebagian item inventory tidak ditemukan.");
            }
        }
    }

    /**
     * Simpan item sesuai urutan input. Di sumber semua item satu transaksi ber-createdAt sama dan urutan
     * "createdAt asc" jatuh ke urutan fisik (= urutan input); di sini createdAt dinaikkan 1 ms per item
     * agar urutan itu deterministik.
     */
    private void persistItems(String mrId, List<ItemInput> items) {
        Instant base = Instant.now();
        int seq = 0;
        for (ItemInput in : items) {
            GaMaterialRequestItem it = new GaMaterialRequestItem();
            it.setCreatedAt(base.plusMillis(seq));
            it.setUpdatedAt(base.plusMillis(seq++));
            it.setMaterialRequestId(mrId);
            it.setInventoryItemId(in.inventoryItemId());
            it.setItemName(in.itemName());
            it.setSku(in.sku());
            it.setUom(in.uom());
            it.setQuantity(in.quantity());
            it.setNotes(in.notes());
            em.persist(it);
        }
    }

    /** Logika create bersama (endpoint standalone & dari-WO) → {@code { id, code }}. */
    @ErpTx
    public Map<String, Object> create(HeaderInput in, String userId) {
        if (in.workOrderId() != null && em.find(GaWorkOrder.class, in.workOrderId()) == null) {
            throw ErpException.business("Work order tidak ditemukan.");
        }
        if (in.assetId() != null && em.find(GaAsset.class, in.assetId()) == null) {
            throw ErpException.business("Aset tidak ditemukan.");
        }
        checkLinkedItems(in.items());
        String code = ErpGaWorkOrderService.nextCode(em, "GaMaterialRequest", "MR", 0xa71000L);
        GaMaterialRequest mr = new GaMaterialRequest();
        mr.setCode(code);
        mr.setStatus(GaMaterialRequestStatus.DRAFT);
        mr.setWorkOrderId(in.workOrderId());
        mr.setAssetId(in.assetId());
        mr.setMachineTarget(in.machineTarget());
        mr.setNotes(in.notes());
        mr.setRequestedById(userId);
        mr.setRequestedAt(Instant.now());
        em.persist(mr);
        em.flush();
        persistItems(mr.getId(), in.items());
        em.flush();
        return GaOpsJson.map("id", mr.getId(), "code", mr.getCode());
    }

    /** POST /from-work-order/:workOrderId — MR langsung dari WO (workOrderId + aset dari WO). */
    @ErpTx
    public Map<String, Object> createFromWorkOrder(String workOrderId, HeaderInput in, String userId) {
        GaWorkOrder wo = em.find(GaWorkOrder.class, workOrderId);
        if (wo == null) {
            throw ErpException.notFound("Work order tidak ditemukan.");
        }
        return create(new HeaderInput(wo.getId(), wo.getAssetId(), in.machineTarget(), in.notes(), in.items()), userId);
    }

    /** PUT /:id — ubah header + ganti seluruh item (hanya DRAFT). */
    @ErpTx
    public Map<String, Object> update(String id, HeaderInput in) {
        GaMaterialRequest mr = em.find(GaMaterialRequest.class, id);
        if (mr == null) {
            throw ErpException.business("Permintaan barang tidak ditemukan.");
        }
        if (mr.getStatus() != GaMaterialRequestStatus.DRAFT) {
            throw ErpException.business("Hanya MR berstatus DRAFT yang bisa diubah.");
        }
        if (in.assetId() != null && em.find(GaAsset.class, in.assetId()) == null) {
            throw ErpException.business("Aset tidak ditemukan.");
        }
        checkLinkedItems(in.items());
        em.createQuery("delete from GaMaterialRequestItem i where i.materialRequestId = :id")
                .setParameter("id", mr.getId()).executeUpdate();
        mr.setAssetId(in.assetId());
        mr.setMachineTarget(in.machineTarget());
        mr.setNotes(in.notes());
        mr.setUpdatedAt(Instant.now());
        persistItems(mr.getId(), in.items());
        em.flush();
        return GaOpsJson.map("id", id);
    }

    // ─────────────────────────── siklus status ───────────────────────────

    private interface Mutator {
        void apply(GaMaterialRequest mr);
    }

    /** Transisi status sederhana dengan penjaga BusinessError. */
    private Map<String, Object> transition(String id, List<GaMaterialRequestStatus> allowedFrom, String verbMessage,
                                           Mutator mutator) {
        GaMaterialRequest mr = em.find(GaMaterialRequest.class, id);
        if (mr == null) {
            throw ErpException.business("Permintaan barang tidak ditemukan.");
        }
        if (!allowedFrom.contains(mr.getStatus())) {
            throw ErpException.business(verbMessage.replace("{status}", String.valueOf(mr.getStatus())));
        }
        mutator.apply(mr);
        mr.setUpdatedAt(Instant.now());
        em.flush();
        return GaOpsJson.map("id", mr.getId(), "status", mr.getStatus());
    }

    /** PATCH /:id/submit — DRAFT → SUBMITTED. */
    @ErpTx
    public Map<String, Object> submit(String id) {
        return transition(id, List.of(GaMaterialRequestStatus.DRAFT), "Tidak bisa submit MR berstatus {status}.",
                mr -> mr.setStatus(GaMaterialRequestStatus.SUBMITTED));
    }

    /** PATCH /:id/approve — SUBMITTED → APPROVED (izin edit modul ga_material_request, bukan capability). */
    @ErpTx
    public Map<String, Object> approve(String id, String userId) {
        return transition(id, List.of(GaMaterialRequestStatus.SUBMITTED), "Tidak bisa approve MR berstatus {status}.",
                mr -> {
                    mr.setStatus(GaMaterialRequestStatus.APPROVED);
                    mr.setApprovedById(userId);
                    mr.setApprovedAt(Instant.now());
                });
    }

    static String parseReject(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        return GaZod.reqString(GaZod.field(b, "reason"), 1, "Alasan wajib diisi.", 500);
    }

    /** PATCH /:id/reject — SUBMITTED → REJECTED. */
    @ErpTx
    public Map<String, Object> reject(String id, String reason, String userId) {
        return transition(id, List.of(GaMaterialRequestStatus.SUBMITTED), "Tidak bisa reject MR berstatus {status}.",
                mr -> {
                    mr.setStatus(GaMaterialRequestStatus.REJECTED);
                    mr.setRejectedById(userId);
                    mr.setRejectedAt(Instant.now());
                    mr.setRejectReason(reason);
                });
    }

    /** PATCH /:id/cancel — DRAFT/SUBMITTED/APPROVED → CANCELLED. */
    @ErpTx
    public Map<String, Object> cancel(String id) {
        return transition(id, List.of(GaMaterialRequestStatus.DRAFT, GaMaterialRequestStatus.SUBMITTED,
                        GaMaterialRequestStatus.APPROVED), "Tidak bisa membatalkan MR berstatus {status}.",
                mr -> mr.setStatus(GaMaterialRequestStatus.CANCELLED));
    }

    // ─────────────────────────── pengadaan per item ───────────────────────────

    record ProcurementInput(JsonNode body, String vendorName, BigDecimal unitPrice, String poNumber,
                            String procurementStatus) {
        boolean has(String f) {
            return !GaZod.absent(GaZod.field(body, f));
        }
    }

    static ProcurementInput parseProcurement(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String vendor = GaZod.optString(GaZod.field(b, "vendorName"), 0, null, 200);
        BigDecimal price = GaZod.optNumberNotNull(GaZod.field(b, "unitPrice"), GaZod.Sign.NONNEGATIVE, null);
        String po = GaZod.optString(GaZod.field(b, "poNumber"), 0, null, 100);
        String status = GaZod.optEnum(GaZod.field(b, "procurementStatus"), PROCUREMENT_STATUS);
        return new ProcurementInput(b, vendor, price, po, status);
    }

    private GaMaterialRequestItem findItem(String mrId, String itemId) {
        List<GaMaterialRequestItem> rows = em.createQuery("select i from GaMaterialRequestItem i "
                        + "where i.id = :itemId and i.materialRequestId = :mrId", GaMaterialRequestItem.class)
                .setParameter("itemId", itemId).setParameter("mrId", mrId).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.business("Item permintaan tidak ditemukan.");
        }
        return rows.get(0);
    }

    /** PATCH /:id/items/:itemId/procurement — vendor/harga/PO/status (non-stok). */
    @ErpTx
    public Map<String, Object> updateProcurement(String mrId, String itemId, ProcurementInput in) {
        GaMaterialRequestItem item = findItem(mrId, itemId);
        GaMaterialRequest mr = em.find(GaMaterialRequest.class, item.getMaterialRequestId());
        GaMaterialRequestStatus st = mr.getStatus();
        if (st == GaMaterialRequestStatus.REJECTED || st == GaMaterialRequestStatus.CANCELLED) {
            throw ErpException.business("Tidak bisa mengubah procurement pada MR berstatus " + st + ".");
        }
        // RECEIVED tidak boleh di-set manual lewat endpoint ini (harus lewat receive).
        if ("RECEIVED".equals(in.procurementStatus()) && item.getProcurementStatus() != GaProcurementStatus.RECEIVED) {
            throw ErpException.business("Status RECEIVED hanya bisa lewat aksi Terima Barang (receive).");
        }
        if (in.has("vendorName")) {
            item.setVendorName(in.vendorName());
        }
        if (in.unitPrice() != null) {
            item.setUnitPrice(in.unitPrice());
        }
        if (in.has("poNumber")) {
            item.setPoNumber(in.poNumber());
        }
        if (in.procurementStatus() != null) {
            item.setProcurementStatus(GaProcurementStatus.valueOf(in.procurementStatus()));
        }
        item.setUpdatedAt(Instant.now());
        em.flush();
        return GaOpsJson.map("id", item.getId(), "procurementStatus", item.getProcurementStatus());
    }

    // ─────────────────────────── penerimaan barang ───────────────────────────

    record ReceiveInput(BigDecimal receivedQty, String locationId, String inventoryItemId, String newSku,
                        String newCategoryId, boolean hasNewItem) {
    }

    static ReceiveInput parseReceive(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        BigDecimal qty = GaZod.reqNumber(GaZod.field(b, "receivedQty"), GaZod.Sign.POSITIVE,
                "Qty diterima harus lebih dari 0.");
        String loc = GaZod.reqString(GaZod.field(b, "locationId"), 1, "Lokasi gudang wajib dipilih.", 0);
        String inv = GaZod.optString(GaZod.field(b, "inventoryItemId"), 1, null, 0);
        JsonNode ni = GaZod.optObject(GaZod.field(b, "newItem"));
        String sku = null;
        String cat = null;
        if (ni != null) {
            sku = GaZod.reqString(GaZod.field(ni, "sku"), 1, "SKU wajib diisi.", 100);
            cat = GaZod.reqString(GaZod.field(ni, "categoryId"), 1, "Kategori wajib dipilih.", 0);
        }
        return new ReceiveInput(qty, loc, inv, sku, cat, ni != null);
    }

    /**
     * PATCH /:id/items/:itemId/receive — satu transaksi: resolusi/buat item inventaris, upsert atomik ke
     * stok per lokasi, movement IN (referenceNo MR_&lt;code&gt;). MR menjadi FULFILLED setelah semua item
     * RECEIVED atau CANCELLED.
     */
    @ErpTx
    public Map<String, Object> receive(String mrId, String itemId, ReceiveInput in, String userId) {
        GaMaterialRequest mr = em.find(GaMaterialRequest.class, mrId);
        if (mr == null) {
            throw ErpException.business("Permintaan barang tidak ditemukan.");
        }
        if (mr.getStatus() != GaMaterialRequestStatus.APPROVED) {
            throw ErpException.business("Barang hanya bisa diterima saat MR berstatus APPROVED (sekarang "
                    + mr.getStatus() + ").");
        }
        GaMaterialRequestItem item = findItem(mr.getId(), itemId);
        if (item.getProcurementStatus() == GaProcurementStatus.RECEIVED) {
            throw ErpException.business("Item ini sudah diterima.");
        }
        if (item.getProcurementStatus() == GaProcurementStatus.CANCELLED) {
            throw ErpException.business("Item ini sudah dibatalkan.");
        }
        if (em.find(GaLocation.class, in.locationId()) == null) {
            throw ErpException.business("Lokasi gudang tidak ditemukan.");
        }

        String inventoryItemId = item.getInventoryItemId() != null ? item.getInventoryItemId() : in.inventoryItemId();
        if (inventoryItemId == null) {
            if (!in.hasNewItem()) {
                throw ErpException.business("Item belum tertaut ke inventaris GA. Pilih item inventaris yang ada, "
                        + "atau isi SKU + kategori untuk membuat item baru.");
            }
            if (em.find(GaInventoryCategory.class, in.newCategoryId()) == null) {
                throw ErpException.business("Kategori inventaris tidak ditemukan.");
            }
            long dupe = em.createQuery("select count(i) from GaInventoryItem i where i.sku = :sku", Long.class)
                    .setParameter("sku", in.newSku()).getSingleResult();
            if (dupe > 0) {
                throw ErpException.business("SKU \"" + in.newSku()
                        + "\" sudah dipakai item lain. Pilih item itu atau gunakan SKU lain.");
            }
            GaInventoryItem created = new GaInventoryItem();
            created.setSku(in.newSku());
            created.setName(item.getItemName());
            created.setCategoryId(in.newCategoryId());
            created.setUom(item.getUom());
            em.persist(created);
            em.flush();
            inventoryItemId = created.getId();
        } else if (em.find(GaInventoryItem.class, inventoryItemId) == null) {
            throw ErpException.business("Item inventaris tidak ditemukan.");
        }

        BigDecimal qty = in.receivedQty();
        // Upsert + increment atomik (kebalikan dari konsumsi part).
        GaStock.adjust(em, inventoryItemId, in.locationId(), qty);

        GaInventoryMovement m = new GaInventoryMovement();
        m.setItemId(inventoryItemId);
        m.setType(GaMovementType.IN);
        m.setQuantity(qty);
        m.setToLocationId(in.locationId());
        m.setReferenceNo("MR_" + mr.getCode());
        m.setNotes("Penerimaan sparepart dari MR " + mr.getCode());
        m.setCreatedBy(userId);
        em.persist(m);

        item.setInventoryItemId(inventoryItemId);
        item.setProcurementStatus(GaProcurementStatus.RECEIVED);
        item.setReceivedQty(qty);
        item.setReceivedAt(Instant.now());
        item.setReceivedIntoLocationId(in.locationId());
        item.setUpdatedAt(Instant.now());
        em.flush();

        // Auto-fulfill: semua item sudah RECEIVED atau CANCELLED → MR FULFILLED.
        long remaining = em.createQuery("select count(i) from GaMaterialRequestItem i where i.materialRequestId = :id "
                        + "and i.procurementStatus not in :done", Long.class)
                .setParameter("id", mr.getId())
                .setParameter("done", List.of(GaProcurementStatus.RECEIVED, GaProcurementStatus.CANCELLED))
                .getSingleResult();
        if (remaining == 0) {
            mr.setStatus(GaMaterialRequestStatus.FULFILLED);
            mr.setUpdatedAt(Instant.now());
            em.flush();
        }
        log.info("MR item received into GA stock: materialRequestId={} itemId={} inventoryItemId={} qty={} locationId={}",
                mr.getId(), item.getId(), inventoryItemId, GaOpsJson.numText(qty), in.locationId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("itemId", item.getId());
        out.put("inventoryItemId", inventoryItemId);
        out.put("fulfilled", remaining == 0);
        return out;
    }
}
