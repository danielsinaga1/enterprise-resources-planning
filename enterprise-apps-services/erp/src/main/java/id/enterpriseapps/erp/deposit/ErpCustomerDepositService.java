package id.enterpriseapps.erp.deposit;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpAccountBalanceService;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpPartyBalanceService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService.MappedAccount;
import id.enterpriseapps.erp.core.accounting.SystemAccountRole;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.CustomerDepositApplication;
import id.enterpriseapps.erp.entity.DocumentStatus;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.JournalEntry;
import id.enterpriseapps.erp.entity.Party;
import id.enterpriseapps.erp.entity.Payment;
import id.enterpriseapps.erp.entity.PaymentType;
import id.enterpriseapps.erp.entity.SalesInvoice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static id.enterpriseapps.erp.deposit.DepositSupport.CENT;
import static id.enterpriseapps.erp.deposit.DepositSupport.fixed2;

/**
 * Uang muka pelanggan (akun 2.1.2, saldo mitra {@code customer_deposit_balance}) — port alur tulis
 * {@code routes/customerDeposits.ts}: batal uang muka, aplikasi ke invoice penjualan (DR 2.1.2 / CR
 * Piutang), batal aplikasi, serta penyelesaian sisa lewat offset piutang FIFO dan/atau refund kas.
 *
 * <p>Penerimaan uang muka (DR Kas / CR 2.1.2) dibuat di rute pembayaran (paymentType CustomerDeposit),
 * bukan di sini. Saldo mitra diubah lewat {@link ErpPartyBalanceService#incrementParty} (atomik) dan
 * outstanding mitra dihitung ulang dengan {@link ErpAccountBalanceService#recalcPartyOutstanding},
 * sama seperti sumber.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpCustomerDepositService {

    private static final String BALANCE_COLUMN = "customer_deposit_balance";

    private final ErpDepositQueryService queries;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService accountBalances;
    private final ErpPartyBalanceService partyBalances;
    private final ErpFiscalYearService fiscalYears;
    private final ErpSystemAccountService systemAccounts;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── batal uang muka ─────────────────────────────

    /**
     * PATCH /:id/cancel — hanya bila belum ada aplikasi aktif dan belum pernah direfund kas (refund punya
     * jurnal sendiri; pembalikan parsial tidak didukung). Jurnal penerimaan dibatalkan (saldo akun
     * dibalik, ledger di-soft-cancel — TANPA jurnal [BATAL] tambahan agar ledger tidak ganda); impor lama
     * tanpa jurnal dibalik langsung DR Uang Muka Pelanggan / CR Ekuitas Saldo Awal.
     */
    @ErpTx
    public Map<String, Object> cancel(String id) {
        Payment deposit = em.find(Payment.class, id);
        if (deposit == null) {
            throw ErpException.business("Deposit tidak ditemukan.");
        }
        if (deposit.getPaymentType() != PaymentType.CustomerDeposit) {
            throw ErpException.business("Bukan tipe uang muka customer.");
        }
        if (deposit.getStatus() == DocumentStatus.Cancelled) {
            throw ErpException.business("Deposit sudah dibatalkan.");
        }
        if (queries.activeApplicationCount(DepositKind.CUSTOMER, id) > 0) {
            throw ErpException.business("Deposit sudah digunakan untuk invoice. Batalkan penggunaan terlebih dahulu.");
        }
        DepositSupport.assertNotRefunded(deposit.getRefundedAmount());

        BigDecimal depositAmount = Money.nz(deposit.getAmount());
        JournalEntry journal = deposit.getJournalEntryId() == null ? null
                : em.find(JournalEntry.class, deposit.getJournalEntryId());
        if (journal != null) {
            // Balik saldo dari baris jurnal + status Cancelled + soft-cancel ledger asli.
            gl.cancelJournal(journal.getId());
        } else {
            // Tanpa jurnal tertaut (impor lama) — balik saldo akun langsung.
            String customerDepositAccount = systemAccounts.getAccountId(SystemAccountRole.CUSTOMER_DEPOSIT);
            String openingEquityAccount = systemAccounts.getAccountId(SystemAccountRole.OPENING_EQUITY);
            accountBalances.updateAccountBalance(customerDepositAccount, depositAmount, BigDecimal.ZERO);
            accountBalances.updateAccountBalance(openingEquityAccount, BigDecimal.ZERO, depositAmount);
            // Jangan tambah JV pembalik [BATAL]; soft-cancel baris ledger impor lama secara defensif.
            DepositSupport.cancelLegacyLedgerRows(em, deposit.getPaymentNumber());
        }

        partyBalances.incrementParty(deposit.getPartyId(), BALANCE_COLUMN, depositAmount.negate());

        deposit.setStatus(DocumentStatus.Cancelled);
        deposit.setCancelledAt(Instant.now());
        return Map.of("message", "Uang muka customer dibatalkan.");
    }

    // ───────────────────────────── aplikasi ke invoice ─────────────────────────────

    /** POST /apply — DR Uang Muka Pelanggan / CR Piutang Usaha, kurangi outstanding invoice. Status 201. */
    @ErpTx
    public Map<String, Object> apply(DepositRequests.ApplyCustomer body, String userId) {
        // Kunci baris agar aplikasi bersamaan tidak melebihi sisa.
        DepositSupport.lockRow(em, "payments", body.depositPaymentId());
        DepositSupport.lockRow(em, "sales_invoices", body.salesInvoiceId());

        Payment deposit = em.find(Payment.class, body.depositPaymentId());
        if (deposit == null) {
            throw ErpException.business("Deposit tidak ditemukan.");
        }
        if (deposit.getPaymentType() != PaymentType.CustomerDeposit) {
            throw ErpException.business("Pembayaran bukan tipe Uang Muka Customer.");
        }
        if (deposit.getStatus() != DocumentStatus.Submitted) {
            throw ErpException.business("Deposit sudah dibatalkan.");
        }

        SalesInvoice invoice = em.find(SalesInvoice.class, body.salesInvoiceId());
        if (invoice == null) {
            throw ErpException.business("Invoice penjualan tidak ditemukan.");
        }
        if (invoice.getStatus() == InvoiceStatus.Cancelled) {
            throw ErpException.business("Invoice sudah dibatalkan.");
        }
        if (invoice.getStatus() == InvoiceStatus.Paid) {
            throw ErpException.business("Invoice sudah lunas.");
        }
        if (!deposit.getPartyId().equals(invoice.getPartyId())) {
            throw ErpException.business("Deposit dan invoice harus dari customer yang sama.");
        }

        // Sisa = amount − aplikasi aktif − refund kas, sama dengan rute supplier. Rute customer ENTARIS lupa
        // mengurangkan refund sehingga uang muka yang sudah direfund bisa diaplikasikan lagi (2.1.2 minus).
        BigDecimal totalApplied = queries.activeAppliedTotal(DepositKind.CUSTOMER, deposit.getId());
        BigDecimal refunded = Money.nz(deposit.getRefundedAmount());
        BigDecimal depositRemaining = Money.nz(deposit.getAmount()).subtract(totalApplied).subtract(refunded);
        BigDecimal invoiceOutstanding = Money.nz(invoice.getOutstanding());
        BigDecimal applyAmount = body.amount();

        if (applyAmount.compareTo(depositRemaining) > 0) {
            throw ErpException.business("Sisa deposit hanya Rp " + fixed2(depositRemaining) + ". Tidak cukup.");
        }
        if (applyAmount.compareTo(invoiceOutstanding) > 0) {
            throw ErpException.business("Sisa outstanding invoice hanya Rp " + fixed2(invoiceOutstanding) + ".");
        }

        Instant parsedDate = Instant.now();
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(parsedDate);
        MappedAccount arAccount = systemAccounts.getAccount(SystemAccountRole.AR);
        MappedAccount depositAccount = systemAccounts.getAccount(SystemAccountRole.CUSTOMER_DEPOSIT);

        String description = "Aplikasi UM Pelanggan: " + deposit.getPaymentNumber();
        JournalEntry journal = gl.journal()
                .date(parsedDate)
                .narration("Aplikasi Uang Muka Pelanggan: " + deposit.getPaymentNumber() + " → " + invoice.getInvoiceNumber())
                .fiscalYear(fiscalYear.getId())
                .createdBy(userId)
                .line(depositAccount.id(), applyAmount, BigDecimal.ZERO, description, invoice.getPartyId()) // DR liabilitas (berkurang)
                .line(arAccount.id(), BigDecimal.ZERO, applyAmount, description, invoice.getPartyId())      // CR piutang (berkurang)
                .post();

        BigDecimal newOutstanding = invoiceOutstanding.subtract(applyAmount);
        invoice.setOutstanding(Money.round2(newOutstanding));
        invoice.setStatus(newOutstanding.compareTo(CENT) <= 0 ? InvoiceStatus.Paid : InvoiceStatus.PartiallyPaid);
        em.flush();

        partyBalances.incrementParty(invoice.getPartyId(), BALANCE_COLUMN, applyAmount.negate());
        accountBalances.recalcPartyOutstanding(invoice.getPartyId());

        CustomerDepositApplication application = new CustomerDepositApplication();
        application.setDepositPaymentId(deposit.getId());
        application.setSalesInvoiceId(invoice.getId());
        application.setAppliedAmount(Money.round2(applyAmount));
        application.setJournalEntryId(journal.getId());
        application.setCreatedBy(userId);
        em.persist(application);
        em.flush();
        return applicationJson(application);
    }

    /**
     * POST /apply/:id/cancel (capability doc.cancel dicek controller) — batalkan jurnal aplikasi,
     * kembalikan outstanding invoice (Submitted bila kembali penuh), saldo uang muka mitra naik lagi.
     */
    @ErpTx
    public Map<String, Object> cancelApplication(String id) {
        CustomerDepositApplication application = em.find(CustomerDepositApplication.class, id);
        if (application == null) {
            throw ErpException.business("Aplikasi uang muka tidak ditemukan.");
        }
        if (Boolean.TRUE.equals(application.getIsCancelled())) {
            throw ErpException.business("Aplikasi sudah dibatalkan.");
        }
        BigDecimal applyAmount = Money.nz(application.getAppliedAmount());

        if (application.getJournalEntryId() != null && em.find(JournalEntry.class, application.getJournalEntryId()) != null) {
            gl.cancelJournal(application.getJournalEntryId());
        }

        // Kunci faktur sebelum menulis outstanding absolut (cegah lost update dengan pembayaran bersamaan).
        DepositSupport.lockRow(em, "sales_invoices", application.getSalesInvoiceId());
        SalesInvoice invoice = em.find(SalesInvoice.class, application.getSalesInvoiceId());
        BigDecimal newOutstanding = Money.nz(invoice.getOutstanding()).add(applyAmount);
        invoice.setOutstanding(Money.round2(newOutstanding));
        invoice.setStatus(newOutstanding.compareTo(Money.nz(invoice.getGrandTotal())) >= 0
                ? InvoiceStatus.Submitted : InvoiceStatus.PartiallyPaid);
        em.flush();

        partyBalances.incrementParty(invoice.getPartyId(), BALANCE_COLUMN, applyAmount);
        accountBalances.recalcPartyOutstanding(invoice.getPartyId());

        application.setIsCancelled(true);
        application.setCancelledAt(Instant.now());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", application.getId());
        out.put("status", "Cancelled");
        return out;
    }

    // ───────────────────────────── refund / offset ─────────────────────────────

    /**
     * POST /:id/refund — selesaikan sisa uang muka: offset ke piutang (FIFO, faktur terlama dulu; satu
     * jurnal + aplikasi per faktur) dan/atau refund kas (DR 2.1.2 / CR Kas-Bank). Offset yang tidak
     * habis teralokasi membatalkan seluruh transaksi.
     */
    @ErpTx
    public Map<String, Object> refund(String depositId, DepositRequests.Refund body, String userId) {
        DepositSupport.lockRow(em, "payments", depositId);

        Payment deposit = em.find(Payment.class, depositId);
        if (deposit == null) {
            throw ErpException.business("Uang muka tidak ditemukan.");
        }
        if (deposit.getPaymentType() != PaymentType.CustomerDeposit) {
            throw ErpException.business("Bukan tipe uang muka customer.");
        }
        if (deposit.getStatus() != DocumentStatus.Submitted) {
            throw ErpException.business("Uang muka sudah dibatalkan.");
        }

        BigDecimal totalApplied = queries.activeAppliedTotal(DepositKind.CUSTOMER, deposit.getId());
        BigDecimal alreadyRefunded = Money.nz(deposit.getRefundedAmount());
        BigDecimal remaining = Money.nz(deposit.getAmount()).subtract(totalApplied).subtract(alreadyRefunded);

        BigDecimal offsetReq = body.offset();
        BigDecimal cashReq = body.cash();
        BigDecimal totalReq = offsetReq.add(cashReq);
        if (totalReq.compareTo(remaining.add(CENT)) > 0) {
            throw ErpException.business("Total " + fixed2(totalReq) + " melebihi sisa uang muka " + fixed2(remaining) + ".");
        }

        Instant parsedDate = ErpDates.tryInstant(body.date());
        if (parsedDate == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(parsedDate);

        MappedAccount depositAccount = systemAccounts.getAccount(SystemAccountRole.CUSTOMER_DEPOSIT);
        MappedAccount arAccount = systemAccounts.getAccount(SystemAccountRole.AR);
        String partyId = deposit.getPartyId();
        String paymentNumber = deposit.getPaymentNumber();

        List<String> createdApplications = new ArrayList<>();
        String cashJournalId = null;

        // Offset ke piutang (FIFO, terlama dulu).
        if (offsetReq.signum() > 0) {
            DepositSupport.lockOpenInvoices(em, "sales_invoices", partyId);
            List<SalesInvoice> invoices = em.createQuery(
                            "select i from SalesInvoice i where i.partyId = :partyId and i.status in :statuses "
                                    + "order by i.date asc, i.invoiceNumber asc", SalesInvoice.class)
                    .setParameter("partyId", partyId)
                    .setParameter("statuses", List.of(InvoiceStatus.Submitted, InvoiceStatus.PartiallyPaid, InvoiceStatus.Overdue))
                    .getResultList();

            BigDecimal remainingOffset = offsetReq;
            for (SalesInvoice inv : invoices) {
                if (remainingOffset.signum() <= 0) {
                    break;
                }
                BigDecimal invOut = Money.nz(inv.getOutstanding());
                if (invOut.signum() <= 0) {
                    continue;
                }
                BigDecimal apply = invOut.min(remainingOffset);

                JournalEntry je = gl.journal()
                        .date(parsedDate)
                        .narration("Offset UM Pelanggan: " + paymentNumber + " → " + inv.getInvoiceNumber())
                        .fiscalYear(fiscalYear.getId())
                        .createdBy(userId)
                        .line(depositAccount.id(), apply, BigDecimal.ZERO, "Offset UM Pelanggan: " + paymentNumber, partyId)
                        .line(arAccount.id(), BigDecimal.ZERO, apply, "Offset piutang: " + inv.getInvoiceNumber(), partyId)
                        .post();

                BigDecimal newOut = invOut.subtract(apply);
                inv.setOutstanding(Money.round2(newOut));
                inv.setStatus(newOut.compareTo(CENT) <= 0 ? InvoiceStatus.Paid : InvoiceStatus.PartiallyPaid);

                CustomerDepositApplication app = new CustomerDepositApplication();
                app.setDepositPaymentId(deposit.getId());
                app.setSalesInvoiceId(inv.getId());
                app.setAppliedAmount(Money.round2(apply));
                app.setJournalEntryId(je.getId());
                app.setCreatedBy(userId);
                em.persist(app);
                createdApplications.add(app.getId());

                partyBalances.incrementParty(partyId, BALANCE_COLUMN, apply.negate());
                remainingOffset = remainingOffset.subtract(apply);
            }

            if (remainingOffset.compareTo(CENT) > 0) {
                throw ErpException.business("Piutang customer tidak cukup untuk offset. Sisa offset yang tidak teralokasi: Rp "
                        + fixed2(remainingOffset) + ".");
            }
            em.flush();
            accountBalances.recalcPartyOutstanding(partyId);
        }

        // Refund kas ke pelanggan: DR Uang Muka Pelanggan / CR Kas-Bank.
        if (cashReq.signum() > 0) {
            Account cashAccount = em.find(Account.class, body.cashAccountId());
            if (cashAccount == null) {
                throw ErpException.business("Akun kas/bank tidak ditemukan.");
            }
            if (!systemAccounts.isCashAccount(cashAccount.getAccountNumber())) {
                throw ErpException.business("Akun yang dipilih bukan kas/bank.");
            }
            Party party = em.find(Party.class, partyId);
            String description = "Refund UM Pelanggan: " + paymentNumber;
            JournalEntry je = gl.journal()
                    .date(parsedDate)
                    .narration("Refund UM Pelanggan: " + paymentNumber + " - " + party.getName())
                    .fiscalYear(fiscalYear.getId())
                    .createdBy(userId)
                    .line(depositAccount.id(), cashReq, BigDecimal.ZERO, description, partyId)
                    .line(cashAccount.getId(), BigDecimal.ZERO, cashReq, description)
                    .post();
            cashJournalId = je.getId();

            partyBalances.incrementParty(partyId, BALANCE_COLUMN, cashReq.negate());
            DepositSupport.incrementRefunded(em, deposit.getId(), cashReq);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Uang muka diselesaikan.");
        out.put("offsetApplied", offsetReq);
        out.put("cashRefunded", cashReq);
        out.put("applications", createdApplications);
        out.put("cashJournalId", cashJournalId);
        return out;
    }

    private static Map<String, Object> applicationJson(CustomerDepositApplication a) {
        return DepositSupport.applicationJson(a.getId(), a.getDepositPaymentId(), "salesInvoiceId", a.getSalesInvoiceId(),
                a.getAppliedAmount(), a.getJournalEntryId(), a.getIsCancelled(), a.getAppliedAt(), a.getCancelledAt(),
                a.getCreatedBy());
    }
}
