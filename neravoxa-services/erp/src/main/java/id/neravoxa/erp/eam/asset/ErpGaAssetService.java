package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.storage.ErpFileStorage;
import id.neravoxa.erp.entity.FixedAsset;
import id.neravoxa.erp.entity.GaAsset;
import id.neravoxa.erp.entity.GaAssetCondition;
import id.neravoxa.erp.entity.GaAssetPhoto;
import id.neravoxa.erp.entity.GaAssetQr;
import id.neravoxa.erp.entity.GaAssetStatus;
import id.neravoxa.erp.entity.GaVehicleData;
import id.neravoxa.erp.eam.asset.GaAssetUpdate.AssetInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Registri aset fisik GA — port {@code routes/ga/assets.ts}.
 *
 * <p>Kode aset {@code {PREFIX_KATEGORI}-{KODE_CABANG}-{NNNN}}: nomor urut per kategori+cabang di bawah
 * advisory lock transaksi; QR dirender klien dari {@code qrCode} acak {@code GA-<uuid>}. Foto aset
 * disimpan PUBLIK ({@link ErpFileStorage#savePublic}) karena juga tampil di halaman scan QR tanpa login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpGaAssetService {

    static final List<String> SORT_FIELDS = List.of("code", "name", "status", "condition", "acquisitionDate", "createdAt");
    static final List<String> BULK_FIELDS = List.of("status", "condition", "branchId", "costCenterId", "picUserId", "location");
    static final List<String> PHOTO_MIME = List.of("image/jpeg", "image/png", "image/webp");
    static final long PHOTO_MAX_BYTES = 5L * 1024 * 1024;

    /** Prefix kategori (port lib/categories.ts GA Monitor) + kategori warisan Aset Tetap (FA_*). */
    private static final Map<String, String> CATEGORY_PREFIX = Map.ofEntries(
            Map.entry("KENDARAAN", "KND"),
            Map.entry("MESIN_PRODUKSI", "MSN"),
            Map.entry("ALAT_TIMBANG", "ALT"),
            Map.entry("IT_EQUIPMENT", "IT"),
            Map.entry("BANGUNAN", "BLD"),
            Map.entry("FURNITURE", "FRN"),
            Map.entry("GUDANG", "GDG"),
            Map.entry("EQUIPMENT", "EQP"),
            Map.entry("TANAH", "TNH"),
            Map.entry("FA_MESIN", "MSN"),
            Map.entry("FA_BANGUNAN", "BLD"),
            Map.entry("FA_TANAH", "TNH"),
            Map.entry("FA_PERALATAN", "PRL"));

    private final ErpFileStorage storage;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    static String categoryPrefix(String code) {
        String p = CATEGORY_PREFIX.get(code);
        if (p != null) {
            return p;
        }
        String cleaned = code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return cleaned.isEmpty() ? "AST" : cleaned.substring(0, Math.min(3, cleaned.length()));
    }

    /** {@code {CATEGORY_PREFIX}-{BRANCH_CODE}-}; null bila kategori/cabang tidak ada. */
    private String resolveAssetCodePrefix(String categoryId, String branchId) {
        List<String> cat = em.createQuery("select c.code from GaAssetCategory c where c.id = :id", String.class)
                .setParameter("id", categoryId).getResultList();
        List<String> br = em.createQuery("select b.code from GaBranch b where b.id = :id", String.class)
                .setParameter("id", branchId).getResultList();
        if (cat.isEmpty() || br.isEmpty()) {
            return null;
        }
        String brClean = br.get(0).replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        String brPrefix = brClean.isEmpty() ? "BR" : brClean.substring(0, Math.min(6, brClean.length()));
        return categoryPrefix(cat.get(0)) + "-" + brPrefix + "-";
    }

    /** Kode berikutnya untuk prefix; advisory lock transaksi agar generator paralel berurutan. */
    private String nextAssetCodeInTx(String prefix) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
                .setParameter("k", GaJs.advisoryKey(prefix))
                .getSingleResult();
        List<String> latest = em.createQuery(
                        "select a.code from GaAsset a where a.code like :p order by a.code desc", String.class)
                .setParameter("p", ErpQuery.startsWith(prefix))
                .setMaxResults(1)
                .getResultList();
        return GaJs.nextSequence(prefix, latest.isEmpty() ? null : latest.get(0));
    }

    private Map<String, String> resolvePicNames(Collection<String> picUserIds) {
        List<String> ids = picUserIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<String, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Object[] r : em.createQuery("select u.id, u.fullName from User u where u.id in :ids", Object[].class)
                .setParameter("ids", ids).getResultList()) {
            out.put((String) r[0], (String) r[1]);
        }
        return out;
    }

    private static JsonNode pic(String picUserId, Map<String, String> names) {
        if (picUserId == null) {
            return NullNode.getInstance();
        }
        return ErpJson.tree(ErpJson.map("id", picUserId, "fullName", names.get(picUserId)));
    }

    private static GaAssetStatus statusOrFail(String raw, String fallback) {
        try {
            return GaAssetStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            // Prisma menolak nilai enum tak dikenal → handleRouteError 500 dengan pesan rute.
            throw ErpException.of(500, fallback);
        }
    }

    private static GaAssetCondition conditionOrFail(String raw, String fallback) {
        try {
            return GaAssetCondition.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw ErpException.of(500, fallback);
        }
    }

    // ───────────────────────────── GET / ─────────────────────────────

    /**
     * Daftar aset. {@code categoryKind=vehicle|building|equipment} menyelesaikan himpunan kategori di
     * server; {@code paginated=1} → {@code { data, total, page, limit }}, selain itu array; {@code thumb=1}
     * menambah foto pertama per aset.
     */
    @ErpTx(readOnly = true)
    public Object list(Map<String, String> q) {
        String fail = "Gagal mengambil data aset.";
        GaCategoryKind.Parsed kind = GaCategoryKind.parse(q.get("categoryKind"));
        if (kind.invalid()) {
            throw ErpException.business("categoryKind tidak valid.");
        }
        ErpQuery<GaAsset> query = ErpQuery.of(em, GaAsset.class, "a")
                .fetch("left join fetch a.category", "left join fetch a.branch", "left join fetch a.costCenter");

        if (kind.kind() != null) {
            List<String> ids = GaCategoryKind.resolveCategoryIds(em, kind.kind());
            query.where("a.categoryId in :kindIds", "kindIds", ErpQuery.nonEmpty(ids, "__none__"));
        } else if (GaJs.truthy(q.get("categoryId"))) {
            // Multi kategori dipisah koma (halaman Gedung & Sewa menggabung Bangunan + Gudang).
            List<String> ids = java.util.Arrays.stream(q.get("categoryId").split(","))
                    .map(String::strip).filter(s -> !s.isEmpty()).toList();
            if (ids.size() > 1) {
                query.where("a.categoryId in :catIds", "catIds", ids);
            } else if (ids.size() == 1) {
                query.where("a.categoryId = :catId", "catId", ids.get(0));
            }
        }
        if (GaJs.truthy(q.get("branchId"))) {
            query.where("a.branchId = :branchId", "branchId", q.get("branchId"));
        }
        if (GaJs.truthy(q.get("costCenterId"))) {
            query.where("a.costCenterId = :ccId", "ccId", q.get("costCenterId"));
        }
        if (GaJs.truthy(q.get("picUserId"))) {
            query.where("a.picUserId = :picId", "picId", q.get("picUserId"));
        }
        if (GaJs.truthy(q.get("status"))) {
            query.where("a.status = :status", "status", statusOrFail(q.get("status"), fail));
        }
        if (GaJs.truthy(q.get("condition"))) {
            query.where("a.condition = :condition", "condition", conditionOrFail(q.get("condition"), fail));
        }
        if (GaJs.truthy(q.get("acquisitionFrom"))) {
            Instant d = ErpDates.tryInstant(q.get("acquisitionFrom"));
            query.whereIf(d != null, "a.acquisitionDate >= :acqFrom", "acqFrom", d);
        }
        if (GaJs.truthy(q.get("acquisitionTo"))) {
            Instant d = ErpDates.tryInstant(q.get("acquisitionTo"));
            if (d != null) {
                // d.setHours(23, 59, 59, 999) di server ber-TZ UTC.
                Instant end = d.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
                        .atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
                query.where("a.acquisitionDate <= :acqTo", "acqTo", end);
            }
        }
        if (GaJs.truthy(q.get("q"))) {
            query.where("(lower(a.code) like :q or lower(a.name) like :q or lower(a.serialNumber) like :q "
                    + "or lower(a.location) like :q)", "q", ErpQuery.contains(q.get("q")));
        }

        Integer rawLimit = GaJs.parseInt(q.getOrDefault("limit", "100"));
        int limit = Math.min(Math.max(rawLimit == null || rawLimit == 0 ? 100 : rawLimit, 1), 500);
        Integer rawPage = GaJs.parseInt(q.getOrDefault("page", "1"));
        int page = Math.max(rawPage == null || rawPage == 0 ? 1 : rawPage, 1);
        int skip = (page - 1) * limit;

        ListQuery.Sort sort = ListQuery.sort(q.get("sort"), SORT_FIELDS, new ListQuery.Sort("code", false));
        query.orderBy("code".equals(sort.field()) ? "a.code " + sort.dir()
                : "a." + sort.field() + " " + sort.dir() + ", a.code asc");
        boolean withThumb = "1".equals(q.get("thumb"));

        List<GaAsset> items = query.list(skip, limit);
        long total = query.count();
        List<String> ids = items.stream().map(GaAsset::getId).toList();

        Map<String, String> qrByAsset = new HashMap<>();
        Map<String, FixedAsset> faByAsset = new HashMap<>();
        Map<String, String> plateByAsset = new HashMap<>();
        Map<String, List<GaAssetPhoto>> photosByAsset = new HashMap<>();
        if (!ids.isEmpty()) {
            for (GaAssetQr qr : em.createQuery("select q from GaAssetQr q where q.assetId in :ids", GaAssetQr.class)
                    .setParameter("ids", ids).getResultList()) {
                qrByAsset.put(qr.getAssetId(), qr.getQrCode());
            }
            for (FixedAsset fa : em.createQuery("select f from FixedAsset f where f.gaAssetId in :ids", FixedAsset.class)
                    .setParameter("ids", ids).getResultList()) {
                faByAsset.put(fa.getGaAssetId(), fa);
            }
            for (GaVehicleData vd : em.createQuery("select v from GaVehicleData v where v.assetId in :ids", GaVehicleData.class)
                    .setParameter("ids", ids).getResultList()) {
                plateByAsset.put(vd.getAssetId(), vd.getNoPlat());
            }
            if (withThumb) {
                for (GaAssetPhoto p : em.createQuery("select p from GaAssetPhoto p where p.assetId in :ids "
                                + "order by p.orderIdx asc, p.createdAt asc", GaAssetPhoto.class)
                        .setParameter("ids", ids).getResultList()) {
                    photosByAsset.computeIfAbsent(p.getAssetId(), k -> new ArrayList<>()).add(p);
                }
            }
        }
        Map<String, String> picNames = resolvePicNames(items.stream().map(GaAsset::getPicUserId).toList());

        ArrayNode data = ErpJson.arrayNode();
        for (GaAsset a : items) {
            ObjectNode o = GaJs.scalars(a);
            o.set("category", GaJs.pick(a.getCategory(), "code", "name"));
            o.set("branch", GaJs.pick(a.getBranch(), "code", "name"));
            o.set("costCenter", GaJs.pick(a.getCostCenter(), "code", "name"));
            String qr = qrByAsset.get(a.getId());
            o.set("qr", qr == null ? NullNode.getInstance() : ErpJson.tree(ErpJson.map("qrCode", qr)));
            o.set("fixedAsset", GaJs.pick(faByAsset.get(a.getId()), "id", "assetNumber", "bookValue"));
            o.set("vehicleData", plateByAsset.containsKey(a.getId())
                    ? ErpJson.tree(ErpJson.map("noPlat", plateByAsset.get(a.getId()))) : NullNode.getInstance());
            if (withThumb) {
                ArrayNode photos = ErpJson.arrayNode();
                List<GaAssetPhoto> list = photosByAsset.getOrDefault(a.getId(), List.of());
                if (!list.isEmpty()) {
                    photos.add(GaJs.pick(list.get(0), "id", "filePath", "caption"));
                }
                o.set("photos", photos);
            }
            o.set("pic", pic(a.getPicUserId(), picNames));
            data.add(o);
        }
        if ("1".equals(q.get("paginated"))) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("data", data);
            out.put("total", total);
            out.put("page", page);
            out.put("limit", limit);
            return out;
        }
        return data;
    }

    // ───────────────────────────── GET /_next-code ─────────────────────────────

    @ErpTx
    public Map<String, Object> nextCode(String categoryId, String branchId) {
        String prefix = resolveAssetCodePrefix(categoryId, branchId);
        if (prefix == null) {
            throw ErpException.notFound("Kategori atau cabang tidak ditemukan.");
        }
        return ErpJson.map("code", nextAssetCodeInTx(prefix));
    }

    // ───────────────────────────── GET /:id ─────────────────────────────

    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<GaAsset> rows = em.createQuery("select a from GaAsset a left join fetch a.category "
                        + "left join fetch a.branch left join fetch a.costCenter where a.id = :id", GaAsset.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("Aset tidak ditemukan.");
        }
        GaAsset a = rows.get(0);
        ObjectNode o = GaJs.scalars(a);
        o.set("category", GaJs.orNull(GaJs.scalars(a.getCategory())));
        o.set("branch", GaJs.orNull(GaJs.scalars(a.getBranch())));
        o.set("costCenter", GaJs.orNull(GaJs.scalars(a.getCostCenter())));
        List<GaAssetQr> qr = em.createQuery("select q from GaAssetQr q where q.assetId = :id", GaAssetQr.class)
                .setParameter("id", id).getResultList();
        o.set("qr", qr.isEmpty() ? NullNode.getInstance() : GaJs.scalars(qr.get(0)));
        ArrayNode photos = ErpJson.arrayNode();
        em.createQuery("select p from GaAssetPhoto p where p.assetId = :id order by p.orderIdx asc, p.createdAt asc",
                        GaAssetPhoto.class)
                .setParameter("id", id).getResultList()
                .forEach(p -> photos.add(GaJs.scalars(p)));
        o.set("photos", photos);
        // Profil keuangan/penyusutan (EAM): baris aset tetap lengkap.
        List<FixedAsset> fa = em.createQuery("select f from FixedAsset f where f.gaAssetId = :id", FixedAsset.class)
                .setParameter("id", id).getResultList();
        o.set("fixedAsset", fa.isEmpty() ? NullNode.getInstance() : GaJs.scalars(fa.get(0)));
        Map<String, String> names = resolvePicNames(a.getPicUserId() == null ? List.of() : List.of(a.getPicUserId()));
        o.set("pic", pic(a.getPicUserId(), names));
        return o;
    }

    // ───────────────────────────── POST / ─────────────────────────────

    /**
     * Buat aset + QR. Bila kode kosong, kode digenerate atomik di transaksi ini sehingga create paralel
     * tidak bertabrakan pada saran yang sama.
     */
    @ErpTx
    public Map<String, Object> create(AssetInput input) {
        String code = input.code() == null ? "" : input.code().strip();
        if (code.isEmpty()) {
            String prefix = resolveAssetCodePrefix(input.categoryId(), input.branchId());
            if (prefix == null) {
                log.error("POST /ga/assets: Kategori atau cabang tidak ditemukan untuk auto-generate kode.");
                throw ErpException.of(500, "Gagal membuat aset.");
            }
            code = nextAssetCodeInTx(prefix);
        }
        GaAsset a = new GaAsset();
        a.setCode(code);
        a.setName(input.name());
        a.setCategoryId(input.categoryId());
        a.setBranchId(input.branchId());
        a.setCostCenterId(GaAssetUpdate.orNull(input.costCenterId()));
        a.setPicUserId(GaAssetUpdate.orNull(input.picUserId()));
        a.setSerialNumber(GaAssetUpdate.orNull(input.serialNumber()));
        String acq = GaAssetUpdate.orNull(input.acquisitionDate());
        a.setAcquisitionDate(acq == null ? null : ErpDates.toInstant(acq));
        a.setAcquisitionCost(input.acquisitionCost());
        a.setStatus(GaAssetStatus.valueOf(input.status()));
        a.setCondition(GaAssetCondition.valueOf(input.condition()));
        a.setLocation(GaAssetUpdate.orNull(input.location()));
        a.setNotes(GaAssetUpdate.orNull(input.notes()));
        a.setCustomFields(input.customFields());
        em.persist(a);

        GaAssetQr qr = new GaAssetQr();
        qr.setAssetId(a.getId());
        qr.setQrCode("GA-" + UUID.randomUUID());
        em.persist(qr);
        em.flush();
        return ErpJson.map("id", a.getId(), "code", a.getCode());
    }

    // ───────────────────────────── PUT /:id ─────────────────────────────

    @ErpTx
    public Map<String, Object> update(String id, AssetInput input) {
        GaAsset a = em.find(GaAsset.class, id);
        if (a == null) {
            throw GaJs.p2025();
        }
        GaAssetUpdate.apply(a, input);
        em.flush();
        return ErpJson.map("id", a.getId());
    }

    // ───────────────────────────── PATCH /bulk/update ─────────────────────────────

    /** Validasi body bulk sama urutannya dengan sumber; nilai tak valid → 500 pesan rute (Prisma gagal). */
    @ErpTx
    public Map<String, Object> bulkUpdate(JsonNode body) {
        List<String> ids = idsOf(body);
        JsonNode data = body == null ? null : body.get("data");
        if (data == null || !data.isContainerNode() || data.isEmpty()) {
            throw ErpException.business("data wajib berisi field yang akan diupdate.");
        }
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        if (data.isArray()) {
            for (int i = 0; i < data.size(); i++) {
                fields.put(String.valueOf(i), data.get(i));
            }
        } else {
            data.properties().forEach(e -> fields.put(e.getKey(), e.getValue()));
        }
        for (String key : fields.keySet()) {
            if (!BULK_FIELDS.contains(key)) {
                throw ErpException.business("Field '" + key + "' tidak diizinkan untuk bulk update.");
            }
        }
        String fail = "Gagal bulk update aset.";
        StringBuilder set = new StringBuilder("update GaAsset a set a.updatedAt = :now");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("now", Instant.now());
        for (Map.Entry<String, JsonNode> e : fields.entrySet()) {
            JsonNode v = e.getValue();
            Object value;
            if (v == null || v.isNull()) {
                value = null;
            } else if (!v.isTextual()) {
                throw ErpException.of(500, fail);
            } else if ("status".equals(e.getKey())) {
                value = statusOrFail(v.asText(), fail);
            } else if ("condition".equals(e.getKey())) {
                value = conditionOrFail(v.asText(), fail);
            } else {
                value = v.asText();
            }
            if (value == null && List.of("status", "condition", "branchId").contains(e.getKey())) {
                // Kolom wajib tidak bisa di-null-kan (Prisma menolak) → 500 pesan rute.
                throw ErpException.of(500, fail);
            }
            set.append(", a.").append(e.getKey()).append(" = :").append(e.getKey());
            params.put(e.getKey(), value);
        }
        set.append(" where a.id in :ids");
        Query uq = em.createQuery(set.toString());
        params.forEach(uq::setParameter);
        uq.setParameter("ids", ids);
        int updated = uq.executeUpdate();
        return ErpJson.map("updated", updated);
    }

    private static List<String> idsOf(JsonNode body) {
        JsonNode ids = body == null ? null : body.get("ids");
        if (ids == null || !ids.isArray() || ids.isEmpty()) {
            throw ErpException.business("ids wajib berupa array non-kosong.");
        }
        if (ids.size() > 200) {
            throw ErpException.business("Maksimal 200 aset per batch.");
        }
        List<String> out = new ArrayList<>();
        ids.forEach(n -> out.add(n.isNull() ? null : n.asText()));
        return out;
    }

    // ───────────────────────────── POST /bulk/delete ─────────────────────────────

    /** Foto & QR ikut terhapus (FK cascade); berkas foto dihapus dari disk dulu agar tidak yatim. */
    @ErpTx
    public Map<String, Object> bulkDelete(JsonNode body) {
        List<String> ids = idsOf(body);
        List<String> nonNull = ids.stream().filter(Objects::nonNull).toList();
        List<String> files = nonNull.isEmpty() ? List.of() : em.createQuery(
                        "select p.filePath from GaAssetPhoto p where p.assetId in :ids", String.class)
                .setParameter("ids", nonNull).getResultList();
        files.forEach(storage::deletePublic);
        if (!nonNull.isEmpty()) {
            em.createQuery("delete from GaAsset a where a.id in :ids").setParameter("ids", nonNull).executeUpdate();
        }
        return ErpJson.map("deleted", ids.size());
    }

    // ───────────────────────────── PATCH /:id/condition ─────────────────────────────

    /** PIC atau pemegang izin tulis ga_assets (gerbang mount) memperbarui kondisi. */
    @ErpTx
    public Map<String, Object> updateCondition(String id, JsonNode body) {
        String condition = body != null && body.hasNonNull("condition") && truthyNode(body.get("condition"))
                ? body.get("condition").asText() : "";
        JsonNode rawNotes = body == null ? null : body.get("notes");
        String notes = truthyNode(rawNotes) ? rawNotes.asText() : null;
        if (notes != null && notes.length() > 2000) {
            notes = notes.substring(0, 2000);
        }
        if (!List.of(GaAssetUpdate.CONDITIONS).contains(condition)) {
            throw ErpException.business("Kondisi tidak valid.");
        }
        GaAsset a = em.find(GaAsset.class, id);
        if (a == null) {
            throw ErpException.notFound("Aset tidak ditemukan.");
        }
        a.setCondition(GaAssetCondition.valueOf(condition));
        if (notes != null) {
            a.setNotes(notes);
        }
        em.flush();
        return ErpJson.map("id", a.getId(), "condition", a.getCondition());
    }

    private static boolean truthyNode(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) {
            return false;
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.isNumber()) {
            return v.asDouble() != 0;
        }
        if (v.isTextual()) {
            return !v.asText().isEmpty();
        }
        return true;
    }

    // ───────────────────────────── foto ─────────────────────────────

    /** Berkas foto masuk (sudah lolos filter tipe & ukuran ala multer). */
    record PhotoUpload(byte[] bytes, String originalName, String mimeType) {
    }

    @ErpTx
    public ObjectNode addPhoto(String id, PhotoUpload file, String caption) {
        GaAsset a = em.find(GaAsset.class, id);
        if (a == null) {
            throw ErpException.notFound("Aset tidak ditemukan.");
        }
        ErpFileStorage.SaveResult saved = storage.savePublic(file.bytes(), file.originalName(), file.mimeType(), "ga/assets");
        GaAssetPhoto p = new GaAssetPhoto();
        p.setAssetId(a.getId());
        p.setFilePath(saved.filePath());
        p.setCaption(caption == null || caption.isEmpty() ? null : caption);
        em.persist(p);
        em.flush();
        return GaJs.scalars(p);
    }

    @ErpTx
    public void deletePhoto(String photoId) {
        GaAssetPhoto p = em.find(GaAssetPhoto.class, photoId);
        if (p != null) {
            storage.deletePublic(p.getFilePath());
            em.remove(p);
            em.flush();
        }
    }
}
