package id.enterpriseapps.erp.inventory;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.Money;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Daftar item stok, difilter & diurutkan di memori — port {@code services/inventoryItemList.ts}.
 * Dengan {@code warehouseId} stok = saldo per gudang (tidak bisa diurutkan di DB). Pemanggil yang
 * tidak mengirim sort/stockStatus/category (picker, AIRA) mendapat perilaku lama ({@link #usesListMode}).
 */
final class InventoryItemList {

    static final List<String> SORT_FIELDS = List.of("code", "name", "category", "currentStock", "minimumStock", "stockValue");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("currentStock", true);
    static final List<String> STOCK_STATUSES = List.of("out", "low", "ok");

    private InventoryItemList() {
    }

    record Filters(String search, Boolean isActive, String warehouseId, String category, String stockStatus) {
    }

    /** Satu baris daftar: JSON (bisa diubah) + nilai untuk filter/sort. */
    record Row(Map<String, Object> json, String code, String name, String category, Boolean isActive,
               BigDecimal currentStock, BigDecimal minimumStock, BigDecimal averageCost) {
    }

    /** Status stok: out (≤ 0), low (di bawah minimum yang diisi), ok. */
    static String stockStatus(BigDecimal currentStock, BigDecimal minimumStock) {
        BigDecimal cur = Money.nz(currentStock);
        BigDecimal min = Money.nz(minimumStock);
        if (cur.signum() <= 0) {
            return "out";
        }
        if (min.signum() > 0 && cur.compareTo(min) < 0) {
            return "low";
        }
        return "ok";
    }

    /** Parse filter; 400 dengan pesan ENTARIS bila tidak valid. */
    static Filters parse(Map<String, String> query) {
        Boolean isActive = null;
        String a = ListQuery.str(query, "isActive");
        if (a != null) {
            if (!"true".equals(a) && !"false".equals(a)) {
                throw ErpException.business("Filter isActive harus true atau false.");
            }
            isActive = "true".equals(a);
        }
        String status = ListQuery.str(query, "stockStatus");
        if (status != null && !STOCK_STATUSES.contains(status)) {
            throw ErpException.business("Status stok tidak dikenal.");
        }
        return new Filters(ListQuery.str(query, "search"), isActive, ListQuery.str(query, "warehouseId"),
                ListQuery.str(query, "category"), status);
    }

    /** True bila pemanggil memakai parameter halaman daftar (mode sort/filter baru). */
    static boolean usesListMode(Map<String, String> query) {
        return ListQuery.str(query, "sort") != null || ListQuery.str(query, "stockStatus") != null
                || ListQuery.str(query, "category") != null;
    }

    /** Bagian filter yang dijalankan di DB (search, aktif, kategori); alias item {@code i}. */
    static JpqlWhere dbWhere(Filters f) {
        JpqlWhere w = new JpqlWhere();
        if (f.isActive() != null) {
            w.and("i.isActive = :isActive", "isActive", f.isActive());
        }
        if (f.category() != null) {
            w.and("i.category = :category", "category", f.category());
        }
        if (f.search() != null) {
            w.and("lower(i.name) like :search or lower(i.code) like :search", "search", JpqlWhere.contains(f.search()));
        }
        return w;
    }

    static BigDecimal stockValue(Row r) {
        return Money.nz(r.currentStock()).multiply(Money.nz(r.averageCost()));
    }

    /** Urutkan salinan; seri dipecah kode menaik agar halaman tidak berpindah-pindah. */
    static List<Row> sort(List<Row> rows, ListQuery.Sort sort) {
        Collator coll = InvJson.idCollator();
        int sign = sort.desc() ? -1 : 1;
        Comparator<Row> primary = switch (sort.field()) {
            case "currentStock" -> Comparator.comparing(r -> Money.nz(r.currentStock()));
            case "minimumStock" -> Comparator.comparing(r -> Money.nz(r.minimumStock()));
            case "stockValue" -> Comparator.comparing(InventoryItemList::stockValue);
            case "category" -> (x, y) -> coll.compare(lower(x.category()), lower(y.category()));
            case "name" -> (x, y) -> coll.compare(lower(x.name()), lower(y.name()));
            default -> (x, y) -> coll.compare(x.code(), y.code());
        };
        List<Row> out = new ArrayList<>(rows);
        out.sort((x, y) -> {
            int p = primary.compare(x, y);
            if (p != 0) {
                return Integer.signum(p) * sign;
            }
            return coll.compare(x.code(), y.code());
        });
        return out;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.forLanguageTag("id-ID"));
    }

    static List<Row> filterByStockStatus(List<Row> rows, String status) {
        if (status == null) {
            return new ArrayList<>(rows);
        }
        return rows.stream().filter(r -> status.equals(stockStatus(r.currentStock(), r.minimumStock()))).toList();
    }

    /** KPI: jumlah, aktif, rendah, habis, nilai stok (Σ stok × WAC) dan kategori unik semua item. */
    static Map<String, Object> summarize(List<Row> rows, List<String> allCategories) {
        int active = 0;
        int low = 0;
        int out = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (Row r : rows) {
            if (!Boolean.FALSE.equals(r.isActive())) {
                active++;
            }
            String st = stockStatus(r.currentStock(), r.minimumStock());
            if ("low".equals(st)) {
                low++;
            }
            if ("out".equals(st)) {
                out++;
            }
            value = value.add(stockValue(r));
        }
        Set<String> cats = new LinkedHashSet<>();
        for (String c : allCategories) {
            if (c != null && !c.trim().isEmpty()) {
                cats.add(c);
            }
        }
        List<String> categories = new ArrayList<>(cats);
        categories.sort(InvJson.idCollator());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", rows.size());
        m.put("activeCount", active);
        m.put("lowCount", low);
        m.put("outCount", out);
        m.put("stockValue", InvJson.jsRound(value, 2));
        m.put("categories", categories);
        return m;
    }
}
