package id.enterpriseapps.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.storage.ErpFileStorage;
import id.enterpriseapps.erp.entity.GaInventoryItem;
import id.enterpriseapps.erp.entity.GaInventoryMovement;
import id.enterpriseapps.erp.entity.GaLocation;
import id.enterpriseapps.erp.entity.GaMovementType;
import id.enterpriseapps.erp.entity.GaStockByLocation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stok sparepart/consumable milik GA — port {@code routes/ga/inventory.ts}. Terpisah dari persediaan
 * akuntansi: tidak ada jurnal maupun saldo akun. Setiap mutasi mengubah stok per lokasi secara atomik
 * di dalam satu transaksi, dan total stok selalu turunan dari baris per lokasi (tanpa kolom cache).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpGaInventoryService {

    static final List<String> MOVEMENT_TYPES = GaInventoryList.MOVEMENT_TYPES;
    static final List<String> IMAGE_MIME = List.of("image/jpeg", "image/png", "image/webp");
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final ErpFileStorage storage;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ─────────────────────────── items ───────────────────────────

    /** Item lengkap dengan kategori + stok (untuk daftar, ringkasan, laporan). */
    private List<GaInventoryItem> loadItems(GaInventoryList.ItemFilter filter, boolean activeOnly) {
        ErpQuery<GaInventoryItem> q = ErpQuery.of(em, GaInventoryItem.class, "i")
                .fetch("left join fetch i.category", "left join fetch i.stocks")
                .orderBy("i.sku asc");
        if (filter != null) {
            filter.apply(q);
        }
        q.whereIf(activeOnly, "i.isActive = true");
        return q.list();
    }

    /**
     * GET /items tanpa {@code paginated=1}: array terurut SKU (limit ≤ 500; picker bergantung padanya),
     * filter categoryId & q.
     */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> listItems(Map<String, String> query) {
        String categoryId = query.get("categoryId");
        String q = query.get("q");
        Integer rawLimit = GaOpsJson.jsParseInt(query.getOrDefault("limit", "200"));
        int limit = Math.min(Math.max(rawLimit == null || rawLimit == 0 ? 200 : rawLimit, 1), 500);
        Integer rawPage = GaOpsJson.jsParseInt(query.getOrDefault("page", "1"));
        int page = Math.max(rawPage == null || rawPage == 0 ? 1 : rawPage, 1);
        ErpQuery<GaInventoryItem> eq = ErpQuery.of(em, GaInventoryItem.class, "i")
                .fetch("left join fetch i.category", "left join fetch i.stocks")
                .whereIf(categoryId != null && !categoryId.isEmpty(), "i.categoryId = :cat", "cat", categoryId)
                .whereIf(q != null && !q.isEmpty(), "(lower(i.sku) like :q or lower(i.name) like :q)", "q",
                        q == null || q.isEmpty() ? null : "%" + ErpQuery.escapeLike(q.toLowerCase(java.util.Locale.ROOT)) + "%")
                .orderBy("i.sku asc");
        List<Map<String, Object>> out = new ArrayList<>();
        for (GaInventoryItem it : eq.list((page - 1) * limit, limit)) {
            out.add(GaInventoryList.toRow(it, null).row());
        }
        return out;
    }

    private List<GaInventoryList.Computed> loadFiltered(GaInventoryList.ItemFilter filter) {
        List<GaInventoryList.Computed> rows = new ArrayList<>();
        for (GaInventoryItem it : loadItems(filter, false)) {
            rows.add(GaInventoryList.toRow(it, filter.locationId()));
        }
        return GaInventoryList.filter(rows, filter.stockStatus());
    }

    /** GET /items?paginated=1 → {@code { data, total, page, limit }} (+ locationStock bila locationId). */
    @ErpTx(readOnly = true)
    public Map<String, Object> listItemsPaged(Map<String, String> query) {
        GaInventoryList.ItemFilter filter = GaInventoryList.itemFilter(query);
        ListQuery.Paging paging = ListQuery.paging(query, 200, 500);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), GaInventoryList.ITEM_SORT_FIELDS,
                GaInventoryList.DEFAULT_ITEM_SORT);
        List<GaInventoryList.Computed> rows = GaInventoryList.sort(loadFiltered(filter), sort);
        List<Map<String, Object>> data = rows.stream()
                .skip(paging.skip()).limit(paging.limit()).map(GaInventoryList.Computed::row).toList();
        return ListQuery.result(data, rows.size(), paging);
    }

    /** GET /items/summary — KPI untuk filter yang sama dengan daftar berpaginasi. */
    @ErpTx(readOnly = true)
    public Map<String, Object> itemSummary(Map<String, String> query) {
        GaInventoryList.ItemFilter filter = GaInventoryList.itemFilter(query);
        return GaInventoryList.summarize(loadFiltered(filter));
    }

    /** GET /items/:id — detail dengan stok per lokasi. */
    @ErpTx(readOnly = true)
    public ObjectNode itemDetail(String id) {
        List<GaInventoryItem> rows = em.createQuery("select i from GaInventoryItem i left join fetch i.category "
                        + "left join fetch i.stocks s left join fetch s.location where i.id = :id", GaInventoryItem.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("Item tidak ditemukan.");
        }
        GaInventoryItem item = rows.get(0);
        ObjectNode o = GaOpsJson.scalars(item);
        o.set("minStock", ErpJson.tree(GaOpsJson.num(item.getMinStock())));
        o.set("category", item.getCategory() == null ? null : ErpJson.tree(item.getCategory()));
        if (o.get("category") != null && o.get("category").isObject()) {
            ((ObjectNode) o.get("category")).remove("items");
        }
        ArrayNode stocks = ErpJson.arrayNode();
        for (GaStockByLocation s : item.getStocks()) {
            ObjectNode so = GaOpsJson.scalars(s);
            so.set("quantity", ErpJson.tree(GaOpsJson.num(s.getQuantity())));
            so.set("location", ErpJson.tree(GaOpsJson.locFull(s.getLocation())));
            stocks.add(so);
        }
        o.set("stocks", stocks);
        return o;
    }

    /** Input {@code InventoryItemSchema} (atau versi {@code .partial()}). */
    record ItemInput(String sku, String name, String categoryId, String uom, BigDecimal minStock, Boolean isActive) {
    }

    /**
     * Zod 4: {@code .default()} tetap berlaku di dalam {@code .partial()}, jadi PUT tanpa minStock/isActive
     * mengisi 0/true — perilaku sumber dipertahankan.
     */
    static ItemInput parseItem(JsonNode raw, boolean partial) {
        JsonNode b = GaZod.object(raw);
        String sku = partial ? GaZod.optStringNotNull(GaZod.field(b, "sku"), 1, null, 100)
                : GaZod.reqString(GaZod.field(b, "sku"), 1, null, 100);
        String name = partial ? GaZod.optStringNotNull(GaZod.field(b, "name"), 1, null, 200)
                : GaZod.reqString(GaZod.field(b, "name"), 1, null, 200);
        String categoryId = partial ? GaZod.optStringNotNull(GaZod.field(b, "categoryId"), 1, null, 0)
                : GaZod.reqString(GaZod.field(b, "categoryId"), 1, null, 0);
        String uom = partial ? GaZod.optStringNotNull(GaZod.field(b, "uom"), 1, null, 20)
                : GaZod.reqString(GaZod.field(b, "uom"), 1, null, 20);
        BigDecimal minStock = GaZod.numberDefault(GaZod.field(b, "minStock"), GaZod.Sign.NONNEGATIVE, null, BigDecimal.ZERO);
        boolean isActive = GaZod.boolDefault(GaZod.field(b, "isActive"), true);
        return new ItemInput(sku, name, categoryId, uom, minStock, isActive);
    }

    /** POST /items → 201 {@code { id }}. */
    @ErpTx
    public Map<String, Object> createItem(ItemInput in) {
        GaInventoryItem it = new GaInventoryItem();
        it.setSku(in.sku());
        it.setName(in.name());
        it.setCategoryId(in.categoryId());
        it.setUom(in.uom());
        it.setMinStock(in.minStock());
        it.setIsActive(in.isActive());
        em.persist(it);
        em.flush();
        return GaOpsJson.map("id", it.getId());
    }

    /** PUT /items/:id (parsial) → {@code { id }}; id tak dikenal = P2025. */
    @ErpTx
    public Map<String, Object> updateItem(String id, ItemInput in) {
        GaInventoryItem it = em.find(GaInventoryItem.class, id);
        if (it == null) {
            throw ErpException.business("Data terkait tidak ditemukan. Periksa referensi yang dipilih.");
        }
        if (in.sku() != null) {
            it.setSku(in.sku());
        }
        if (in.name() != null) {
            it.setName(in.name());
        }
        if (in.categoryId() != null) {
            it.setCategoryId(in.categoryId());
        }
        if (in.uom() != null) {
            it.setUom(in.uom());
        }
        it.setMinStock(in.minStock());
        it.setIsActive(in.isActive());
        em.flush();
        return GaOpsJson.map("id", it.getId());
    }

    // ─────────────────────────── gambar item ───────────────────────────

    /** POST /items/:id/image — simpan foto publik baru lalu hapus berkas lama agar tidak menumpuk. */
    @ErpTx
    public Map<String, Object> uploadImage(String id, byte[] bytes, String originalName, String mimeType) {
        GaInventoryItem it = em.find(GaInventoryItem.class, id);
        if (it == null) {
            throw ErpException.notFound("Item tidak ditemukan.");
        }
        ErpFileStorage.SaveResult saved = storage.savePublic(bytes, originalName, mimeType, "ga/items");
        if (it.getImagePath() != null) {
            storage.deletePublic(it.getImagePath());
        }
        it.setImagePath(saved.filePath());
        em.flush();
        return GaOpsJson.map("id", it.getId(), "imagePath", saved.filePath());
    }

    /** Berkas gambar item untuk GET /items/:id/image. */
    public record ImageFile(Path path, String mimeType) {
    }

    @ErpTx(readOnly = true)
    public ImageFile image(String id) {
        GaInventoryItem it = em.find(GaInventoryItem.class, id);
        if (it == null || it.getImagePath() == null) {
            throw ErpException.notFound("Gambar tidak ditemukan.");
        }
        // Path-traversal guard ada di resolvePublic (400 "Path file tidak valid.").
        Path full = storage.resolvePublic(it.getImagePath());
        if (!Files.isRegularFile(full)) {
            throw ErpException.notFound("Gambar tidak ditemukan.");
        }
        String lower = full.getFileName().toString().toLowerCase();
        String mime = lower.endsWith(".png") ? "image/png" : lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return new ImageFile(full, mime);
    }

    /** DELETE /items/:id/image → 204. */
    @ErpTx
    public void deleteImage(String id) {
        GaInventoryItem it = em.find(GaInventoryItem.class, id);
        if (it == null) {
            throw ErpException.notFound("Item tidak ditemukan.");
        }
        if (it.getImagePath() != null) {
            storage.deletePublic(it.getImagePath());
            it.setImagePath(null);
            em.flush();
        }
    }

    // ─────────────────────────── mutasi ───────────────────────────

    private static ObjectNode movementRow(GaInventoryMovement m) {
        ObjectNode o = GaOpsJson.scalars(m);
        o.set("quantity", ErpJson.tree(GaOpsJson.num(m.getQuantity())));
        o.set("item", ErpJson.tree(GaOpsJson.itemBrief(m.getItem())));
        o.set("fromLocation", ErpJson.tree(GaOpsJson.locCodeName(m.getFromLocation())));
        o.set("toLocation", ErpJson.tree(GaOpsJson.locCodeName(m.getToLocation())));
        return o;
    }

    private static final String[] MOVEMENT_FETCH = {
            "left join fetch m.item", "left join fetch m.fromLocation", "left join fetch m.toLocation"};

    /** GET /movements tanpa {@code page}: 100 terbaru sebagai array. Tipe tak dikenal = galat Prisma → 500. */
    @ErpTx(readOnly = true)
    public List<ObjectNode> listMovements(Map<String, String> query) {
        String itemId = query.get("itemId");
        String type = query.get("type");
        GaMovementType mt = null;
        if (type != null && !type.isEmpty()) {
            if (!MOVEMENT_TYPES.contains(type)) {
                throw ErpException.of(500, "Gagal mengambil data movement.");
            }
            mt = GaMovementType.valueOf(type);
        }
        ErpQuery<GaInventoryMovement> q = ErpQuery.of(em, GaInventoryMovement.class, "m")
                .fetch(MOVEMENT_FETCH)
                .whereIf(itemId != null && !itemId.isEmpty(), "m.itemId = :itemId", "itemId", itemId)
                .whereIf(mt != null, "m.type = :type", "type", mt)
                .orderBy("m.createdAt desc");
        return q.list(0, 100).stream().map(ErpGaInventoryService::movementRow).toList();
    }

    /** GET /movements?page= → {@code { data, total, page, limit }} (limit ≤ 200). */
    @ErpTx(readOnly = true)
    public Map<String, Object> listMovementsPaged(Map<String, String> query) {
        ErpQuery<GaInventoryMovement> q = ErpQuery.of(em, GaInventoryMovement.class, "m")
                .join("left join m.item mi")
                .fetch(MOVEMENT_FETCH);
        GaInventoryList.movementWhere(q, query);
        ListQuery.Paging paging = ListQuery.paging(query, 25, 200);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), GaInventoryList.MOVEMENT_SORT_FIELDS,
                GaInventoryList.DEFAULT_MOVEMENT_SORT);
        q.orderBy(GaInventoryList.movementOrderBy(sort));
        long total = q.count();
        List<ObjectNode> data = q.list(paging.skip(), paging.limit()).stream().map(ErpGaInventoryService::movementRow).toList();
        return ListQuery.result(data, total, paging);
    }

    record MovementInput(String itemId, String type, BigDecimal quantity, String fromLocationId,
                         String toLocationId, String referenceNo, String notes) {
    }

    /** {@code MovementSchema} + syarat lokasi per tipe. */
    static MovementInput parseMovement(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String itemId = GaZod.reqString(GaZod.field(b, "itemId"), 1, null, 0);
        String type = GaZod.reqEnum(GaZod.field(b, "type"), MOVEMENT_TYPES);
        BigDecimal qty = GaZod.reqNumber(GaZod.field(b, "quantity"), GaZod.Sign.POSITIVE, "Qty harus lebih dari 0.");
        String from = GaZod.optString(GaZod.field(b, "fromLocationId"), 0, null, 0);
        String to = GaZod.optString(GaZod.field(b, "toLocationId"), 0, null, 0);
        String ref = GaZod.optString(GaZod.field(b, "referenceNo"), 0, null, 0);
        String notes = GaZod.optString(GaZod.field(b, "notes"), 0, null, 0);
        boolean hasFrom = from != null && !from.isEmpty();
        boolean hasTo = to != null && !to.isEmpty();
        if ("IN".equals(type) && !hasTo) {
            throw ErpException.business("IN butuh toLocation.");
        }
        if ("OUT".equals(type) && !hasFrom) {
            throw ErpException.business("OUT butuh fromLocation.");
        }
        if ("TRANSFER".equals(type) && (!hasFrom || !hasTo)) {
            throw ErpException.business("TRANSFER butuh fromLocation dan toLocation.");
        }
        if ("ADJUSTMENT".equals(type) && !hasTo) {
            throw ErpException.business("ADJUSTMENT butuh toLocation.");
        }
        return new MovementInput(itemId, type, qty, hasFrom ? from : null, hasTo ? to : null,
                ref == null || ref.isEmpty() ? null : ref, notes == null || notes.isEmpty() ? null : notes);
    }

    /** Ubah stok satu lokasi; hasil minus ditolak (sumber: Error biasa → 500 "Gagal mencatat movement."). */
    private void applyStock(String itemId, String locationId, BigDecimal delta) {
        BigDecimal next = GaStock.adjust(em, itemId, locationId, delta);
        if (next.signum() < 0) {
            throw ErpException.of(500, "Gagal mencatat movement.");
        }
    }

    /**
     * POST /movements: IN menambah toLocation, OUT mengambil dari fromLocation (tidak boleh di bawah nol),
     * TRANSFER keduanya, ADJUSTMENT menambah delta ke toLocation.
     */
    @ErpTx
    public Map<String, Object> createMovement(MovementInput in, String userId) {
        switch (in.type()) {
            case "IN" -> applyStock(in.itemId(), in.toLocationId(), in.quantity());
            case "OUT" -> applyStock(in.itemId(), in.fromLocationId(), in.quantity().negate());
            case "TRANSFER" -> {
                applyStock(in.itemId(), in.fromLocationId(), in.quantity().negate());
                applyStock(in.itemId(), in.toLocationId(), in.quantity());
            }
            default -> applyStock(in.itemId(), in.toLocationId(), in.quantity());
        }
        GaInventoryMovement m = new GaInventoryMovement();
        m.setItemId(in.itemId());
        m.setType(GaMovementType.valueOf(in.type()));
        m.setQuantity(in.quantity());
        m.setFromLocationId(in.fromLocationId());
        m.setToLocationId(in.toLocationId());
        m.setReferenceNo(in.referenceNo());
        m.setNotes(in.notes());
        m.setCreatedBy(userId);
        em.persist(m);
        em.flush();
        logIfLow(in.itemId());
        return GaOpsJson.map("id", m.getId());
    }

    /** Hook pasca-mutasi: catat peringatan bila stok ≤ minimum (notifikasi di luar cakupan sumber). */
    private void logIfLow(String itemId) {
        try {
            Object[] row = (Object[]) em.createNativeQuery("SELECT i.sku, i.min_stock, "
                            + "COALESCE((SELECT SUM(s.quantity) FROM ga_stock_by_location s WHERE s.item_id = i.id), 0) "
                            + "FROM ga_inventory_items i WHERE i.id = :id")
                    .setParameter("id", itemId).getSingleResult();
            BigDecimal min = GaStock.toDecimal(row[1]);
            BigDecimal total = GaStock.toDecimal(row[2]);
            if (min.signum() > 0 && total.compareTo(min) <= 0) {
                log.warn("GA inventory stock at/below minimum: itemId={} sku={} totalStock={} minStock={}",
                        itemId, row[0], GaOpsJson.numText(total), GaOpsJson.numText(min));
            }
        } catch (RuntimeException e) {
            log.warn("GA stock-low hook failed: {}", e.getMessage());
        }
    }

    // ─────────────────────────── laporan & dashboard ───────────────────────────

    private static BigDecimal totalStock(GaInventoryItem it) {
        BigDecimal t = BigDecimal.ZERO;
        for (GaStockByLocation s : it.getStocks()) {
            t = t.add(s.getQuantity());
        }
        return t;
    }

    /** GET /low-stock — item aktif di bawah minStock, kekurangan terbesar dulu. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> lowStock() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GaInventoryItem it : loadItems(null, true)) {
            BigDecimal total = totalStock(it);
            BigDecimal min = it.getMinStock();
            BigDecimal shortfall = min.subtract(total);
            if (shortfall.signum() <= 0) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", it.getId());
            m.put("sku", it.getSku());
            m.put("name", it.getName());
            m.put("uom", it.getUom());
            m.put("category", it.getCategory().getName());
            m.put("type", String.valueOf(it.getCategory().getType()));
            m.put("totalStock", GaOpsJson.num(total));
            m.put("minStock", GaOpsJson.num(min));
            m.put("shortfall", GaOpsJson.num(shortfall));
            out.add(m);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("shortfall")).reversed());
        return out;
    }

    private static final class Bucket {
        final Map<String, Object> head = new LinkedHashMap<>();
        int items;
        BigDecimal qty = BigDecimal.ZERO;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>(head);
            m.put("items", items);
            m.put("qty", GaOpsJson.num(qty));
            return m;
        }
    }

    /**
     * GET /dashboard — ringkasan stok untuk kartu & grafik, dihitung langsung dari stok per lokasi dan
     * log mutasi (tidak ada kolom cache) sehingga selalu konsisten dengan mutasi.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> dashboard() {
        Instant since = Instant.now().minus(Duration.ofDays(30));
        List<GaInventoryItem> items = loadItems(null, false);
        List<GaLocation> locations = em.createQuery("select l from GaLocation l order by l.code asc", GaLocation.class)
                .getResultList();
        List<Object[]> movements30 = em.createQuery("select m.type, m.quantity, m.itemId from GaInventoryMovement m "
                + "where m.createdAt >= :since", Object[].class).setParameter("since", since).getResultList();
        List<GaInventoryMovement> recent = ErpQuery.of(em, GaInventoryMovement.class, "m")
                .fetch(MOVEMENT_FETCH).orderBy("m.createdAt desc").list(0, 8);

        BigDecimal totalQty = BigDecimal.ZERO;
        int lowStock = 0;
        int outOfStock = 0;
        int activeItems = 0;
        Map<String, Bucket> byCategory = new LinkedHashMap<>();
        Map<String, Bucket> byLocation = new LinkedHashMap<>();
        for (GaLocation loc : locations) {
            Bucket b = new Bucket();
            b.head.put("code", loc.getCode());
            b.head.put("name", loc.getName());
            byLocation.put(loc.getId(), b);
        }
        List<Map<String, Object>> lowList = new ArrayList<>();
        for (GaInventoryItem it : items) {
            BigDecimal total = totalStock(it);
            BigDecimal min = it.getMinStock();
            totalQty = totalQty.add(total);
            if (Boolean.TRUE.equals(it.getIsActive())) {
                activeItems++;
            }
            if (total.signum() <= 0) {
                outOfStock++;
            }
            if (total.compareTo(min) < 0) {
                lowStock++;
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("id", it.getId());
                l.put("sku", it.getSku());
                l.put("name", it.getName());
                l.put("uom", it.getUom());
                l.put("category", it.getCategory() == null ? "—" : it.getCategory().getName());
                l.put("totalStock", GaOpsJson.num(total));
                l.put("minStock", GaOpsJson.num(min));
                l.put("shortfall", GaOpsJson.num(min.subtract(total)));
                lowList.add(l);
            }
            Bucket cat = byCategory.computeIfAbsent(it.getCategoryId(), k -> {
                Bucket b = new Bucket();
                b.head.put("name", it.getCategory() == null ? "—" : it.getCategory().getName());
                b.head.put("type", it.getCategory() == null ? "—" : String.valueOf(it.getCategory().getType()));
                return b;
            });
            cat.items++;
            cat.qty = cat.qty.add(total);
            for (GaStockByLocation s : it.getStocks()) {
                Bucket bucket = byLocation.get(s.getLocationId());
                if (bucket == null) {
                    continue;
                }
                bucket.qty = bucket.qty.add(s.getQuantity());
                if (s.getQuantity().signum() != 0) {
                    bucket.items++;
                }
            }
        }

        Map<String, Map<String, Object>> movementTotals = new LinkedHashMap<>();
        Map<String, BigDecimal> movementQty = new LinkedHashMap<>();
        Map<String, Integer> movementCount = new LinkedHashMap<>();
        for (String t : MOVEMENT_TYPES) {
            movementQty.put(t, BigDecimal.ZERO);
            movementCount.put(t, 0);
        }
        Set<String> movedItems = new HashSet<>();
        for (Object[] m : movements30) {
            String t = String.valueOf(m[0]);
            if (movementCount.containsKey(t)) {
                movementCount.merge(t, 1, Integer::sum);
                movementQty.merge(t, ((BigDecimal) m[1]).abs(), BigDecimal::add);
            }
            movedItems.add((String) m[2]);
        }
        for (String t : MOVEMENT_TYPES) {
            movementTotals.put(t, GaOpsJson.map("count", movementCount.get(t), "qty", GaOpsJson.num(movementQty.get(t))));
        }

        Comparator<Map<String, Object>> byQtyDesc =
                Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("qty")).reversed();
        List<Map<String, Object>> categories = new ArrayList<>(byCategory.values().stream().map(Bucket::toMap).toList());
        categories.sort(byQtyDesc);
        List<Map<String, Object>> locs = new ArrayList<>(byLocation.values().stream().map(Bucket::toMap).toList());
        locs.sort(byQtyDesc);
        lowList.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("shortfall")).reversed());

        List<Map<String, Object>> recentRows = new ArrayList<>();
        for (GaInventoryMovement m : recent) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", m.getId());
            r.put("type", m.getType());
            r.put("quantity", GaOpsJson.num(m.getQuantity()));
            r.put("createdAt", m.getCreatedAt());
            r.put("item", GaOpsJson.itemBrief(m.getItem()));
            r.put("fromLocation", m.getFromLocation() == null ? null : GaOpsJson.map("code", m.getFromLocation().getCode()));
            r.put("toLocation", m.getToLocation() == null ? null : GaOpsJson.map("code", m.getToLocation().getCode()));
            r.put("referenceNo", m.getReferenceNo());
            recentRows.add(r);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalItems", items.size());
        summary.put("activeItems", activeItems);
        summary.put("totalQty", GaOpsJson.num(totalQty));
        summary.put("lowStockCount", lowStock);
        summary.put("outOfStockCount", outOfStock);
        summary.put("locationCount", locations.size());
        summary.put("categoryCount", byCategory.size());
        summary.put("movements30d", movements30.size());
        summary.put("itemsMoved30d", movedItems.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("movementTotals", movementTotals);
        out.put("byCategory", categories);
        out.put("byLocation", locs);
        out.put("lowStockItems", lowList.subList(0, Math.min(10, lowList.size())));
        out.put("recentMovements", recentRows);
        return out;
    }

    // ─────────────────────────── ekspor & opname ───────────────────────────

    private static final Pattern CSV_SPECIAL = Pattern.compile("[\",\n;]");

    private static String csvCell(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        return CSV_SPECIAL.matcher(s).find() ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    /** Hasil ekspor CSV. */
    public record CsvFile(String filename, byte[] bytes) {
    }

    /** GET /export?type=items|movements — CSV (BOM, pemisah titik koma, CRLF). */
    @ErpTx(readOnly = true)
    public CsvFile export(String type) {
        List<List<Object>> rows = new ArrayList<>();
        String filename;
        if ("movements".equals(type)) {
            List<GaInventoryMovement> movements = ErpQuery.of(em, GaInventoryMovement.class, "m")
                    .fetch(MOVEMENT_FETCH).orderBy("m.createdAt desc").list(0, 5000);
            rows.add(List.of("Tanggal", "Tipe", "SKU", "Nama Item", "Qty", "Satuan", "Dari", "Ke", "Referensi", "Catatan"));
            for (GaInventoryMovement m : movements) {
                String iso = ErpJson.tree(m.getCreatedAt()).asText();
                List<Object> r = new ArrayList<>();
                r.add(iso.substring(0, 19).replace('T', ' '));
                r.add(m.getType());
                r.add(m.getItem() == null ? "" : m.getItem().getSku());
                r.add(m.getItem() == null ? "" : m.getItem().getName());
                r.add(GaOpsJson.numText(m.getQuantity()));
                r.add(m.getItem() == null ? "" : m.getItem().getUom());
                r.add(m.getFromLocation() == null ? "" : m.getFromLocation().getCode());
                r.add(m.getToLocation() == null ? "" : m.getToLocation().getCode());
                r.add(m.getReferenceNo() == null ? "" : m.getReferenceNo());
                r.add(m.getNotes() == null ? "" : m.getNotes());
                rows.add(r);
            }
            filename = "ga-inventory-movements";
        } else {
            rows.add(List.of("SKU", "Nama", "Kategori", "Tipe", "Satuan", "Total Stok", "Stok Minimum", "Status", "Aktif"));
            for (GaInventoryItem it : loadItems(null, false)) {
                BigDecimal total = totalStock(it);
                BigDecimal min = it.getMinStock();
                List<Object> r = new ArrayList<>();
                r.add(it.getSku());
                r.add(it.getName());
                r.add(it.getCategory() == null ? "" : it.getCategory().getName());
                r.add(it.getCategory() == null ? "" : String.valueOf(it.getCategory().getType()));
                r.add(it.getUom());
                r.add(GaOpsJson.numText(total));
                r.add(GaOpsJson.numText(min));
                r.add(total.compareTo(min) < 0 ? "Stok rendah" : "Aman");
                r.add(Boolean.TRUE.equals(it.getIsActive()) ? "Ya" : "Tidak");
                rows.add(r);
            }
            filename = "ga-inventory-items";
        }
        StringBuilder sb = new StringBuilder("﻿");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append("\r\n");
            }
            List<Object> r = rows.get(i);
            for (int j = 0; j < r.size(); j++) {
                if (j > 0) {
                    sb.append(';');
                }
                sb.append(csvCell(r.get(j)));
            }
        }
        String stamp = id.enterpriseapps.erp.core.ErpDates.isoDay(Instant.now());
        return new CsvFile(filename + "-" + stamp + ".csv", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    record StockSetInput(String itemId, String locationId, BigDecimal quantity, String notes) {
    }

    static StockSetInput parseStockSet(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String itemId = GaZod.reqString(GaZod.field(b, "itemId"), 1, null, 0);
        String locationId = GaZod.reqString(GaZod.field(b, "locationId"), 1, null, 0);
        BigDecimal qty = GaZod.reqNumber(GaZod.field(b, "quantity"), GaZod.Sign.NONNEGATIVE,
                "Stok hasil opname tidak boleh negatif.");
        String notes = GaZod.optString(GaZod.field(b, "notes"), 0, null, 0);
        return new StockSetInput(itemId, locationId, qty, notes);
    }

    /**
     * POST /stock-set — opname cepat: set stok ABSOLUT per lokasi. Selisih terhadap stok sistem dicatat
     * sebagai movement ADJUSTMENT (boleh minus) supaya total stok tetap 100% turunan log mutasi.
     */
    @ErpTx
    public Map<String, Object> stockSet(StockSetInput in, String userId) {
        BigDecimal target = in.quantity();
        BigDecimal existing = GaStock.lockCurrent(em, in.itemId(), in.locationId());
        BigDecimal current = existing == null ? BigDecimal.ZERO : existing;
        BigDecimal delta = target.subtract(current);
        if (delta.signum() == 0) {
            return GaOpsJson.map("changed", false, "delta", 0, "before", GaOpsJson.num(current),
                    "after", GaOpsJson.num(target));
        }
        GaStock.set(em, in.itemId(), in.locationId(), target);
        GaInventoryMovement m = new GaInventoryMovement();
        m.setItemId(in.itemId());
        m.setType(GaMovementType.ADJUSTMENT);
        m.setQuantity(delta);
        m.setToLocationId(in.locationId());
        m.setReferenceNo("OPNAME");
        m.setNotes(in.notes() != null && !in.notes().isEmpty() ? in.notes()
                : "Opname: " + GaOpsJson.numText(current) + " → " + GaOpsJson.numText(target));
        m.setCreatedBy(userId);
        em.persist(m);
        em.flush();
        return GaOpsJson.map("changed", true, "delta", GaOpsJson.num(delta), "before", GaOpsJson.num(current),
                "after", GaOpsJson.num(target));
    }
}
