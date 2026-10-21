package id.enterpriseapps.erp.inventory;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpAccountBalanceService;
import id.enterpriseapps.erp.core.accounting.ErpDocumentNumberService;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.accounting.SystemAccountRole;
import id.enterpriseapps.erp.core.inventory.ErpWarehouseStockService;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.InventoryItem;
import id.enterpriseapps.erp.entity.StockMovement;
import id.enterpriseapps.erp.entity.StockMovementType;
import id.enterpriseapps.erp.entity.StockOpname;
import id.enterpriseapps.erp.entity.StockOpnameItem;
import id.enterpriseapps.erp.entity.StockOpnameStatus;
import id.enterpriseapps.erp.entity.Warehouse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stok opname — port {@code routes/stockOpname.ts} + {@code services/stockOpnameList.ts}.
 *
 * <p>Satu sesi = satu gudang. POST membuat sekaligus men-submit (finalisasi): selisih qty menjadi
 * gerakan AdjustmentIn/AdjustmentOut (referenceType StockOpname) dan satu jurnal {@code JV-<nomor>}:
 * surplus DR Persediaan / CR Selisih Persediaan, defisit sebaliknya. Nilai satuan manual yang berbeda
 * dari averageCost merevaluasi item (selisih nilai × stok sebelum penyesuaian) ke Selisih Persediaan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpStockOpnameService {

    static final List<String> SORT_FIELDS = List.of("date", "opnameNumber");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);
    /** referenceType gerakan yang ditulis POST /stock-opname. */
    static final String OPNAME_REFERENCE_TYPE = "StockOpname";

    private final ErpWarehouseStockService stock;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpSystemAccountService systemAccounts;
    private final ErpDocumentNumberService numbers;
    private final ErpFiscalYearService fiscalYears;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── daftar ─────────────────────────────

    /**
     * Filter daftar (port buildStockOpnameWhere). Gudang cocok bila header, gerakan penyesuaian
     * ({@code warehouseOpnameIds}) atau baris opname mengenai gudang tsb (sesi lama tanpa kolom header).
     */
    static JpqlWhere where(Map<String, String> query, List<String> warehouseOpnameIds) {
        JpqlWhere w = new JpqlWhere();
        String status = ListQuery.str(query, "status");
        if (status != null) {
            StockOpnameStatus st;
            try {
                st = StockOpnameStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw ErpException.business("Status stok opname tidak dikenal.");
            }
            w.and("o.status = :status", "status", st);
        }
        StockMovementList.dateRange(w, query, "o.date");
        String warehouseId = ListQuery.str(query, "warehouseId");
        if (warehouseId != null) {
            w.and("o.warehouseId = :wh or o.id in :whIds or exists (select 1 from StockOpnameItem x "
                            + "where x.stockOpnameId = o.id and x.warehouseId = :wh)",
                    "wh", warehouseId,
                    "whIds", ErpQuery.nonEmpty(warehouseOpnameIds == null ? List.of() : warehouseOpnameIds, "__none__"));
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            w.and("lower(o.opnameNumber) like :search or lower(o.notes) like :search or lower(cb.fullName) like :search",
                    "search", JpqlWhere.contains(search));
        }
        return w;
    }

    static String orderBy(ListQuery.Sort sort) {
        String primary = "o." + sort.field() + " " + sort.dir();
        return "opnameNumber".equals(sort.field()) ? primary : primary + ", o.opnameNumber " + sort.dir();
    }

    /**
     * GET /stock-opname — tiap baris membawa totalVariance + itemsWithDiff; {@code warehouses} hanya
     * bila {@code page} dikirim (list kit), agar respons pemanggil lama tetap sama.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 20, 100);
        String warehouseId = ListQuery.str(query, "warehouseId");
        List<String> warehouseOpnameIds = warehouseId == null ? null : em.createQuery(
                        "select distinct m.referenceId from StockMovement m where m.referenceType = :rt "
                                + "and m.warehouseId = :wh and m.referenceId is not null", String.class)
                .setParameter("rt", OPNAME_REFERENCE_TYPE).setParameter("wh", warehouseId).getResultList();
        JpqlWhere where = where(query, warehouseOpnameIds);
        String order = ListQuery.str(query, "sort") != null
                ? orderBy(ListQuery.sort(query.get("sort"), SORT_FIELDS, DEFAULT_SORT))
                : "o.date desc";
        ErpQuery<StockOpname> q = where.applyTo(ErpQuery.of(em, StockOpname.class, "o")
                .join("left join o.createdBy cb")
                .fetch("left join fetch o.createdBy", "left join fetch o.warehouse")
                .orderBy(order));
        long total = q.count();
        List<StockOpname> page = q.list(paging.skip(), paging.limit());
        List<String> ids = page.stream().map(StockOpname::getId).toList();

        Map<String, Long> itemCounts = new HashMap<>();
        Map<String, BigDecimal> variance = new HashMap<>();
        Map<String, Long> withDiff = new HashMap<>();
        Map<String, List<Map<String, Object>>> movementWarehouses = new HashMap<>();
        boolean withWarehouses = query.containsKey("page");
        if (!ids.isEmpty()) {
            for (Object[] r : em.createQuery("select x.stockOpnameId, x.difference, x.totalValue from StockOpnameItem x "
                    + "where x.stockOpnameId in :ids", Object[].class).setParameter("ids", ids).getResultList()) {
                String sid = (String) r[0];
                itemCounts.merge(sid, 1L, Long::sum);
                variance.merge(sid, Money.nz((BigDecimal) r[2]).abs(), BigDecimal::add);
                if (Money.nz((BigDecimal) r[1]).signum() != 0) {
                    withDiff.merge(sid, 1L, Long::sum);
                }
            }
            if (withWarehouses) {
                List<StockMovement> movs = em.createQuery("select m from StockMovement m left join fetch m.warehouse "
                                + "where m.referenceType = :rt and m.referenceId in :ids order by m.createdAt asc", StockMovement.class)
                        .setParameter("rt", OPNAME_REFERENCE_TYPE).setParameter("ids", ids).getResultList();
                for (StockMovement m : movs) {
                    if (m.getReferenceId() == null || m.getWarehouse() == null) {
                        continue;
                    }
                    List<Map<String, Object>> list = movementWarehouses.computeIfAbsent(m.getReferenceId(), k -> new ArrayList<>());
                    addWarehouse(list, InvJson.pick(m.getWarehouse(), "id", "code", "name"));
                }
            }
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (StockOpname so : page) {
            Map<String, Object> row = InvJson.scalars(so);
            row.put("createdBy", InvJson.pick(so.getCreatedBy(), "fullName"));
            Map<String, Object> header = InvJson.pick(so.getWarehouse(), "id", "code", "name");
            row.put("warehouse", header);
            row.put("_count", Map.of("items", itemCounts.getOrDefault(so.getId(), 0L)));
            row.put("totalVariance", variance.getOrDefault(so.getId(), BigDecimal.ZERO));
            row.put("itemsWithDiff", withDiff.getOrDefault(so.getId(), 0L));
            if (withWarehouses) {
                // Gudang header lebih dulu, lalu gudang lain yang disentuh gerakan (tanpa duplikat).
                List<Map<String, Object>> merged = new ArrayList<>();
                if (header != null) {
                    merged.add(header);
                }
                for (Map<String, Object> w : movementWarehouses.getOrDefault(so.getId(), List.of())) {
                    addWarehouse(merged, w);
                }
                row.put("warehouses", merged);
            }
            data.add(row);
        }
        return ListQuery.result(data, total, paging);
    }

    private static void addWarehouse(List<Map<String, Object>> list, Map<String, Object> w) {
        if (list.stream().noneMatch(x -> x.get("id").equals(w.get("id")))) {
            list.add(w);
        }
    }

    /** GET /stock-opname/:id — detail + baris (item ringkas) urut kode item. */
    @ErpTx(readOnly = true)
    public Map<String, Object> detail(String id) {
        List<StockOpname> rows = em.createQuery("select o from StockOpname o left join fetch o.createdBy "
                + "left join fetch o.warehouse where o.id = :id", StockOpname.class).setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("Stok opname tidak ditemukan.");
        }
        StockOpname so = rows.get(0);
        Map<String, Object> m = InvJson.scalars(so);
        m.put("createdBy", InvJson.pick(so.getCreatedBy(), "fullName"));
        m.put("warehouse", InvJson.pick(so.getWarehouse(), "id", "code", "name"));
        m.put("items", lines(id, "id", "name", "code", "unit", "currentStock", "averageCost"));
        return m;
    }

    private List<Map<String, Object>> lines(String opnameId, String... itemFields) {
        return em.createQuery("select x from StockOpnameItem x join fetch x.item it where x.stockOpnameId = :id "
                        + "order by it.code asc", StockOpnameItem.class)
                .setParameter("id", opnameId).getResultList().stream().map(x -> {
                    Map<String, Object> lm = InvJson.scalars(x);
                    lm.put("item", InvJson.pick(x.getItem(), itemFields));
                    return lm;
                }).toList();
    }

    // ───────────────────────────── buat + submit ─────────────────────────────

    private record Line(String itemId, BigDecimal systemStock, BigDecimal actualStock, BigDecimal difference,
                        BigDecimal unitCost, BigDecimal totalValue, String notes) {
    }

    private record Revaluation(String itemId, BigDecimal oldCost, BigDecimal newCost) {
    }

    private record JLine(String accountId, BigDecimal debit, BigDecimal credit, String description) {
    }

    /** POST /stock-opname — buat dan submit dalam satu langkah. */
    @ErpTx(timeout = 30)
    public Map<String, Object> create(StockOpnameRequest body, String userId) {
        Instant date = ErpDates.toInstant(body.date());
        FiscalYear fy = fiscalYears.findOpen(date)
                .orElseThrow(() -> ErpException.business("Tidak ada tahun fiskal aktif untuk tanggal ini."));

        Long drafts = em.createQuery("select count(o) from StockOpname o where o.status = :d", Long.class)
                .setParameter("d", StockOpnameStatus.Draft).getSingleResult();
        if (drafts > 0) {
            throw ErpException.business("Masih ada stok opname Draft yang belum selesai.");
        }
        String opnameNumber = numbers.generate("SO", date, fy.getId());

        // Stok sistem: saldo gudang terpilih (opname hanya memengaruhi gudang itu), selain itu total global.
        List<String> itemIds = body.items().stream().map(StockOpnameRequest.Item::itemId).toList();
        Map<String, InventoryItem> itemMap = new HashMap<>();
        em.createQuery("select i from InventoryItem i where i.id in :ids", InventoryItem.class)
                .setParameter("ids", itemIds).getResultList().forEach(i -> itemMap.put(i.getId(), i));
        for (StockOpnameRequest.Item in : body.items()) {
            if (!itemMap.containsKey(in.itemId())) {
                throw ErpException.business("Item dengan ID " + in.itemId() + " tidak ditemukan.");
            }
        }
        String bodyWarehouse = body.warehouseId() == null || body.warehouseId().isEmpty() ? null : body.warehouseId();
        Map<String, BigDecimal> perWarehouse = new HashMap<>();
        if (bodyWarehouse != null) {
            em.createQuery("select s.itemId, s.currentStock from InventoryStock s where s.warehouseId = :w "
                            + "and s.itemId in :ids", Object[].class)
                    .setParameter("w", bodyWarehouse).setParameter("ids", itemIds).getResultList()
                    .forEach(r -> perWarehouse.put((String) r[0], (BigDecimal) r[1]));
        }

        // Revaluasi: item yang nilai satuannya diubah manual lewat form opname.
        List<Revaluation> revaluations = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        for (StockOpnameRequest.Item in : body.items()) {
            InventoryItem item = itemMap.get(in.itemId());
            BigDecimal systemStock = bodyWarehouse != null
                    ? perWarehouse.getOrDefault(in.itemId(), BigDecimal.ZERO)
                    : Money.nz(item.getCurrentStock());
            BigDecimal actual = in.actualStock();
            BigDecimal diff = actual.subtract(systemStock).setScale(3, RoundingMode.HALF_UP);
            // Tolak negatif hanya bila hitungannya diubah: form mengirim semua item, item yang memang
            // sudah minus di sistem tidak boleh memblokir submit.
            if (actual.signum() < 0 && diff.signum() != 0) {
                throw ErpException.business("Stok aktual \"" + item.getName() + "\" tidak boleh negatif.");
            }
            BigDecimal oldCost = Money.nz(item.getAverageCost());
            boolean manual = in.unitCost() != null;
            BigDecimal unitCost = manual ? Money.round2(in.unitCost()) : oldCost;
            if (manual && unitCost.compareTo(oldCost) != 0) {
                revaluations.add(new Revaluation(in.itemId(), oldCost, unitCost));
            }
            BigDecimal totalValue = Money.round2(diff.abs().multiply(unitCost));
            lines.add(new Line(in.itemId(), systemStock, actual, diff, unitCost, totalValue,
                    in.notes() == null || in.notes().isEmpty() ? null : in.notes()));
        }
        List<Line> withDiff = lines.stream().filter(l -> l.difference().signum() != 0).toList();
        // Perlu diproses bila ada selisih qty ATAU revaluasi nilai.
        boolean needsProcessing = !withDiff.isEmpty() || !revaluations.isEmpty();

        // Satu sesi = satu gudang: pilihan UI, fallback gudang default. Header & semua baris menyimpannya
        // agar daftar tetap menampilkan Gudang walau tanpa selisih.
        String opnameWarehouseId = stock.resolveWarehouseId(bodyWarehouse);
        if (bodyWarehouse != null && em.find(Warehouse.class, bodyWarehouse) == null) {
            throw ErpException.business("Referensi data tidak valid. Periksa data yang dipilih.");
        }

        StockOpname so = new StockOpname();
        so.setOpnameNumber(opnameNumber);
        so.setDate(date);
        so.setStatus(StockOpnameStatus.Submitted);
        so.setNotes(body.notes() == null || body.notes().isEmpty() ? null : body.notes());
        so.setWarehouseId(opnameWarehouseId);
        so.setFiscalYearId(fy.getId());
        so.setCreatedById(userId);
        so.setSubmittedAt(Instant.now());
        em.persist(so);
        List<StockOpnameItem> opItems = new ArrayList<>();
        for (Line l : lines) {
            StockOpnameItem x = new StockOpnameItem();
            x.setStockOpnameId(so.getId());
            x.setItemId(l.itemId());
            x.setWarehouseId(opnameWarehouseId);
            x.setSystemStock(l.systemStock());
            x.setActualStock(l.actualStock());
            x.setDifference(l.difference());
            x.setUnitCost(l.unitCost());
            x.setTotalValue(l.totalValue());
            x.setNotes(l.notes());
            em.persist(x);
            opItems.add(x);
        }
        em.flush();

        if (needsProcessing) {
            String defaultInv = systemAccounts.getAccountId(SystemAccountRole.INVENTORY);
            String varianceAcc = systemAccounts.getAccountId(SystemAccountRole.INVENTORY_VARIANCE);
            // Penyesuaian butuh gudang: tanpa default, pengguna wajib memilih (multi gudang = satu sesi per gudang).
            if (opnameWarehouseId == null) {
                throw ErpException.business(
                        "Gudang opname wajib dipilih. Pilih gudang di form atau set salah satu gudang sebagai default.");
            }
            List<JLine> journal = new ArrayList<>();

            // Revaluasi: averageCost global per item → selisih nilai dijurnal atas seluruh stok item pada qty
            // sebelum penyesuaian; selisih qty di bawah memakai harga baru.
            for (Revaluation rev : revaluations) {
                InventoryItem item = itemMap.get(rev.itemId());
                BigDecimal delta = Money.round2(Money.nz(item.getCurrentStock())
                        .multiply(rev.newCost().subtract(rev.oldCost())));
                stock.setAverageCost(rev.itemId(), rev.newCost());
                if (delta.signum() == 0) {
                    continue;
                }
                String inv = item.getAccountId() != null ? item.getAccountId() : defaultInv;
                String desc = "Revaluasi nilai " + item.getName() + " (" + InvJson.js(rev.oldCost()) + " -> "
                        + InvJson.js(rev.newCost()) + "): " + opnameNumber;
                if (delta.signum() > 0) {
                    journal.add(new JLine(inv, delta, BigDecimal.ZERO, desc));
                    journal.add(new JLine(varianceAcc, BigDecimal.ZERO, delta, desc));
                } else {
                    journal.add(new JLine(varianceAcc, delta.negate(), BigDecimal.ZERO, desc));
                    journal.add(new JLine(inv, BigDecimal.ZERO, delta.negate(), desc));
                }
            }

            Map<String, String> movementByItem = new LinkedHashMap<>();
            for (Line l : withDiff) {
                InventoryItem item = itemMap.get(l.itemId());
                boolean increase = l.difference().signum() > 0;
                StockMovement m = new StockMovement();
                m.setMovementNumber(numbers.generate("SM", date, fy.getId()));
                m.setDate(date);
                m.setItemId(l.itemId());
                m.setWarehouseId(opnameWarehouseId);
                m.setMovementType(increase ? StockMovementType.AdjustmentIn : StockMovementType.AdjustmentOut);
                m.setQuantity(l.difference().abs());
                m.setUnitCost(l.unitCost());
                m.setTotalValue(l.totalValue());
                m.setReferenceType(OPNAME_REFERENCE_TYPE);
                m.setReferenceId(so.getId());
                m.setReferenceNumber(opnameNumber);
                m.setOffsetAccountId(varianceAcc);
                m.setNotes("Stok opname " + opnameNumber + ": " + item.getName());
                m.setFiscalYearId(fy.getId());
                m.setCreatedById(userId);
                em.persist(m);
                em.flush();
                movementByItem.put(l.itemId(), m.getId());

                // Stok total item + per gudang.
                stock.adjustItemStock(l.itemId(), l.difference());
                stock.adjustWarehouseStock(l.itemId(), opnameWarehouseId, l.difference());

                String inv = item.getAccountId() != null ? item.getAccountId() : defaultInv;
                if (increase) {
                    // Surplus: DR Persediaan / CR Selisih Persediaan.
                    String desc = "Surplus stok " + item.getName() + ": " + opnameNumber;
                    journal.add(new JLine(inv, l.totalValue(), BigDecimal.ZERO, desc));
                    journal.add(new JLine(varianceAcc, BigDecimal.ZERO, l.totalValue(), desc));
                } else {
                    // Defisit: DR Selisih Persediaan / CR Persediaan.
                    String desc = "Defisit stok " + item.getName() + ": " + opnameNumber;
                    journal.add(new JLine(varianceAcc, l.totalValue(), BigDecimal.ZERO, desc));
                    journal.add(new JLine(inv, BigDecimal.ZERO, l.totalValue(), desc));
                }
            }

            // Jurnal JV-<nomor opname>. Baris bernilai nol tidak diposting (kernel menolak jurnal tanpa nilai).
            boolean hasValue = journal.stream().anyMatch(j -> j.debit().signum() != 0 || j.credit().signum() != 0);
            if (hasValue) {
                ErpGlPostingService.Builder b = gl.journal().number("JV-" + opnameNumber).date(date)
                        .narration("Penyesuaian stok opname: " + opnameNumber).fiscalYear(fy.getId()).createdBy(userId);
                for (JLine j : journal) {
                    b.line(j.accountId(), j.debit(), j.credit(), j.description());
                }
                b.post();
                // Saldo dihitung ulang dari ledger untuk akun terdampak (mencegah drift).
                Set<String> affected = new LinkedHashSet<>();
                journal.forEach(j -> affected.add(j.accountId()));
                balances.recalculateAccountBalances(affected);
            }

            for (StockOpnameItem x : opItems) {
                String movementId = movementByItem.get(x.getItemId());
                if (movementId != null) {
                    x.setMovementId(movementId);
                }
            }
            em.flush();
        }
        log.info("Stock opname submitted number={} lines={} adjustments={}", opnameNumber, lines.size(), withDiff.size());

        em.clear();
        StockOpname fresh = em.createQuery("select o from StockOpname o left join fetch o.createdBy where o.id = :id",
                StockOpname.class).setParameter("id", so.getId()).getSingleResult();
        Map<String, Object> out = InvJson.scalars(fresh);
        out.put("createdBy", InvJson.pick(fresh.getCreatedBy(), "fullName"));
        out.put("items", lines(fresh.getId(), "id", "name", "code", "unit"));
        return out;
    }

    // ───────────────────────────── batal ─────────────────────────────

    /** PUT /stock-opname/:id/cancel — balik semua penyesuaian stok & jurnal (termasuk revisinya). */
    @ErpTx(timeout = 15)
    public Map<String, Object> cancel(String id) {
        StockOpname so = em.find(StockOpname.class, id);
        if (so == null) {
            throw ErpException.business("Stok opname tidak ditemukan.");
        }
        if (so.getStatus() == StockOpnameStatus.Cancelled) {
            throw ErpException.business("Stok opname sudah dibatalkan.");
        }
        if (so.getStatus() == StockOpnameStatus.Draft) {
            throw ErpException.business("Stok opname Draft tidak perlu dibatalkan.");
        }
        List<StockOpnameItem> items = em.createQuery("select x from StockOpnameItem x join fetch x.item "
                + "where x.stockOpnameId = :id", StockOpnameItem.class).setParameter("id", id).getResultList();
        for (StockOpnameItem x : items) {
            if (x.getMovementId() == null) {
                continue;
            }
            BigDecimal diff = Money.nz(x.getDifference());
            if (diff.signum() == 0) {
                continue;
            }
            // Pembalikan surplus mengurangi stok: stok harus cukup.
            if (diff.signum() > 0 && Money.nz(x.getItem().getCurrentStock()).compareTo(diff) < 0) {
                throw ErpException.business("Tidak dapat membatalkan: stok '" + x.getItem().getName()
                        + "' tidak mencukupi untuk pembalikan.");
            }
            stock.adjustItemStock(x.getItemId(), diff.negate());
            StockMovement mov = em.find(StockMovement.class, x.getMovementId());
            if (mov != null) {
                stock.adjustWarehouseStock(x.getItemId(), mov.getWarehouseId(), diff.negate());
                mov.setIsCancelled(true);
            }
        }
        em.flush();

        // Batalkan SEMUA jurnal terkait (termasuk revisi) + ledger; akun terdampak dikumpulkan sebelumnya.
        String jvNumber = "JV-" + so.getOpnameNumber();
        List<String> affected = em.createQuery("select distinct i.accountId from JournalItem i, JournalEntry j "
                        + "where i.journalEntryId = j.id and j.entryNumber like :p "
                        + "and j.status <> id.enterpriseapps.erp.entity.DocumentStatus.Cancelled", String.class)
                .setParameter("p", ErpQuery.startsWith(jvNumber)).getResultList();
        gl.cancelJournalsByPrefix(jvNumber);
        if (!affected.isEmpty()) {
            balances.recalculateAccountBalances(affected);
        }
        so.setStatus(StockOpnameStatus.Cancelled);
        so.setCancelledAt(Instant.now());
        em.flush();
        return Map.of("message", "Stok opname dibatalkan.");
    }
}
