package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpAccountBalanceService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.entity.AccountingLedgerEntry;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.JournalItem;
import id.neravoxa.erp.entity.RootType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Tahun fiskal — port {@code routes/fiscalYears.ts}: daftar, buat (tanpa tumpang tindih), tutup
 * buku (jurnal penutup laba/rugi ke Laba Ditahan + nolkan saldo pendapatan/beban tahun itu) dan
 * buka kembali (hapus jurnal penutup, kembalikan saldo).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpFiscalYearClosingService {

    private static final List<RootType> PROFIT_AND_LOSS = List.of(RootType.REVENUE, RootType.EXPENSE);

    private final ErpSystemAccountService systemAccounts;
    private final ErpAccountBalanceService balances;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET /fiscal-years — terbaru dulu. */
    @ErpTx(readOnly = true)
    public ArrayNode list() {
        ArrayNode arr = ErpJson.arrayNode();
        for (FiscalYear y : em.createQuery("select f from FiscalYear f order by f.startDate desc", FiscalYear.class)
                .getResultList()) {
            arr.add(AccountingJson.pick(y, AccountingJson.FISCAL_YEAR));
        }
        return arr;
    }

    /** POST /fiscal-years — rentang tanggal tidak boleh bertumpang tindih dengan tahun lain. */
    @ErpTx
    public ObjectNode create(AccountingRequests.FiscalYearCreate body) {
        Instant start = ErpDates.toInstant(body.startDate());
        Instant end = ErpDates.toInstant(body.endDate());
        if (!start.isBefore(end)) {
            throw ErpException.business("Tanggal mulai harus sebelum tanggal akhir.");
        }
        List<FiscalYear> overlapping = em.createQuery(
                        "select f from FiscalYear f where f.startDate <= :end and f.endDate >= :start", FiscalYear.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .setMaxResults(1)
                .getResultList();
        if (!overlapping.isEmpty()) {
            throw ErpException.conflict("Tahun fiskal bertumpang tindih dengan \"" + overlapping.get(0).getName()
                    + "\". Periksa rentang tanggal.");
        }
        long sameName = em.createQuery("select count(f) from FiscalYear f where f.name = :n", Long.class)
                .setParameter("n", body.name()).getSingleResult();
        if (sameName > 0) {
            throw ErpException.conflict("Nama tahun fiskal sudah digunakan.");
        }
        FiscalYear y = new FiscalYear();
        y.setName(body.name());
        y.setStartDate(start);
        y.setEndDate(end);
        y.setIsClosed(false);
        em.persist(y);
        try {
            em.flush();
        } catch (PersistenceException e) {
            if (AccountingDb.isUniqueViolation(e)) {
                throw ErpException.conflict("Nama tahun fiskal sudah digunakan.");
            }
            throw e;
        }
        em.refresh(y);
        return AccountingJson.pick(y, AccountingJson.FISCAL_YEAR);
    }

    /**
     * POST /fiscal-years/:id/close — laba bersih tahun ini (pendapatan − beban dari ledger aktif)
     * dipindahkan dari Laba Tahun Berjalan ke Laba Ditahan lewat jurnal {@code CLOSE-<nama>}, lalu saldo
     * akun pendapatan/beban dikurangi kontribusi tahun ini saja agar tahun berikutnya mulai dari nol.
     */
    @ErpTx(timeout = 30)
    public ObjectNode close(String id, String userId) {
        // Kunci baris tahun buku: penutupan paralel tidak boleh mengurangi saldo dua kali.
        FiscalYear year = em.find(FiscalYear.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (year == null) {
            throw ErpException.business("Tahun buku tidak ditemukan.");
        }
        if (Boolean.TRUE.equals(year.getIsClosed())) {
            throw ErpException.business("Tahun buku sudah ditutup.");
        }

        BigDecimal[] revenue = totals(id, RootType.REVENUE);
        BigDecimal[] expense = totals(id, RootType.EXPENSE);
        BigDecimal netRevenue = revenue[1].subtract(revenue[0]);
        BigDecimal netExpense = expense[0].subtract(expense[1]);
        BigDecimal profit = netRevenue.subtract(netExpense);

        ErpSystemAccountService.MappedAccount retained = systemAccounts.getAccount(SystemAccountRole.RETAINED_EARNINGS);
        ErpSystemAccountService.MappedAccount currentProfit = systemAccounts.getAccount(SystemAccountRole.CURRENT_PROFIT);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        if (profit.signum() != 0) {
            // Pindahkan laba/rugi ke Laba Ditahan.
            BigDecimal amount = Money.round2(profit.abs());
            boolean gain = profit.signum() > 0;
            BigDecimal profitDebit = gain ? amount : BigDecimal.ZERO;
            BigDecimal profitCredit = gain ? BigDecimal.ZERO : amount;
            postClosingJournal(year, userId, now, currentProfit.id(), profitDebit, profitCredit,
                    retained.id(), profitCredit, profitDebit);
        }

        // Kurangi saldo Pendapatan/Beban sebesar kontribusi tahun ini saja (debit/kredit ditukar = dibalik).
        for (Object[] row : profitAndLossTotals(id)) {
            balances.updateAccountBalance((String) row[0], Money.nz((BigDecimal) row[2]), Money.nz((BigDecimal) row[1]));
        }

        year.setIsClosed(true);
        year.setClosedAt(now);
        year.setClosedBy(userId);
        em.flush();
        em.refresh(year);
        log.info("Tahun buku {} ditutup (laba {})", year.getName(), profit.toPlainString());
        return AccountingJson.pick(year, AccountingJson.FISCAL_YEAR);
    }

    /**
     * Jurnal penutup {@code CLOSE-<nama>} bertanggal akhir tahun. Ditulis langsung (bukan lewat
     * builder kernel) karena keterangan ledger sengaja berbeda dari keterangan baris jurnal di sumber.
     */
    private void postClosingJournal(FiscalYear year, String userId, Instant now,
                                    String profitAccountId, BigDecimal profitDebit, BigDecimal profitCredit,
                                    String retainedAccountId, BigDecimal retainedDebit, BigDecimal retainedCredit) {
        String name = year.getName();
        JournalEntry je = new JournalEntry();
        je.setEntryNumber("CLOSE-" + name);
        je.setDate(year.getEndDate());
        je.setNarration("Tutup Buku Tahun " + name);
        je.setStatus(DocumentStatus.Submitted);
        je.setFiscalYearId(year.getId());
        je.setCreatedBy(userId);
        je.setSubmittedAt(now);
        em.persist(je);

        persistItem(je, profitAccountId, profitDebit, profitCredit, "Tutup akun laba tahun berjalan " + name);
        persistItem(je, retainedAccountId, retainedDebit, retainedCredit, "Transfer ke laba ditahan " + name);
        persistLedger(year, je, profitAccountId, profitDebit, profitCredit, "Tutup buku " + name);
        persistLedger(year, je, retainedAccountId, retainedDebit, retainedCredit, "Laba ditahan " + name);
        AccountingDb.flush(em);

        balances.updateAccountBalance(profitAccountId, profitDebit, profitCredit);
        balances.updateAccountBalance(retainedAccountId, retainedDebit, retainedCredit);
    }

    private void persistItem(JournalEntry je, String accountId, BigDecimal debit, BigDecimal credit, String description) {
        JournalItem it = new JournalItem();
        it.setJournalEntryId(je.getId());
        it.setAccountId(accountId);
        it.setDebit(debit);
        it.setCredit(credit);
        it.setDescription(description);
        em.persist(it);
    }

    private void persistLedger(FiscalYear year, JournalEntry je, String accountId, BigDecimal debit, BigDecimal credit,
                               String description) {
        AccountingLedgerEntry le = new AccountingLedgerEntry();
        le.setDate(year.getEndDate());
        le.setAccountId(accountId);
        le.setDebit(debit);
        le.setCredit(credit);
        le.setReferenceType(ErpGlPostingService.REF_JOURNAL);
        le.setReferenceId(je.getId());
        le.setDescription(description);
        le.setFiscalYearId(year.getId());
        em.persist(le);
    }

    /**
     * POST /fiscal-years/:id/reopen — balik & hapus ledger serta jurnal penutup {@code CLOSE-<nama>},
     * lalu tambahkan kembali kontribusi tahun ini ke saldo akun pendapatan/beban.
     */
    @ErpTx(timeout = 30)
    public ObjectNode reopen(String id) {
        FiscalYear year = em.find(FiscalYear.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (year == null) {
            throw ErpException.business("Tahun buku tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(year.getIsClosed())) {
            throw ErpException.business("Tahun buku belum ditutup.");
        }

        List<String> closeIds = em.createQuery(
                        "select j.id from JournalEntry j where j.entryNumber = :no and j.fiscalYearId = :fy", String.class)
                .setParameter("no", "CLOSE-" + year.getName())
                .setParameter("fy", id)
                .setMaxResults(1)
                .getResultList();
        if (!closeIds.isEmpty()) {
            String closeId = closeIds.get(0);
            List<AccountingLedgerEntry> ledger = em.createQuery(
                            "select l from AccountingLedgerEntry l where l.referenceType = :rt and l.referenceId = :jid "
                                    + "and l.fiscalYearId = :fy", AccountingLedgerEntry.class)
                    .setParameter("rt", ErpGlPostingService.REF_JOURNAL)
                    .setParameter("jid", closeId)
                    .setParameter("fy", id)
                    .getResultList();
            // Balik saldo dari ledger penutup.
            for (AccountingLedgerEntry le : ledger) {
                balances.updateAccountBalance(le.getAccountId(), le.getCredit(), le.getDebit());
            }
            em.createQuery("delete from AccountingLedgerEntry l where l.referenceType = :rt and l.referenceId = :jid "
                            + "and l.fiscalYearId = :fy")
                    .setParameter("rt", ErpGlPostingService.REF_JOURNAL)
                    .setParameter("jid", closeId)
                    .setParameter("fy", id)
                    .executeUpdate();
            // Baris jurnal ikut terhapus lewat ON DELETE CASCADE.
            em.createQuery("delete from JournalEntry j where j.id = :jid")
                    .setParameter("jid", closeId)
                    .executeUpdate();
        }

        // Tambahkan kembali kontribusi tahun ini yang dikurangi saat penutupan.
        for (Object[] row : profitAndLossTotals(id)) {
            balances.updateAccountBalance((String) row[0], Money.nz((BigDecimal) row[1]), Money.nz((BigDecimal) row[2]));
        }

        year.setIsClosed(false);
        year.setClosedAt(null);
        year.setClosedBy(null);
        em.flush();
        em.refresh(year);
        log.info("Tahun buku {} dibuka kembali", year.getName());
        return AccountingJson.pick(year, AccountingJson.FISCAL_YEAR);
    }

    /** [Σ debit, Σ kredit] ledger aktif tahun ini untuk akun dengan root type tertentu. */
    private BigDecimal[] totals(String fiscalYearId, RootType rootType) {
        Object[] r = em.createQuery("select coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0) "
                        + "from AccountingLedgerEntry l join l.account a "
                        + "where l.fiscalYearId = :fy and l.isCancelled = false and a.rootType = :rt", Object[].class)
                .setParameter("fy", fiscalYearId)
                .setParameter("rt", rootType)
                .getSingleResult();
        return new BigDecimal[]{Money.of(r[0]), Money.of(r[1])};
    }

    /** [accountId, Σ debit, Σ kredit] per akun Pendapatan/Beban dari ledger aktif tahun ini. */
    private List<Object[]> profitAndLossTotals(String fiscalYearId) {
        return em.createQuery("select l.accountId, sum(l.debit), sum(l.credit) "
                        + "from AccountingLedgerEntry l join l.account a "
                        + "where l.fiscalYearId = :fy and l.isCancelled = false and a.rootType in :rts "
                        + "group by l.accountId", Object[].class)
                .setParameter("fy", fiscalYearId)
                .setParameter("rts", PROFIT_AND_LOSS)
                .getResultList();
    }
}
