package id.enterpriseapps.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.accounting.ErpDocumentNumberService;
import id.enterpriseapps.erp.core.approval.ErpApprovalGateService;
import id.enterpriseapps.erp.core.security.ErpActor;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import id.enterpriseapps.erp.entity.ProductionRunDraft;
import id.enterpriseapps.erp.production.ErpProductionRunPostingSupport.PostedRun;
import id.enterpriseapps.erp.production.ProductionRequests.InputLine;
import id.enterpriseapps.erp.production.ProductionRequests.OutputLine;
import id.enterpriseapps.erp.production.ProductionRequests.ProductionRunRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draft Data Produksi — port {@code services/productionRunDraft.ts} + bagian data
 * {@code routes/inventory/productionRunDrafts.ts}.
 *
 * <p>Input produksi TIDAK langsung diposting: disimpan dulu sebagai draft (production_run_drafts)
 * tanpa efek stok/GL/HPP/COGS/OEE. Posting = aksi eksplisit — langsung untuk role
 * {@code approval.bypass}, lewat antrian approval untuk role lain. Perubahan status memakai klaim
 * atomik (baris dikunci {@code FOR UPDATE} + guard status) agar tidak diproses dua kali.
 */
@Service
@RequiredArgsConstructor
public class ErpProductionRunDraftService {

    public static final List<String> DRAFT_STATUSES = List.of("Draft", "PendingApproval", "Posted", "Discarded");
    static final String NOT_FOUND = "Draft produksi tidak ditemukan.";
    static final String NOT_POSTABLE = "Draft sudah diproses atau tidak lagi berstatus Draft.";
    static final String DOCUMENT_TYPE = "ProductionRun";

    private final ErpProductionBodyParser parser;
    private final ErpDocumentNumberService numbers;
    private final ErpApprovalGateService approvalGate;
    private final ErpProductionRunSubmitService submitter;
    private final ErpProductionRunPostingSupport support;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── baca ─────────────────────────────

    /**
     * GET / — {@code { data, total, page, limit, counts }}; Discarded tidak ditampilkan. Daftar tidak
     * membawa payload penuh (besar), ambil via GET /:id. {@code counts} = jumlah per status (tanpa filter).
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        String status = ListQuery.str(query, "status");
        if (status != null && !DRAFT_STATUSES.contains(status)) {
            throw ErpException.business("Status draft tidak dikenal.");
        }
        String startDate = ListQuery.dateParam(query, "startDate", "Format tanggal harus YYYY-MM-DD.");
        String endDate = ListQuery.dateParam(query, "endDate", "Format tanggal harus YYYY-MM-DD.");

        ErpQuery<ProductionRunDraft> q = ErpQuery.of(em, ProductionRunDraft.class, "d");
        if (status != null) {
            q.where("d.status = :st", "st", status);
        } else {
            q.where("d.status <> 'Discarded'");
        }
        if (startDate != null || endDate != null) {
            Instant[] range = ErpDates.localDayRange(startDate, endDate);
            q.whereIf(range[0] != null, "d.date >= :gte", "gte", range[0]);
            q.whereIf(range[1] != null, "d.date < :lt", "lt", range[1]);
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            q.where("(lower(d.draftNumber) like :s or lower(d.notes) like :s or lower(d.postedRunNumber) like :s)",
                    "s", ErpQuery.contains(search));
        }
        q.orderBy("d.date desc, d.createdAt desc");
        long total = q.count();
        List<ProductionRunDraft> rows = q.list(paging.skip(), paging.limit());

        Map<String, Object> counts = new LinkedHashMap<>();
        for (Object[] g : em.createQuery("select d.status, count(d) from ProductionRunDraft d where d.status <> 'Discarded' "
                + "group by d.status", Object[].class).getResultList()) {
            counts.put((String) g[0], ((Number) g[1]).longValue());
        }
        List<Map<String, Object>> data = withCreators(rows.stream().map(d -> ProdJson.scalarsExcept(d, "payload")).toList());
        Map<String, Object> out = ListQuery.result(data, total, paging);
        out.put("counts", counts);
        return out;
    }

    /** GET /:id — draft lengkap (payload untuk mengisi modal) + createdBy. */
    @ErpTx(readOnly = true)
    public Map<String, Object> get(String id) {
        ProductionRunDraft d = em.find(ProductionRunDraft.class, id);
        if (d == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        return withCreators(List.of(ProdJson.scalars(d))).get(0);
    }

    /** Ringkasan untuk cek akses rute: pembuat + payload; null bila draft tidak ada. */
    @ErpTx(readOnly = true)
    public DraftRef ref(String id) {
        ProductionRunDraft d = em.find(ProductionRunDraft.class, id);
        return d == null ? null : new DraftRef(d.getCreatedById(), d.getPayload());
    }

    public record DraftRef(String createdById, JsonNode payload) {
    }

    /** {@code createdBy: { id, fullName }} (fullName null bila pengguna tidak ada). */
    private List<Map<String, Object>> withCreators(List<Map<String, Object>> rows) {
        Set<String> ids = new HashSet<>();
        rows.forEach(r -> ids.add((String) r.get("createdById")));
        Map<String, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] u : em.createQuery("select u.id, u.fullName from User u where u.id in :ids", Object[].class)
                    .setParameter("ids", ids).getResultList()) {
                names.put((String) u[0], (String) u[1]);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            Map<String, Object> creator = new LinkedHashMap<>();
            creator.put("id", r.get("createdById"));
            creator.put("fullName", names.get((String) r.get("createdById")));
            m.put("createdBy", creator);
            out.add(m);
        }
        return out;
    }

    // ───────────────────────────── ringkasan & peringatan ─────────────────────────────

    private record StockInfo(String name, String unit, BigDecimal currentStock) {
    }

    private Map<String, StockInfo> loadItemInfo(ProductionRunRequest body) {
        Set<String> ids = new HashSet<>();
        body.inputs().forEach(i -> ids.add(i.itemId()));
        body.outputs().forEach(o -> ids.add(o.itemId()));
        Map<String, StockInfo> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Object[] r : em.createQuery("select i.id, i.name, i.unit, i.currentStock from InventoryItem i where i.id in :ids",
                Object[].class).setParameter("ids", ids).getResultList()) {
            map.put((String) r[0], new StockInfo((String) r[1], (String) r[2], (BigDecimal) r[3]));
        }
        return map;
    }

    /** Ringkasan untuk daftar (port {@code summarizeDraftPayload}). */
    static Map<String, Object> summarize(ProductionRunRequest body, Map<String, String> names) {
        BigDecimal inputQty = BigDecimal.ZERO;
        for (InputLine i : body.inputs()) {
            inputQty = inputQty.add(i.quantity() == null ? BigDecimal.ZERO : i.quantity());
        }
        BigDecimal outputQty = BigDecimal.ZERO;
        for (OutputLine o : body.outputs()) {
            outputQty = outputQty.add(o.quantity() == null ? BigDecimal.ZERO : o.quantity());
        }
        OutputLine main = body.outputs().stream().filter(o -> !o.byProduct()).findFirst()
                .orElse(body.outputs().isEmpty() ? null : body.outputs().get(0));
        Set<String> inputNames = new LinkedHashSet<>();
        for (InputLine i : body.inputs()) {
            String n = names.get(i.itemId());
            if (n != null && !n.isEmpty()) {
                inputNames.add(n);
            }
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("inputQty", ProdJson.plain(inputQty));
        s.put("outputQty", ProdJson.plain(outputQty));
        s.put("inputCount", body.inputs().size());
        s.put("outputCount", body.outputs().size());
        s.put("mainOutput", main == null ? null : names.get(main.itemId()));
        s.put("inputNames", new ArrayList<>(inputNames));
        return s;
    }

    /** Peringatan non-blocking: stok input saat ini kurang (dicek ulang saat posting), dijumlah per item. */
    private static List<String> stockWarnings(List<InputLine> inputs, Map<String, StockInfo> stock) {
        Map<String, BigDecimal> need = new LinkedHashMap<>();
        for (InputLine i : inputs) {
            need.merge(i.itemId(), i.quantity(), BigDecimal::add);
        }
        List<String> out = new ArrayList<>();
        need.forEach((itemId, qty) -> {
            StockInfo s = stock.get(itemId);
            if (s != null && s.currentStock().compareTo(qty) < 0) {
                out.add("Stok '" + s.name() + "' saat ini " + ProdJson.idNum(s.currentStock(), 3) + " " + s.unit()
                        + ", dibutuhkan " + ProdJson.idNum(qty, 3) + " " + s.unit() + ". Dicek ulang saat posting.");
            }
        });
        return out;
    }

    private record Described(Map<String, Object> summary, List<String> warnings) {
    }

    private Described describe(ProductionRunRequest body) {
        Map<String, StockInfo> info = loadItemInfo(body);
        Map<String, String> names = new HashMap<>();
        info.forEach((k, v) -> names.put(k, v.name()));
        Map<String, Object> summary = summarize(body, names);
        List<String> warnings = body.negativeStock() ? List.of() : stockWarnings(body.inputs(), info);
        return new Described(summary, warnings);
    }

    // ───────────────────────────── tulis ─────────────────────────────

    /** POST /production-runs — selalu draft (tanpa efek stok/GL/HPP/OEE). Body mentah disimpan apa adanya. */
    @ErpTx
    public Map<String, Object> createDraft(ProductionRunRequest body, JsonNode rawBody, String userId) {
        Described desc = describe(body);
        ProductionRunDraft d = new ProductionRunDraft();
        d.setDraftNumber(numbers.generate("DPR", Instant.now(), ""));
        d.setDate(ErpDates.toInstant(body.date()));
        d.setNotes(body.notes());
        d.setWarehouseId(body.warehouseId());
        d.setSummary(ErpJson.tree(desc.summary()));
        d.setPayload(rawBody == null ? ErpJson.object() : rawBody.deepCopy());
        d.setStatus("Draft");
        d.setCreatedById(userId);
        em.persist(d);
        em.flush();
        return result(d, desc.warnings());
    }

    /** PUT /drafts/:id — hanya selagi status Draft (klaim atomik). */
    @ErpTx
    public Map<String, Object> updateDraft(String id, ProductionRunRequest body, JsonNode rawBody, String userId) {
        Described desc = describe(body);
        ProductionRunDraft d = em.find(ProductionRunDraft.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (d == null || !"Draft".equals(d.getStatus())) {
            throw ErpException.business("Draft hanya bisa diubah selama berstatus Draft.");
        }
        d.setDate(ErpDates.toInstant(body.date()));
        d.setNotes(body.notes());
        d.setWarehouseId(body.warehouseId());
        d.setSummary(ErpJson.tree(desc.summary()));
        d.setPayload(rawBody == null ? ErpJson.object() : rawBody.deepCopy());
        d.setUpdatedById(userId);
        d.setLastError(null);
        em.flush();
        return result(d, desc.warnings());
    }

    private static Map<String, Object> result(ProductionRunDraft d, List<String> warnings) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("draft", ProdJson.scalars(d));
        out.put("warnings", warnings);
        return out;
    }

    /** DELETE /drafts/:id — buang (soft: Discarded), hanya selagi Draft. */
    @ErpTx
    public void discardDraft(String id, String userId) {
        ProductionRunDraft d = em.find(ProductionRunDraft.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (d == null || !"Draft".equals(d.getStatus())) {
            throw ErpException.business("Hanya draft berstatus Draft yang bisa dihapus.");
        }
        d.setStatus("Discarded");
        d.setUpdatedById(userId);
        em.flush();
    }

    /** Validasi ulang payload draft (CreateProductionRunSchema). */
    ProductionRunRequest parsePayload(JsonNode payload) {
        try {
            return parser.parseProductionRun(payload);
        } catch (ErpException e) {
            throw ErpException.business("Payload draft tidak valid: " + (e.getMessage() == null ? "" : e.getMessage()));
        }
    }

    /** Klaim Draft → status tujuan; gagal = sudah diklaim proses lain. Baris sudah dikunci pemanggil. */
    private static void claim(ProductionRunDraft d, String toStatus, String userId) {
        if (!"Draft".equals(d.getStatus())) {
            throw ErpException.business(NOT_POSTABLE);
        }
        d.setStatus(toStatus);
        d.setLastError(null);
        d.setUpdatedById(userId);
        if ("Posted".equals(toStatus)) {
            d.setPostedById(userId);
            d.setPostedAt(Instant.now());
        }
    }

    /** Role bypass: klaim + submit dalam transaksi yang sama (atas nama pembuat draft). Respons = run + items. */
    @ErpTx(timeout = 30)
    public Map<String, Object> postDraftDirect(String id, String userId) {
        ProductionRunDraft d = em.find(ProductionRunDraft.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (d == null) {
            throw ErpException.business("Draft tidak ditemukan.");
        }
        claim(d, "Posted", userId);
        em.flush();
        PostedRun run = submitter.submit(parsePayload(d.getPayload()), d.getCreatedById());
        d.setPostedRunId(run.id());
        d.setPostedRunNumber(run.runNumber());
        em.flush();
        return support.runWithItems(run.id());
    }

    /**
     * Role non-bypass: klaim + antrekan approval (efek dokumen terjadi saat approve) lalu notifikasi
     * admin. Respons 202 {@code { pendingApproval, request, message }}.
     */
    @ErpTx
    public Map<String, Object> postDraftForApproval(String id, ErpActor actor) {
        ProductionRunDraft d = em.find(ProductionRunDraft.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (d == null) {
            throw ErpException.business("Draft tidak ditemukan.");
        }
        ProductionRunRequest body = parsePayload(d.getPayload());
        claim(d, "PendingApproval", actor.userId());
        BigDecimal amount = BigDecimal.ZERO;
        for (InputLine i : body.inputs()) {
            amount = amount.add(i.quantity() == null ? BigDecimal.ZERO : i.quantity());
        }
        ApprovalRequest request = approvalGate.queueApproval(DOCUMENT_TYPE, d.getPayload(), amount, d.getCreatedById(),
                "Data Produksi " + d.getDraftNumber());
        d.setApprovalRequestId(request.getId());
        em.flush();
        approvalGate.notifyPending(request, actor, ProdJson.idNum(amount) + " kg input");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pendingApproval", true);
        out.put("request", ProdJson.scalars(request));
        out.put("message", "Data produksi menunggu persetujuan manajer sebelum diproses.");
        return out;
    }

    /** Di dalam transaksi approve: draft yang tertaut → Posted. No-op untuk antrian lama tanpa draft. */
    @ErpTx
    public int markDraftPostedByApproval(String requestId, PostedRun run, String approverId) {
        Instant now = Instant.now();
        return em.createQuery("update ProductionRunDraft d set d.status = 'Posted', d.postedRunId = :rid, "
                        + "d.postedRunNumber = :rn, d.postedAt = :now, d.postedById = :by, d.lastError = null, d.updatedAt = :now "
                        + "where d.approvalRequestId = :req and d.status = 'PendingApproval'")
                .setParameter("rid", run.id()).setParameter("rn", run.runNumber()).setParameter("now", now)
                .setParameter("by", approverId).setParameter("req", requestId).executeUpdate();
    }

    /** Draft PendingApproval yang tertaut ke pengajuan ini masih ada? (sebelum keputusan tolak/revisi). */
    @ErpTx(readOnly = true)
    public boolean hasPendingDraft(String requestId) {
        return em.createQuery("select count(d) from ProductionRunDraft d where d.approvalRequestId = :req "
                        + "and d.status = 'PendingApproval'", Long.class)
                .setParameter("req", requestId).getSingleResult() > 0;
    }

    /** Tolak / minta revisi: draft kembali ke Draft + catatan; false bila tidak ada draft tertaut. */
    @ErpTx
    public boolean returnDraftToEditable(String requestId, String note, String label) {
        int n = em.createQuery("update ProductionRunDraft d set d.status = 'Draft', d.approvalRequestId = null, "
                        + "d.lastError = :err, d.updatedAt = :now where d.approvalRequestId = :req and d.status = 'PendingApproval'")
                .setParameter("err", label + ": " + (note == null || note.isEmpty() ? "-" : note))
                .setParameter("now", Instant.now()).setParameter("req", requestId).executeUpdate();
        return n > 0;
    }

    /** Simpan alasan gagal posting (best-effort, hanya selagi Draft) — transaksi terpisah setelah rollback. */
    @ErpTx
    public void recordDraftError(String id, String message) {
        em.createQuery("update ProductionRunDraft d set d.lastError = :m, d.updatedAt = :now where d.id = :id and d.status = 'Draft'")
                .setParameter("m", message).setParameter("now", Instant.now()).setParameter("id", id).executeUpdate();
    }
}
