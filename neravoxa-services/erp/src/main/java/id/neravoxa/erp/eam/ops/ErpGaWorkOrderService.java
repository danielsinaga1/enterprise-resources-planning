package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.storage.ErpFileStorage;
import id.neravoxa.erp.entity.GaAsset;
import id.neravoxa.erp.entity.GaInventoryItem;
import id.neravoxa.erp.entity.GaInventoryMovement;
import id.neravoxa.erp.entity.GaMaintenanceSchedule;
import id.neravoxa.erp.entity.GaMaterialRequest;
import id.neravoxa.erp.entity.GaMovementType;
import id.neravoxa.erp.entity.GaVehicleServiceLog;
import id.neravoxa.erp.entity.GaVehicleServiceRequest;
import id.neravoxa.erp.entity.GaVehicleServiceType;
import id.neravoxa.erp.entity.GaWorkOrder;
import id.neravoxa.erp.entity.GaWorkOrderPart;
import id.neravoxa.erp.entity.GaWorkOrderPhoto;
import id.neravoxa.erp.entity.GaWorkOrderPhotoType;
import id.neravoxa.erp.entity.GaWorkOrderPriority;
import id.neravoxa.erp.entity.GaWorkOrderStatus;
import id.neravoxa.erp.entity.GaWorkOrderType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
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
 * Work order GA — bagian {@code /work-orders} dari {@code routes/ga/maintenance.ts}.
 *
 * <p>Siklus: OPEN → ASSIGNED → IN_PROGRESS → DONE → CLOSED (atau CANCELLED). Menyelesaikan WO yang
 * berasal dari jadwal preventif menggulirkan jadwalnya; part WO memakai stok GA secara atomik (movement
 * OUT) di dalam transaksi. Menutup WO mencatat log servis kendaraan bersumber {@code WORK_ORDER}
 * (riwayat & total biaya kendaraan) — TANPA efek GL: log ini tidak masuk antrian Tagihan EAM dan tidak
 * bisa dijurnal (kernel ErpVehicleServiceJournalService hanya memposting log bersumber EAM).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpGaWorkOrderService {

    static final List<String> PRIORITIES = GaWorkOrderList.PRIORITIES;
    static final List<String> TYPES = GaWorkOrderList.TYPES;
    static final List<String> PHOTO_MIME = List.of("image/jpeg", "image/png", "image/webp");
    static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

    private final ErpFileStorage storage;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ─────────────────────────── helper ───────────────────────────

    private record PartStat(long count, BigDecimal cost) {
    }

    /** Jumlah part & Σ(qty × unitCost) per WO (dipecah per 1000 id agar parameter IN tetap wajar). */
    private Map<String, PartStat> partStats(Collection<String> ids) {
        Map<String, PartStat> out = new HashMap<>();
        List<String> all = new ArrayList<>(ids);
        for (int i = 0; i < all.size(); i += 1000) {
            List<String> chunk = all.subList(i, Math.min(all.size(), i + 1000));
            List<Object[]> rows = em.createQuery("select p.workOrderId, count(p), coalesce(sum(p.quantity * p.unitCost), 0) "
                    + "from GaWorkOrderPart p where p.workOrderId in :ids group by p.workOrderId", Object[].class)
                    .setParameter("ids", chunk).getResultList();
            for (Object[] r : rows) {
                out.put((String) r[0], new PartStat((Long) r[1], GaStock.toDecimal(r[2])));
            }
        }
        return out;
    }

    private Map<String, Long> photoCounts(Collection<String> ids) {
        Map<String, Long> out = new HashMap<>();
        List<String> all = new ArrayList<>(ids);
        for (int i = 0; i < all.size(); i += 1000) {
            List<String> chunk = all.subList(i, Math.min(all.size(), i + 1000));
            List<Object[]> rows = em.createQuery("select p.workOrderId, count(p) from GaWorkOrderPhoto p "
                    + "where p.workOrderId in :ids group by p.workOrderId", Object[].class)
                    .setParameter("ids", chunk).getResultList();
            for (Object[] r : rows) {
                out.put((String) r[0], (Long) r[1]);
            }
        }
        return out;
    }

    /** Total biaya WO (tenaga kerja + vendor luar + part). */
    static BigDecimal totalCost(BigDecimal labor, BigDecimal external, BigDecimal parts) {
        return (labor == null ? BigDecimal.ZERO : labor)
                .add(external == null ? BigDecimal.ZERO : external)
                .add(parts == null ? BigDecimal.ZERO : parts);
    }

    /** Baris daftar: {@code { ...wo, asset, _count: { parts, photos }, totalCost }}. */
    private List<ObjectNode> listRows(List<GaWorkOrder> items) {
        Set<String> ids = new LinkedHashSet<>();
        items.forEach(w -> ids.add(w.getId()));
        Map<String, PartStat> parts = partStats(ids);
        Map<String, Long> photos = photoCounts(ids);
        List<ObjectNode> out = new ArrayList<>();
        for (GaWorkOrder w : items) {
            PartStat ps = parts.getOrDefault(w.getId(), new PartStat(0, BigDecimal.ZERO));
            ObjectNode o = GaOpsJson.scalars(w);
            o.set("asset", ErpJson.tree(GaOpsJson.assetBrief(w.getAsset())));
            o.set("_count", ErpJson.tree(GaOpsJson.map("parts", ps.count(), "photos", photos.getOrDefault(w.getId(), 0L))));
            o.set("totalCost", ErpJson.tree(GaOpsJson.num(totalCost(w.getLaborCost(), w.getExternalCost(), ps.cost()))));
            out.add(o);
        }
        return out;
    }

    private static <E extends Enum<E>> E enumOr500(Class<E> type, String raw, String fallback) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            // Nilai enum tak dikenal = PrismaClientValidationError → handleRouteError 500.
            throw ErpException.of(500, fallback);
        }
    }

    // ─────────────────────────── daftar & detail ───────────────────────────

    /**
     * GET /work-orders tanpa {@code page}: array semua WO yang cocok (dashboard GA bergantung padanya),
     * dengan totalCost per baris supaya UI tidak perlu N+1 request ke detail.
     */
    @ErpTx(readOnly = true)
    public List<ObjectNode> list(Map<String, String> query, String userId) {
        String fallback = "Gagal mengambil work order.";
        GaWhere w = new GaWhere();
        String status = query.get("status");
        if (status != null && !status.isEmpty()) {
            w.and("w.status = :status", "status", enumOr500(GaWorkOrderStatus.class, status, fallback));
        }
        String priority = query.get("priority");
        if (priority != null && !priority.isEmpty()) {
            w.and("w.priority = :priority", "priority", enumOr500(GaWorkOrderPriority.class, priority, fallback));
        }
        String type = query.get("type");
        if (type != null && !type.isEmpty()) {
            w.and("w.type = :type", "type", enumOr500(GaWorkOrderType.class, type, fallback));
        }
        String assetId = query.get("assetId");
        w.andIf(assetId != null && !assetId.isEmpty(), "w.assetId = :assetId", "assetId", assetId);
        String assignedToId = query.get("assignedToId");
        if ("true".equals(query.get("mine")) && userId != null) {
            w.and("w.assignedToId = :assignee", "assignee", userId);
        } else if (assignedToId != null && !assignedToId.isEmpty()) {
            w.and("w.assignedToId = :assignee", "assignee", assignedToId);
        }
        List<GaWorkOrder> items = w.bind(em.createQuery("select w from GaWorkOrder w left join fetch w.asset"
                + w.clause() + " order by " + GaWorkOrderList.DEFAULT_ORDER, GaWorkOrder.class)).getResultList();
        return listRows(items);
    }

    private Map<String, String> userNames(Collection<String> ids) {
        Set<String> unique = new LinkedHashSet<>();
        ids.forEach(id -> {
            if (id != null) {
                unique.add(id);
            }
        });
        Map<String, String> out = new HashMap<>();
        if (unique.isEmpty()) {
            return out;
        }
        for (Object[] r : em.createQuery("select u.id, u.fullName from User u where u.id in :ids", Object[].class)
                .setParameter("ids", unique).getResultList()) {
            out.put((String) r[0], (String) r[1]);
        }
        return out;
    }

    /** GET /work-orders?page= → {@code { data, total, page, limit }} (limit ≤ 200) dengan {@code assignee}. */
    @ErpTx(readOnly = true)
    public Map<String, Object> listPaged(Map<String, String> query, String userId) {
        ListQuery.Paging paging = ListQuery.paging(query, 25, 200);
        GaWhere where = GaWorkOrderList.where(query, userId, Instant.now());
        String sortRaw = ListQuery.str(query, "sort");
        ListQuery.Sort sort = sortRaw == null ? null
                : ListQuery.sort(sortRaw, GaWorkOrderList.SORT_FIELDS, new ListQuery.Sort("code", true));
        ErpQuery<GaWorkOrder> q = ErpQuery.of(em, GaWorkOrder.class, "w")
                .join("left join w.asset a")
                .fetch("left join fetch w.asset")
                .orderBy(GaWorkOrderList.orderBy(sort));
        where.applyTo(q);
        long total = q.count();
        List<GaWorkOrder> items = q.list(paging.skip(), paging.limit());
        Map<String, String> names = userNames(items.stream().map(GaWorkOrder::getAssignedToId).toList());
        List<ObjectNode> data = listRows(items);
        for (int i = 0; i < items.size(); i++) {
            String assignee = items.get(i).getAssignedToId();
            data.get(i).set("assignee", assignee == null ? null
                    : ErpJson.tree(GaOpsJson.map("id", assignee, "fullName", names.get(assignee))));
        }
        return ListQuery.result(data, total, paging);
    }

    /**
     * GET /work-orders/summary — KPI untuk filter yang sama dengan daftar berpaginasi:
     * {@code { count, active, overdue, doneThisMonth, totalCost }} (biaya = tenaga kerja + vendor + part).
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query, String userId) {
        Instant now = Instant.now();
        GaWhere base = GaWorkOrderList.where(query, userId, now);
        String from = " from GaWorkOrder w left join w.asset a";
        long count = base.bind(em.createQuery("select count(w)" + from + base.clause(), Long.class)).getSingleResult();
        GaWhere activeW = base.plus("w.status in :kpiActive", "kpiActive", GaWorkOrderList.ACTIVE);
        long active = activeW.bind(em.createQuery("select count(w)" + from + activeW.clause(), Long.class)).getSingleResult();
        GaWhere overdueW = activeW.plus("w.dueAt < :kpiNow", "kpiNow", now);
        long overdue = overdueW.bind(em.createQuery("select count(w)" + from + overdueW.clause(), Long.class)).getSingleResult();
        Instant[] month = GaWorkOrderList.currentMonthRange(now);
        GaWhere doneW = base.plus("w.status in :kpiDone and w.completedAt >= :mFrom and w.completedAt < :mTo",
                "kpiDone", List.of(GaWorkOrderStatus.DONE, GaWorkOrderStatus.CLOSED), "mFrom", month[0], "mTo", month[1]);
        long done = doneW.bind(em.createQuery("select count(w)" + from + doneW.clause(), Long.class)).getSingleResult();
        Object[] costs = base.bind(em.createQuery("select coalesce(sum(w.laborCost), 0), coalesce(sum(w.externalCost), 0)"
                + from + base.clause(), Object[].class)).getSingleResult();
        BigDecimal partsCost = GaStock.toDecimal(base.bind(em.createQuery(
                "select coalesce(sum(p.quantity * p.unitCost), 0) from GaWorkOrderPart p where p.workOrderId in "
                        + "(select w.id" + from + base.clause() + ")", Object.class)).getSingleResult());
        BigDecimal total = totalCost(GaStock.toDecimal(costs[0]), GaStock.toDecimal(costs[1]), partsCost);
        return GaOpsJson.map("count", count, "active", active, "overdue", overdue, "doneThisMonth", done,
                "totalCost", GaOpsJson.num(total));
    }

    /** GET /work-orders/:id — detail + part/foto/jadwal/MR + total biaya. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<GaWorkOrder> rows = em.createQuery("select w from GaWorkOrder w left join fetch w.asset "
                + "left join fetch w.schedule where w.id = :id", GaWorkOrder.class).setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("Work order tidak ditemukan.");
        }
        GaWorkOrder wo = rows.get(0);
        List<GaWorkOrderPhoto> photos = em.createQuery("select p from GaWorkOrderPhoto p where p.workOrderId = :id "
                + "order by p.createdAt asc", GaWorkOrderPhoto.class).setParameter("id", id).getResultList();
        List<GaWorkOrderPart> parts = em.createQuery("select p from GaWorkOrderPart p left join fetch p.inventoryItem "
                + "left join fetch p.fromLocation where p.workOrderId = :id order by p.createdAt asc, p.id asc",
                GaWorkOrderPart.class).setParameter("id", id).getResultList();
        List<GaMaterialRequest> mrs = em.createQuery("select m from GaMaterialRequest m where m.workOrderId = :id "
                + "order by m.createdAt desc", GaMaterialRequest.class).setParameter("id", id).getResultList();
        Map<String, Long> mrItemCounts = new HashMap<>();
        if (!mrs.isEmpty()) {
            for (Object[] r : em.createQuery("select i.materialRequestId, count(i) from GaMaterialRequestItem i "
                    + "where i.materialRequestId in :ids group by i.materialRequestId", Object[].class)
                    .setParameter("ids", mrs.stream().map(GaMaterialRequest::getId).toList()).getResultList()) {
                mrItemCounts.put((String) r[0], (Long) r[1]);
            }
        }

        ObjectNode o = GaOpsJson.scalars(wo);
        o.set("asset", ErpJson.tree(GaOpsJson.assetWithPic(wo.getAsset())));
        o.set("schedule", wo.getSchedule() == null ? null : GaOpsJson.scalars(wo.getSchedule()));
        ArrayNode photoArr = ErpJson.arrayNode();
        photos.forEach(p -> photoArr.add(GaOpsJson.scalars(p)));
        o.set("photos", photoArr);
        ArrayNode partArr = ErpJson.arrayNode();
        BigDecimal partsCost = BigDecimal.ZERO;
        for (GaWorkOrderPart p : parts) {
            ObjectNode po = GaOpsJson.scalars(p);
            po.set("inventoryItem", ErpJson.tree(GaOpsJson.itemBrief(p.getInventoryItem())));
            po.set("fromLocation", ErpJson.tree(GaOpsJson.locCodeName(p.getFromLocation())));
            partArr.add(po);
            partsCost = partsCost.add(p.getQuantity().multiply(p.getUnitCost()));
        }
        o.set("parts", partArr);
        ArrayNode mrArr = ErpJson.arrayNode();
        for (GaMaterialRequest m : mrs) {
            mrArr.add(ErpJson.tree(GaOpsJson.map("id", m.getId(), "code", m.getCode(), "status", m.getStatus(),
                    "createdAt", m.getCreatedAt(), "_count", GaOpsJson.map("items", mrItemCounts.getOrDefault(m.getId(), 0L)))));
        }
        o.set("materialRequests", mrArr);
        o.set("totalCost", ErpJson.tree(GaOpsJson.num(totalCost(wo.getLaborCost(), wo.getExternalCost(), partsCost))));
        return o;
    }

    // ─────────────────────────── create & lifecycle ───────────────────────────

    record CreateInput(String assetId, String type, String title, String description, String component,
                       String priority, String scheduleId, String dueAt) {
    }

    static CreateInput parseCreate(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String assetId = GaZod.reqString(GaZod.field(b, "assetId"), 1, "assetId wajib diisi.", 0);
        String type = GaZod.enumDefault(GaZod.field(b, "type"), TYPES, "CORRECTIVE");
        String title = GaZod.reqString(GaZod.field(b, "title"), 1, "Judul wajib diisi.", 200);
        String description = GaZod.optString(GaZod.field(b, "description"), 0, null, 0);
        String component = GaZod.optString(GaZod.field(b, "component"), 0, null, 200);
        String priority = GaZod.enumDefault(GaZod.field(b, "priority"), PRIORITIES, "MEDIUM");
        String scheduleId = GaZod.optString(GaZod.field(b, "scheduleId"), 0, null, 0);
        String dueAt = GaZod.optString(GaZod.field(b, "dueAt"), 0, null, 0);
        return new CreateInput(assetId, type, title, description, component, priority, scheduleId, dueAt);
    }

    /**
     * Kode berikutnya {@code <prefix>-YYYY-NNNN}, diserialkan advisory lock per tahun. Urutan string
     * {@code code desc} dan {@code parseInt} meniru sumber (seq non-angka → mulai dari 1).
     */
    static String nextCode(EntityManager em, String entity, String prefixBase, long lockBase) {
        int year = ErpDates.utcYear(Instant.now());
        String prefix = prefixBase + "-" + year + "-";
        em.createNativeQuery("SELECT pg_advisory_xact_lock(:k)").setParameter("k", lockBase + (year - 2000))
                .getSingleResult();
        List<String> latest = em.createQuery("select x.code from " + entity + " x where x.code like :p "
                + "order by x.code desc", String.class).setParameter("p", ErpQuery.startsWith(prefix))
                .setMaxResults(1).getResultList();
        Integer last = latest.isEmpty() ? Integer.valueOf(0) : GaOpsJson.jsParseInt(latest.get(0).substring(prefix.length()));
        int next = last == null ? 1 : last + 1;
        return prefix + String.format("%04d", next);
    }

    /** POST /work-orders → 201 {@code { id, code }}. */
    @ErpTx
    public Map<String, Object> create(CreateInput in, String userId) {
        if (em.find(GaAsset.class, in.assetId()) == null) {
            throw ErpException.business("Aset tidak ditemukan.");
        }
        if (in.scheduleId() != null && em.find(GaMaintenanceSchedule.class, in.scheduleId()) == null) {
            throw ErpException.business("Jadwal maintenance tidak ditemukan.");
        }
        Instant dueAt = in.dueAt() == null || in.dueAt().isEmpty() ? null
                : ErpGaMaintenanceScheduleService.date(in.dueAt(), "Gagal membuat work order.");
        String code = nextCode(em, "GaWorkOrder", "WO", 0xa70000L);
        GaWorkOrder wo = new GaWorkOrder();
        wo.setCode(code);
        wo.setAssetId(in.assetId());
        wo.setType(GaWorkOrderType.valueOf(in.type()));
        wo.setStatus(GaWorkOrderStatus.OPEN);
        wo.setPriority(GaWorkOrderPriority.valueOf(in.priority()));
        wo.setTitle(in.title());
        wo.setDescription(in.description());
        wo.setComponent(in.component());
        wo.setReportedById(userId);
        wo.setReportedAt(Instant.now());
        wo.setScheduleId(in.scheduleId());
        wo.setDueAt(dueAt);
        em.persist(wo);
        em.flush();
        return GaOpsJson.map("id", wo.getId(), "code", wo.getCode());
    }

    private GaWorkOrder require(String id) {
        GaWorkOrder wo = em.find(GaWorkOrder.class, id);
        if (wo == null) {
            throw ErpException.business("Work order tidak ditemukan.");
        }
        return wo;
    }

    private static Map<String, Object> idStatus(GaWorkOrder wo) {
        return GaOpsJson.map("id", wo.getId(), "status", wo.getStatus());
    }

    record AssignInput(String assignedToId, String priority) {
    }

    static AssignInput parseAssign(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String assignedToId = GaZod.reqString(GaZod.field(b, "assignedToId"), 1, "Teknisi wajib dipilih.", 0);
        String priority = GaZod.optEnum(GaZod.field(b, "priority"), PRIORITIES);
        return new AssignInput(assignedToId, priority);
    }

    /** PATCH /work-orders/:id/assign — OPEN/ASSIGNED → ASSIGNED. */
    @ErpTx
    public Map<String, Object> assign(String id, AssignInput in, String userId) {
        GaWorkOrder wo = require(id);
        if (wo.getStatus() != GaWorkOrderStatus.OPEN && wo.getStatus() != GaWorkOrderStatus.ASSIGNED) {
            throw ErpException.business("Tidak bisa assign dari status " + wo.getStatus() + ".");
        }
        wo.setAssignedToId(in.assignedToId());
        wo.setAssignedAt(Instant.now());
        wo.setAssignedById(userId);
        wo.setStatus(GaWorkOrderStatus.ASSIGNED);
        if (in.priority() != null) {
            wo.setPriority(GaWorkOrderPriority.valueOf(in.priority()));
        }
        em.flush();
        return idStatus(wo);
    }

    /** PATCH /work-orders/:id/start — ASSIGNED → IN_PROGRESS (hanya teknisi yang ditugaskan). */
    @ErpTx
    public Map<String, Object> start(String id, String userId) {
        GaWorkOrder wo = require(id);
        if (!userId.equals(wo.getAssignedToId())) {
            throw ErpException.forbidden("Hanya teknisi yang ditugaskan yang bisa mulai.");
        }
        if (wo.getStatus() != GaWorkOrderStatus.ASSIGNED) {
            throw ErpException.business("Tidak bisa start dari status " + wo.getStatus() + ".");
        }
        wo.setStatus(GaWorkOrderStatus.IN_PROGRESS);
        wo.setStartedAt(Instant.now());
        em.flush();
        return idStatus(wo);
    }

    record CompleteInput(String resolution, ArrayNode checklist, List<String> missingRequired,
                         BigDecimal laborHours, BigDecimal laborCost, String externalVendor, BigDecimal externalCost) {
    }

    /** {@code CompleteWoSchema}; checklist dinormalisasi seperti keluaran Zod (default required/done=false). */
    static CompleteInput parseComplete(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String resolution = GaZod.optString(GaZod.field(b, "resolution"), 0, null, 0);
        List<JsonNode> items = GaZod.optArray(GaZod.field(b, "checklist"));
        ArrayNode checklist = null;
        List<String> missing = new ArrayList<>();
        if (items != null) {
            checklist = ErpJson.arrayNode();
            for (JsonNode el : items) {
                JsonNode e = GaZod.reqObject(el);
                String cid = GaZod.reqString(GaZod.field(e, "id"), 0, null, 0);
                String label = GaZod.reqString(GaZod.field(e, "label"), 0, null, 0);
                boolean required = GaZod.boolDefault(GaZod.field(e, "required"), false);
                boolean done = GaZod.boolDefault(GaZod.field(e, "done"), false);
                JsonNode notesRaw = GaZod.field(e, "notes");
                String notes = GaZod.optString(notesRaw, 0, null, 0);
                ObjectNode c = ErpJson.object();
                c.put("id", cid);
                c.put("label", label);
                c.put("required", required);
                c.put("done", done);
                if (!GaZod.absent(notesRaw)) {
                    c.put("notes", notes);
                }
                checklist.add(c);
                if (required && !done) {
                    missing.add(label);
                }
            }
        }
        BigDecimal laborHours = GaZod.optNumber(GaZod.field(b, "laborHours"), GaZod.Sign.NONNEGATIVE, null);
        BigDecimal laborCost = GaZod.optNumber(GaZod.field(b, "laborCost"), GaZod.Sign.NONNEGATIVE, null);
        String vendor = GaZod.optString(GaZod.field(b, "externalVendor"), 0, null, 0);
        BigDecimal externalCost = GaZod.optNumber(GaZod.field(b, "externalCost"), GaZod.Sign.NONNEGATIVE, null);
        return new CompleteInput(resolution, checklist, missing, laborHours, laborCost, vendor, externalCost);
    }

    /** Jatuh tempo berikutnya jadwal preventif setelah selesai (interval km saja → tidak bergeser). */
    static Instant nextDue(Instant current, Integer intervalDays, Integer intervalHours) {
        if (intervalDays != null && intervalDays != 0) {
            return current.plus(Duration.ofDays(intervalDays));
        }
        if (intervalHours != null && intervalHours != 0) {
            return current.plus(Duration.ofDays((long) Math.ceil(intervalHours / 24.0)));
        }
        return current;
    }

    /** PATCH /work-orders/:id/complete — ASSIGNED/IN_PROGRESS → DONE (hanya teknisi yang ditugaskan). */
    @ErpTx
    public Map<String, Object> complete(String id, CompleteInput in, String userId) {
        GaWorkOrder wo = require(id);
        if (!userId.equals(wo.getAssignedToId())) {
            throw ErpException.forbidden("Hanya teknisi yang ditugaskan yang bisa tandai selesai.");
        }
        if (wo.getStatus() != GaWorkOrderStatus.ASSIGNED && wo.getStatus() != GaWorkOrderStatus.IN_PROGRESS) {
            throw ErpException.business("Tidak bisa complete dari status " + wo.getStatus() + ".");
        }
        if (in.checklist() != null && !in.missingRequired().isEmpty()) {
            throw ErpException.business("Checklist wajib belum lengkap: " + String.join(", ", in.missingRequired()));
        }
        Instant completedAt = Instant.now();
        boolean wasLate = wo.getDueAt() != null && completedAt.isAfter(wo.getDueAt());
        wo.setStatus(GaWorkOrderStatus.DONE);
        wo.setCompletedAt(completedAt);
        wo.setCompletedById(userId);
        wo.setResolution(in.resolution());
        if (in.checklist() != null) {
            wo.setChecklist(in.checklist());
        }
        wo.setLaborHours(in.laborHours());
        wo.setLaborCost(in.laborCost());
        wo.setExternalVendor(in.externalVendor());
        wo.setExternalCost(in.externalCost());
        wo.setWasLate(wasLate);

        // Gulirkan jadwal preventif yang melahirkan WO ini.
        if (wo.getScheduleId() != null) {
            GaMaintenanceSchedule s = em.find(GaMaintenanceSchedule.class, wo.getScheduleId());
            if (s != null) {
                s.setLastDoneAt(completedAt);
                s.setNextDueAt(nextDue(completedAt, s.getIntervalDays(), s.getIntervalHours()));
                s.setUpdatedAt(Instant.now());
            }
        }
        em.flush();
        return idStatus(wo);
    }

    /**
     * Data log servis dari WO yang ditutup — port {@code services/gaWorkOrderServiceLog.ts}. Bila WO berasal
     * dari permintaan servis kendaraan, odometer & jenis servisnya ikut, dan vendor luar menjadi bengkel.
     */
    static GaVehicleServiceLog serviceLogFrom(GaWorkOrder wo, GaVehicleServiceRequest req, Instant closedAt, String userId) {
        BigDecimal total = (wo.getLaborCost() == null ? BigDecimal.ZERO : wo.getLaborCost())
                .add(wo.getExternalCost() == null ? BigDecimal.ZERO : wo.getExternalCost());
        GaVehicleServiceLog log = new GaVehicleServiceLog();
        log.setAssetId(wo.getAssetId());
        log.setTanggal(closedAt);
        log.setOdometer(req == null ? null : req.getOdometer());
        log.setJenisService(req == null || req.getJenisService() == null ? GaVehicleServiceType.LAINNYA : req.getJenisService());
        String deskripsi = wo.getTitle() != null && !wo.getTitle().isEmpty() ? wo.getTitle()
                : wo.getDescription() != null && !wo.getDescription().isEmpty() ? wo.getDescription()
                : "Work Order (auto dari close WO)";
        log.setDeskripsi(deskripsi);
        String vendor = wo.getExternalVendor() == null ? null : wo.getExternalVendor().strip();
        log.setBengkel(vendor == null || vendor.isEmpty() ? null : vendor);
        log.setBiaya(total.signum() > 0 ? total : null);
        log.setWorkOrderId(wo.getId());
        log.setSource("WORK_ORDER");
        log.setCreatedById(userId);
        return log;
    }

    /**
     * PATCH /work-orders/:id/close — DONE → CLOSED. Biaya WO (tenaga kerja + vendor) dicerminkan ke
     * GaVehicleServiceLog, satu per workOrderId, agar total biaya kendaraan ikut menghitungnya; log tetap
     * dibuat walau biaya null (sebagai riwayat servis).
     */
    @ErpTx
    public Map<String, Object> close(String id, String userId) {
        GaWorkOrder wo = require(id);
        if (wo.getStatus() != GaWorkOrderStatus.DONE) {
            throw ErpException.business("Tidak bisa close dari status " + wo.getStatus() + ".");
        }
        List<GaVehicleServiceRequest> reqs = em.createQuery("select r from GaVehicleServiceRequest r "
                + "where r.workOrderId = :id", GaVehicleServiceRequest.class).setParameter("id", wo.getId())
                .setMaxResults(1).getResultList();
        GaVehicleServiceRequest serviceReq = reqs.isEmpty() ? null : reqs.get(0);

        Instant closedAt = Instant.now();
        wo.setStatus(GaWorkOrderStatus.CLOSED);
        wo.setClosedAt(closedAt);
        wo.setClosedById(userId);
        em.flush();

        Long existing = em.createQuery("select count(l) from GaVehicleServiceLog l where l.workOrderId = :id", Long.class)
                .setParameter("id", wo.getId()).getSingleResult();
        if (existing == 0) {
            GaVehicleServiceLog log = serviceLogFrom(wo, serviceReq, closedAt, userId);
            em.persist(log);
            em.flush();
            if (serviceReq != null) {
                GaVehicleOdometer.bump(em, wo.getAssetId(), serviceReq.getOdometer());
                if (serviceReq.getServiceLogId() == null) {
                    serviceReq.setServiceLogId(log.getId());
                    serviceReq.setUpdatedAt(Instant.now());
                    em.flush();
                }
            }
        }
        return idStatus(wo);
    }

    static String parseCancelReason(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        return GaZod.reqString(GaZod.field(b, "reason"), 1, "Alasan wajib diisi.", 500);
    }

    /** PATCH /work-orders/:id/cancel — status non-terminal → CANCELLED. */
    @ErpTx
    public Map<String, Object> cancel(String id, String reason, String userId) {
        GaWorkOrder wo = require(id);
        GaWorkOrderStatus st = wo.getStatus();
        if (st == GaWorkOrderStatus.DONE || st == GaWorkOrderStatus.CLOSED || st == GaWorkOrderStatus.CANCELLED) {
            throw ErpException.business("Tidak bisa cancel dari status " + st + ".");
        }
        wo.setStatus(GaWorkOrderStatus.CANCELLED);
        wo.setCancelledAt(Instant.now());
        wo.setCancelledById(userId);
        wo.setCancelReason(reason);
        em.flush();
        return idStatus(wo);
    }

    // ─────────────────────────── foto & part ───────────────────────────

    /** POST /work-orders/:id/photos — foto BEFORE/AFTER (maks 6), hanya pelapor atau teknisi. */
    @ErpTx
    public ObjectNode addPhoto(String id, String type, String caption, byte[] bytes, String originalName,
                               String mimeType, String userId) {
        GaWorkOrder wo = em.find(GaWorkOrder.class, id);
        if (wo == null) {
            throw ErpException.notFound("Work order tidak ditemukan.");
        }
        long photos = em.createQuery("select count(p) from GaWorkOrderPhoto p where p.workOrderId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (photos >= 6) {
            throw ErpException.business("Maksimal 6 foto per work order.");
        }
        if (!userId.equals(wo.getReportedById()) && !userId.equals(wo.getAssignedToId())) {
            throw ErpException.forbidden("Hanya pelapor atau teknisi yang bisa upload foto.");
        }
        ErpFileStorage.SaveResult saved = storage.savePublic(bytes, originalName, mimeType, "ga/workorders");
        GaWorkOrderPhoto photo = new GaWorkOrderPhoto();
        photo.setWorkOrderId(wo.getId());
        photo.setFilePath(saved.filePath());
        photo.setType(GaWorkOrderPhotoType.valueOf(type));
        photo.setCaption(caption == null || caption.isEmpty() ? null : caption);
        em.persist(photo);
        em.flush();
        return GaOpsJson.scalars(photo);
    }

    record PartInput(String inventoryItemId, String fromLocationId, BigDecimal quantity, BigDecimal unitCost) {
    }

    static PartInput parsePart(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String itemId = GaZod.reqString(GaZod.field(b, "inventoryItemId"), 1, "Item wajib dipilih.", 0);
        String from = GaZod.optString(GaZod.field(b, "fromLocationId"), 0, null, 0);
        BigDecimal qty = GaZod.reqNumber(GaZod.field(b, "quantity"), GaZod.Sign.POSITIVE, "Qty harus lebih dari 0.");
        BigDecimal unitCost = GaZod.reqNumber(GaZod.field(b, "unitCost"), GaZod.Sign.NONNEGATIVE, null);
        return new PartInput(itemId, from, qty, unitCost);
    }

    /**
     * POST /work-orders/:id/parts — pakai stok GA di dalam transaksi: movement OUT, pengurangan atomik
     * stok per lokasi (boleh minus, dengan peringatan), lalu tautkan movement ke part baru.
     */
    @ErpTx
    public Map<String, Object> addPart(String id, PartInput in, String userId) {
        GaWorkOrder wo = require(id);
        if (wo.getStatus() == GaWorkOrderStatus.CLOSED || wo.getStatus() == GaWorkOrderStatus.CANCELLED) {
            throw ErpException.business("Tidak bisa menambah part pada WO berstatus " + wo.getStatus() + ".");
        }
        if (em.find(GaInventoryItem.class, in.inventoryItemId()) == null) {
            throw ErpException.business("Item inventory tidak ditemukan.");
        }
        String movementId = null;
        String warning = null;
        if (in.fromLocationId() != null && !in.fromLocationId().isEmpty()) {
            BigDecimal newQty;
            try {
                newQty = GaStock.adjust(em, in.inventoryItemId(), in.fromLocationId(), in.quantity().negate());
            } catch (PersistenceException e) {
                // $queryRaw gagal (mis. lokasi tak dikenal) = P2010 → handleRouteError 500.
                throw ErpException.of(500, "Gagal mencatat pemakaian part.");
            }
            if (newQty.signum() < 0) {
                warning = "Stok tidak cukup untuk item " + in.inventoryItemId() + " (sisa " + GaOpsJson.numText(newQty)
                        + "). Tetap diizinkan (stok negatif).";
                log.warn("WO part consumed below zero stock: workOrderId={} itemId={} newQty={}", wo.getId(),
                        in.inventoryItemId(), GaOpsJson.numText(newQty));
            }
            GaInventoryMovement m = new GaInventoryMovement();
            m.setItemId(in.inventoryItemId());
            m.setType(GaMovementType.OUT);
            m.setQuantity(in.quantity());
            m.setFromLocationId(in.fromLocationId());
            m.setReferenceNo("MAINTENANCE_" + wo.getCode());
            m.setNotes("Pemakaian untuk WO " + wo.getCode());
            m.setCreatedBy(userId);
            em.persist(m);
            em.flush();
            movementId = m.getId();
        }
        GaWorkOrderPart part = new GaWorkOrderPart();
        part.setWorkOrderId(wo.getId());
        part.setInventoryItemId(in.inventoryItemId());
        part.setFromLocationId(in.fromLocationId()); // sumber: `?? null` ("" tetap "" → FK ditolak)
        part.setQuantity(in.quantity());
        part.setUnitCost(in.unitCost());
        part.setInventoryMovementId(movementId);
        em.persist(part);
        em.flush();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", part.getId());
        out.put("warning", warning);
        return out;
    }
}
