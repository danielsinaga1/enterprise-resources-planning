package id.enterpriseapps.erp.inventory;

import id.enterpriseapps.erp.core.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nilai stok dashboard — port {@code services/inventoryDashboard.ts}. Nilai stok = qty fisik × WAC
 * (sama dengan /dashboard/metrics dan /items/summary), bukan saldo GL.
 */
final class InventoryDashboardCalc {

    static final List<String> GROUPS = List.of("category", "warehouse");
    /** Batang maksimum; ekor dilipat menjadi satu grup "Lainnya". */
    static final int GROUP_LIMIT = 8;
    static final String OTHER_GROUP_KEY = "__other__";
    static final String NONE_GROUP_KEY = "__none__";

    private InventoryDashboardCalc() {
    }

    /** Kosong → category (default); nilai tak dikenal → null (400). */
    static String parseGroupBy(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "category";
        }
        return GROUPS.contains(raw) ? raw : null;
    }

    /** Satu posisi stok: item (per kategori) atau item di gudang. */
    record Row(String key, String label, BigDecimal quantity, BigDecimal unitCost) {
    }

    private static final class Acc {
        final String key;
        final String label;
        int itemCount;
        BigDecimal value = BigDecimal.ZERO;

        Acc(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    /**
     * Kelompokkan posisi stok per kunci, urut nilai menurun; lebih dari {@code limit} grup → grup kecil
     * dilipat ke "Lainnya (n)" agar grafik tetap terbaca (total tidak berubah).
     */
    static Map<String, Object> summarize(List<Row> rows, String groupBy, int limit) {
        Map<String, Acc> map = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Row r : rows) {
            String rawKey = r.key() != null ? r.key().trim() : "";
            String key = rawKey.isEmpty() ? NONE_GROUP_KEY : rawKey;
            String label;
            if (NONE_GROUP_KEY.equals(key)) {
                label = "category".equals(groupBy) ? "Tanpa kategori" : "Tanpa gudang";
            } else {
                String l = r.label() != null ? r.label().trim() : "";
                label = l.isEmpty() ? rawKey : l;
            }
            BigDecimal qty = Money.nz(r.quantity());
            BigDecimal value = qty.multiply(Money.nz(r.unitCost()));
            total = total.add(value);
            Acc a = map.computeIfAbsent(key, k -> new Acc(k, label));
            a.value = a.value.add(value);
            if (qty.signum() > 0) {
                a.itemCount++;
            }
        }
        Collator coll = InvJson.idCollator();
        List<Acc> sorted = new ArrayList<>(map.values());
        sorted.sort((a, b) -> {
            int c = b.value.compareTo(a.value);
            return c != 0 ? c : coll.compare(a.label, b.label);
        });
        int max = Math.max(1, limit);
        List<Acc> groups = new ArrayList<>();
        if (sorted.size() > max) {
            groups.addAll(sorted.subList(0, max - 1));
            List<Acc> tail = sorted.subList(max - 1, sorted.size());
            Acc other = new Acc(OTHER_GROUP_KEY, "Lainnya (" + tail.size() + ")");
            for (Acc t : tail) {
                other.itemCount += t.itemCount;
                other.value = other.value.add(t.value);
            }
            groups.add(other);
        } else {
            groups.addAll(sorted);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Acc g : groups) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", g.key);
            m.put("label", g.label);
            m.put("itemCount", g.itemCount);
            m.put("value", InvJson.jsRound(g.value, 2));
            m.put("share", total.signum() > 0
                    ? InvJson.jsRound(g.value.divide(total, 12, RoundingMode.HALF_UP), 4)
                    : BigDecimal.ZERO);
            out.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("groupBy", groupBy);
        res.put("total", InvJson.jsRound(total, 2));
        res.put("groups", out);
        return res;
    }
}
