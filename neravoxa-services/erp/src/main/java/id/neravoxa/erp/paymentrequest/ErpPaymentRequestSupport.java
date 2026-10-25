package id.neravoxa.erp.paymentrequest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.core.security.ErpActor;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.PaymentRequest;
import id.neravoxa.erp.entity.PaymentRequestHistory;
import id.neravoxa.erp.entity.PaymentRequestType;
import id.neravoxa.erp.paymentrequest.PrRequests.LineAccount;
import id.neravoxa.erp.paymentrequest.PrRules.JournalLine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Bagian bersama service Pengajuan Pembayaran: konteks pengguna ({@code viewerOf}), muat + visibilitas
 * ({@code loadRequest} / {@code visibleRequest}), klaim status optimistis ({@code claimStatus} /
 * {@code claimSettlement}), riwayat dan posting jurnal ({@code postJournalInTx}).
 */
@Component
@RequiredArgsConstructor
public class ErpPaymentRequestSupport {

    static final String NOT_FOUND = "Pengajuan tidak ditemukan.";

    private final ErpAccess access;
    private final ErpGlPostingService gl;
    private final ErpSystemAccountService systemAccounts;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** {@code viewerOf(req)}: super admin, Keuangan (payment_request edit), pelihat "Semua" (payment_request view). */
    PrViewer viewer() {
        ErpActor a = access.actor();
        return new PrViewer(a.userId(), a.role(), a.roleKeys(), access.isSuperAdmin(),
                access.can(PrRules.PR_MODULE, "edit"), access.can(PrRules.PR_MODULE, "view"));
    }

    /** {@code loadRequest}: 404 bila tidak ada. */
    PaymentRequest loadRequest(String id) {
        PaymentRequest pr = id == null ? null : em.find(PaymentRequest.class, id);
        if (pr == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        return pr;
    }

    PaymentRequestType typeOf(PaymentRequest pr) {
        return em.find(PaymentRequestType.class, pr.getTypeId());
    }

    /** Pengajuan yang boleh dilihat pengguna, atau 404 (tanpa membocorkan keberadaannya). */
    @ErpTx(readOnly = true)
    public PaymentRequest visibleRequest(String id, PrViewer ctx) {
        PaymentRequest pr = id == null ? null : em.find(PaymentRequest.class, id);
        if (pr == null || !PrRules.canViewRequest(PrRules.Ref.of(pr), ctx)) {
            throw ErpException.notFound(NOT_FOUND);
        }
        return pr;
    }

    /**
     * Klaim status — padanan {@code updateMany where status IN (...)} + cek count. Baris dikunci
     * ({@code SELECT … FOR UPDATE}) dan dibaca ulang: keputusan paralel yang sudah COMMIT membuat status berbeda
     * → {@link PrRules#STALE_STATUS_ERROR}. Panggil SEBELUM mengubah field entity (refresh membuang perubahan).
     */
    void claimStatus(PaymentRequest pr, Collection<String> from) {
        em.refresh(pr, LockModeType.PESSIMISTIC_WRITE);
        if (!from.contains(pr.getStatus())) {
            throw ErpException.business(PrRules.STALE_STATUS_ERROR);
        }
    }

    /** Klaim status pertanggungjawaban pengajuan PAID ({@code claimSettlement}). */
    void claimSettlement(PaymentRequest pr, Collection<String> from) {
        em.refresh(pr, LockModeType.PESSIMISTIC_WRITE);
        if (!"PAID".equals(pr.getStatus()) || pr.getSettlementStatus() == null || !from.contains(pr.getSettlementStatus())) {
            throw ErpException.business(PrRules.STALE_STATUS_ERROR);
        }
    }

    void addHistory(String requestId, String action, String actorId, String fromStatus, String toStatus, String note) {
        PaymentRequestHistory h = new PaymentRequestHistory();
        h.setPaymentRequestId(requestId);
        h.setAction(action);
        h.setActorId(actorId);
        h.setFromStatus(fromStatus);
        h.setToStatus(toStatus);
        h.setNote(note);
        em.persist(h);
    }

    /** Pemetaan akun "Uang Muka Karyawan" pertama (urut sortOrder); null bila belum dipetakan. */
    String resolveAdvanceAccountId() {
        List<String> ids = em.createQuery("select m.accountId from SystemAccountMapping m where m.role = :role "
                        + "order by m.sortOrder asc", String.class)
                .setParameter("role", SystemAccountRole.EMPLOYEE_ADVANCE.name())
                .setMaxResults(1).getResultList();
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * Akun biaya per baris oleh Keuangan ({@code applyLineAccounts}). Tiap id wajib baris milik pengajuan ini.
     */
    void applyLineAccounts(String requestId, List<LineAccount> lineAccounts) {
        for (LineAccount la : lineAccounts) {
            if (la.expenseAccountId() != null) {
                systemAccounts.assertDetailAccount(la.expenseAccountId(), "Akun biaya");
            }
            int n = em.createQuery("update PaymentRequestLine l set l.expenseAccountId = :acc "
                            + "where l.id = :id and l.paymentRequestId = :pr")
                    .setParameter("acc", la.expenseAccountId())
                    .setParameter("id", la.id())
                    .setParameter("pr", requestId)
                    .executeUpdate();
            if (n != 1) {
                throw ErpException.business("Baris rincian tidak ditemukan pada pengajuan ini.");
            }
        }
    }

    /**
     * Jurnal Submitted + ledger + saldo untuk baris yang sudah seimbang ({@code postJournalInTx}). Party (vendor)
     * hanya di baris jurnal (kolom "Pihak" Kas &amp; Bank); ledger tetap tanpa party.
     */
    JournalEntry postJournal(Instant date, String narration, String fiscalYearId, String actorId, Instant now,
                             List<JournalLine> lines, String partyId) {
        ErpGlPostingService.Builder b = gl.journal()
                .date(date).narration(narration).fiscalYear(fiscalYearId).createdBy(actorId).submittedAt(now);
        if (partyId != null) {
            b.itemsParty(partyId);
        }
        for (JournalLine l : lines) {
            b.line(l.accountId(), l.debit(), l.credit(), l.description());
        }
        return b.post();
    }

    /** Baris pengajuan setelah ditulis — padanan {@code findUniqueOrThrow({ where: { id } })}. */
    ObjectNode json(PaymentRequest pr) {
        em.flush();
        return PrJson.scalars(pr);
    }
}
