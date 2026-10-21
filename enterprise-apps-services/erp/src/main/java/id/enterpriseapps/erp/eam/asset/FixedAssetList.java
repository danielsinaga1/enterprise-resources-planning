package id.enterpriseapps.erp.eam.asset;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.entity.FixedAsset;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Port {@code services/fixedAssetList.ts}: register aset tetap. Tanpa {@code page} endpoint mengembalikan
 * array semua aset (status/kategori persis, terbaru dulu); dengan {@code page} → {@code { data, total, page, limit }}.
 */
final class FixedAssetList {

    /** Status tersimpan (string polos, lihat POST /:id/dispose). */
    static final List<String> STATUSES = List.of("Active", "Disposed");
    static final List<String> SORT_FIELDS = List.of("createdAt", "assetNumber", "name", "category", "acquisitionDate",
            "acquisitionCost", "accumulatedDepreciation", "bookValue");
    /** Default: catatan terbaru dulu. */
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("createdAt", true);

    private FixedAssetList() {
    }

    /**
     * Filter register berpaginasi: status (Active | Disposed), category (persis), startDate/endDate
     * (tanggal perolehan, YYYY-MM-DD zona bisnis), search (nomor aset, nama, kategori).
     */
    static ErpQuery<FixedAsset> buildWhere(ErpQuery<FixedAsset> q, Map<String, String> query) {
        String status = ListQuery.str(query, "status");
        if (status != null) {
            if (!STATUSES.contains(status)) {
                throw ErpException.business("Status aset tidak dikenal.");
            }
            q.where("f.status = :status", "status", status);
        }
        String category = ListQuery.str(query, "category");
        if (category != null) {
            q.where("f.category = :category", "category", category);
        }
        String msg = "Format tanggal harus YYYY-MM-DD.";
        String startDate = ListQuery.dateParam(query, "startDate", msg);
        String endDate = ListQuery.dateParam(query, "endDate", msg);
        Instant[] range = ErpDates.localDayRange(startDate, endDate);
        q.whereIf(range[0] != null, "f.acquisitionDate >= :acqGte", "acqGte", range[0]);
        q.whereIf(range[1] != null, "f.acquisitionDate < :acqLt", "acqLt", range[1]);
        String search = ListQuery.str(query, "search");
        if (search != null) {
            q.where("(lower(f.assetNumber) like :s or lower(f.name) like :s or lower(f.category) like :s)",
                    "s", ErpQuery.contains(search));
        }
        return q;
    }

    /** Urutan utama + nomor aset sebagai pemecah seri stabil (baris tidak melompat halaman). */
    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        if ("assetNumber".equals(sort.field())) {
            return "f.assetNumber " + dir;
        }
        return "f." + sort.field() + " " + dir + ", f.assetNumber " + dir;
    }
}
