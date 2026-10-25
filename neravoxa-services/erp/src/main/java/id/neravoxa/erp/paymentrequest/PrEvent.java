package id.neravoxa.erp.paymentrequest;

import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.PaymentRequest;

import java.math.BigDecimal;

/**
 * Event transisi pengajuan untuk notifikasi setelah COMMIT — padanan {@code PrEvent} (paymentRequestService.ts).
 *
 * @param kind pending_supervisor | pending_finance | pending_director | approved | rejected | revision | paid |
 *             payment_cancelled | cancelled | settlement_pending_supervisor | settlement_pending_finance |
 *             settlement_revision | settlement_settled | settlement_posting_cancelled
 */
record PrEvent(String kind, String requestId, String number, String requesterId, String supervisorId,
               String directorRole, String actorId, BigDecimal amount, String note) {

    /** {@code eventOf(pr, kind, actorId, note)}. */
    static PrEvent of(PaymentRequest pr, String kind, String actorId, String note) {
        return new PrEvent(kind, pr.getId(), pr.getNumber(), pr.getRequesterId(), pr.getSupervisorId(),
                pr.getDirectorRole(), actorId, Money.nz(pr.getTotalAmount()), note);
    }

    /**
     * {@code settlementEvent}: nominal = realisasi bila sudah ada, penerima "atasan" = atasan snapshot
     * pertanggungjawaban.
     */
    static PrEvent settlement(PaymentRequest pr, String kind, String actorId, String note) {
        BigDecimal amount = pr.getSettlementRealized() != null ? pr.getSettlementRealized() : Money.nz(pr.getTotalAmount());
        return new PrEvent(kind, pr.getId(), pr.getNumber(), pr.getRequesterId(), pr.getSettlementSupervisorId(),
                pr.getDirectorRole(), actorId, amount, note);
    }
}
