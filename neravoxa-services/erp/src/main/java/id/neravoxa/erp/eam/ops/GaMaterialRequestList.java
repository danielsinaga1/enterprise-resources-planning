package id.neravoxa.erp.eam.ops;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.GaMaterialRequestStatus;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daftar permintaan barang GA — port {@code services/gaMaterialRequestList.ts}. Tanpa {@code page}
 * array polos (status/workOrderId/mine, terbaru dulu); dengan {@code page} → {@code { data, total, page, limit }}.
 * /summary memakai filter yang sama. Alias root JPQL {@code m}, aset {@code a}, WO {@code wo}.
 */
final class GaMaterialRequestList {

    private GaMaterialRequestList() {
    }

    static final List<String> STATUSES = Arrays.stream(GaMaterialRequestStatus.values()).map(Enum::name).toList();
    static final List<String> SORT_FIELDS = List.of("code", "requestedAt", "status");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("requestedAt", true);

    static final String STATUS_RANK = GaWorkOrderList.rank("m.status", GaMaterialRequestStatus.class);

    /**
     * status (diabaikan bila {@code ignoreStatus}, untuk hitungan per status), workOrderId/assetId,
     * mine=true, startDate/endDate (tanggal permintaan, zona bisnis), search (kode, mesin/target, aset,
     * kode WO, nama barang).
     */
    static GaWhere where(Map<String, String> query, String userId, boolean ignoreStatus) {
        GaWhere w = new GaWhere();
        String status = ListQuery.str(query, "status");
        if (status != null && !ignoreStatus) {
            if (!STATUSES.contains(status)) {
                throw ErpException.business("Status permintaan barang tidak dikenal.");
            }
            w.and("m.status = :status", "status", GaMaterialRequestStatus.valueOf(status));
        }
        String workOrderId = ListQuery.str(query, "workOrderId");
        w.andIf(workOrderId != null, "m.workOrderId = :woId", "woId", workOrderId);
        String assetId = ListQuery.str(query, "assetId");
        w.andIf(assetId != null, "m.assetId = :assetId", "assetId", assetId);
        if ("true".equals(ListQuery.str(query, "mine")) && userId != null) {
            w.and("m.requestedById = :mineId", "mineId", userId);
        }
        String start = ListQuery.dateParam(query, "startDate", "Format tanggal harus YYYY-MM-DD.");
        String end = ListQuery.dateParam(query, "endDate", "Format tanggal harus YYYY-MM-DD.");
        Instant[] range = ErpDates.localDayRange(start, end);
        w.andIf(range[0] != null, "m.requestedAt >= :reqFrom", "reqFrom", range[0]);
        w.andIf(range[1] != null, "m.requestedAt < :reqTo", "reqTo", range[1]);
        String search = ListQuery.str(query, "search");
        if (search != null) {
            w.and("lower(m.code) like :s or lower(m.machineTarget) like :s or lower(a.code) like :s "
                    + "or lower(a.name) like :s or lower(wo.code) like :s or exists (select 1 from GaMaterialRequestItem si "
                    + "where si.materialRequestId = m.id and lower(si.itemName) like :s)", "s", ErpQuery.contains(search));
        }
        return w;
    }

    /** Sortir yang diminta + kode sebagai pemecah seri (status mengikuti urutan enum). */
    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        return switch (sort.field()) {
            case "code" -> "m.code " + dir;
            case "status" -> STATUS_RANK + " " + dir + ", m.code " + dir;
            default -> "m." + sort.field() + " " + dir + ", m.code " + dir;
        };
    }

    /** {@code { DRAFT: 0, … }} — semua status selalu ada. */
    static Map<String, Long> countByStatus(List<Object[]> groups) {
        Map<String, Long> out = new LinkedHashMap<>();
        STATUSES.forEach(s -> out.put(s, 0L));
        for (Object[] g : groups) {
            String s = String.valueOf(g[0]);
            if (out.containsKey(s)) {
                out.put(s, (Long) g[1]);
            }
        }
        return out;
    }
}
