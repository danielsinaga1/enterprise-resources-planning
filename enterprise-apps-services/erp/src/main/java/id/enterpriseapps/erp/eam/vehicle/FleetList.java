package id.enterpriseapps.erp.eam.vehicle;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ListQuery;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Daftar armada — port {@code services/gaFleetList.ts}: difilter &amp; dipaging di memori (armada kecil).
 * Status jatuh tempo per kewajiban: overdue (sebelum hari ini), dueSoon (dalam DUE_SOON_DAYS), ok,
 * null (tidak ada record aktif). Per jenis pajak record ACTIVE terbaru yang berlaku; satu grup
 * dokumen memakai status terburuknya.
 */
final class FleetList {

    private FleetList() {
    }

    static final List<String> DOCS = List.of("tax", "stnk", "kir", "insurance", "inspection");
    static final List<String> ASSET_STATUSES = List.of("ACTIVE", "IN_REPAIR", "RETIRED", "LOST");
    static final List<String> DUE_FILTERS = List.of("overdue", "dueSoon", "attention");
    static final List<String> SORT_FIELDS = List.of("code", "noPlat", "name", "odometer", "nextDue", "status");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("code", false);

    /** Jenis pajak per grup dokumen (pajak tahunan bisa dicatat sebagai salah satu dari dua jenis). */
    static final Map<String, String> TAX_TYPE_GROUP = Map.of(
            "STNK_TAHUNAN", "tax",
            "PAJAK_KENDARAAN", "tax",
            "STNK_5_TAHUNAN", "stnk",
            "KIR", "kir");

    private static final Map<String, Integer> SEVERITY = Map.of("overdue", 2, "dueSoon", 1, "ok", 0);
    private static final Collator COLLATOR = Collator.getInstance(Locale.ENGLISH);

    record DueInfo(String dueDate, String state) {
        static final DueInfo NONE = new DueInfo(null, null);

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dueDate", dueDate);
            m.put("state", state);
            return m;
        }
    }

    record AssetSource(String id, String code, String name, String status, String condition, String picUserId,
                       String categoryName, String noPlat, String brand, String tipe, Integer tahunProduksi,
                       String jenisBBM, Integer odometerTerakhir) {
    }

    record TaxSource(String assetId, String type, Instant dueDate) {
    }

    record DatedSource(String assetId, Instant date) {
    }

    record Sources(List<AssetSource> assets, List<TaxSource> taxes, List<DatedSource> insurances,
                   List<DatedSource> inspectionsNewestFirst, List<String> openIncidentAssetIds,
                   Map<String, String> driverNames) {
    }

    record Row(String id, String code, String name, String status, String condition, String categoryName,
               String noPlat, String brand, String tipe, Integer tahunProduksi, String jenisBBM, Integer odometer,
               String driverId, String driverName, Map<String, DueInfo> due, String attention, String nextDueDate,
               int openIncidentCount) {

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("code", code);
            m.put("name", name);
            m.put("status", status);
            m.put("condition", condition);
            m.put("categoryName", categoryName);
            m.put("noPlat", noPlat);
            m.put("brand", brand);
            m.put("tipe", tipe);
            m.put("tahunProduksi", tahunProduksi);
            m.put("jenisBBM", jenisBBM);
            m.put("odometer", odometer);
            if (driverId == null) {
                m.put("driver", null);
            } else {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("id", driverId);
                d.put("fullName", driverName);
                m.put("driver", d);
            }
            Map<String, Object> dueJson = new LinkedHashMap<>();
            for (String doc : DOCS) {
                dueJson.put(doc, due.get(doc).json());
            }
            m.put("due", dueJson);
            m.put("attention", attention);
            m.put("nextDueDate", nextDueDate);
            m.put("openIncidentCount", openIncidentCount);
            return m;
        }
    }

    /** {@code deriveFleetDueState}: ErpDates.dueState memberi null untuk "ok", di sini tetap "ok". */
    static String dueState(Instant dueDate, Instant[] bounds) {
        if (dueDate == null) {
            return null;
        }
        String s = ErpDates.dueState(dueDate, bounds);
        return s == null ? "ok" : s;
    }

    private static DueInfo worse(DueInfo a, DueInfo b) {
        if (a.state() == null) {
            return b;
        }
        if (b.state() == null) {
            return a;
        }
        int sa = SEVERITY.get(a.state());
        int sb = SEVERITY.get(b.state());
        if (sa != sb) {
            return sb > sa ? b : a;
        }
        // Status sama: tanggal lebih awal = lebih mendesak.
        String da = a.dueDate() == null ? "" : a.dueDate();
        String db = b.dueDate() == null ? "" : b.dueDate();
        return db.compareTo(da) < 0 ? b : a;
    }

    private static DueInfo info(Instant date, Instant[] bounds) {
        return date == null ? DueInfo.NONE : new DueInfo(VehicleJs.iso(date), dueState(date, bounds));
    }

    /** Satu baris per aset dari sumber yang dimuat batch (tanpa query per aset). */
    static List<Row> build(Sources src, Instant[] bounds) {
        // Record ACTIVE terbaru per (aset, jenis pajak) = cakupan berlaku jenis itu.
        Map<String, Instant> latestTax = new LinkedHashMap<>();
        for (TaxSource t : src.taxes()) {
            String k = t.assetId() + "|" + t.type();
            Instant cur = latestTax.get(k);
            if (cur == null || t.dueDate().isAfter(cur)) {
                latestTax.put(k, t.dueDate());
            }
        }
        Map<String, Map<String, DueInfo>> taxDue = new HashMap<>();
        latestTax.forEach((k, date) -> {
            int bar = k.indexOf('|');
            String assetId = k.substring(0, bar);
            String group = TAX_TYPE_GROUP.get(k.substring(bar + 1));
            if (group == null) {
                return;
            }
            Map<String, DueInfo> perAsset = taxDue.computeIfAbsent(assetId, x -> new HashMap<>());
            perAsset.put(group, worse(perAsset.getOrDefault(group, DueInfo.NONE), info(date, bounds)));
        });
        Map<String, Instant> latestInsurance = new HashMap<>();
        for (DatedSource i : src.insurances()) {
            Instant cur = latestInsurance.get(i.assetId());
            if (cur == null || i.date().isAfter(cur)) {
                latestInsurance.put(i.assetId(), i.date());
            }
        }
        Map<String, Instant> latestInspection = new HashMap<>();
        for (DatedSource insp : src.inspectionsNewestFirst()) {
            if (!latestInspection.containsKey(insp.assetId()) && insp.date() != null) {
                latestInspection.put(insp.assetId(), insp.date());
            }
        }
        Map<String, Integer> incidents = new HashMap<>();
        for (String a : src.openIncidentAssetIds()) {
            incidents.merge(a, 1, Integer::sum);
        }

        List<Row> rows = new ArrayList<>();
        for (AssetSource a : src.assets()) {
            Map<String, DueInfo> t = taxDue.getOrDefault(a.id(), Map.of());
            Map<String, DueInfo> due = new LinkedHashMap<>();
            due.put("tax", t.getOrDefault("tax", DueInfo.NONE));
            due.put("stnk", t.getOrDefault("stnk", DueInfo.NONE));
            due.put("kir", t.getOrDefault("kir", DueInfo.NONE));
            due.put("insurance", info(latestInsurance.get(a.id()), bounds));
            due.put("inspection", info(latestInspection.get(a.id()), bounds));
            DueInfo worst = DueInfo.NONE;
            for (String d : DOCS) {
                worst = worse(worst, due.get(d));
            }
            String attention = "overdue".equals(worst.state()) || "dueSoon".equals(worst.state()) ? worst.state() : null;
            List<String> dates = DOCS.stream().map(d -> due.get(d).dueDate()).filter(Objects::nonNull).sorted().toList();
            String driverName = a.picUserId() == null ? null : src.driverNames().get(a.picUserId());
            rows.add(new Row(a.id(), a.code(), a.name(), a.status(), a.condition(), a.categoryName(), a.noPlat(),
                    a.brand(), a.tipe(), a.tahunProduksi(), a.jenisBBM(), a.odometerTerakhir(), a.picUserId(),
                    driverName, due, attention, attention != null ? worst.dueDate() : (dates.isEmpty() ? null : dates.get(0)),
                    incidents.getOrDefault(a.id(), 0)));
        }
        return rows;
    }

    record Filters(String id, String search, String status, String due, String doc) {
    }

    /** {@code parseFleetFilters}: nilai tak dikenal → 400, yang absen diabaikan. */
    static Filters parseFilters(Map<String, String> query) {
        String id = ListQuery.str(query, "id");
        // `query.search ?? query.q`: search kosong yang dikirim tetap menutupi q.
        String search = query.containsKey("search") ? ListQuery.str(query, "search") : ListQuery.str(query, "q");
        String status = ListQuery.str(query, "status");
        String due = ListQuery.str(query, "due");
        String doc = ListQuery.str(query, "doc");
        if (status != null && !ASSET_STATUSES.contains(status)) {
            throw ErpException.business("Status tidak valid.");
        }
        if (due != null && !DUE_FILTERS.contains(due)) {
            throw ErpException.business("Filter jatuh tempo tidak valid.");
        }
        if (doc != null && !DOCS.contains(doc)) {
            throw ErpException.business("Jenis dokumen tidak valid.");
        }
        return new Filters(id, search, status, due, doc);
    }

    private static boolean matchesDue(Row row, String due, String doc) {
        if (due == null && doc == null) {
            return true;
        }
        List<String> states = (doc != null ? List.of(doc) : DOCS).stream()
                .map(d -> row.due().get(d).state()).collect(Collectors.toList());
        if (due == null) {
            return states.stream().anyMatch(Objects::nonNull); // hanya doc: punya record aktif jenis itu
        }
        if ("attention".equals(due)) {
            return states.stream().anyMatch(s -> "overdue".equals(s) || "dueSoon".equals(s));
        }
        return states.stream().anyMatch(due::equals);
    }

    static List<Row> filter(List<Row> rows, Filters f) {
        String q = f.search() == null ? null : f.search().toLowerCase(Locale.ROOT);
        List<Row> out = new ArrayList<>();
        for (Row r : rows) {
            if (f.id() != null && !r.id().equals(f.id())) {
                continue;
            }
            if (f.status() != null && !r.status().equals(f.status())) {
                continue;
            }
            if (q != null) {
                String hay = java.util.stream.Stream.of(r.code(), r.name(), r.noPlat(), r.brand(), r.tipe(), r.driverName())
                        .filter(s -> s != null && !s.isEmpty())
                        .collect(Collectors.joining(" ")).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) {
                    continue;
                }
            }
            if (matchesDue(r, f.due(), f.doc())) {
                out.add(r);
            }
        }
        return out;
    }

    static ListQuery.Sort parseSort(String raw) {
        return ListQuery.sort(raw, SORT_FIELDS, DEFAULT_SORT);
    }

    private static Comparable<?> key(Row r, String field) {
        return switch (field) {
            case "noPlat" -> r.noPlat() == null ? null : r.noPlat().toUpperCase(Locale.ROOT);
            case "name" -> r.name().toLowerCase(Locale.ROOT);
            case "odometer" -> r.odometer();
            case "nextDue" -> r.nextDueDate();
            case "status" -> r.status();
            default -> r.code();
        };
    }

    /** Null selalu di akhir di kedua arah; seri jatuh ke kode aset. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static List<Row> sort(List<Row> rows, ListQuery.Sort sort) {
        int dir = sort.desc() ? -1 : 1;
        List<Row> out = new ArrayList<>(rows);
        out.sort((a, b) -> {
            Comparable ka = key(a, sort.field());
            Comparable kb = key(b, sort.field());
            if (ka == null && kb != null) {
                return 1;
            }
            if (kb == null && ka != null) {
                return -1;
            }
            if (ka != null && !ka.equals(kb)) {
                return Integer.signum(ka.compareTo(kb)) * dir;
            }
            return COLLATOR.compare(a.code(), b.code());
        });
        return out;
    }

    static List<Row> page(List<Row> rows, ListQuery.Paging paging) {
        int from = Math.min(paging.skip(), rows.size());
        int to = Math.min(paging.skip() + paging.limit(), rows.size());
        return rows.subList(from, to);
    }
}
