package id.enterpriseapps.erp.paymentrequest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.JournalEntry;
import id.enterpriseapps.erp.entity.PaymentRequest;
import id.enterpriseapps.erp.entity.PaymentRequestLine;
import id.enterpriseapps.erp.entity.PaymentRequestSettlementLine;
import id.enterpriseapps.erp.entity.PaymentRequestType;
import id.enterpriseapps.erp.entity.User;
import id.enterpriseapps.erp.paymentrequest.ErpPaymentRequestService.PrTransition;
import id.enterpriseapps.erp.paymentrequest.PrRequests.LineAccount;
import id.enterpriseapps.erp.paymentrequest.PrRequests.SettlementDraft;
import id.enterpriseapps.erp.paymentrequest.PrRequests.SettlementLineInput;
import id.enterpriseapps.erp.paymentrequest.PrRequests.SettlementPost;
import id.enterpriseapps.erp.paymentrequest.PrRules.Decide;
import id.enterpriseapps.erp.paymentrequest.PrRules.Ref;
import id.enterpriseapps.erp.paymentrequest.PrRules.SubmitTarget;
import id.enterpriseapps.erp.paymentrequest.PrSettlementRules.LineIn;
import id.enterpriseapps.erp.paymentrequest.PrSettlementRules.Preview;
import id.enterpriseapps.erp.paymentrequest.PrSettlementRules.PreviewLine;
import id.enterpriseapps.erp.paymentrequest.PrSettlementRules.SettlementJournal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static id.enterpriseapps.erp.paymentrequest.ErpPaymentRequestService.assertOk;

/**
 * Pertanggungjawaban uang muka / perjalanan dinas (tahap 2) — port {@code services/paymentRequestSettlementService.ts}.
 * Pola sama dengan {@link ErpPaymentRequestService}: muat → cek aturan murni → klaim optimistis
 * ({@code settlementStatus IN (...)}) → riwayat → event notifikasi setelah COMMIT.
 *
 * <p>Posting (tahap Keuangan) menulis SATU jurnal seimbang pada tanggal pertanggungjawaban di bawah guard tahun
 * fiskal terbuka; batal posting membatalkan jurnal itu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpPaymentRequestSettlementService {

    private final ErpPaymentRequestSupport support;
    private final ErpPaymentRequestService requests;
    private final ErpFiscalYearService fiscalYears;
    private final ErpSystemAccountService systemAccounts;
    private final ErpGlPostingService gl;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private static Instant toDay(String d) {
        return d == null || d.isEmpty() ? null : ErpDates.toInstant(d);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    // ───────────────────────────── pengaju: simpan draf / ajukan ─────────────────────────────

    /** PUT /:id/settlement — pengaju menyimpan baris realisasi (status tidak berubah). */
    @ErpTx
    public ObjectNode saveDraft(String id, PrViewer actor, SettlementDraft input) {
        support.visibleRequest(id, actor);
        PaymentRequest pr = support.loadRequest(id);
        PaymentRequestType type = support.typeOf(pr);
        assertOk(PrSettlementRules.checkSettlementEditable(Ref.of(pr), actor.id()));
        List<PaymentRequestLine> planned = em.createQuery("select l from PaymentRequestLine l where l.paymentRequestId = :id",
                        PaymentRequestLine.class)
                .setParameter("id", id).getResultList();
        Map<String, PaymentRequestLine> plannedById = new HashMap<>();
        planned.forEach(p -> plannedById.put(p.getId(), p));
        for (int i = 0; i < input.lines().size(); i++) {
            SettlementLineInput l = input.lines().get(i);
            if (l.plannedLineId() != null && !plannedById.containsKey(l.plannedLineId())) {
                throw ErpException.business("Baris realisasi " + (i + 1) + ": baris rencana tidak ditemukan.");
            }
            if (l.expenseAccountId() != null) {
                systemAccounts.assertDetailAccount(l.expenseAccountId(), "Akun biaya baris " + (i + 1));
            }
        }
        Instant deadline = toDay(input.deadline());
        if (deadline != null && pr.getPaymentDate() != null && deadline.isBefore(pr.getPaymentDate())) {
            throw ErpException.business("Batas waktu tidak boleh sebelum tanggal pembayaran uang muka.");
        }
        support.claimSettlement(pr, List.of(pr.getSettlementStatus()));
        pr.setSettlementNote(emptyToNull(input.note() == null ? null : PrZod.jsTrim(input.note())));
        pr.setSettlementDeadline(deadline);
        em.createQuery("delete from PaymentRequestSettlementLine l where l.paymentRequestId = :id")
                .setParameter("id", id).executeUpdate();
        for (int i = 0; i < input.lines().size(); i++) {
            SettlementLineInput l = input.lines().get(i);
            PaymentRequestSettlementLine row = new PaymentRequestSettlementLine();
            row.setPaymentRequestId(id);
            row.setLineNo(i + 1);
            row.setDescription(PrZod.jsTrim(l.description()));
            row.setExpenseDate(toDay(l.expenseDate()));
            row.setAmount(l.amount().setScale(2, RoundingMode.HALF_UP));
            row.setCategory(l.category());
            row.setPlannedLineId(l.plannedLineId());
            // Akun default: pilihan pengaju/Keuangan, lalu akun baris rencana, lalu akun default jenis.
            String acc = l.expenseAccountId();
            if (acc == null && l.plannedLineId() != null) {
                acc = plannedById.get(l.plannedLineId()).getExpenseAccountId();
            }
            if (acc == null) {
                acc = type.getDefaultExpenseAccountId();
            }
            row.setExpenseAccountId(acc);
            em.persist(row);
        }
        return support.json(pr);
    }

    @ErpTx
    public PrTransition submit(String id, PrViewer actor) {
        support.visibleRequest(id, actor);
        PaymentRequest pr = support.loadRequest(id);
        List<BigDecimal> amounts = em.createQuery("select l.amount from PaymentRequestSettlementLine l "
                        + "where l.paymentRequestId = :id", BigDecimal.class)
                .setParameter("id", id).getResultList();
        User requester = em.find(User.class, pr.getRequesterId());
        User supervisor = requester != null && requester.getSupervisorId() != null
                ? em.find(User.class, requester.getSupervisorId()) : null;
        BigDecimal realized = PrRules.computeTotal(amounts);
        assertOk(PrSettlementRules.checkSettlementSubmittable(Ref.of(pr), actor.id(), amounts.size(), realized));
        SubmitTarget target = PrRules.resolveSubmitStatus(pr.getRequesterId(),
                supervisor == null ? null : supervisor.getId(), supervisor == null ? null : supervisor.getIsActive());
        String from = pr.getSettlementStatus();
        support.claimSettlement(pr, List.of(from));
        pr.setSettlementStatus(target.status());
        pr.setSettlementSupervisorId(target.supervisorId());
        pr.setSettlementSubmittedAt(Instant.now());
        pr.setSettlementSupervisorApprovedAt(null);
        pr.setSettlementLastNote(null);
        pr.setSettlementRealized(realized);
        support.addHistory(id, "SETTLE_SUBMIT", actor.id(), from, target.status(), null);
        ObjectNode json = support.json(pr);
        String kind = "SUBMITTED".equals(target.status()) ? "settlement_pending_supervisor" : "settlement_pending_finance";
        return new PrTransition(json, List.of(PrEvent.settlement(pr, kind, actor.id(), null)));
    }

    // ───────────────────────────── atasan / Keuangan: setujui, kembalikan ─────────────────────────────

    /** Tahap atasan: setujui → Keuangan. (Keuangan memakai {@link #post}.) */
    @ErpTx
    public PrTransition approve(String id, PrViewer actor, String note) {
        support.visibleRequest(id, actor);
        PaymentRequest pr = support.loadRequest(id);
        Decide decide = PrSettlementRules.checkSettlementDecide(Ref.of(pr), actor);
        if (!decide.ok()) {
            throw ErpException.business(decide.error());
        }
        if (!"SUPERVISOR".equals(decide.step())) {
            throw ErpException.business("Tahap Keuangan diselesaikan dengan memposting pertanggungjawaban.");
        }
        support.claimSettlement(pr, List.of("SUBMITTED"));
        pr.setSettlementStatus("PENDING_FINANCE");
        pr.setSettlementSupervisorApprovedAt(Instant.now());
        pr.setSettlementLastNote(note == null ? null : emptyToNull(PrZod.jsTrim(note)));
        support.addHistory(id, "SETTLE_APPROVE", actor.id(), "SUBMITTED", "PENDING_FINANCE", note);
        ObjectNode json = support.json(pr);
        return new PrTransition(json, List.of(PrEvent.settlement(pr, "settlement_pending_finance", actor.id(), note)));
    }

    /** Kembalikan untuk revisi (atasan atau Keuangan): pengaju mengubah lalu mengajukan ulang. */
    @ErpTx
    public PrTransition returnForRevision(String id, PrViewer actor, String reason) {
        support.visibleRequest(id, actor);
        assertOk(PrRules.checkReason(reason));
        PaymentRequest pr = support.loadRequest(id);
        Decide decide = PrSettlementRules.checkSettlementDecide(Ref.of(pr), actor);
        if (!decide.ok()) {
            throw ErpException.business(decide.error());
        }
        String note = PrZod.jsTrim(reason);
        String from = pr.getSettlementStatus();
        support.claimSettlement(pr, List.of(from));
        pr.setSettlementStatus("REVISION");
        pr.setSettlementLastNote(note);
        support.addHistory(id, "SETTLE_RETURN", actor.id(), from, "REVISION", note);
        ObjectNode json = support.json(pr);
        return new PrTransition(json, List.of(PrEvent.settlement(pr, "settlement_revision", actor.id(), note)));
    }

    // ───────────────────────────── Keuangan: pratinjau, posting, batal posting ─────────────────────────────

    /** Akun yang didebit saat uang muka dibayar (debit terbesar jurnal bayar); cadangan: pemetaan akun sistem. */
    private String resolveAdvanceAccount(String journalEntryId) {
        if (journalEntryId != null) {
            List<String> ids = em.createQuery("select i.accountId from JournalItem i where i.journalEntryId = :id "
                            + "and i.debit > 0 order by i.debit desc", String.class)
                    .setParameter("id", journalEntryId).setMaxResults(1).getResultList();
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        return support.resolveAdvanceAccountId();
    }

    private record PostingLine(String id, String description, BigDecimal amount, String expenseAccountId) {
    }

    private record PostingContext(PaymentRequest pr, List<PostingLine> lines, String advanceAccountId, String narration) {
        List<LineIn> lineIns() {
            return lines.stream().map(l -> new LineIn(l.description(), l.amount(), l.expenseAccountId())).toList();
        }
    }

    private PostingContext loadPostingContext(String id, List<LineAccount> lineAccounts) {
        PaymentRequest pr = support.loadRequest(id);
        List<PaymentRequestSettlementLine> rows = em.createQuery("select l from PaymentRequestSettlementLine l "
                        + "where l.paymentRequestId = :id order by l.lineNo asc", PaymentRequestSettlementLine.class)
                .setParameter("id", id).getResultList();
        Map<String, String> overrides = new LinkedHashMap<>();
        Set<String> rowIds = new LinkedHashSet<>();
        rows.forEach(r -> rowIds.add(r.getId()));
        if (lineAccounts != null) {
            for (LineAccount la : lineAccounts) {
                overrides.put(la.id(), la.expenseAccountId());
            }
            for (LineAccount la : lineAccounts) {
                if (!rowIds.contains(la.id())) {
                    throw ErpException.business("Baris realisasi tidak ditemukan pada pengajuan ini.");
                }
                if (la.expenseAccountId() != null) {
                    systemAccounts.assertDetailAccount(la.expenseAccountId(), "Akun biaya");
                }
            }
        }
        List<PostingLine> lines = new ArrayList<>();
        for (PaymentRequestSettlementLine r : rows) {
            String acc = overrides.containsKey(r.getId()) ? overrides.get(r.getId()) : r.getExpenseAccountId();
            lines.add(new PostingLine(r.getId(), r.getDescription(), r.getAmount(), acc));
        }
        String advanceAccountId = resolveAdvanceAccount(pr.getJournalEntryId());
        String narration = PrRules.slice("Pertanggungjawaban " + pr.getNumber() + ": " + pr.getPurpose(), 250);
        return new PostingContext(pr, lines, advanceAccountId, narration);
    }

    /** Bentuk JSON pratinjau (angka gaya JS); {@code labels} = id akun → "nomor · nama". */
    static Map<String, Object> previewJson(Preview p, Map<String, String> labels) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (PreviewLine l : p.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("side", l.side());
            m.put("accountId", l.accountId());
            if (labels != null) {
                m.put("accountLabel", l.accountId() != null ? labels.get(l.accountId()) : null);
            }
            m.put("amount", PrJson.num(l.amount()));
            m.put("description", l.description());
            lines.add(m);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("realized", PrJson.num(p.realized()));
        m.put("advance", PrJson.num(p.advance()));
        m.put("difference", PrJson.num(p.difference()));
        m.put("mode", p.mode());
        m.put("cashRequired", p.cashRequired());
        m.put("lines", lines);
        m.put("problems", p.problems());
        return m;
    }

    /** Pratinjau jurnal (tidak menyimpan apa pun): apa yang akan dibukukan dengan akun yang dipilih. */
    @ErpTx(readOnly = true)
    public Map<String, Object> preview(String id, PrViewer actor, List<LineAccount> lineAccounts, String cashAccountId) {
        support.visibleRequest(id, actor);
        PostingContext ctx = loadPostingContext(id, lineAccounts);
        Preview p = PrSettlementRules.previewSettlementJournal(ctx.lineIns(), ctx.pr().getTotalAmount(),
                ctx.advanceAccountId(), cashAccountId, ctx.narration());
        Set<String> ids = new LinkedHashSet<>();
        p.lines().forEach(l -> {
            if (l.accountId() != null) {
                ids.add(l.accountId());
            }
        });
        Map<String, String> labels = new HashMap<>();
        if (!ids.isEmpty()) {
            em.createQuery("select a from Account a where a.id in :ids", Account.class).setParameter("ids", ids)
                    .getResultList()
                    .forEach(a -> labels.put(a.getId(), a.getAccountNumber() + " · " + a.getName()));
        }
        return previewJson(p, labels);
    }

    @ErpTx
    public PrTransition post(String id, PrViewer actor, SettlementPost input) {
        support.visibleRequest(id, actor);
        PostingContext ctx = loadPostingContext(id, input.lineAccounts());
        PaymentRequest pr = ctx.pr();
        Decide decide = PrSettlementRules.checkSettlementDecide(Ref.of(pr), actor);
        if (!decide.ok()) {
            throw ErpException.business(decide.error());
        }
        if (!"FINANCE".equals(decide.step())) {
            throw ErpException.business("Pertanggungjawaban belum disetujui atasan.");
        }
        if (!ErpDates.isIsoDate(input.settlementDate())) {
            throw ErpException.business("Tanggal pertanggungjawaban tidak valid.");
        }
        Instant date = ErpDates.toInstant(input.settlementDate());
        if (pr.getPaymentDate() != null && date.isBefore(pr.getPaymentDate())) {
            throw ErpException.business("Tanggal pertanggungjawaban tidak boleh sebelum tanggal pembayaran uang muka.");
        }
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(date); // periode tutup → 400

        BigDecimal advance = Money.nz(pr.getTotalAmount());
        boolean needsCash = PrRules.computeTotal(ctx.lines().stream().map(PostingLine::amount).toList())
                .compareTo(advance) != 0;
        String cashAccountId = needsCash ? input.cashAccountId() : null;
        if (cashAccountId != null) {
            systemAccounts.assertCashAccount(cashAccountId);
        }
        SettlementJournal built = PrSettlementRules.buildSettlementJournalLines(ctx.lineIns(), advance,
                ctx.advanceAccountId(), cashAccountId, ctx.narration());
        if (!built.ok()) {
            throw ErpException.business(built.error());
        }

        // Simpan akun pilihan Keuangan (juga bila baru diubah di langkah ini).
        for (PostingLine l : ctx.lines()) {
            em.createQuery("update PaymentRequestSettlementLine l set l.expenseAccountId = :acc where l.id = :id")
                    .setParameter("acc", l.expenseAccountId()).setParameter("id", l.id()).executeUpdate();
        }

        Instant now = Instant.now();
        support.claimSettlement(pr, List.of("PENDING_FINANCE"));
        pr.setSettlementStatus("SETTLED");
        pr.setSettledAt(now);
        pr.setSettlementDate(date);
        pr.setSettlementRealized(Money.round2(built.preview().realized()));
        pr.setSettlementCashAccountId(cashAccountId);
        pr.setSettlementReference(emptyToNull(input.reference()));
        pr.setSettlementLastNote(input.note() == null ? null : emptyToNull(PrZod.jsTrim(input.note())));
        JournalEntry journal = support.postJournal(date, ctx.narration(), fiscalYear.getId(), actor.id(), now,
                built.lines(), null);
        pr.setSettlementJournalId(journal.getId());
        String ref = emptyToNull(input.reference());
        support.addHistory(id, "SETTLE_POST", actor.id(), "PENDING_FINANCE", "SETTLED",
                journal.getEntryNumber() + (ref != null ? " · Ref " + ref : ""));
        ObjectNode json = support.json(pr);
        log.info("Payment request settlement posted id={} journal={} mode={} by={}", id, journal.getEntryNumber(),
                built.preview().mode(), actor.id());
        return new PrTransition(json, List.of(PrEvent.settlement(pr, "settlement_settled", actor.id(), null)));
    }

    @ErpTx
    public PrTransition cancelPosting(String id, PrViewer actor, String reason) {
        support.visibleRequest(id, actor);
        assertOk(PrRules.checkReason(reason));
        PaymentRequest pr = support.loadRequest(id);
        assertOk(PrSettlementRules.checkCancelSettlementPosting(Ref.of(pr), actor));
        String journalId = pr.getSettlementJournalId();
        requests.assertJournalYearOpen(journalId,
                "Tahun buku jurnal pertanggungjawaban sudah ditutup; posting tidak bisa dibatalkan.");
        String note = PrZod.jsTrim(reason);
        support.claimSettlement(pr, List.of("SETTLED"));
        pr.setSettlementStatus("PENDING_FINANCE");
        pr.setSettledAt(null);
        pr.setSettlementDate(null);
        pr.setSettlementCashAccountId(null);
        pr.setSettlementReference(null);
        pr.setSettlementLastNote(note);
        if (journalId != null) {
            gl.cancelJournalAndRecalcParties(journalId);
            pr.setSettlementJournalId(null);
        }
        support.addHistory(id, "SETTLE_CANCEL", actor.id(), "SETTLED", "PENDING_FINANCE", note);
        ObjectNode json = support.json(pr);
        return new PrTransition(json, List.of(PrEvent.settlement(pr, "settlement_posting_cancelled", actor.id(), note)));
    }
}
