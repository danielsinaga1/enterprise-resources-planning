package id.neravoxa.erp.sales;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.Notification;
import id.neravoxa.erp.entity.QuotationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Operasi DB siklus hidup penawaran untuk {@link ErpQuotationSweeperJob} — port fungsi di
 * {@code jobs/quotationSweeper.ts}.
 */
@Service
public class ErpQuotationSweeperService {

    /** Penawaran Sent yang berlaku ≤ sekian hari lagi diberi notifikasi (sekali per penawaran). */
    static final int EXPIRY_NOTIFICATION_DAYS = 3;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Kandidat notifikasi "akan kadaluarsa". */
    public record Candidate(String id, String quotationNumber, Instant validUntil, String createdBy, String customerName) {
    }

    /**
     * Tandai penawaran {@code Sent} yang {@code validUntil}-nya lewat sebagai {@code Expired}. UPDATE atomik
     * sehingga aman terhadap endpoint accept yang berbalapan — accept juga memvalidasi ulang
     * {@code validUntil >= now} di transaksinya sendiri.
     */
    @ErpTx
    public int expireOverdueQuotations() {
        Instant now = Instant.now();
        return em.createQuery("update Quotation q set q.status = :expired, q.expiredAt = :now, q.updatedAt = :now "
                        + "where q.status = :sent and q.validUntil < :now")
                .setParameter("expired", QuotationStatus.Expired)
                .setParameter("sent", QuotationStatus.Sent)
                .setParameter("now", now)
                .executeUpdate();
    }

    /** Penawaran Sent yang akan kadaluarsa dalam {@value #EXPIRY_NOTIFICATION_DAYS} hari dan belum dinotifikasi. */
    @ErpTx(readOnly = true)
    public List<Candidate> findExpiringCandidates() {
        Instant now = Instant.now();
        Instant horizon = now.plus(Duration.ofDays(EXPIRY_NOTIFICATION_DAYS));
        List<Object[]> rows = em.createQuery(
                        "select q.id, q.quotationNumber, q.validUntil, q.createdBy, c.name from Quotation q join q.customer c "
                                + "where q.status = :sent and q.validUntil >= :now and q.validUntil <= :horizon "
                                + "and q.expiryNotifiedAt is null", Object[].class)
                .setParameter("sent", QuotationStatus.Sent)
                .setParameter("now", now)
                .setParameter("horizon", horizon)
                .getResultList();
        List<Candidate> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(new Candidate((String) r[0], (String) r[1], (Instant) r[2], (String) r[3], (String) r[4]));
        }
        return out;
    }

    /**
     * Notifikasi pembuat penawaran + tandai {@code expiryNotifiedAt} dalam SATU transaksi tersendiri
     * (seperti {@code prisma.$transaction} per kandidat di sumber): tiap baris hanya pernah mendapat
     * satu notifikasi, dan kegagalan satu kandidat tidak membatalkan yang lain.
     */
    @ErpTx(propagation = Propagation.REQUIRES_NEW)
    public void notifyExpiring(Candidate q) {
        Notification n = new Notification();
        n.setUserId(q.createdBy());
        n.setType("quotation_expiring");
        n.setTitle("Penawaran akan kadaluarsa");
        n.setMessage("Penawaran " + q.quotationNumber() + " untuk " + q.customerName() + " akan kadaluarsa pada "
                + ErpDates.isoDay(q.validUntil()) + ".");
        n.setIsRead(false);
        n.setMetadata(ErpJson.tree(ErpJson.map("quotationId", q.id(), "quotationNumber", q.quotationNumber())));
        em.persist(n);
        Instant now = Instant.now();
        em.createQuery("update Quotation q set q.expiryNotifiedAt = :now, q.updatedAt = :now where q.id = :id")
                .setParameter("now", now)
                .setParameter("id", q.id())
                .executeUpdate();
    }
}
