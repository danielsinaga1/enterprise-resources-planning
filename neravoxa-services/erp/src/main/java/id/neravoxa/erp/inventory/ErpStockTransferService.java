package id.neravoxa.erp.inventory;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.inventory.ErpWarehouseStockService;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.InventoryItem;
import id.neravoxa.erp.entity.StockMovement;
import id.neravoxa.erp.entity.StockMovementType;
import id.neravoxa.erp.entity.StockTransfer;
import id.neravoxa.erp.entity.StockTransferItem;
import id.neravoxa.erp.entity.Warehouse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transfer stok antar gudang — port {@code routes/stockTransfers.ts}, {@code services/stockTransfer.ts}
 * dan {@code services/stockTransferList.ts}.
 *
 * <p>Transfer menghasilkan pasangan gerakan TransferOut/TransferIn TANPA dampak GL (akun sama, net nol);
 * {@code inventory_items.current_stock} (total lintas gudang) tidak berubah.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpStockTransferService {

    static final List<String> SORT_FIELDS = List.of("date", "transferNumber");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);

    private final ErpWarehouseStockService stock;
    private final ErpDocumentNumberService numbers;
    private final ErpFiscalYearService fiscalYears;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── daftar ─────────────────────────────

    /**
     * Filter daftar (port buildStockTransferWhere): status, rentang tanggal hari bisnis, gudang
     * (asal ATAU tujuan), search (nomor, catatan, mobil, supir, penerima, nama gudang).
     */
    static JpqlWhere where(Map<String, String> query) {
        JpqlWhere w = new JpqlWhere();
        String status = ListQuery.str(query, "status");
        if (status != null) {
            DocumentStatus st;
            try {
                st = DocumentStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw ErpException.business("Status transfer tidak dikenal.");
            }
            w.and("t.status = :status", "status", st);
        }
        StockMovementList.dateRange(w, query, "t.date");
        String warehouseId = ListQuery.str(query, "warehouseId");
        if (warehouseId != null) {
            w.and("t.fromWarehouseId = :wh or t.toWarehouseId = :wh", "wh", warehouseId);
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            w.and("lower(t.transferNumber) like :search or lower(t.notes) like :search "
                    + "or lower(t.nomorMobil) like :search or lower(t.driverName) like :search "
                    + "or lower(t.recipientName) like :search or lower(fw.name) like :search or lower(tw.name) like :search",
                    "search", JpqlWhere.contains(search));
        }
        return w;
    }

    /** ORDER BY; nomor transfer sebagai pemecah seri agar baris tidak berpindah halaman. */
    static String orderBy(ListQuery.Sort sort) {
        String primary = "t." + sort.field() + " " + sort.dir();
        return "transferNumber".equals(sort.field()) ? primary : primary + ", t.transferNumber " + sort.dir();
    }

    /** GET /stock-transfers — tanpa {@code sort} urutan lama (tanggal desc). */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        JpqlWhere where = where(query);
        String order = ListQuery.str(query, "sort") != null
                ? orderBy(ListQuery.sort(query.get("sort"), SORT_FIELDS, DEFAULT_SORT))
                : "t.date desc";
        ErpQuery<StockTransfer> q = where.applyTo(ErpQuery.of(em, StockTransfer.class, "t")
                .join("join t.fromWarehouse fw", "join t.toWarehouse tw")
                .fetch("left join fetch t.fromWarehouse", "left join fetch t.toWarehouse", "left join fetch t.user",
                        "left join fetch t.items ti", "left join fetch ti.item")
                .orderBy(order));
        long total = q.count();
        List<Map<String, Object>> data = q.list(paging.skip(), paging.limit()).stream().map(t -> {
            Map<String, Object> m = InvJson.scalars(t);
            m.put("fromWarehouse", InvJson.pick(t.getFromWarehouse(), "id", "code", "name"));
            m.put("toWarehouse", InvJson.pick(t.getToWarehouse(), "id", "code", "name"));
            m.put("items", sortedItems(t).stream().map(ti -> {
                Map<String, Object> im = InvJson.scalars(ti);
                im.put("item", InvJson.pick(ti.getItem(), "id", "code", "name", "unit"));
                return im;
            }).toList());
            m.put("user", InvJson.pick(t.getUser(), "id", "fullName"));
            return m;
        }).toList();
        return ListQuery.result(data, total, paging);
    }

    /** GET /stock-transfers/:id — detail lengkap (gudang & item penuh). */
    @ErpTx(readOnly = true)
    public Map<String, Object> detail(String id) {
        List<StockTransfer> rows = em.createQuery("select t from StockTransfer t left join fetch t.fromWarehouse "
                        + "left join fetch t.toWarehouse left join fetch t.user left join fetch t.items ti "
                        + "left join fetch ti.item where t.id = :id", StockTransfer.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("Transfer tidak ditemukan.");
        }
        StockTransfer t = rows.get(0);
        Map<String, Object> m = InvJson.scalars(t);
        m.put("fromWarehouse", InvJson.scalars(t.getFromWarehouse()));
        m.put("toWarehouse", InvJson.scalars(t.getToWarehouse()));
        m.put("items", sortedItems(t).stream().map(ti -> {
            Map<String, Object> im = InvJson.scalars(ti);
            im.put("item", InvJson.scalars(ti.getItem()));
            return im;
        }).toList());
        m.put("user", InvJson.pick(t.getUser(), "id", "fullName"));
        return m;
    }

    private static List<StockTransferItem> sortedItems(StockTransfer t) {
        List<StockTransferItem> items = new ArrayList<>(t.getItems());
        items.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
        return items;
    }

    // ───────────────────────────── submit ─────────────────────────────

    /** POST /stock-transfers — buat + submit sekaligus (port submitStockTransferInTx). */
    @ErpTx(timeout = 30)
    public Map<String, Object> submit(StockTransferRequest body, String userId) {
        Instant date = ErpDates.tryInstant(body.date());
        if (date == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        if (body.fromWarehouseId().equals(body.toWarehouseId())) {
            throw ErpException.business("Gudang sumber dan tujuan tidak boleh sama.");
        }
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
        String transferNumber = numbers.generate("ST", date, fy.getId());

        Warehouse fromWh = em.find(Warehouse.class, body.fromWarehouseId());
        Warehouse toWh = em.find(Warehouse.class, body.toWarehouseId());
        if (fromWh == null || toWh == null) {
            throw ErpException.business("Gudang tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(fromWh.getIsActive()) || !Boolean.TRUE.equals(toWh.getIsActive())) {
            throw ErpException.business("Gudang sumber/tujuan tidak aktif.");
        }

        // Cek stok per item di gudang asal.
        for (StockTransferRequest.Item it : body.items()) {
            BigDecimal available = stock.warehouseStock(it.itemId(), body.fromWarehouseId());
            if (available.compareTo(it.quantity()) < 0) {
                InventoryItem item = em.find(InventoryItem.class, it.itemId());
                throw ErpException.business("Stok item \"" + (item != null ? item.getName() : it.itemId()) + "\" di "
                        + fromWh.getName() + " hanya " + available.setScale(3, RoundingMode.HALF_UP).toPlainString() + " "
                        + (item != null ? item.getUnit() : "") + ", tidak cukup untuk transfer "
                        + InvJson.js(it.quantity()) + ".");
            }
        }

        StockTransfer transfer = new StockTransfer();
        transfer.setTransferNumber(transferNumber);
        transfer.setDate(date);
        transfer.setFromWarehouseId(body.fromWarehouseId());
        transfer.setToWarehouseId(body.toWarehouseId());
        transfer.setStatus(DocumentStatus.Submitted);
        transfer.setNotes(emptyToNull(body.notes()));
        transfer.setNomorMobil(body.nomorMobil());
        transfer.setDriverName(body.driverName());
        transfer.setRecipientName(body.recipientName());
        transfer.setFiscalYearId(fy.getId());
        transfer.setCreatedBy(userId);
        transfer.setSubmittedAt(Instant.now());
        em.persist(transfer);
        List<StockTransferItem> lines = new ArrayList<>();
        for (StockTransferRequest.Item it : body.items()) {
            StockTransferItem ti = new StockTransferItem();
            ti.setTransferId(transfer.getId());
            ti.setItemId(it.itemId());
            ti.setQuantity(it.quantity());
            ti.setDescription(emptyToNull(it.description()));
            ti.setSourceMovementId(emptyToNull(it.sourceMovementId()));
            ti.setCreatedAt(Instant.now());
            em.persist(ti);
            lines.add(ti);
        }
        em.flush();

        // Sesuaikan stok + buat pasangan gerakan per baris.
        for (StockTransferRequest.Item it : body.items()) {
            InventoryItem item = em.find(InventoryItem.class, it.itemId());
            if (item == null) {
                throw ErpException.business("Item tidak ditemukan.");
            }
            BigDecimal qty = it.quantity();
            // Batch terpilih mengunci unitCost ke harga batch agar buku konsumsi batch tetap konsisten.
            BigDecimal unitCost = Money.nz(item.getAverageCost());
            String sourceId = emptyToNull(it.sourceMovementId());
            if (sourceId != null) {
                StockMovement batch = em.find(StockMovement.class, sourceId);
                if (batch == null) {
                    throw ErpException.business("Batch sumber tidak ditemukan untuk item \"" + item.getName() + "\".");
                }
                if (!batch.getItemId().equals(item.getId())) {
                    throw ErpException.business("Batch sumber tidak cocok dengan item \"" + item.getName() + "\".");
                }
                if (Boolean.TRUE.equals(batch.getIsCancelled())) {
                    throw ErpException.business("Batch sumber \"" + item.getName() + "\" sudah dibatalkan.");
                }
                StockMovementType bt = batch.getMovementType();
                if (bt != StockMovementType.In && bt != StockMovementType.AdjustmentIn && bt != StockMovementType.TransferIn) {
                    throw ErpException.business("Batch sumber untuk \"" + item.getName() + "\" bukan stok masuk.");
                }
                unitCost = Money.nz(batch.getUnitCost());
            }
            BigDecimal totalValue = Money.round2(qty.multiply(unitCost));

            stock.adjustWarehouseStock(it.itemId(), body.fromWarehouseId(), qty.negate());
            stock.adjustWarehouseStock(it.itemId(), body.toWarehouseId(), qty);

            StockMovement out = movement(numbers.generate("SM", date, fy.getId()), date, it.itemId(),
                    body.fromWarehouseId(), StockMovementType.TransferOut, qty, unitCost, totalValue,
                    "Transfer ke " + toWh.getName(), transfer, fy.getId(), userId);
            // Tautan balik batch untuk buku konsumsi batch.
            out.setSourceMovementId(sourceId);
            em.persist(out);
            em.flush();
            StockMovement in = movement(numbers.generate("SM", date, fy.getId()), date, it.itemId(),
                    body.toWarehouseId(), StockMovementType.TransferIn, qty, unitCost, totalValue,
                    "Transfer dari " + fromWh.getName() + " (ref: " + out.getMovementNumber() + ")", transfer,
                    fy.getId(), userId);
            em.persist(in);
            em.flush();
        }
        log.info("Stock transfer submitted transferNumber={} items={}", transferNumber, body.items().size());

        Map<String, Object> m = InvJson.scalars(transfer);
        m.put("items", lines.stream().map(InvJson::scalars).toList());
        return m;
    }

    private static StockMovement movement(String number, Instant date, String itemId, String warehouseId,
                                          StockMovementType type, BigDecimal qty, BigDecimal unitCost,
                                          BigDecimal totalValue, String notes, StockTransfer transfer,
                                          String fiscalYearId, String userId) {
        StockMovement m = new StockMovement();
        m.setMovementNumber(number);
        m.setDate(date);
        m.setItemId(itemId);
        m.setWarehouseId(warehouseId);
        m.setMovementType(type);
        m.setQuantity(qty);
        m.setUnitCost(unitCost);
        m.setTotalValue(totalValue);
        m.setNotes(notes);
        m.setReferenceType("StockTransfer");
        m.setReferenceId(transfer.getId());
        m.setReferenceNumber(transfer.getTransferNumber());
        m.setFiscalYearId(fiscalYearId);
        m.setCreatedById(userId);
        return m;
    }

    // ───────────────────────────── header / print / batal ─────────────────────────────

    /** PATCH /stock-transfers/:id — edit field header saja (catatan, mobil, supir, penerima). */
    @ErpTx
    public Map<String, Object> updateHeader(String id, StockTransferHeaderRequest body) {
        StockTransfer t = em.find(StockTransfer.class, id);
        if (t == null) {
            throw ErpException.notFound("Transfer tidak ditemukan.");
        }
        if (t.getStatus() != DocumentStatus.Submitted) {
            throw ErpException.business("Hanya transfer status Submitted yang bisa diedit.");
        }
        // `??` di sumber: null/tidak dikirim = pertahankan nilai lama.
        if (body.notes() != null) {
            t.setNotes(body.notes());
        }
        if (body.nomorMobil() != null) {
            t.setNomorMobil(body.nomorMobil());
        }
        if (body.driverName() != null) {
            t.setDriverName(body.driverName());
        }
        if (body.recipientName() != null) {
            t.setRecipientName(body.recipientName());
        }
        t.setUpdatedAt(Instant.now());
        em.flush();
        return InvJson.scalars(t);
    }

    /** POST /stock-transfers/:id/mark-printed — catat waktu SJG dicetak. */
    @ErpTx
    public Map<String, Object> markPrinted(String id) {
        StockTransfer t = em.find(StockTransfer.class, id);
        if (t == null) {
            // P2025 di sumber → handleRouteError.
            throw ErpException.business("Data terkait tidak ditemukan. Periksa referensi yang dipilih.");
        }
        t.setPrintedAt(Instant.now());
        em.flush();
        return InvJson.scalars(t);
    }

    /** PATCH /stock-transfers/:id/cancel — pulihkan kedua gudang dan batalkan pasangan gerakan. */
    @ErpTx
    public Map<String, Object> cancel(String id) {
        List<StockTransfer> rows = em.createQuery("select t from StockTransfer t left join fetch t.items where t.id = :id",
                StockTransfer.class).setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.business("Transfer tidak ditemukan.");
        }
        StockTransfer t = rows.get(0);
        if (t.getStatus() == DocumentStatus.Cancelled) {
            throw ErpException.business("Transfer sudah dibatalkan.");
        }
        for (StockTransferItem ti : t.getItems()) {
            BigDecimal qty = Money.nz(ti.getQuantity());
            stock.adjustWarehouseStock(ti.getItemId(), t.getFromWarehouseId(), qty);
            stock.adjustWarehouseStock(ti.getItemId(), t.getToWarehouseId(), qty.negate());
        }
        em.createQuery("update StockMovement m set m.isCancelled = true, m.updatedAt = :now "
                        + "where m.referenceType = 'StockTransfer' and m.referenceId = :id")
                .setParameter("now", Instant.now()).setParameter("id", id).executeUpdate();
        t.setStatus(DocumentStatus.Cancelled);
        t.setCancelledAt(Instant.now());
        em.flush();
        Map<String, Object> m = new LinkedHashMap<>(InvJson.scalars(t));
        return m;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
