package id.neravoxa.erp.inventory;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.InventoryItem;
import id.neravoxa.erp.entity.StockMovementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Master item stok — port {@code routes/inventory/items.ts} + {@code services/inventoryItemList.ts}. */
@Slf4j
@Service
public class ErpInventoryItemService {

    private static final BigDecimal BATCH_MIN_AVAILABLE = new BigDecimal("0.001");
    private static final List<StockMovementType> INBOUND = List.of(StockMovementType.In,
            StockMovementType.AdjustmentIn, StockMovementType.TransferIn);
    private static final List<StockMovementType> OUTBOUND = List.of(StockMovementType.Out,
            StockMovementType.AdjustmentOut, StockMovementType.TransferOut);

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── daftar ─────────────────────────────

    /**
     * GET /inventory/items. Dengan {@code warehouseId}, currentStock = stok di gudang itu (0 bila
     * belum ada baris), selain itu total global. Mengirim sort/stockStatus/category beralih ke mode
     * halaman daftar; picker memakai respons default.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        InventoryItemList.Filters filters = InventoryItemList.parse(query);

        if (InventoryItemList.usesListMode(query)) {
            ListQuery.Paging paging = ListQuery.paging(query, 100, 200);
            ListQuery.Sort sort = ListQuery.sort(query.get("sort"), InventoryItemList.SORT_FIELDS,
                    InventoryItemList.DEFAULT_SORT);
            List<InventoryItemList.Row> loaded = loadItems(InventoryItemList.dbWhere(filters), filters.warehouseId());
            List<InventoryItemList.Row> rows = InventoryItemList.sort(
                    InventoryItemList.filterByStockStatus(loaded, filters.stockStatus()), sort);
            List<Map<String, Object>> data = new ArrayList<>();
            int end = Math.min(rows.size(), paging.skip() + paging.limit());
            for (int i = paging.skip(); i < end; i++) {
                InventoryItemList.Row r = rows.get(i);
                Map<String, Object> m = r.json();
                m.put("stockStatus", InventoryItemList.stockStatus(r.currentStock(), r.minimumStock()));
                data.add(m);
            }
            return ListQuery.result(data, rows.size(), paging);
        }

        // Mode lama (picker): parameter mentah seperti di sumber.
        String rawPage = query.containsKey("page") ? query.get("page") : "1";
        String rawLimit = query.containsKey("limit") ? query.get("limit") : "100";
        double limitNum = InvJson.jsNumber(rawLimit);
        double take = Math.min(Double.isNaN(limitNum) || limitNum == 0 ? 100 : limitNum, 200);
        double page = InvJson.jsNumber(rawPage);
        double skip = (page - 1) * take;

        JpqlWhere where = new JpqlWhere();
        if (query.containsKey("isActive")) {
            where.and("i.isActive = :isActive", "isActive", "true".equals(query.get("isActive")));
        }
        String search = query.get("search");
        if (search != null && !search.isEmpty()) {
            where.and("lower(i.name) like :search or lower(i.code) like :search", "search", JpqlWhere.contains(search));
        }
        String warehouseId = query.get("warehouseId");
        List<InventoryItemList.Row> items = loadItems(where,
                warehouseId != null && !warehouseId.isEmpty() ? warehouseId : null);
        TypedQuery<Long> cq = em.createQuery("select count(i) from InventoryItem i" + where.clause(), Long.class);
        where.bind(cq);
        long total = cq.getSingleResult();

        // Urut stok terbanyak (sort stabil seperti Array.prototype.sort).
        List<InventoryItemList.Row> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Money.nz(b.currentStock()).compareTo(Money.nz(a.currentStock())));
        List<Map<String, Object>> paged = InvJson.jsSlice(sorted, skip, skip + take).stream()
                .map(InventoryItemList.Row::json).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", paged);
        out.put("total", total);
        out.put("page", InvJson.jsonNumber(page));
        out.put("limit", InvJson.jsonNumber(take));
        return out;
    }

    /** GET /inventory/items/summary — KPI untuk filter yang sama dengan daftar. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        InventoryItemList.Filters filters = InventoryItemList.parse(query);
        List<InventoryItemList.Row> loaded = loadItems(InventoryItemList.dbWhere(filters), filters.warehouseId());
        List<String> categories = em.createQuery("select distinct i.category from InventoryItem i", String.class)
                .getResultList();
        return InventoryItemList.summarize(InventoryItemList.filterByStockStatus(loaded, filters.stockStatus()), categories);
    }

    /**
     * Muat item sesuai filter DB beserta {@code account { id, name, accountNumber }}; dengan
     * {@code warehouseId} currentStock diganti stok gudang tersebut (0 bila tidak ada baris).
     */
    private List<InventoryItemList.Row> loadItems(JpqlWhere where, String warehouseId) {
        TypedQuery<InventoryItem> q = em.createQuery(
                "select i from InventoryItem i left join fetch i.account" + where.clause(), InventoryItem.class);
        where.bind(q);
        List<InventoryItem> all = q.getResultList();
        Map<String, BigDecimal> stockByItem = new HashMap<>();
        if (warehouseId != null && !all.isEmpty()) {
            em.createQuery("select s.itemId, s.currentStock from InventoryStock s "
                            + "where s.warehouseId = :w and s.itemId in :ids", Object[].class)
                    .setParameter("w", warehouseId)
                    .setParameter("ids", all.stream().map(InventoryItem::getId).toList())
                    .getResultList()
                    .forEach(r -> stockByItem.put((String) r[0], (BigDecimal) r[1]));
        }
        List<InventoryItemList.Row> rows = new ArrayList<>(all.size());
        for (InventoryItem i : all) {
            Map<String, Object> json = itemJson(i);
            BigDecimal current = i.getCurrentStock();
            if (warehouseId != null) {
                current = stockByItem.getOrDefault(i.getId(), BigDecimal.ZERO);
                json.put("currentStock", current);
            }
            rows.add(new InventoryItemList.Row(json, i.getCode(), i.getName(), i.getCategory(), i.getIsActive(),
                    current, i.getMinimumStock(), i.getAverageCost()));
        }
        return rows;
    }

    /** Item + {@code account: { id, name, accountNumber }} (null bila tanpa akun). */
    private Map<String, Object> itemJson(InventoryItem i) {
        Map<String, Object> m = InvJson.scalars(i);
        m.put("account", InvJson.pick(i.getAccount(), "id", "name", "accountNumber"));
        return m;
    }

    // ───────────────────────────── batch ─────────────────────────────

    /**
     * GET /inventory/items/:id/batches?warehouseId= — batch masuk (gerakan masuk non-batal) dengan
     * sisa qty untuk picker batch produksi. Pemanggil membandingkan total batch dengan currentStock.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> batches(String itemId, String warehouseId) {
        InventoryItem item = em.find(InventoryItem.class, itemId);
        if (item == null) {
            throw ErpException.notFound("Item tidak ditemukan.");
        }
        Map<String, Object> itemJson = InvJson.pick(item, "id", "code", "name", "unit", "currentStock");

        BigDecimal currentStock = Money.nz(item.getCurrentStock());
        if (warehouseId != null) {
            List<BigDecimal> rows = em.createQuery("select s.currentStock from InventoryStock s "
                            + "where s.itemId = :i and s.warehouseId = :w", BigDecimal.class)
                    .setParameter("i", itemId).setParameter("w", warehouseId).setMaxResults(1).getResultList();
            currentStock = rows.isEmpty() ? BigDecimal.ZERO : Money.nz(rows.get(0));
        }

        // TransferIn dihitung batch: membawa WAC saat transfer, jadi barang antar gudang tetap bisa dipilih.
        String jpql = "select m.id, m.movementNumber, m.date, m.quantity, m.unitCost, m.referenceType, m.referenceId, "
                + "m.referenceNumber from StockMovement m where m.itemId = :i and m.isCancelled = false "
                + "and m.movementType in :types" + (warehouseId != null ? " and m.warehouseId = :w" : "")
                + " order by m.date asc, m.createdAt asc, m.id asc";
        TypedQuery<Object[]> iq = em.createQuery(jpql, Object[].class)
                .setParameter("i", itemId).setParameter("types", INBOUND);
        if (warehouseId != null) {
            iq.setParameter("w", warehouseId);
        }
        List<Object[]> inbound = iq.getResultList();

        Map<String, Object> out = new LinkedHashMap<>();
        if (inbound.isEmpty()) {
            out.put("data", List.of());
            out.put("item", itemJson);
            out.put("currentStock", currentStock);
            return out;
        }

        // Konsumsi = gerakan keluar yang menautkan batch lewat source_movement_id.
        Map<String, BigDecimal> consumed = new HashMap<>();
        em.createQuery("select m.sourceMovementId, sum(m.quantity) from StockMovement m "
                        + "where m.sourceMovementId in :ids and m.isCancelled = false and m.movementType in :types "
                        + "group by m.sourceMovementId", Object[].class)
                .setParameter("ids", inbound.stream().map(r -> (String) r[0]).toList())
                .setParameter("types", OUTBOUND)
                .getResultList()
                .forEach(r -> {
                    if (r[0] != null) {
                        consumed.put((String) r[0], Money.nz((BigDecimal) r[1]));
                    }
                });

        BigDecimal totalAvailableTracked = BigDecimal.ZERO;
        for (Object[] r : inbound) {
            totalAvailableTracked = totalAvailableTracked.add(
                    Money.nz((BigDecimal) r[3]).subtract(consumed.getOrDefault((String) r[0], BigDecimal.ZERO)));
        }
        // Keluar tak terlacak = max(0, sisa terlacak − stok), diserap FIFO dari batch tertua agar picker
        // tidak pernah menampilkan lebih dari fisik.
        BigDecimal untrackedRemaining = Money.max(BigDecimal.ZERO, totalAvailableTracked.subtract(currentStock));

        List<Map<String, Object>> batches = new ArrayList<>();
        for (Object[] r : inbound) {
            BigDecimal qtyInbound = Money.nz((BigDecimal) r[3]);
            BigDecimal consumedTracked = consumed.getOrDefault((String) r[0], BigDecimal.ZERO);
            BigDecimal qtyAvailable = qtyInbound.subtract(consumedTracked);
            BigDecimal consumedUntracked = BigDecimal.ZERO;
            if (untrackedRemaining.signum() > 0 && qtyAvailable.signum() > 0) {
                BigDecimal absorb = Money.min(qtyAvailable, untrackedRemaining);
                qtyAvailable = qtyAvailable.subtract(absorb);
                consumedUntracked = absorb;
                untrackedRemaining = untrackedRemaining.subtract(absorb);
            }
            if (qtyAvailable.compareTo(BATCH_MIN_AVAILABLE) <= 0) {
                continue;
            }
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("id", r[0]);
            b.put("movementNumber", r[1]);
            b.put("date", (Instant) r[2]);
            b.put("qtyInbound", qtyInbound);
            b.put("unitCost", Money.nz((BigDecimal) r[4]));
            b.put("qtyConsumed", consumedTracked.add(consumedUntracked));
            b.put("qtyAvailable", qtyAvailable);
            b.put("sourceType", r[5] != null ? r[5] : "Other");
            b.put("sourceRef", r[7]);
            b.put("sourceRefId", r[6]);
            batches.add(b);
        }
        out.put("data", batches);
        out.put("item", itemJson);
        out.put("currentStock", currentStock);
        return out;
    }

    // ───────────────────────────── tulis ─────────────────────────────

    /** POST /inventory/items. */
    @ErpTx
    public Map<String, Object> create(InventoryItemCreateRequest body) {
        Long existing = em.createQuery("select count(i) from InventoryItem i where i.code = :c", Long.class)
                .setParameter("c", body.code()).getSingleResult();
        if (existing > 0) {
            throw ErpException.business("Kode item '" + body.code() + "' sudah digunakan.");
        }
        InventoryItem item = new InventoryItem();
        item.setCode(body.code());
        item.setName(body.name());
        item.setUnit(body.unit());
        item.setCategory(emptyToNull(body.category()));
        item.setDescription(emptyToNull(body.description()));
        item.setMinimumStock(body.minimumStock() != null ? body.minimumStock() : BigDecimal.ZERO);
        item.setAccountId(emptyToNull(body.accountId()));
        em.persist(item);
        em.flush();
        return reload(item.getId());
    }

    /** PUT /inventory/items/:id — update parsial; field yang tidak dikirim tidak diubah. */
    @ErpTx
    public Map<String, Object> update(String id, InventoryItemUpdateRequest body) {
        InventoryItem item = em.find(InventoryItem.class, id);
        if (item == null) {
            throw ErpException.notFound("Item stok tidak ditemukan.");
        }
        if (body.getCode() != null && !body.getCode().isEmpty()) {
            if (!body.getCode().equals(item.getCode())) {
                Long dup = em.createQuery("select count(i) from InventoryItem i where i.code = :c and i.id <> :id", Long.class)
                        .setParameter("c", body.getCode()).setParameter("id", id).getSingleResult();
                if (dup > 0) {
                    throw ErpException.business("Data duplikat terdeteksi. Silakan coba lagi.");
                }
            }
            item.setCode(body.getCode());
        }
        if (body.getName() != null && !body.getName().isEmpty()) {
            item.setName(body.getName());
        }
        if (body.getUnit() != null && !body.getUnit().isEmpty()) {
            item.setUnit(body.getUnit());
        }
        if (body.has("category")) {
            item.setCategory(clearable(body.getCategory()));
        }
        if (body.has("description")) {
            item.setDescription(clearable(body.getDescription()));
        }
        if (body.has("minimumStock")) {
            // z.coerce.number(): null → Number(null) = 0.
            item.setMinimumStock(body.getMinimumStock() != null ? body.getMinimumStock() : BigDecimal.ZERO);
        }
        if (body.has("accountId")) {
            item.setAccountId(emptyToNull(body.getAccountId()));
        }
        if (body.has("isActive") && body.getIsActive() != null) {
            item.setIsActive(body.getIsActive());
        }
        em.flush();
        return reload(id);
    }

    /** DELETE /inventory/items/:id — hanya bila tidak ada mutasi aktif & tidak dipakai invoice. */
    @ErpTx
    public Map<String, Object> delete(String id) {
        Long movementCount = em.createQuery("select count(m) from StockMovement m "
                        + "where m.itemId = :id and m.isCancelled = false", Long.class)
                .setParameter("id", id).getSingleResult();
        if (movementCount > 0) {
            throw ErpException.business("Item memiliki " + movementCount + " mutasi stok aktif. "
                    + "Batalkan semua mutasi terlebih dahulu atau nonaktifkan item.");
        }
        Long purchaseUsage = em.createQuery("select count(p) from PurchaseInvoiceItem p where p.inventoryItemId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        Long salesUsage = em.createQuery("select count(s) from SalesInvoiceItem s where s.inventoryItemId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (purchaseUsage > 0 || salesUsage > 0) {
            throw ErpException.business("Item sudah digunakan di invoice. Nonaktifkan item jika tidak ingin dipakai lagi.");
        }
        // Aman dihapus — sekalian buang gerakan yang sudah dibatalkan.
        em.createQuery("delete from StockMovement m where m.itemId = :id").setParameter("id", id).executeUpdate();
        InventoryItem item = em.find(InventoryItem.class, id);
        if (item == null) {
            throw ErpException.notFound("Item stok tidak ditemukan.");
        }
        em.remove(item);
        em.flush();
        return Map.of("message", "Item stok dihapus.");
    }

    private Map<String, Object> reload(String id) {
        em.clear();
        InventoryItem fresh = em.createQuery("select i from InventoryItem i left join fetch i.account where i.id = :id",
                InventoryItem.class).setParameter("id", id).getSingleResult();
        return itemJson(fresh);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /** undefined = tetap, null/kosong = hapus, selain itu teks ter-trim (utils/clearable.ts). */
    private static String clearable(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
