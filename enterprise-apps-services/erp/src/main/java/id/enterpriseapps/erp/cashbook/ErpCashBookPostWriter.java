package id.enterpriseapps.erp.cashbook;

import id.enterpriseapps.erp.cashbook.CashBookRules.JournalLineDraft;
import id.enterpriseapps.erp.cashbook.CashBookRules.RowResult;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.CashBookDraft;
import id.enterpriseapps.erp.entity.CashBookDraftRow;
import id.enterpriseapps.erp.entity.CashBookLearnedMap;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.JournalEntry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bagian transaksional posting Draft Buku Kas — port {@code postInTx}/{@code recordPostErrors}
 * ({@code services/cashBookDraftPost.ts}). Dipanggil lewat {@link ErpCashBookPostService} agar posting
 * yang diblokir di-rollback dulu, lalu error per baris dicatat di transaksi terpisah.
 */
@Service
@RequiredArgsConstructor
public class ErpCashBookPostWriter {

    private final ErpSystemAccountService systemAccounts;
    private final ErpFiscalYearService fiscalYears;
    private final ErpGlPostingService gl;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    record PostFailure(int idx, String error) {
        String fmt() {
            return "Baris " + idx + ": " + error;
        }
    }

    /** Ada baris bermasalah dan posting tidak boleh sebagian → seluruh transaksi di-rollback. */
    static final class PostBlockedException extends RuntimeException {
        final transient List<PostFailure> failed;

        PostBlockedException(List<PostFailure> failed) {
            super("Posting dibatalkan: " + failed.size() + " baris bermasalah.");
            this.failed = failed;
        }
    }

    private record ValidRow(CashBookDraftRow row, List<JournalLineDraft> lines, String narration, String fiscalYearId) {
    }

    @ErpTx
    public Map<String, Object> postInTx(String id, String userId, boolean partial) {
        // Klaim atomik: UPDATE mengambil row lock; posting paralel menunggu lalu gagal di penjaga status.
        int claim = em.createQuery("update CashBookDraft d set d.lastError = null, d.updatedAt = :now "
                        + "where d.id = :id and d.status in :st")
                .setParameter("now", Instant.now()).setParameter("id", id)
                .setParameter("st", ErpCashBookDraftService.EDITABLE_STATUSES).executeUpdate();
        if (claim == 0) {
            Long exists = em.createQuery("select count(d) from CashBookDraft d where d.id = :id", Long.class)
                    .setParameter("id", id).getSingleResult();
            if (exists == 0) {
                throw ErpException.business(ErpCashBookDraftService.NOT_FOUND);
            }
            throw ErpException.business("Draft sudah diposting atau tidak lagi bisa diposting.");
        }
        CashBookDraft draft = em.find(CashBookDraft.class, id);
        List<CashBookDraftRow> rows = em.createQuery("select r from CashBookDraftRow r where r.draftId = :id order by r.idx asc",
                CashBookDraftRow.class).setParameter("id", id).getResultList();
        systemAccounts.assertCashAccount(draft.getCashAccountId());

        List<CashBookDraftRow> todo = rows.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getSkip()) && r.getJournalEntryId() == null).toList();
        boolean alreadyPosted = rows.stream().anyMatch(r -> r.getJournalEntryId() != null);
        if (todo.isEmpty()) {
            if (!alreadyPosted) {
                throw ErpException.business("Tidak ada baris untuk diposting (semua baris dilewati).");
            }
            draft.setStatus("Posted");
            draft.setPostedAt(Instant.now());
            draft.setPostedById(userId);
            draft.setLastError(null);
            return result(id, draft.getDraftNumber(), "Posted", 0, List.of(), List.of(), 0, BigDecimal.ZERO);
        }

        Set<String> accountIds = new LinkedHashSet<>();
        todo.forEach(r -> {
            if (r.getAccountId() != null) {
                accountIds.add(r.getAccountId());
            }
        });
        Map<String, Account> accMap = new HashMap<>();
        if (!accountIds.isEmpty()) {
            for (Account a : em.createQuery("select a from Account a where a.id in :ids", Account.class)
                    .setParameter("ids", accountIds).getResultList()) {
                accMap.put(a.getId(), a);
            }
        }
        Map<String, Object> fyCache = new HashMap<>(); // hari → id tahun fiskal (String) atau pesan error (Exception)

        List<PostFailure> failed = new ArrayList<>();
        List<ValidRow> valid = new ArrayList<>();
        for (CashBookDraftRow row : todo) {
            String narration = JsCompat.slice("Buku Kas " + draft.getDraftNumber() + " #" + row.getIdx() + ": " + row.getDescription(), 250);
            Account acc = row.getAccountId() != null ? accMap.get(row.getAccountId()) : null;
            if (row.getAccountId() != null && (acc == null || Boolean.TRUE.equals(acc.getIsGroup())
                    || !Boolean.TRUE.equals(acc.getIsActive()))) {
                failed.add(new PostFailure(row.getIdx(), "Akun lawan tidak valid (tidak ditemukan, akun grup, atau tidak aktif)."));
                continue;
            }
            RowResult<List<JournalLineDraft>> built = CashBookRules.buildRowJournalLines(row.getDirection(), row.getTxnType(),
                    row.getAmount(), row.getAccountId(), row.getDescription(), draft.getCashAccountId(), narration);
            if (!built.isOk()) {
                failed.add(new PostFailure(row.getIdx(), built.error()));
                continue;
            }
            String day = ErpCashBookDraftService.dayString(row.getRowDate());
            Object fy = fyCache.computeIfAbsent(day, k -> fiscalYears.findOpen(row.getRowDate())
                    .<Object>map(FiscalYear::getId)
                    .orElse(new IllegalStateException("Tahun fiskal tidak ditemukan atau sudah ditutup untuk tanggal ini.")));
            if (fy instanceof Exception e) {
                failed.add(new PostFailure(row.getIdx(), "Tanggal " + day + ": " + e.getMessage()));
                continue;
            }
            valid.add(new ValidRow(row, built.value(), narration, (String) fy));
        }
        if (!failed.isEmpty() && (!partial || valid.isEmpty())) {
            throw new PostBlockedException(failed);
        }

        Instant now = Instant.now();
        List<Map<String, Object>> journals = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ValidRow v : valid) {
            ErpGlPostingService.Builder b = gl.journal().date(v.row().getRowDate()).narration(v.narration())
                    .fiscalYear(v.fiscalYearId()).createdBy(userId).submittedAt(now);
            for (JournalLineDraft l : v.lines()) {
                b.line(l.accountId(), l.debit(), l.credit(), l.description());
            }
            JournalEntry journal = b.post();
            v.row().setJournalEntryId(journal.getId());
            v.row().setPostedAt(now);
            v.row().setError(null);
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("rowIdx", v.row().getIdx());
            j.put("journalEntryId", journal.getId());
            j.put("entryNumber", journal.getEntryNumber());
            journals.add(j);
            totalAmount = totalAmount.add(v.row().getAmount());
        }
        for (PostFailure f : failed) {
            todo.stream().filter(r -> r.getIdx() == f.idx()).findFirst().ifPresent(r -> r.setError(f.error()));
        }
        int learned = learnFromRows(valid.stream().map(ValidRow::row).toList(), userId);

        String status = failed.isEmpty() ? "Posted" : "PartiallyPosted";
        draft.setStatus(status);
        draft.setPostedAt(now);
        draft.setPostedById(userId);
        draft.setLastError(failed.isEmpty() ? null
                : failed.size() + " baris belum diposting: " + String.join(" | ",
                failed.subList(0, Math.min(3, failed.size())).stream().map(PostFailure::fmt).toList()));
        return result(id, draft.getDraftNumber(), status, valid.size(), failed, journals, learned, totalAmount);
    }

    private static Map<String, Object> result(String id, String number, String status, int postedCount, List<PostFailure> failed,
                                              List<Map<String, Object>> journals, int learned, BigDecimal totalAmount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("draftId", id);
        m.put("draftNumber", number);
        m.put("status", status);
        m.put("postedCount", postedCount);
        List<Map<String, Object>> f = new ArrayList<>();
        for (PostFailure x : failed) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("idx", x.idx());
            e.put("error", x.error());
            f.add(e);
        }
        m.put("failed", f);
        m.put("journals", journals);
        m.put("learned", learned);
        m.put("totalAmount", JsCompat.jsonNumber(CashBookRules.roundMoney(totalAmount)));
        return m;
    }

    /** Pola → akun untuk baris yang akunnya dipilih/ditegaskan manusia. Mengembalikan jumlah peta tersentuh. */
    private int learnFromRows(List<CashBookDraftRow> rows, String userId) {
        int touched = 0;
        for (CashBookDraftRow r : rows) {
            if (!Boolean.TRUE.equals(r.getUserEdited()) || r.getAccountId() == null || !CashBookRules.isTxnType(r.getTxnType())
                    || CashBookRules.isAdvanceType(r.getTxnType())) {
                continue;
            }
            String pattern = CashBookRules.descriptionPattern(r.getDescription());
            if (pattern.isEmpty()) {
                continue;
            }
            List<CashBookLearnedMap> found = em.createQuery("select m from CashBookLearnedMap m where m.pattern = :p "
                            + "and m.txnType = :t", CashBookLearnedMap.class)
                    .setParameter("p", pattern).setParameter("t", r.getTxnType()).getResultList();
            if (found.isEmpty()) {
                CashBookLearnedMap m = new CashBookLearnedMap();
                m.setPattern(pattern);
                m.setTxnType(r.getTxnType());
                m.setAccountId(r.getAccountId());
                m.setHits(1);
                m.setUpdatedBy(userId);
                em.persist(m);
                em.flush();
            } else {
                CashBookLearnedMap existing = found.get(0);
                if (existing.getAccountId().equals(r.getAccountId())) {
                    em.createQuery("update CashBookLearnedMap m set m.hits = m.hits + 1, m.updatedBy = :u, m.updatedAt = :now "
                                    + "where m.id = :id")
                            .setParameter("u", userId).setParameter("now", Instant.now())
                            .setParameter("id", existing.getId()).executeUpdate();
                    em.refresh(existing);
                } else {
                    // Manusia memilih akun lain dari yang dipelajari: koreksi terbaru menang, keyakinan mulai ulang.
                    existing.setAccountId(r.getAccountId());
                    existing.setHits(1);
                    existing.setUpdatedBy(userId);
                    em.flush();
                }
            }
            touched += 1;
        }
        return touched;
    }

    /** Simpan error baris setelah posting di-rollback agar layar tinjau menampilkannya (best effort). */
    @ErpTx
    public void recordPostErrors(String id, List<PostFailure> failed) {
        for (PostFailure f : failed) {
            em.createQuery("update CashBookDraftRow r set r.error = :e where r.draftId = :d and r.idx = :i "
                            + "and r.journalEntryId is null")
                    .setParameter("e", f.error()).setParameter("d", id).setParameter("i", f.idx()).executeUpdate();
        }
        em.createQuery("update CashBookDraft d set d.lastError = :e, d.updatedAt = :now where d.id = :id and d.status in :st")
                .setParameter("e", "Posting gagal: " + String.join(" | ",
                        failed.subList(0, Math.min(3, failed.size())).stream().map(PostFailure::fmt).toList()))
                .setParameter("now", Instant.now())
                .setParameter("id", id)
                .setParameter("st", ErpCashBookDraftService.EDITABLE_STATUSES)
                .executeUpdate();
    }
}
