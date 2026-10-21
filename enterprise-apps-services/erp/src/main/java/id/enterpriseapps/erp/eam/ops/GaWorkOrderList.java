package id.enterpriseapps.erp.eam.ops;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.entity.GaWorkOrderPriority;
import id.enterpriseapps.erp.entity.GaWorkOrderStatus;
import id.enterpriseapps.erp.entity.GaWorkOrderType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Daftar work order GA — port {@code services/gaWorkOrderList.ts}. Tanpa {@code page} mengembalikan
 * array polos (dashboard GA bergantung padanya); dengan {@code page} → {@code { data, total, page, limit }}.
 * /work-orders/summary memakai filter yang sama. Alias root JPQL: {@code w}, aset: {@code a}.
 */
final class GaWorkOrderList {

    private GaWorkOrderList() {
    }

    static final List<String> STATUSES = Arrays.stream(GaWorkOrderStatus.values()).map(Enum::name).toList();
    /** Status yang masih butuh pengerjaan (yang dihitung "aktif" dan "terlambat"). */
    static final List<GaWorkOrderStatus> ACTIVE = List.of(
            GaWorkOrderStatus.OPEN, GaWorkOrderStatus.ASSIGNED, GaWorkOrderStatus.IN_PROGRESS);
    static final String STATUS_ACTIVE = "ACTIVE";
    static final List<String> PRIORITIES = Arrays.stream(GaWorkOrderPriority.values()).map(Enum::name).toList();
    static final List<String> TYPES = Arrays.stream(GaWorkOrderType.values()).map(Enum::name).toList();
    static final List<String> DUE_FILTERS = List.of("overdue", "dueSoon");
    static final int DUE_SOON_DAYS = 7;
    static final List<String> SORT_FIELDS = List.of("code", "reportedAt", "dueAt", "priority", "status");

    private static final String E = "id.enterpriseapps.erp.entity.";

    /**
     * Urutan enum PostgreSQL (urutan deklarasi), bukan urutan abjad: kolom di db_erp bertipe VARCHAR,
     * sedangkan ENTARIS memakai tipe enum Postgres sehingga {@code orderBy status} mengikuti deklarasi.
     */
    static String rank(String path, Class<? extends Enum<?>> type) {
        StringBuilder sb = new StringBuilder("case");
        Enum<?>[] values = type.getEnumConstants();
        for (int i = 0; i < values.length; i++) {
            sb.append(" when ").append(path).append(" = ").append(E).append(type.getSimpleName()).append('.')
                    .append(values[i].name()).append(" then ").append(i);
        }
        return sb.append(" else ").append(values.length).append(" end").toString();
    }

    static final String STATUS_RANK = rank("w.status", GaWorkOrderStatus.class);
    static final String PRIORITY_RANK = rank("w.priority", GaWorkOrderPriority.class);

    /** Urutan lama: pekerjaan terbuka dulu, paling mendesak dulu, terbaru dulu. */
    static final String DEFAULT_ORDER = STATUS_RANK + " asc, " + PRIORITY_RANK + " desc, w.createdAt desc";

    /**
     * Filter daftar berpaginasi & ringkasan:
     * status (enum atau ACTIVE), priority, type, assetId, mine=true (menang atas assignedToId),
     * due (overdue | dueSoon, keduanya berstatus aktif), startDate/endDate (tanggal lapor, zona bisnis),
     * search (kode, judul, komponen, kode/nama aset).
     */
    static GaWhere where(Map<String, String> query, String userId, Instant now) {
        GaWhere w = new GaWhere();
        String status = ListQuery.str(query, "status");
        if (status != null) {
            if (STATUS_ACTIVE.equals(status)) {
                w.and("w.status in :activeStatuses", "activeStatuses", ACTIVE);
            } else if (STATUSES.contains(status)) {
                w.and("w.status = :status", "status", GaWorkOrderStatus.valueOf(status));
            } else {
                throw ErpException.business("Status work order tidak dikenal.");
            }
        }
        String priority = ListQuery.str(query, "priority");
        if (priority != null) {
            if (!PRIORITIES.contains(priority)) {
                throw ErpException.business("Prioritas tidak dikenal.");
            }
            w.and("w.priority = :priority", "priority", GaWorkOrderPriority.valueOf(priority));
        }
        String type = ListQuery.str(query, "type");
        if (type != null) {
            if (!TYPES.contains(type)) {
                throw ErpException.business("Tipe work order tidak dikenal.");
            }
            w.and("w.type = :type", "type", GaWorkOrderType.valueOf(type));
        }
        String assetId = ListQuery.str(query, "assetId");
        w.andIf(assetId != null, "w.assetId = :assetId", "assetId", assetId);
        if ("true".equals(ListQuery.str(query, "mine")) && userId != null) {
            w.and("w.assignedToId = :mineId", "mineId", userId);
        } else {
            String assignedToId = ListQuery.str(query, "assignedToId");
            w.andIf(assignedToId != null, "w.assignedToId = :assignedToId", "assignedToId", assignedToId);
        }
        String due = ListQuery.str(query, "due");
        if (due != null) {
            if (!DUE_FILTERS.contains(due)) {
                throw ErpException.business("Filter jatuh tempo tidak dikenal.");
            }
            if ("overdue".equals(due)) {
                w.and("w.status in :dueActive and w.dueAt < :dueNow", "dueActive", ACTIVE, "dueNow", now);
            } else {
                w.and("w.status in :dueActive and w.dueAt >= :dueNow and w.dueAt < :dueSoon",
                        "dueActive", ACTIVE, "dueNow", now, "dueSoon", now.plus(Duration.ofDays(DUE_SOON_DAYS)));
            }
        }
        String start = ListQuery.dateParam(query, "startDate", "Format tanggal harus YYYY-MM-DD.");
        String end = ListQuery.dateParam(query, "endDate", "Format tanggal harus YYYY-MM-DD.");
        Instant[] range = ErpDates.localDayRange(start, end);
        w.andIf(range[0] != null, "w.reportedAt >= :repFrom", "repFrom", range[0]);
        w.andIf(range[1] != null, "w.reportedAt < :repTo", "repTo", range[1]);
        String search = ListQuery.str(query, "search");
        if (search != null) {
            w.and("lower(w.code) like :s or lower(w.title) like :s or lower(w.component) like :s "
                    + "or lower(a.code) like :s or lower(a.name) like :s", "s", ErpQuery.contains(search));
        }
        return w;
    }

    /** Sortir yang diminta + kode sebagai pemecah seri; tanpa sortir → urutan lama. */
    static String orderBy(ListQuery.Sort sort) {
        if (sort == null) {
            return DEFAULT_ORDER;
        }
        String dir = sort.dir();
        return switch (sort.field()) {
            case "code" -> "w.code " + dir;
            case "dueAt" -> "w.dueAt " + dir + " nulls last, w.code desc";
            case "status" -> STATUS_RANK + " " + dir + ", w.code desc";
            case "priority" -> PRIORITY_RANK + " " + dir + ", w.code desc";
            default -> "w." + sort.field() + " " + dir + ", w.code desc";
        };
    }

    /** Awal bulan kalender ini dan bulan berikutnya, zona bisnis. */
    static Instant[] currentMonthRange(Instant now) {
        LocalDate local = now.atZone(ErpDates.businessZone()).toLocalDate().withDayOfMonth(1);
        return new Instant[]{
                local.atStartOfDay(ErpDates.businessZone()).toInstant(),
                local.plusMonths(1).atStartOfDay(ErpDates.businessZone()).toInstant()};
    }
}
