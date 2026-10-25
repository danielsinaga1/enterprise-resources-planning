package id.neravoxa.erp.eam.asset;

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
import id.neravoxa.erp.entity.GaDocument;
import id.neravoxa.erp.entity.GaDocumentStatus;
import id.neravoxa.erp.entity.GaDocumentVersion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dokumen legal & sertifikat GA dengan versi PDF dan pelacakan masa berlaku — port
 * {@code routes/ga/documents.ts}. Versi baru = max(versionNo) + 1 per dokumen (unik documentId+versionNo).
 * Berkas disimpan PRIVAT ({@link ErpFileStorage#save}) dan hanya diunduh lewat endpoint ber-izin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpGaDocumentService {

    static final String[] STATUSES = {"ACTIVE", "EXPIRED", "REVOKED", "ARCHIVED"};
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private final ErpFileStorage storage;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Body {@code DocumentSchema} tervalidasi; {@code z} membedakan absen vs null. */
    record DocumentInput(GaZod z, String code, String title, String typeId, String assetId, String issuedBy,
                         String issuedNumber, String effectiveDate, String expiryDate, String status,
                         JsonNode customFields) {
    }

    static DocumentInput parse(JsonNode body, boolean partial) {
        GaZod z = GaZod.of(body, partial);
        String code = z.codeUnion("code", 100);
        String title = z.requiredString("title", 1, null, 200);
        String typeId = z.requiredString("typeId", 1, null, null);
        String assetId = z.optString("assetId", null);
        String issuedBy = z.optString("issuedBy", null);
        String issuedNumber = z.optString("issuedNumber", null);
        String effectiveDate = z.optString("effectiveDate", null);
        String expiryDate = z.optString("expiryDate", null);
        String status = z.enumValue("status", "ACTIVE", false, false, STATUSES);
        JsonNode customFields = z.record("customFields");
        z.throwIfInvalid();
        return new DocumentInput(z, code, title, typeId, assetId, issuedBy, issuedNumber, effectiveDate, expiryDate,
                status, customFields);
    }

    private static Instant date(String s) {
        return s == null || s.isEmpty() ? null : ErpDates.toInstant(s);
    }

    // ───────────────────────────── JSON ─────────────────────────────

    /** Baris daftar: type {code,name,category}, asset {id,code,name}, versi terbaru. */
    private ArrayNode listRows(List<GaDocument> docs, Instant now, boolean withExpiryState) {
        List<String> ids = docs.stream().map(GaDocument::getId).toList();
        Map<String, GaDocumentVersion> latest = new HashMap<>();
        if (!ids.isEmpty()) {
            for (GaDocumentVersion v : em.createQuery("select v from GaDocumentVersion v where v.documentId in :ids "
                            + "and v.versionNo = (select max(v2.versionNo) from GaDocumentVersion v2 "
                            + "where v2.documentId = v.documentId)", GaDocumentVersion.class)
                    .setParameter("ids", ids).getResultList()) {
                latest.put(v.getDocumentId(), v);
            }
        }
        Instant[] window = GaDocumentList.expiryWindow(now);
        ArrayNode out = ErpJson.arrayNode();
        for (GaDocument d : docs) {
            ObjectNode o = GaJs.scalars(d);
            o.set("type", GaJs.pick(d.getType(), "code", "name", "category"));
            o.set("asset", GaJs.pick(d.getAsset(), "id", "code", "name"));
            ArrayNode versions = ErpJson.arrayNode();
            if (latest.containsKey(d.getId())) {
                versions.add(GaJs.scalars(latest.get(d.getId())));
            }
            o.set("versions", versions);
            o.set("daysToExpiry", ErpJson.tree(GaDocumentList.daysToExpiry(d.getExpiryDate(), now)));
            if (withExpiryState) {
                o.set("expiryState", ErpJson.tree(GaDocumentList.deriveExpiryState(d.getStatus(), d.getExpiryDate(), window)));
            }
            out.add(o);
        }
        return out;
    }

    private ErpQuery<GaDocument> baseQuery() {
        return ErpQuery.of(em, GaDocument.class, "d")
                .join("left join d.asset sa")
                .fetch("left join fetch d.type", "left join fetch d.asset");
    }

    // ───────────────────────────── GET / ─────────────────────────────

    /**
     * Tanpa {@code page}: array (drawer aset & alat AIRA bergantung padanya). Dengan {@code page}:
     * {@code { data, total, page, limit }} (limit ≤ 200) plus {@code expiryState}.
     */
    @ErpTx(readOnly = true)
    public Object list(Map<String, String> query) {
        Instant now = Instant.now();
        if (query.containsKey("page")) {
            return listPaged(query, now);
        }
        ErpQuery<GaDocument> q = baseQuery();
        String typeId = query.get("typeId");
        if (GaJs.truthy(typeId)) {
            q.where("d.typeId = :typeId", "typeId", typeId);
        }
        String assetId = query.get("assetId");
        if (GaJs.truthy(assetId)) {
            q.where("d.assetId = :assetId", "assetId", assetId);
        }
        String rawExpiring = query.containsKey("expiringDays") ? query.get("expiringDays") : query.get("expiringSoon");
        boolean expiring = query.containsKey("expiringDays") || query.containsKey("expiringSoon");
        String status = query.get("status");
        if (GaJs.truthy(status) && !expiring) {
            q.where("d.status = :status", "status", statusOrFail(status));
        }
        if (GaJs.truthy(query.get("q"))) {
            q.where("(lower(d.code) like :q or lower(d.title) like :q or lower(d.issuedNumber) like :q)",
                    "q", ErpQuery.contains(query.get("q")));
        }
        if (expiring) {
            // expiring-soon: dokumen ACTIVE yang kedaluwarsa dalam N hari ke depan (default 60).
            double days = rawExpiring == null ? Double.NaN : GaZod.jsNumber(rawExpiring);
            double window = !Double.isNaN(days) && !Double.isInfinite(days) && days > 0 ? days : 60;
            Instant cutoff = now.plusMillis((long) (window * DAY_MS));
            q.where("d.status = :expStatus and d.expiryDate >= :expNow and d.expiryDate <= :expCut",
                    "expStatus", GaDocumentStatus.ACTIVE, "expNow", now, "expCut", cutoff);
        }
        q.orderBy("d.expiryDate asc");
        return listRows(q.list(), now, false);
    }

    private static GaDocumentStatus statusOrFail(String raw) {
        try {
            return GaDocumentStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw ErpException.of(500, "Gagal mengambil daftar dokumen.");
        }
    }

    private Map<String, Object> listPaged(Map<String, String> query, Instant now) {
        ErpQuery<GaDocument> q = GaDocumentList.buildWhere(baseQuery(), query, now);
        ListQuery.Paging paging = ListQuery.paging(query, 25, 200);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), GaDocumentList.SORT_FIELDS, GaDocumentList.DEFAULT_SORT);
        q.orderBy(GaDocumentList.orderBy(sort));
        List<GaDocument> docs = q.list(paging.skip(), paging.limit());
        long total = q.count();
        List<JsonNode> data = new ArrayList<>();
        listRows(docs, now, true).forEach(data::add);
        return ListQuery.result(data, total, paging);
    }

    // ───────────────────────────── GET /summary ─────────────────────────────

    /** KPI (kedaluwarsa / segera ≤30 hari / berlaku) untuk filter yang sama dengan daftar berpaginasi. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        Instant now = Instant.now();
        Instant[] window = GaDocumentList.expiryWindow(now);
        long count = GaDocumentList.buildWhere(countQuery(), query, now).count();
        ErpQuery<GaDocument> expired = GaDocumentList.buildWhere(countQuery(), query, now);
        GaDocumentList.expiryWhere(expired, "expired", window, "S");
        ErpQuery<GaDocument> expiring = GaDocumentList.buildWhere(countQuery(), query, now);
        GaDocumentList.expiryWhere(expiring, "expiring", window, "S");
        ErpQuery<GaDocument> valid = GaDocumentList.buildWhere(countQuery(), query, now);
        GaDocumentList.expiryWhere(valid, "valid", window, "S");
        return ErpJson.map("count", count, "expiredCount", expired.count(), "expiringCount", expiring.count(),
                "validCount", valid.count());
    }

    private ErpQuery<GaDocument> countQuery() {
        return ErpQuery.of(em, GaDocument.class, "d").join("left join d.asset sa");
    }

    // ───────────────────────────── GET /:id ─────────────────────────────

    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<GaDocument> rows = em.createQuery("select d from GaDocument d left join fetch d.type "
                        + "left join fetch d.asset where d.id = :id", GaDocument.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("Dokumen tidak ditemukan.");
        }
        GaDocument d = rows.get(0);
        ObjectNode o = GaJs.scalars(d);
        o.set("type", GaJs.orNull(GaJs.scalars(d.getType())));
        o.set("asset", GaJs.pick(d.getAsset(), "id", "code", "name"));
        ArrayNode versions = ErpJson.arrayNode();
        em.createQuery("select v from GaDocumentVersion v where v.documentId = :id order by v.versionNo desc",
                        GaDocumentVersion.class)
                .setParameter("id", id).getResultList()
                .forEach(v -> versions.add(GaJs.scalars(v)));
        o.set("versions", versions);
        return o;
    }

    // ───────────────────────────── POST / ─────────────────────────────

    /** Prefix {@code {KODE_TIPE}-{YYYY}-} (kode tipe disanitasi, maks. 12 karakter); null bila tipe tidak ada. */
    private String resolveDocCodePrefix(String typeId) {
        List<String> codes = em.createQuery("select t.code from GaDocumentType t where t.id = :id", String.class)
                .setParameter("id", typeId).getResultList();
        if (codes.isEmpty()) {
            return null;
        }
        String clean = codes.get(0).replaceAll("[^A-Za-z0-9_]", "").toUpperCase(Locale.ROOT);
        String typePrefix = clean.isEmpty() ? "DOC" : clean.substring(0, Math.min(12, clean.length()));
        int year = Instant.now().atZone(ZoneOffset.UTC).getYear();
        return typePrefix + "-" + year + "-";
    }

    private String nextDocCodeInTx(String prefix) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
                .setParameter("k", GaJs.advisoryKey(prefix))
                .getSingleResult();
        List<String> latest = em.createQuery(
                        "select d.code from GaDocument d where d.code like :p order by d.code desc", String.class)
                .setParameter("p", ErpQuery.startsWith(prefix))
                .setMaxResults(1)
                .getResultList();
        return GaJs.nextSequence(prefix, latest.isEmpty() ? null : latest.get(0));
    }

    @ErpTx
    public Map<String, Object> create(DocumentInput in, String userId) {
        String code = in.code() == null ? "" : in.code().strip();
        if (code.isEmpty()) {
            String prefix = resolveDocCodePrefix(in.typeId());
            if (prefix == null) {
                log.error("POST /ga/documents: Tipe dokumen tidak ditemukan untuk auto-generate kode.");
                throw ErpException.of(500, "Gagal membuat dokumen.");
            }
            code = nextDocCodeInTx(prefix);
        }
        GaDocument d = new GaDocument();
        d.setCode(code);
        d.setTitle(in.title());
        d.setTypeId(in.typeId());
        d.setAssetId(GaAssetUpdate.orNull(in.assetId()));
        d.setIssuedBy(GaAssetUpdate.orNull(in.issuedBy()));
        d.setIssuedNumber(GaAssetUpdate.orNull(in.issuedNumber()));
        d.setEffectiveDate(date(in.effectiveDate()));
        d.setExpiryDate(date(in.expiryDate()));
        d.setStatus(GaDocumentStatus.valueOf(in.status()));
        d.setCustomFields(in.customFields());
        d.setCreatedBy(userId);
        em.persist(d);
        em.flush();
        return ErpJson.map("id", d.getId(), "code", d.getCode());
    }

    // ───────────────────────────── PUT /:id ─────────────────────────────

    /**
     * Sama dengan sumber: field nullable yang tidak dikirim ikut DIKOSONGKAN ({@code input.x ?? null}),
     * sedangkan code/title/typeId/status/customFields yang absen dibiarkan.
     */
    @ErpTx
    public Map<String, Object> update(String id, DocumentInput in) {
        GaDocument d = em.find(GaDocument.class, id);
        if (d == null) {
            throw GaJs.p2025();
        }
        if (in.code() != null && !in.code().isEmpty()) {
            d.setCode(in.code());
        }
        if (in.title() != null) {
            d.setTitle(in.title());
        }
        if (in.typeId() != null) {
            d.setTypeId(in.typeId());
        }
        d.setAssetId(in.assetId());
        d.setIssuedBy(in.issuedBy());
        d.setIssuedNumber(in.issuedNumber());
        d.setEffectiveDate(date(in.effectiveDate()));
        d.setExpiryDate(date(in.expiryDate()));
        if (in.status() != null) {
            d.setStatus(GaDocumentStatus.valueOf(in.status()));
        }
        if (in.customFields() != null) {
            d.setCustomFields(in.customFields());
        }
        em.flush();
        return ErpJson.map("id", d.getId());
    }

    // ───────────────────────────── DELETE /:id ─────────────────────────────

    /** Versi ikut terhapus (FK cascade). Berkas fisik dibiarkan seperti sumber. */
    @ErpTx
    public Map<String, Object> delete(String id) {
        int n = em.createQuery("delete from GaDocument d where d.id = :id").setParameter("id", id).executeUpdate();
        if (n == 0) {
            throw GaJs.p2025();
        }
        return ErpJson.map("message", "Dokumen dihapus.");
    }

    // ───────────────────────────── versi ─────────────────────────────

    @ErpTx
    public ObjectNode addVersion(String documentId, byte[] bytes, String originalName, String notes, String userId) {
        GaDocument d = em.find(GaDocument.class, documentId);
        if (d == null) {
            throw ErpException.notFound("Dokumen tidak ditemukan.");
        }
        Integer last = em.createQuery("select max(v.versionNo) from GaDocumentVersion v where v.documentId = :id",
                        Integer.class)
                .setParameter("id", documentId).getSingleResult();
        int nextVersion = (last == null ? 0 : last) + 1;
        ErpFileStorage.SaveResult saved = storage.save(bytes, originalName, "application/pdf", "ga/documents");
        GaDocumentVersion v = new GaDocumentVersion();
        v.setDocumentId(documentId);
        v.setVersionNo(nextVersion);
        v.setFilePath(saved.filePath());
        v.setFileSize((int) saved.fileSize());
        v.setUploadedBy(userId);
        v.setNotes(notes == null || notes.isEmpty() ? null : notes);
        em.persist(v);
        em.flush();
        return GaJs.scalars(v);
    }

    /** Berkas versi untuk diunduh. */
    record Download(Path path, String fileName) {
    }

    /** versionId di-scope ke documentId untuk mencegah IDOR antar dokumen. */
    @ErpTx(readOnly = true)
    public Download versionFile(String documentId, String versionId) {
        List<Object[]> rows = em.createQuery("select v.filePath, v.versionNo, d.code from GaDocumentVersion v "
                        + "join v.document d where v.id = :vid and v.documentId = :id", Object[].class)
                .setParameter("vid", versionId).setParameter("id", documentId).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("File tidak ditemukan.");
        }
        String filePath = (String) rows.get(0)[0];
        Path path = storage.resolve(filePath); // 400 "Path file tidak valid." bila keluar dari root
        if (!Files.isRegularFile(path)) {
            throw ErpException.notFound("File tidak ditemukan.");
        }
        String name = filePath.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot) : "";
        return new Download(path, rows.get(0)[2] + "-v" + rows.get(0)[1] + ext);
    }
}
