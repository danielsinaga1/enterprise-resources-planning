package id.neravoxa.erp.eam.asset;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.GaDocument;
import id.neravoxa.erp.entity.GaDocumentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Port {@code services/gaDocumentList.ts}. expiryState: expired (EXPIRED, atau ACTIVE lewat masa
 * berlaku), expiring (ACTIVE, hari ini s.d. +30 hari), valid (ACTIVE selain itu), null (REVOKED/ARCHIVED).
 * "Hari ini" mengikuti zona bisnis, sehingga dokumen yang habis pada tengah malam UTC tetap valid hari itu.
 * Jendela [awal hari ini, awal hari ini + 31 hari) sama dengan {@link ErpDates#dueBoundaries(Instant)}.
 */
final class GaDocumentList {

    static final List<String> STATUSES = List.of("ACTIVE", "EXPIRED", "REVOKED", "ARCHIVED");
    static final List<String> EXPIRY_STATES = List.of("expired", "expiring", "valid");
    static final List<String> SORT_FIELDS = List.of("expiryDate", "code", "title", "effectiveDate");
    /** Urutan lama daftar dokumen (kedaluwarsa paling dekat dulu). */
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("expiryDate", false);
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private GaDocumentList() {
    }

    /** [awal hari ini, awal hari (hari ini + 30) + 1 hari) zona bisnis. */
    static Instant[] expiryWindow(Instant now) {
        return ErpDates.dueBoundaries(now);
    }

    static String deriveExpiryState(GaDocumentStatus status, Instant expiryDate, Instant[] window) {
        if (status == GaDocumentStatus.EXPIRED) {
            return "expired";
        }
        if (status != GaDocumentStatus.ACTIVE) {
            return null;
        }
        if (expiryDate == null) {
            return "valid";
        }
        String due = ErpDates.dueState(expiryDate, window);
        if ("overdue".equals(due)) {
            return "expired";
        }
        return "dueSoon".equals(due) ? "expiring" : "valid";
    }

    /** Aturan yang sama dengan {@link #deriveExpiryState} sebagai kondisi JPQL (alias dokumen {@code d}). */
    static void expiryWhere(ErpQuery<GaDocument> q, String state, Instant[] window, String suffix) {
        String active = "act" + suffix;
        switch (state) {
            case "expired" -> q.where("(d.status = :exp" + suffix + " or (d.status = :" + active
                            + " and d.expiryDate < :gte" + suffix + "))",
                    "exp" + suffix, GaDocumentStatus.EXPIRED, active, GaDocumentStatus.ACTIVE, "gte" + suffix, window[0]);
            case "expiring" -> q.where("d.status = :" + active + " and d.expiryDate >= :gte" + suffix
                            + " and d.expiryDate < :lt" + suffix,
                    active, GaDocumentStatus.ACTIVE, "gte" + suffix, window[0], "lt" + suffix, window[1]);
            default -> q.where("d.status = :" + active + " and (d.expiryDate is null or d.expiryDate >= :lt" + suffix + ")",
                    active, GaDocumentStatus.ACTIVE, "lt" + suffix, window[1]);
        }
    }

    /**
     * Filter daftar berpaginasi & ringkasan: typeId, assetId, status, expiry (expired|expiring|valid),
     * search (kode, judul, nomor terbit, penerbit, atau kode/nama aset terkait). Filter tak dikenal → 400.
     */
    static ErpQuery<GaDocument> buildWhere(ErpQuery<GaDocument> q, Map<String, String> query, Instant now) {
        String typeId = ListQuery.str(query, "typeId");
        if (typeId != null) {
            q.where("d.typeId = :typeId", "typeId", typeId);
        }
        String assetId = ListQuery.str(query, "assetId");
        if (assetId != null) {
            q.where("d.assetId = :assetId", "assetId", assetId);
        }
        String status = ListQuery.str(query, "status");
        if (status != null) {
            if (!STATUSES.contains(status)) {
                throw ErpException.business("Status dokumen tidak dikenal.");
            }
            q.where("d.status = :status", "status", GaDocumentStatus.valueOf(status));
        }
        String expiry = ListQuery.str(query, "expiry");
        if (expiry != null) {
            if (!EXPIRY_STATES.contains(expiry)) {
                throw ErpException.business("Filter masa berlaku tidak dikenal.");
            }
            expiryWhere(q, expiry, expiryWindow(now), "F");
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            q.where("(lower(d.code) like :s or lower(d.title) like :s or lower(d.issuedNumber) like :s "
                    + "or lower(d.issuedBy) like :s or lower(sa.code) like :s or lower(sa.name) like :s)",
                    "s", ErpQuery.contains(search));
        }
        return q;
    }

    /** Tanggal kosong selalu di akhir di kedua arah; kode sebagai pemecah seri. */
    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        return switch (sort.field()) {
            case "code" -> "d.code " + dir;
            case "title" -> "d.title " + dir + ", d.code asc";
            case "effectiveDate" -> "d.effectiveDate " + dir + " nulls last, d.code asc";
            default -> "d.expiryDate " + dir + " nulls last, d.code asc";
        };
    }

    /** Hari penuh dari sekarang sampai instant kedaluwarsa ({@code daysToExpiry} lama). */
    static Long daysToExpiry(Instant expiryDate, Instant now) {
        return expiryDate == null ? null : Math.floorDiv(expiryDate.toEpochMilli() - now.toEpochMilli(), DAY_MS);
    }
}
