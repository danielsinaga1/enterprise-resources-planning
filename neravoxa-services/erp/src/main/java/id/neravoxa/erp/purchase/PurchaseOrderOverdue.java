package id.neravoxa.erp.purchase;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.entity.DocumentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Deteksi PO terlambat ("Terlambat") — port {@code purchaseOrderOverdue.ts}: PO yang sudah
 * dipesan (Submitted), tidak ditutup, target terima sebelum hari ini (hari kalender zona bisnis,
 * default WIB) dan masih ada baris yang belum diterima penuh.
 */
final class PurchaseOrderOverdue {

    private static final double QTY_EPS = 0.0005;

    private PurchaseOrderOverdue() {
    }

    /** Hari kalender (YYYY-MM-DD) sebuah instant di zona bisnis. */
    static String businessDay(Instant at) {
        return ErpDates.businessDate(at).toString();
    }

    static String businessToday(Instant now) {
        return businessDay(now);
    }

    static boolean hasOutstandingQty(List<PurchaseOrderRules.ReceiptLine> items) {
        return items.stream().anyMatch(it -> JsNumbers.n(it.qtyReceived()) < JsNumbers.n(it.quantity()) - QTY_EPS);
    }

    static boolean isOverdue(DocumentStatus status, Instant closedAt, Instant expectedDeliveryDate,
                             List<PurchaseOrderRules.ReceiptLine> items, String today) {
        if (status != DocumentStatus.Submitted || closedAt != null || expectedDeliveryDate == null) {
            return false;
        }
        if (businessDay(expectedDeliveryDate).compareTo(today) >= 0) {
            return false;
        }
        return hasOutstandingQty(items);
    }

    /** Selisih hari kalender antara target terima dan hari ini (≥ 0). */
    static long daysOverdue(Instant expected, String today) {
        LocalDate a = LocalDate.parse(businessDay(expected));
        LocalDate b = LocalDate.parse(today);
        return Math.max(0, ChronoUnit.DAYS.between(a, b));
    }

    /** Awal hari ini (zona bisnis) — batas {@code expectedDeliveryDate < startOfToday}. */
    static Instant startOfToday(Instant now) {
        return ErpDates.businessStartOfDay(businessToday(now));
    }

    /**
     * Padanan JPQL {@code purchaseOrderOverdueWhere}; alias PO {@code po}, parameter
     * {@code :odSubmitted} dan {@code :odStart} (lihat {@link #overdueParams(Instant)}).
     */
    static final String OVERDUE_JPQL = "po.status = :odSubmitted and po.closedAt is null "
            + "and po.expectedDeliveryDate < :odStart "
            + "and exists (select x.id from PurchaseOrderItem x where x.purchaseOrderId = po.id and x.qtyReceived < x.quantity)";

    static Object[] overdueParams(Instant now) {
        return new Object[]{"odSubmitted", DocumentStatus.Submitted, "odStart", startOfToday(now)};
    }
}
