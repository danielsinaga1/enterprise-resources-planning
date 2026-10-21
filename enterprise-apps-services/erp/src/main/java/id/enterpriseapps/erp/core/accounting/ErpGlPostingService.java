package id.enterpriseapps.erp.core.accounting;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.entity.AccountingLedgerEntry;
import id.enterpriseapps.erp.entity.DocumentStatus;
import id.enterpriseapps.erp.entity.JournalEntry;
import id.enterpriseapps.erp.entity.JournalItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Posting jurnal double-entry — pola {@code tx.journalEntry.create({ items }) +
 * accountingLedgerEntry.createMany + updateAccountBalance} yang tersebar di sumber ENTARIS
 * (dan {@code postJournalInTx} di paymentRequestService).
 *
 * <p>Satu panggilan {@link Builder#post()} membuat: header {@code journal_entries} berstatus
 * Submitted, baris {@code journal_entry_accounts}, baris {@code accounting_ledger_entries}
 * (referenceType "JournalEntry"), lalu memperbarui saldo akun. Jurnal yang tidak seimbang
 * (selisih &gt; 0,01) ditolak — trigger DB juga menolaknya saat commit.
 *
 * <pre>{@code
 * JournalEntry je = gl.journal()
 *     .number("JV-" + invoice.getInvoiceNumber())     // opsional; default JV-YYYYMM-NNNN
 *     .date(date).narration("Penjualan: ...").fiscalYear(fy.getId()).createdBy(userId)
 *     .line(arAccountId, total, ZERO, "Piutang: SI-...", partyId)   // party di item + ledger
 *     .line(salesAccountId, ZERO, total, "Penjualan: SI-...")
 *     .post();
 * }</pre>
 */
@Service
@RequiredArgsConstructor
public class ErpGlPostingService {

    public static final String REF_JOURNAL = "JournalEntry";

    private final ErpDocumentNumberService numbers;
    private final ErpAccountBalanceService balances;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    public record Line(String accountId, BigDecimal debit, BigDecimal credit, String description,
                       String itemPartyId, String ledgerPartyId) {
    }

    public Builder journal() {
        return new Builder();
    }

    public final class Builder {
        private String entryNumber;
        private Instant date;
        private String narration;
        private String fiscalYearId;
        private String createdBy;
        private Instant submittedAt;
        private boolean isDummy;
        private String allItemsPartyId;
        private final List<Line> lines = new ArrayList<>();

        public Builder number(String entryNumber) {
            this.entryNumber = entryNumber;
            return this;
        }

        public Builder date(Instant date) {
            this.date = date;
            return this;
        }

        public Builder narration(String narration) {
            this.narration = narration;
            return this;
        }

        public Builder fiscalYear(String fiscalYearId) {
            this.fiscalYearId = fiscalYearId;
            return this;
        }

        public Builder createdBy(String userId) {
            this.createdBy = userId;
            return this;
        }

        public Builder submittedAt(Instant submittedAt) {
            this.submittedAt = submittedAt;
            return this;
        }

        public Builder dummy(boolean isDummy) {
            this.isDummy = isDummy;
            return this;
        }

        /** Party pada SEMUA baris journal item (kolom "Pihak" Kas & Bank); ledger tetap tanpa party. */
        public Builder itemsParty(String partyId) {
            this.allItemsPartyId = partyId;
            return this;
        }

        /** Baris tanpa party. */
        public Builder line(String accountId, BigDecimal debit, BigDecimal credit, String description) {
            lines.add(new Line(accountId, debit, credit, description, null, null));
            return this;
        }

        /** Baris dengan party di journal item DAN ledger (mis. piutang/hutang per mitra). */
        public Builder line(String accountId, BigDecimal debit, BigDecimal credit, String description, String partyId) {
            lines.add(new Line(accountId, debit, credit, description, partyId, partyId));
            return this;
        }

        /** Kendali penuh atas party item vs ledger. */
        public Builder line(Line line) {
            lines.add(line);
            return this;
        }

        public Builder lines(List<Line> more) {
            lines.addAll(more);
            return this;
        }

        public List<Line> lines() {
            return lines;
        }

        /** Simpan jurnal + ledger + update saldo. Baris debit=kredit=0 dibuang. */
        public JournalEntry post() {
            return ErpGlPostingService.this.post(this);
        }
    }

    /** Dipanggil lewat {@link Builder#post()} — WAJIB di dalam method {@code @ErpTx} pemanggil. */
    protected JournalEntry post(Builder b) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Posting jurnal harus di dalam transaksi @ErpTx");
        }
        if (b.date == null || b.fiscalYearId == null || b.createdBy == null) {
            throw new IllegalArgumentException("Jurnal butuh date, fiscalYearId, createdBy");
        }
        List<Line> lines = b.lines.stream()
                .filter(l -> Money.nz(l.debit()).signum() != 0 || Money.nz(l.credit()).signum() != 0)
                .toList();
        if (lines.isEmpty()) {
            throw ErpException.business("Jurnal tidak memiliki baris bernilai.");
        }
        BigDecimal dr = Money.sum(lines, Line::debit);
        BigDecimal cr = Money.sum(lines, Line::credit);
        if (!Money.eqCents(dr, cr)) {
            throw ErpException.business("Jurnal tidak seimbang: total debit " + Money.idr(dr)
                    + " ≠ total kredit " + Money.idr(cr) + ".");
        }

        JournalEntry je = new JournalEntry();
        je.setEntryNumber(b.entryNumber != null ? b.entryNumber : numbers.generate("JV", b.date, b.fiscalYearId));
        je.setDate(b.date);
        je.setNarration(b.narration);
        je.setStatus(DocumentStatus.Submitted);
        je.setFiscalYearId(b.fiscalYearId);
        je.setCreatedBy(b.createdBy);
        je.setSubmittedAt(b.submittedAt != null ? b.submittedAt : Instant.now());
        je.setIsDummy(b.isDummy);
        em.persist(je);

        List<JournalItem> items = new ArrayList<>();
        for (Line l : lines) {
            JournalItem ji = new JournalItem();
            ji.setJournalEntryId(je.getId());
            ji.setAccountId(l.accountId());
            ji.setPartyId(l.itemPartyId() != null ? l.itemPartyId() : b.allItemsPartyId);
            ji.setDebit(Money.round2(l.debit()));
            ji.setCredit(Money.round2(l.credit()));
            ji.setDescription(l.description());
            em.persist(ji);
            items.add(ji);

            AccountingLedgerEntry le = new AccountingLedgerEntry();
            le.setDate(b.date);
            le.setAccountId(l.accountId());
            le.setPartyId(l.ledgerPartyId());
            le.setDebit(Money.round2(l.debit()));
            le.setCredit(Money.round2(l.credit()));
            le.setReferenceType(REF_JOURNAL);
            le.setReferenceId(je.getId());
            le.setDescription(l.description() != null ? l.description() : b.narration);
            le.setFiscalYearId(b.fiscalYearId);
            em.persist(le);
        }
        em.flush();
        for (Line l : lines) {
            balances.updateAccountBalance(l.accountId(), Money.round2(l.debit()), Money.round2(l.credit()));
        }
        je.setItems(items);
        return je;
    }

    /**
     * Batalkan satu jurnal: status Cancelled, ledger is_cancelled, saldo akun dibalik.
     * No-op bila sudah Cancelled. Mengembalikan id akun yang terdampak.
     */
    @ErpTx
    public Set<String> cancelJournal(String journalEntryId) {
        JournalEntry je = em.find(JournalEntry.class, journalEntryId);
        Set<String> affected = new HashSet<>();
        if (je == null || je.getStatus() == DocumentStatus.Cancelled) {
            return affected;
        }
        je.setStatus(DocumentStatus.Cancelled);
        je.setCancelledAt(Instant.now());
        em.flush();
        em.createQuery("update AccountingLedgerEntry e set e.isCancelled = true where e.referenceId = :id")
                .setParameter("id", journalEntryId)
                .executeUpdate();
        List<JournalItem> items = em.createQuery(
                        "select i from JournalItem i where i.journalEntryId = :id", JournalItem.class)
                .setParameter("id", journalEntryId).getResultList();
        for (JournalItem ji : items) {
            balances.updateAccountBalance(ji.getAccountId(), ji.getCredit(), ji.getDebit());
            affected.add(ji.getAccountId());
        }
        return affected;
    }

    /**
     * Batalkan jurnal + hitung ulang outstanding mitra di baris jurnalnya — port
     * {@code cancelJournalInTx} (gaFinance.ts) yang dipakai semua jalur pembatalan EAM &
     * Pengajuan Pembayaran. 400 bila jurnal tidak ada.
     */
    @ErpTx
    public JournalEntry cancelJournalAndRecalcParties(String journalEntryId) {
        JournalEntry je = em.find(JournalEntry.class, journalEntryId);
        if (je == null) {
            throw ErpException.business("Jurnal tidak ditemukan.");
        }
        if (je.getStatus() == DocumentStatus.Cancelled) {
            return je;
        }
        List<String> partyIds = em.createQuery(
                        "select distinct i.partyId from JournalItem i where i.journalEntryId = :id and i.partyId is not null",
                        String.class)
                .setParameter("id", journalEntryId).getResultList();
        cancelJournal(journalEntryId);
        for (String pid : partyIds) {
            balances.recalcPartyOutstanding(pid);
        }
        return je;
    }

    /**
     * Batalkan SEMUA jurnal aktif yang nomornya diawali prefix (mis. {@code JV-PR0004} beserta
     * revisi {@code -R}, {@code -R2}) — port {@code utils/journalCancel.ts}. Mengembalikan jumlahnya.
     */
    @ErpTx
    public int cancelJournalsByPrefix(String entryNumberPrefix) {
        List<String> ids = em.createQuery(
                        "select j.id from JournalEntry j where j.entryNumber like :p "
                                + "and j.status <> id.enterpriseapps.erp.entity.DocumentStatus.Cancelled", String.class)
                .setParameter("p", entryNumberPrefix.replace("_", "\\_").replace("%", "\\%") + "%")
                .getResultList();
        for (String id : ids) {
            cancelJournal(id);
        }
        return ids.size();
    }
}
