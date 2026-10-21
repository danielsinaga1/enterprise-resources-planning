package id.enterpriseapps.erp.eam.ops;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.entity.GaInventoryItem;
import id.enterpriseapps.erp.entity.GaInventoryMovement;
import id.enterpriseapps.erp.entity.GaMovementType;
import id.enterpriseapps.erp.entity.GaStockByLocation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daftar inventaris GA & mutasinya — port {@code services/gaInventoryList.ts}. Stok item = jumlah baris
 * per lokasi, sehingga filter status stok, sortir, dan paging item dikerjakan di memori; daftar dan
 * kartu KPI memakai filter yang sama ({@link #itemFilter}).
 */
final class GaInventoryList {

    private GaInventoryList() {
    }

    /** out: habis (≤ 0) · low: di bawah minimum · ok: selain itu. */
    static final List<String> STOCK_STATES = List.of("out", "low", "ok");
    static final List<String> ITEM_SORT_FIELDS = List.of("sku", "name", "category", "totalStock", "minStock");
    static final ListQuery.Sort DEFAULT_ITEM_SORT = new ListQuery.Sort("sku", false);

    static final List<String> MOVEMENT_TYPES = List.of("IN", "OUT", "TRANSFER", "ADJUSTMENT");
    static final List<String> MOVEMENT_SORT_FIELDS = List.of("createdAt", "quantity");
    static final ListQuery.Sort DEFAULT_MOVEMENT_SORT = new ListQuery.Sort("createdAt", true);

    /** Ambang sama dengan dashboard: out = total ≤ 0, low = total < minStock. */
    static String stockState(BigDecimal total, BigDecimal min) {
        if (total.signum() <= 0) {
            return "out";
        }
        if (total.compareTo(min) < 0) {
            return "low";
        }
        return "ok";
    }

    /** Filter item ({@code buildGaItemFilter}); pesan error dilempar berurutan seperti sumber. */
    record ItemFilter(String categoryId, String search, Boolean isActive, String locationId, String stockStatus) {

        void apply(ErpQuery<GaInventoryItem> q) {
            q.whereIf(categoryId != null, "i.categoryId = :cat", "cat", categoryId)
                    .whereIf(search != null, "(lower(i.sku) like :s or lower(i.name) like :s)", "s",
                            search == null ? null : ErpQuery.contains(search))
                    .whereIf(isActive != null, "i.isActive = :act", "act", isActive)
                    .whereIf(locationId != null,
                            "exists (select 1 from GaStockByLocation sl where sl.itemId = i.id and sl.locationId = :loc)",
                            "loc", locationId);
        }
    }

    /**
     * search | q, categoryId, locationId (item yang punya baris stok di lokasi itu), isActive (true|false),
     * stockStatus (out|low|ok).
     */
    static ItemFilter itemFilter(Map<String, String> query) {
        String categoryId = ListQuery.str(query, "categoryId");
        String search = ListQuery.str(query, "search");
        if (search == null) {
            search = ListQuery.str(query, "q");
        }
        String isActiveRaw = ListQuery.str(query, "isActive");
        Boolean isActive = null;
        if (isActiveRaw != null) {
            if (!"true".equals(isActiveRaw) && !"false".equals(isActiveRaw)) {
                throw ErpException.business("Filter status item tidak dikenal.");
            }
            isActive = "true".equals(isActiveRaw);
        }
        String locationId = ListQuery.str(query, "locationId");
        String stockStatus = ListQuery.str(query, "stockStatus");
        if (stockStatus != null && !STOCK_STATES.contains(stockStatus)) {
            throw ErpException.business("Filter status stok tidak dikenal.");
        }
        return new ItemFilter(categoryId, search, isActive, locationId, stockStatus);
    }

    /** Baris daftar item + status stok (padanan {@code GaItemComputed}). */
    record Computed(Map<String, Object> row, String stockState) {
        String sku() {
            return (String) row.get("sku");
        }
    }

    /** Bentuk baris daftar lama (kunci & urutan sama) + {@code locationStock} opsional. */
    static Computed toRow(GaInventoryItem it, String locationId) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal at = BigDecimal.ZERO;
        for (GaStockByLocation s : it.getStocks()) {
            total = total.add(s.getQuantity());
            if (locationId != null && locationId.equals(s.getLocationId())) {
                at = at.add(s.getQuantity());
            }
        }
        BigDecimal min = it.getMinStock() == null ? BigDecimal.ZERO : it.getMinStock();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", it.getId());
        row.put("sku", it.getSku());
        row.put("name", it.getName());
        row.put("uom", it.getUom());
        row.put("minStock", GaOpsJson.num(min));
        row.put("imagePath", it.getImagePath());
        row.put("isActive", it.getIsActive());
        row.put("category", it.getCategory() == null ? null : GaOpsJson.map(
                "code", it.getCategory().getCode(), "name", it.getCategory().getName(),
                "type", String.valueOf(it.getCategory().getType())));
        row.put("totalStock", GaOpsJson.num(total));
        row.put("lowStock", total.compareTo(min) < 0);
        if (locationId != null) {
            row.put("locationStock", GaOpsJson.num(at));
        }
        return new Computed(row, stockState(total, min));
    }

    static List<Computed> filter(List<Computed> items, String stockStatus) {
        return stockStatus == null ? new ArrayList<>(items)
                : items.stream().filter(i -> stockStatus.equals(i.stockState())).toList();
    }

    private static Comparable<?> sortValue(Map<String, Object> r, String field) {
        return switch (field) {
            case "name" -> (String) r.get("name");
            case "category" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> c = (Map<String, Object>) r.get("category");
                yield c == null ? "" : (String) c.get("name");
            }
            case "totalStock" -> (BigDecimal) r.get("totalStock");
            case "minStock" -> (BigDecimal) r.get("minStock");
            default -> (String) r.get("sku");
        };
    }

    /**
     * Baris datang dalam urutan SKU dari database (orderBy lama). Sortir SKU mempertahankan urutan itu
     * (dibalik untuk desc) sehingga halaman bawaan identik; field lain jatuh ke urutan itu saat seri.
     */
    static List<Computed> sort(List<Computed> items, ListQuery.Sort sort) {
        List<Computed> out = new ArrayList<>(items);
        if ("sku".equals(sort.field())) {
            if (sort.desc()) {
                java.util.Collections.reverse(out);
            }
            return out;
        }
        int sign = sort.desc() ? -1 : 1;
        Comparator<Computed> cmp = (a, b) -> {
            Object va = sortValue(a.row(), sort.field());
            Object vb = sortValue(b.row(), sort.field());
            int c;
            if (va instanceof BigDecimal x && vb instanceof BigDecimal y) {
                c = x.compareTo(y);
            } else {
                c = GaOpsJson.naturalCompare(String.valueOf(va), String.valueOf(vb));
            }
            return sign * c;
        };
        out.sort(cmp); // List.sort stabil → seri tetap urutan SKU
        return out;
    }

    static Map<String, Object> summarize(List<Computed> items) {
        int count = 0;
        int active = 0;
        int ok = 0;
        int low = 0;
        int out = 0;
        for (Computed c : items) {
            count++;
            if (Boolean.TRUE.equals(c.row().get("isActive"))) {
                active++;
            }
            switch (c.stockState()) {
                case "ok" -> ok++;
                case "low" -> low++;
                default -> out++;
            }
        }
        return GaOpsJson.map("count", count, "activeCount", active, "okCount", ok, "lowCount", low, "outCount", out);
    }

    /**
     * Filter mutasi berpaginasi ({@code buildGaMovementWhere}): type, itemId, locationId (asal atau tujuan),
     * startDate/endDate (zona bisnis), search (SKU/nama item, referensi, catatan).
     */
    static void movementWhere(ErpQuery<GaInventoryMovement> q, Map<String, String> query) {
        String type = ListQuery.str(query, "type");
        if (type != null) {
            if (!MOVEMENT_TYPES.contains(type)) {
                throw ErpException.business("Tipe pergerakan tidak dikenal.");
            }
            q.where("m.type = :type", "type", GaMovementType.valueOf(type));
        }
        String itemId = ListQuery.str(query, "itemId");
        q.whereIf(itemId != null, "m.itemId = :itemId", "itemId", itemId);
        String locationId = ListQuery.str(query, "locationId");
        q.whereIf(locationId != null, "(m.fromLocationId = :loc or m.toLocationId = :loc)", "loc", locationId);
        String start = ListQuery.dateParam(query, "startDate", "Format tanggal harus YYYY-MM-DD.");
        String end = ListQuery.dateParam(query, "endDate", "Format tanggal harus YYYY-MM-DD.");
        Instant[] range = ErpDates.localDayRange(start, end);
        q.whereIf(range[0] != null, "m.createdAt >= :from", "from", range[0]);
        q.whereIf(range[1] != null, "m.createdAt < :to", "to", range[1]);
        String search = ListQuery.str(query, "search");
        if (search != null) {
            q.where("(lower(mi.sku) like :s or lower(mi.name) like :s or lower(m.referenceNo) like :s "
                    + "or lower(m.notes) like :s)", "s", ErpQuery.contains(search));
        }
    }

    static String movementOrderBy(ListQuery.Sort sort) {
        return "quantity".equals(sort.field())
                ? "m.quantity " + sort.dir() + ", m.createdAt desc, m.id asc"
                : "m.createdAt " + sort.dir() + ", m.id asc";
    }
}
