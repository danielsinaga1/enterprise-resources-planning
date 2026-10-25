package id.neravoxa.erp.inventory;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.StockMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Query riwayat gerakan stok — port {@code services/stockMovementList.ts}. Daftar dan KPI (summary)
 * berbagi {@link #where(Map)} sehingga kartu KPI menggambarkan baris yang sama dengan daftar.
 *
 * <p>Filter: itemId, warehouseId, movementType, status (active | cancelled), startDate/endDate
 * (YYYY-MM-DD, hari bisnis), search (no. gerakan, no. referensi, catatan, kode/nama item).
 * Alias: {@code m} = StockMovement, {@code it} = item (join wajib {@link #JOIN}).
 */
final class StockMovementList {

    static final String JOIN = "join m.item it";
    static final List<String> SORT_FIELDS = List.of("date", "movementNumber", "item", "quantity", "totalValue");
    /** Urutan historis endpoint (dipertahankan bila tidak ada {@code sort}). */
    static final String LEGACY_ORDER = "m.movementNumber desc";
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("date", true);
    /** Tipe masuk (stok bertambah). */
    static final Set<StockMovementType> INBOUND = Set.of(StockMovementType.In, StockMovementType.AdjustmentIn,
            StockMovementType.TransferIn);

    private StockMovementList() {
    }

    static JpqlWhere where(Map<String, String> query) {
        JpqlWhere w = new JpqlWhere();
        String itemId = ListQuery.str(query, "itemId");
        if (itemId != null) {
            w.and("m.itemId = :itemId", "itemId", itemId);
        }
        String warehouseId = ListQuery.str(query, "warehouseId");
        if (warehouseId != null) {
            w.and("m.warehouseId = :warehouseId", "warehouseId", warehouseId);
        }
        String type = ListQuery.str(query, "movementType");
        if (type != null) {
            StockMovementType t;
            try {
                t = StockMovementType.valueOf(type);
            } catch (IllegalArgumentException e) {
                throw ErpException.business("Tipe gerakan stok tidak dikenal.");
            }
            w.and("m.movementType = :movementType", "movementType", t);
        }
        String status = ListQuery.str(query, "status");
        if (status != null) {
            if (!"active".equals(status) && !"cancelled".equals(status)) {
                throw ErpException.business("Status gerakan stok tidak dikenal.");
            }
            w.and("m.isCancelled = :cancelled", "cancelled", "cancelled".equals(status));
        }
        dateRange(w, query, "m.date");
        String search = ListQuery.str(query, "search");
        if (search != null) {
            w.and("lower(m.movementNumber) like :search or lower(m.referenceNumber) like :search "
                    + "or lower(m.notes) like :search or lower(it.name) like :search or lower(it.code) like :search",
                    "search", JpqlWhere.contains(search));
        }
        return w;
    }

    /** startDate/endDate YYYY-MM-DD → rentang hari bisnis [start, end+1). 400 bila formatnya salah. */
    static void dateRange(JpqlWhere w, Map<String, String> query, String path) {
        String start = ListQuery.str(query, "startDate");
        String end = ListQuery.str(query, "endDate");
        if ((start != null && !ErpDates.isIsoDate(start)) || (end != null && !ErpDates.isIsoDate(end))) {
            throw ErpException.business("Format tanggal harus YYYY-MM-DD.");
        }
        Instant[] range = ErpDates.localDayRange(start, end);
        if (range[0] != null) {
            w.and(path + " >= :dateFrom", "dateFrom", range[0]);
        }
        if (range[1] != null) {
            w.and(path + " < :dateTo", "dateTo", range[1]);
        }
    }

    /** ORDER BY JPQL; nomor gerakan (unik) sebagai pemecah seri. */
    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        String primary = "item".equals(sort.field()) ? "it.name " + dir : "m." + sort.field() + " " + dir;
        return "movementNumber".equals(sort.field()) ? primary : primary + ", m.movementNumber " + dir;
    }

    /**
     * KPI dari grup (movementType, isCancelled, count, Σqty, Σvalue): count = semua baris (termasuk
     * batal); in/out hanya yang tidak batal.
     */
    static Map<String, Object> summarize(List<Object[]> groups) {
        long count = 0;
        long cancelled = 0;
        long inCount = 0;
        long outCount = 0;
        BigDecimal inQty = BigDecimal.ZERO;
        BigDecimal outQty = BigDecimal.ZERO;
        BigDecimal inValue = BigDecimal.ZERO;
        BigDecimal outValue = BigDecimal.ZERO;
        for (Object[] g : groups) {
            long c = ((Number) g[2]).longValue();
            count += c;
            if (Boolean.TRUE.equals(g[1])) {
                cancelled += c;
                continue;
            }
            BigDecimal q = Money.nz((BigDecimal) g[3]);
            BigDecimal v = Money.nz((BigDecimal) g[4]);
            if (INBOUND.contains((StockMovementType) g[0])) {
                inCount += c;
                inQty = inQty.add(q);
                inValue = inValue.add(v);
            } else {
                outCount += c;
                outQty = outQty.add(q);
                outValue = outValue.add(v);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", count);
        m.put("cancelledCount", cancelled);
        m.put("inCount", inCount);
        m.put("inQty", Money.round(inQty, 3));
        m.put("inValue", Money.round2(inValue));
        m.put("outCount", outCount);
        m.put("outQty", Money.round(outQty, 3));
        m.put("outValue", Money.round2(outValue));
        return m;
    }
}
