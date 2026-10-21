package id.enterpriseapps.erp.cashbank;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.cashbank.CashBankRequests.CreateGuarantee;
import id.enterpriseapps.erp.cashbank.CashBankRequests.SettleGuarantee;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpDocumentNumberService;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.accounting.SystemAccountRole;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.JournalEntry;
import id.enterpriseapps.erp.entity.Party;
import id.enterpriseapps.erp.entity.ProjectGuarantee;
import id.enterpriseapps.erp.entity.ProjectGuaranteeSettlement;
import id.enterpriseapps.erp.entity.RootType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uang Jaminan Proyek — setoran jaminan (penawaran/pelaksanaan/pemeliharaan) ke pemberi proyek; dikembalikan,
 * dipotong, atau hangus lewat settlement (boleh parsial dan berulang sampai sisa habis). Port penulisan di
 * {@code routes/projectGuarantees.ts}.
 *
 * <pre>
 *   Setor      : DR Uang Jaminan (PROJECT_GUARANTEE, 1.8) / CR Kas-Bank
 *   Settlement : DR Kas-Bank (kembali) + DR Beban (hangus) / CR Uang Jaminan
 *   Batal      : soft-cancel (JE Cancelled + ledger isCancelled + saldo dibalik)
 * </pre>
 * PENTING: semua baris jurnal/ledger modul ini TANPA partyId — dampak party menaikkan outstanding mitra untuk
 * baris ASSET ber-party (tidak ada pengecualian akun 1.8 seperti akun uang muka) sehingga piutang mitra akan
 * terdistorsi. Tautan pihak cukup di baris ProjectGuarantee.
 */
@Service
@RequiredArgsConstructor
public class ErpProjectGuaranteeService {

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpSystemAccountService systemAccounts;
    private final ErpGlPostingService gl;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** POST / — setor jaminan + JV-&lt;UJ-…&gt;. 201 → jaminan + party {id, name}. */
    @ErpTx
    public ObjectNode create(CreateGuarantee body, String userId) {
        Instant parsedDate = ErpDates.tryInstant(body.date());
        if (parsedDate == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(parsedDate);

        Account bankAccount = em.find(Account.class, body.accountId());
        if (bankAccount == null) {
            throw ErpException.business("Akun kas/bank tidak ditemukan.");
        }
        if (!systemAccounts.isCashAccount(bankAccount.getAccountNumber())) {
            throw ErpException.business("Akun yang dipilih bukan akun kas/bank.");
        }

        Party party = em.find(Party.class, body.partyId());
        if (party == null) {
            throw ErpException.business("Data pihak tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(party.getIsActive())) {
            throw ErpException.business("Pihak sudah tidak aktif.");
        }

        String guaranteeAccountId = systemAccounts.getAccount(SystemAccountRole.PROJECT_GUARANTEE).id();
        String guaranteeNumber = numbers.generate("UJ", parsedDate, fiscalYear.getId());
        BigDecimal amount = body.amount();

        String jvNumber = "JV-" + guaranteeNumber;
        assertJournalNumberFree(jvNumber);
        String narration = "Setor Uang Jaminan: " + guaranteeNumber + " - " + body.projectName() + " (" + party.getName() + ")";

        // Baris jurnal/ledger tanpa partyId — lihat catatan kelas.
        JournalEntry je = gl.journal()
                .number(jvNumber).date(parsedDate).narration(narration).fiscalYear(fiscalYear.getId()).createdBy(userId)
                .line(guaranteeAccountId, amount, Money.ZERO, narration)
                .line(body.accountId(), Money.ZERO, amount, narration)
                .post();

        ProjectGuarantee g = new ProjectGuarantee();
        g.setGuaranteeNumber(guaranteeNumber);
        g.setDate(parsedDate);
        g.setPartyId(body.partyId());
        g.setGuaranteeType(body.guaranteeType());
        g.setProjectName(body.projectName());
        g.setReferenceNo(emptyToNull(body.referenceNo()));
        g.setAmount(Money.round2(amount));
        g.setAccountId(body.accountId());
        g.setExpectedReturnDate(emptyToNull(body.expectedReturnDate()) == null ? null : ErpDates.toInstant(body.expectedReturnDate()));
        g.setNotes(emptyToNull(body.notes()));
        g.setStatus(ProjectGuaranteeList.OPEN);
        g.setJournalEntryId(je.getId());
        g.setFiscalYearId(fiscalYear.getId());
        g.setCreatedBy(userId);
        g.setSubmittedAt(Instant.now());
        em.persist(g);
        em.flush();

        ObjectNode o = CashBankJson.scalars(g);
        o.set("party", CashBankJson.pick(party, "id", "name"));
        return o;
    }

    /** POST /:id/settle — kembalikan dan/atau hanguskan (parsial boleh). 201 → { settlement, outstanding }. */
    @ErpTx
    public Map<String, Object> settle(String guaranteeId, SettleGuarantee body, String userId) {
        lockGuarantee(guaranteeId);
        ProjectGuarantee guarantee = guaranteeWithParty(guaranteeId);
        if (guarantee == null) {
            throw ErpException.business("Uang jaminan tidak ditemukan.");
        }
        if (!ProjectGuaranteeList.OPEN.equals(guarantee.getStatus())) {
            throw ErpException.business("Uang jaminan berstatus " + guarantee.getStatus() + ", tidak bisa di-settle.");
        }

        Instant parsedDate = ErpDates.tryInstant(body.date());
        if (parsedDate == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(parsedDate);

        BigDecimal returnAmount = body.returnAmount();
        BigDecimal forfeitAmount = body.forfeitAmount();
        BigDecimal total = returnAmount.add(forfeitAmount);
        BigDecimal outstanding = Money.nz(guarantee.getAmount()).subtract(sumSettled(activeSettlements(guaranteeId)));
        if (total.compareTo(outstanding.add(Money.CENT)) > 0) {
            throw ErpException.business("Jumlah settlement (" + CashBankFormat.toFixed2(total)
                    + ") melebihi sisa jaminan (" + CashBankFormat.toFixed2(outstanding) + ").");
        }

        // Akun pengembalian: wajib kas/bank.
        String returnAccountId = null;
        if (returnAmount.signum() > 0) {
            Account acc = em.find(Account.class, body.returnAccountId());
            if (acc == null) {
                throw ErpException.business("Akun kas/bank tujuan tidak ditemukan.");
            }
            if (!systemAccounts.isCashAccount(acc.getAccountNumber())) {
                throw ErpException.business("Akun tujuan pengembalian bukan akun kas/bank.");
            }
            returnAccountId = acc.getId();
        }

        // Akun hangus: pilihan user (harus EXPENSE detail) atau default Beban Lain-lain.
        String forfeitAccountId = null;
        if (forfeitAmount.signum() > 0) {
            if (body.expenseAccountId() != null && !body.expenseAccountId().isEmpty()) {
                Account acc = em.find(Account.class, body.expenseAccountId());
                if (acc == null) {
                    throw ErpException.business("Akun beban tidak ditemukan.");
                }
                if (acc.getRootType() != RootType.EXPENSE) {
                    throw ErpException.business("Akun hangus harus akun beban (EXPENSE).");
                }
                if (Boolean.TRUE.equals(acc.getIsGroup())) {
                    throw ErpException.business("Akun beban tidak boleh akun grup.");
                }
                forfeitAccountId = acc.getId();
            } else {
                forfeitAccountId = systemAccounts.getAccount(SystemAccountRole.OTHER_EXPENSE).id();
            }
        }

        String guaranteeAccountId = systemAccounts.getAccount(SystemAccountRole.PROJECT_GUARANTEE).id();
        // Satu jaminan bisa beberapa settlement → nomor JV digenerate (bukan JV-<UJ-no>).
        String jvNumber = numbers.generate("JV", parsedDate, fiscalYear.getId());
        String narration = "Settlement Uang Jaminan: " + guarantee.getGuaranteeNumber() + " - " + guarantee.getProjectName()
                + " (" + guarantee.getParty().getName() + ")";

        ErpGlPostingService.Builder journal = gl.journal()
                .number(jvNumber).date(parsedDate).narration(narration).fiscalYear(fiscalYear.getId()).createdBy(userId);
        if (returnAmount.signum() > 0 && returnAccountId != null) {
            journal.line(returnAccountId, returnAmount, Money.ZERO, narration + " (dikembalikan)");
        }
        if (forfeitAmount.signum() > 0 && forfeitAccountId != null) {
            journal.line(forfeitAccountId, forfeitAmount, Money.ZERO, narration + " (hangus)");
        }
        journal.line(guaranteeAccountId, Money.ZERO, total, narration);
        JournalEntry je = journal.post();

        ProjectGuaranteeSettlement settlement = new ProjectGuaranteeSettlement();
        settlement.setGuaranteeId(guaranteeId);
        settlement.setDate(parsedDate);
        settlement.setReturnAmount(Money.round2(returnAmount));
        settlement.setForfeitAmount(Money.round2(forfeitAmount));
        settlement.setReturnAccountId(returnAccountId);
        settlement.setForfeitAccountId(forfeitAccountId);
        settlement.setJournalEntryId(je.getId());
        settlement.setNotes(emptyToNull(body.notes()));
        settlement.setCreatedBy(userId);
        em.persist(settlement);

        BigDecimal newOutstanding = outstanding.subtract(total);
        if (newOutstanding.compareTo(Money.CENT) <= 0) {
            guarantee.setStatus(ProjectGuaranteeList.SETTLED);
        }
        em.flush();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("settlement", CashBankJson.scalars(settlement));
        out.put("outstanding", newOutstanding);
        return out;
    }

    /** PATCH /:id/cancel — batalkan setoran (belum boleh ada settlement aktif). */
    @ErpTx
    public Map<String, Object> cancel(String guaranteeId) {
        lockGuarantee(guaranteeId);
        ProjectGuarantee guarantee = em.find(ProjectGuarantee.class, guaranteeId);
        if (guarantee == null) {
            throw ErpException.business("Uang jaminan tidak ditemukan.");
        }
        if (ProjectGuaranteeList.CANCELLED.equals(guarantee.getStatus())) {
            throw ErpException.business("Uang jaminan sudah dibatalkan.");
        }
        if (!activeSettlements(guaranteeId).isEmpty()) {
            throw ErpException.business("Sudah ada settlement aktif. Batalkan settlement terlebih dahulu.");
        }
        // Soft-cancel: JANGAN buat jurnal balikan — menggandakan DR/CR mentah di ledger.
        if (guarantee.getJournalEntryId() != null) {
            gl.cancelJournal(guarantee.getJournalEntryId());
        }
        guarantee.setStatus(ProjectGuaranteeList.CANCELLED);
        guarantee.setCancelledAt(Instant.now());
        em.flush();
        return message("Uang jaminan dibatalkan.");
    }

    /** POST /settlements/:id/cancel — batalkan satu settlement; jaminan Selesai dibuka lagi bila masih bersisa. */
    @ErpTx
    public Map<String, Object> cancelSettlement(String settlementId) {
        ProjectGuaranteeSettlement settlement = em.find(ProjectGuaranteeSettlement.class, settlementId);
        if (settlement == null) {
            throw ErpException.business("Settlement tidak ditemukan.");
        }
        if (Boolean.TRUE.equals(settlement.getIsCancelled())) {
            throw ErpException.business("Settlement sudah dibatalkan.");
        }
        lockGuarantee(settlement.getGuaranteeId());
        ProjectGuarantee guarantee = em.find(ProjectGuarantee.class, settlement.getGuaranteeId());
        if (guarantee == null) {
            throw ErpException.business("Uang jaminan tidak ditemukan.");
        }
        if (ProjectGuaranteeList.CANCELLED.equals(guarantee.getStatus())) {
            throw ErpException.business("Uang jaminan induk sudah dibatalkan.");
        }
        List<ProjectGuaranteeSettlement> active = activeSettlements(guarantee.getId());

        if (settlement.getJournalEntryId() != null) {
            gl.cancelJournal(settlement.getJournalEntryId());
        }
        settlement.setIsCancelled(true);
        settlement.setCancelledAt(Instant.now());

        List<ProjectGuaranteeSettlement> remainingActive = active.stream()
                .filter(s -> !s.getId().equals(settlementId)).toList();
        BigDecimal outstanding = Money.nz(guarantee.getAmount()).subtract(sumSettled(remainingActive));
        if (ProjectGuaranteeList.SETTLED.equals(guarantee.getStatus()) && outstanding.compareTo(Money.CENT) > 0) {
            guarantee.setStatus(ProjectGuaranteeList.OPEN);
        }
        em.flush();
        return message("Settlement dibatalkan.");
    }

    // ─────────────────────────────── helper ───────────────────────────────

    private void lockGuarantee(String guaranteeId) {
        em.createNativeQuery("SELECT id FROM project_guarantees WHERE id = :id FOR UPDATE")
                .setParameter("id", guaranteeId).getResultList();
    }

    private ProjectGuarantee guaranteeWithParty(String id) {
        List<ProjectGuarantee> rows = em.createQuery(
                        "select g from ProjectGuarantee g left join fetch g.party where g.id = :id", ProjectGuarantee.class)
                .setParameter("id", id).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<ProjectGuaranteeSettlement> activeSettlements(String guaranteeId) {
        return em.createQuery("select s from ProjectGuaranteeSettlement s where s.guaranteeId = :id and s.isCancelled = false",
                        ProjectGuaranteeSettlement.class)
                .setParameter("id", guaranteeId).getResultList();
    }

    private static BigDecimal sumSettled(List<ProjectGuaranteeSettlement> settlements) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ProjectGuaranteeSettlement s : settlements) {
            sum = sum.add(Money.nz(s.getReturnAmount())).add(Money.nz(s.getForfeitAmount()));
        }
        return sum;
    }

    private void assertJournalNumberFree(String jvNumber) {
        Long taken = em.createQuery("select count(j) from JournalEntry j where j.entryNumber = :n", Long.class)
                .setParameter("n", jvNumber).getSingleResult();
        if (taken > 0) {
            throw ErpException.business("Nomor jurnal " + jvNumber + " sudah ada. Silakan coba lagi.");
        }
    }

    private static Map<String, Object> message(String text) {
        return ErpJson.map("message", text);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
