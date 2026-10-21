package id.enterpriseapps.erp.paymentrequest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpDocumentNumberService;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.entity.ApprovalRule;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.GaCostCenter;
import id.enterpriseapps.erp.entity.JournalEntry;
import id.enterpriseapps.erp.entity.Party;
import id.enterpriseapps.erp.entity.PaymentRequest;
import id.enterpriseapps.erp.entity.PaymentRequestLine;
import id.enterpriseapps.erp.entity.PaymentRequestType;
import id.enterpriseapps.erp.entity.User;
import id.enterpriseapps.erp.paymentrequest.PrRequests.Decision;
import id.enterpriseapps.erp.paymentrequest.PrRequests.LineAccount;
import id.enterpriseapps.erp.paymentrequest.PrRequests.LineInput;
import id.enterpriseapps.erp.paymentrequest.PrRequests.Pay;
import id.enterpriseapps.erp.paymentrequest.PrRequests.PaymentRequestInput;
import id.enterpriseapps.erp.paymentrequest.PrRules.Check;
import id.enterpriseapps.erp.paymentrequest.PrRules.Decide;
import id.enterpriseapps.erp.paymentrequest.PrRules.PayLine;
import id.enterpriseapps.erp.paymentrequest.PrRules.PaymentJournal;
import id.enterpriseapps.erp.paymentrequest.PrRules.Ref;
import id.enterpriseapps.erp.paymentrequest.PrRules.SubmitTarget;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Transaksi Pengajuan Pembayaran — port {@code services/paymentRequestService.ts}. Setiap transisi memeriksa aktor
 * dengan {@link PrRules}, mengklaim status (keputusan paralel → {@link PrRules#STALE_STATUS_ERROR}), menulis riwayat
 * dan mengembalikan event notifikasi yang dikirim controller setelah COMMIT.
 *
 * <p>GL saat bayar: DR beban per baris (atau DR Uang Muka Karyawan untuk CASH_ADVANCE / TRAVEL) / CR kas-bank,
 * di bawah guard tahun fiskal terbuka. Batal bayar membatalkan jurnal itu (seperti gaFinance).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpPaymentRequestService {

    private final ErpPaymentRequestSupport support;
    private final ErpDocumentNumberService numbers;
    private final ErpFiscalYearService fiscalYears;
    private final ErpSystemAccountService systemAccounts;
    private final ErpGlPostingService gl;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Hasil transisi: baris pengajuan + event untuk notifikasi setelah COMMIT. */
    record PrTransition(ObjectNode request, List<PrEvent> events) {
    }

    static void assertOk(Check check) {
        if (!check.ok()) {
            throw ErpException.business(check.error());
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /** {@code new Date(`${d}T00:00:00.000Z`)}. */
    private static Instant toDay(String d) {
        return d == null || d.isEmpty() ? null : ErpDates.toInstant(d);
    }

    // ───────────────────────────── draf ─────────────────────────────

    private record Normalized(PaymentRequestType type, String partyId, List<PaymentRequestLine> lines, BigDecimal total) {
    }

    private Normalized normalize(PaymentRequestInput input) {
        PaymentRequestType type = em.find(PaymentRequestType.class, input.typeId());
        if (type == null || !Boolean.TRUE.equals(type.getIsActive())) {
            throw ErpException.business("Jenis pengajuan tidak ditemukan atau tidak aktif.");
        }
        String partyId = null;
        if ("VENDOR".equals(input.payeeKind())) {
            if (input.partyId() == null) {
                throw ErpException.business("Pilih supplier penerima pembayaran.");
            }
            Party party = em.find(Party.class, input.partyId());
            if (party == null) {
                throw ErpException.business("Supplier tidak ditemukan.");
            }
            partyId = party.getId();
        }
        if (input.costCenterId() != null && em.find(GaCostCenter.class, input.costCenterId()) == null) {
            throw ErpException.business("Cost center tidak ditemukan.");
        }
        for (int i = 0; i < input.lines().size(); i++) {
            LineInput l = input.lines().get(i);
            if (l.expenseAccountId() != null) {
                systemAccounts.assertDetailAccount(l.expenseAccountId(), "Akun biaya baris " + (i + 1));
            }
            assertOk(PrTravel.checkLineRate(l.amount(), l.quantity(), l.unitAmount(), "Baris " + (i + 1)));
        }
        assertOk(PrTravel.validateTravel(type.getKind(), input.travelDestination(), input.travelStartDate(),
                input.travelEndDate(), false));
        boolean travel = "TRAVEL".equals(type.getKind());
        List<PaymentRequestLine> lines = new ArrayList<>();
        for (int i = 0; i < input.lines().size(); i++) {
            LineInput l = input.lines().get(i);
            PaymentRequestLine line = new PaymentRequestLine();
            line.setLineNo(i + 1);
            line.setDescription(PrZod.jsTrim(l.description()));
            line.setAmount(l.amount().setScale(2, RoundingMode.HALF_UP));
            line.setExpenseAccountId(l.expenseAccountId() != null ? l.expenseAccountId() : type.getDefaultExpenseAccountId());
            line.setCostCenterId(l.costCenterId());
            line.setExpenseDate(toDay(l.expenseDate()));
            line.setCategory(travel ? l.category() : null);
            line.setQuantity(travel && l.quantity() != null ? l.quantity().setScale(2, RoundingMode.HALF_UP) : null);
            line.setUnitAmount(travel && l.unitAmount() != null ? l.unitAmount().setScale(2, RoundingMode.HALF_UP) : null);
            lines.add(line);
        }
        BigDecimal total = PrRules.computeTotal(input.lines().stream().map(LineInput::amount).toList());
        return new Normalized(type, partyId, lines, total);
    }

    /** Header hasil normalisasi; bagian perjalanan hanya untuk jenis TRAVEL (selain itu selalu null). */
    private static void applyHeader(PaymentRequest pr, PaymentRequestInput input, Normalized n) {
        boolean travel = "TRAVEL".equals(n.type().getKind());
        pr.setTypeId(n.type().getId());
        pr.setCostCenterId(input.costCenterId());
        pr.setDepartment(emptyToNull(input.department()));
        pr.setPayeeKind(input.payeeKind());
        pr.setPartyId(n.partyId());
        pr.setPayeeName(PrZod.jsTrim(input.payeeName()));
        pr.setBankName(emptyToNull(input.bankName()));
        pr.setBankAccountNo(emptyToNull(input.bankAccountNo()));
        pr.setBankAccountHolder(emptyToNull(input.bankAccountHolder()));
        pr.setPurpose(PrZod.jsTrim(input.purpose()));
        pr.setNeededBy(toDay(input.neededBy()));
        pr.setTravelDestination(travel ? emptyToNull(PrZod.jsTrim(input.travelDestination())) : null);
        pr.setTravelStartDate(travel ? toDay(input.travelStartDate()) : null);
        pr.setTravelEndDate(travel ? toDay(input.travelEndDate()) : null);
        pr.setTravelParticipants(travel ? emptyToNull(PrZod.jsTrim(input.travelParticipants())) : null);
        pr.setTotalAmount(n.total());
    }

    private void persistLines(String requestId, List<PaymentRequestLine> lines) {
        for (PaymentRequestLine l : lines) {
            l.setPaymentRequestId(requestId);
            em.persist(l);
        }
    }

    /** POST / — draf baru (semua pengguna login). */
    @ErpTx
    public ObjectNode create(PrViewer actor, PaymentRequestInput input) {
        Normalized n = normalize(input);
        Instant now = Instant.now();
        String number = numbers.generate("PP", now, "");
        PaymentRequest pr = new PaymentRequest();
        applyHeader(pr, input, n);
        pr.setNumber(number);
        pr.setRequesterId(actor.id());
        pr.setStatus("DRAFT");
        em.persist(pr);
        persistLines(pr.getId(), n.lines());
        support.addHistory(pr.getId(), "CREATE", actor.id(), null, "DRAFT", null);
        ObjectNode json = support.json(pr);
        log.info("Payment request created id={} number={} by={}", pr.getId(), number, actor.id());
        return json;
    }

    /** PUT /:id — ubah selama DRAFT / REVISION (hanya pengaju). */
    @ErpTx
    public ObjectNode update(String id, PrViewer actor, PaymentRequestInput input) {
        support.visibleRequest(id, actor);
        PaymentRequest pr = support.loadRequest(id);
        assertOk(PrRules.checkEditable(Ref.of(pr), actor.id()));
        Normalized n = normalize(input);
        String from = pr.getStatus();
        support.claimStatus(pr, List.of(from));
        applyHeader(pr, input, n);
        em.createQuery("delete from PaymentRequestLine l where l.paymentRequestId = :id").setParameter("id", id)
                .executeUpdate();
        persistLines(id, n.lines());
        support.addHistory(id, "UPDATE", actor.id(), from, from, null);
        return support.json(pr);
    }

    // ───────────────────────────── ajukan / putuskan / batal ─────────────────────────────

    @ErpTx
    public PrTransition submit(String id, PrViewer actor) {
        support.visibleRequest(id, actor);
        PaymentRequest pr = support.loadRequest(id);
        PaymentRequestType type = support.typeOf(pr);
        long lineCount = em.createQuery("select count(l) from PaymentRequestLine l where l.paymentRequestId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        long attachmentCount = em.createQuery("select count(a) from PaymentRequestAttachment a "
                        + "where a.paymentRequestId = :id and a.scope = 'REQUEST'", Long.class)
                .setParameter("id", id).getSingleResult();
        User requester = em.find(User.class, pr.getRequesterId());
        User supervisor = requester != null && requester.getSupervisorId() != null
                ? em.find(User.class, requester.getSupervisorId()) : null;
        assertOk(PrRules.checkSubmittable(Ref.of(pr), actor.id(), lineCount, pr.getTotalAmount(), attachmentCount,
                Boolean.TRUE.equals(type.getRequiresAttachment()), Boolean.TRUE.equals(type.getIsActive())));
        assertOk(PrTravel.validateTravel(type.getKind(), pr.getTravelDestination(), ErpDates.isoDay(pr.getTravelStartDate()),
                ErpDates.isoDay(pr.getTravelEndDate()), true));
        SubmitTarget target = PrRules.resolveSubmitStatus(pr.getRequesterId(),
                supervisor == null ? null : supervisor.getId(), supervisor == null ? null : supervisor.getIsActive());
        String from = pr.getStatus();
        support.claimStatus(pr, List.of(from));
        pr.setStatus(target.status());
        pr.setSupervisorId(target.supervisorId());
        pr.setDirectorRole(null);
        pr.setSubmittedAt(Instant.now());
        pr.setSupervisorApprovedAt(null);
        pr.setFinanceApprovedAt(null);
        pr.setFinanceApprovedBy(null);
        pr.setDirectorApprovedAt(null);
        pr.setDirectorApprovedBy(null);
        pr.setLastNote(null);
        support.addHistory(id, "SUBMIT", actor.id(), from, target.status(), null);
        ObjectNode json = support.json(pr);
        String kind = "SUBMITTED".equals(target.status()) ? "pending_supervisor" : "pending_finance";
        return new PrTransition(json, List.of(PrEvent.of(pr, kind, actor.id(), null)));
    }

    @ErpTx
    public PrTransition approve(String id, PrViewer actor, Decision input) {
        support.visibleRequest(id, actor);
        PaymentRequest pr = support.loadRequest(id);
        Decide decide = PrRules.checkCanDecide(Ref.of(pr), actor);
        if (!decide.ok()) {
            throw ErpException.business(decide.error());
        }
        Instant now = Instant.now();
        String from = pr.getStatus();
        String directorRole = pr.getDirectorRole();
        if ("FINANCE".equals(decide.step())) {
            if (input.lineAccounts() != null && !input.lineAccounts().isEmpty()) {
                support.applyLineAccounts(id, input.lineAccounts());
            }
            List<ApprovalRule> rules = em.createQuery("select r from ApprovalRule r where r.documentType = :t "
                            + "and r.isActive = true", ApprovalRule.class)
                    .setParameter("t", PrRules.PR_APPROVAL_DOC_TYPE).getResultList();
            directorRole = PrRules.matchDirectorRule(rules, pr.getTotalAmount());
        }
        String next = PrRules.nextStatusAfterApprove(decide.step(), directorRole);
        support.claimStatus(pr, List.of(from));
        pr.setLastNote(emptyToNull(input.note()));
        switch (decide.step()) {
            case "SUPERVISOR" -> pr.setSupervisorApprovedAt(now);
            case "FINANCE" -> {
                pr.setFinanceApprovedAt(now);
                pr.setFinanceApprovedBy(actor.id());
                pr.setDirectorRole(directorRole);
            }
            default -> {
                pr.setDirectorApprovedAt(now);
                pr.setDirectorApprovedBy(actor.id());
            }
        }
        if ("APPROVED".equals(next)) {
            pr.setApprovedAt(now);
        }
        pr.setStatus(next);
        support.addHistory(id, "APPROVE", actor.id(), from, next, input.note());
        ObjectNode json = support.json(pr);
        String kind = "PENDING_FINANCE".equals(next) ? "pending_finance"
                : "PENDING_DIRECTOR".equals(next) ? "pending_director" : "approved";
        return new PrTransition(json, List.of(PrEvent.of(pr, kind, actor.id(), input.note())));
    }

    /** Tolak (final) atau kembalikan untuk revisi — kelayakan sama dengan menyetujui. */
    @ErpTx
    public PrTransition decline(String id, PrViewer actor, String mode, String reason) {
        support.visibleRequest(id, actor);
        assertOk(PrRules.checkReason(reason));
        PaymentRequest pr = support.loadRequest(id);
        Decide decide = PrRules.checkCanDecide(Ref.of(pr), actor);
        if (!decide.ok()) {
            throw ErpException.business(decide.error());
        }
        String note = PrZod.jsTrim(reason);
        String from = pr.getStatus();
        boolean reject = "REJECT".equals(mode);
        String to = reject ? "REJECTED" : "REVISION";
        support.claimStatus(pr, List.of(from));
        pr.setStatus(to);
        pr.setLastNote(note);
        if (reject) {
            pr.setRejectedAt(Instant.now());
        }
        support.addHistory(id, mode, actor.id(), from, to, note);
        ObjectNode json = support.json(pr);
        return new PrTransition(json, List.of(PrEvent.of(pr, reject ? "rejected" : "revision", actor.id(), note)));
    }

    /** Pengaju membatalkan apa pun sebelum PAID. */
    @ErpTx
    public PrTransition cancel(String id, PrViewer actor, String reason) {
        support.visibleRequest(id, actor);
        PaymentRequest pr = support.loadRequest(id);
        assertOk(PrRules.checkRequesterCancel(Ref.of(pr), actor.id()));
        String from = pr.getStatus();
        support.claimStatus(pr, List.of(from));
        pr.setStatus("CANCELLED");
        pr.setCancelledAt(Instant.now());
        pr.setLastNote(emptyToNull(reason));
        support.addHistory(id, "CANCEL", actor.id(), from, "CANCELLED", reason);
        ObjectNode json = support.json(pr);
        // Hanya pemberi persetujuan yang sudah melihatnya yang perlu tahu (bukan untuk draf).
        List<PrEvent> events = "DRAFT".equals(from) || "REVISION".equals(from) ? List.of()
                : List.of(PrEvent.of(pr, "cancelled", actor.id(), reason));
        return new PrTransition(json, events);
    }

    /** PUT /:id/accounts — Keuangan mengatur akun biaya per baris. */
    @ErpTx
    public ObjectNode verifyAccounts(String id, PrViewer actor, List<LineAccount> lineAccounts) {
        support.visibleRequest(id, actor);
        PaymentRequest pr = support.loadRequest(id);
        assertOk(PrRules.checkFinanceVerify(Ref.of(pr), actor));
        support.applyLineAccounts(id, lineAccounts);
        String status = pr.getStatus();
        support.claimStatus(pr, List.of(status));
        pr.setUpdatedAt(Instant.now());
        support.addHistory(id, "VERIFY", actor.id(), status, status, "Akun biaya diperbarui");
        return support.json(pr);
    }

    // ───────────────────────────── bayar / batal bayar ─────────────────────────────

    @ErpTx
    public PrTransition pay(String id, PrViewer actor, Pay input) {
        support.visibleRequest(id, actor);
        PaymentRequest pr = support.loadRequest(id);
        PaymentRequestType type = support.typeOf(pr);
        assertOk(PrRules.checkPayable(Ref.of(pr), actor));
        systemAccounts.assertCashAccount(input.cashAccountId());
        List<PaymentRequestLine> lines = em.createQuery("select l from PaymentRequestLine l where l.paymentRequestId = :id "
                        + "order by l.lineNo asc", PaymentRequestLine.class)
                .setParameter("id", id).getResultList();
        String narration = PrRules.slice("Pembayaran " + pr.getNumber() + ": " + pr.getPurpose(), 250);
        List<PayLine> payLines = lines.stream()
                .map(l -> new PayLine(l.getDescription(), l.getAmount(), l.getExpenseAccountId())).toList();
        PaymentJournal built = PrRules.buildPaymentJournalLines(type.getKind(), payLines, input.cashAccountId(),
                PrRules.isAdvanceKind(type.getKind()) ? support.resolveAdvanceAccountId() : null, narration);
        if (!built.ok()) {
            throw ErpException.business(built.error());
        }
        if (built.total().compareTo(Money.nz(pr.getTotalAmount())) != 0) {
            throw ErpException.business("Total rincian tidak sama dengan total pengajuan. Muat ulang halaman.");
        }
        if (!ErpDates.isIsoDate(input.paymentDate())) {
            throw ErpException.business("Tanggal bayar tidak valid.");
        }
        Instant date = ErpDates.toInstant(input.paymentDate());
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(date); // periode tutup → 400

        // Klaim dulu: "Bayar" kedua yang paralel menunggu kunci baris lalu melihat PAID.
        Instant now = Instant.now();
        support.claimStatus(pr, List.of("APPROVED"));
        pr.setStatus("PAID");
        pr.setPaidAt(now);
        pr.setPaidById(actor.id());
        pr.setPaymentDate(date);
        pr.setCashAccountId(input.cashAccountId());
        pr.setPaymentReference(emptyToNull(input.reference()));
        // Akun posting mengikuti JENIS (uang muka → DR 1.5.6); wajib pertanggungjawaban HANYA dari requiresSettlement.
        pr.setSettlementStatus(PrSettlementRules.initialSettlementStatus(type.getKind(),
                Boolean.TRUE.equals(type.getRequiresSettlement())));

        // Penerima vendor membawa party ke jurnal agar Kas & Bank menampilkan nama vendor.
        String partyId = "VENDOR".equals(pr.getPayeeKind()) ? pr.getPartyId() : null;
        JournalEntry journal = support.postJournal(date, narration, fiscalYear.getId(), actor.id(), now, built.lines(), partyId);
        pr.setJournalEntryId(journal.getId());
        String ref = emptyToNull(input.reference());
        support.addHistory(id, "PAY", actor.id(), "APPROVED", "PAID",
                journal.getEntryNumber() + (ref != null ? " · Ref " + ref : ""));
        ObjectNode json = support.json(pr);
        log.info("Payment request paid id={} journal={} by={}", id, journal.getEntryNumber(), actor.id());
        return new PrTransition(json, List.of(PrEvent.of(pr, "paid", actor.id(), null)));
    }

    @ErpTx
    public PrTransition cancelPayment(String id, PrViewer actor, String reason) {
        support.visibleRequest(id, actor);
        assertOk(PrRules.checkReason(reason));
        PaymentRequest pr = support.loadRequest(id);
        assertOk(PrRules.checkCancelPayment(Ref.of(pr), actor));
        String journalId = pr.getJournalEntryId();
        assertJournalYearOpen(journalId,
                "Tahun buku jurnal pembayaran sudah ditutup; pembayaran tidak bisa dibatalkan.");
        String note = PrZod.jsTrim(reason);
        support.claimStatus(pr, List.of("PAID"));
        pr.setStatus("APPROVED");
        pr.setPaidAt(null);
        pr.setPaidById(null);
        pr.setPaymentDate(null);
        pr.setCashAccountId(null);
        pr.setPaymentReference(null);
        pr.setSettlementStatus(null);
        pr.setSettlementNote(null);
        pr.setSettlementDeadline(null);
        pr.setLastNote(note);
        em.createQuery("delete from PaymentRequestSettlementLine l where l.paymentRequestId = :id")
                .setParameter("id", id).executeUpdate();
        if (journalId != null) {
            gl.cancelJournalAndRecalcParties(journalId);
            pr.setJournalEntryId(null);
        }
        support.addHistory(id, "CANCEL_PAYMENT", actor.id(), "PAID", "APPROVED", note);
        ObjectNode json = support.json(pr);
        return new PrTransition(json, List.of(PrEvent.of(pr, "payment_cancelled", actor.id(), note)));
    }

    /** Jurnal di tahun buku tertutup tidak boleh dibatalkan. */
    void assertJournalYearOpen(String journalEntryId, String message) {
        if (journalEntryId == null) {
            return;
        }
        JournalEntry je = em.find(JournalEntry.class, journalEntryId);
        if (je == null || je.getFiscalYearId() == null) {
            return;
        }
        FiscalYear fy = em.find(FiscalYear.class, je.getFiscalYearId());
        if (fy != null && Boolean.TRUE.equals(fy.getIsClosed())) {
            throw ErpException.business(message);
        }
    }
}
