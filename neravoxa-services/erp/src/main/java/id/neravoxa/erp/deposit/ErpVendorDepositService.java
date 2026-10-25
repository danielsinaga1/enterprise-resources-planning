package id.neravoxa.erp.deposit;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpAccountBalanceService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpPartyBalanceService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService.MappedAccount;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.Party;
import id.neravoxa.erp.entity.Payment;
import id.neravoxa.erp.entity.PaymentType;
import id.neravoxa.erp.entity.PurchaseInvoice;
import id.neravoxa.erp.entity.VendorDepositApplication;
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

import static id.neravoxa.erp.deposit.DepositSupport.CENT;
import static id.neravoxa.erp.deposit.DepositSupport.fixed2;

/**
 * Uang muka supplier (akun 1.3 Uang Muka Pembelian, saldo mitra {@code deposit_balance}) — port alur
 * tulis {@code routes/vendorDeposits.ts}: batal uang muka, aplikasi ke invoice pembelian (DR Hutang /
 * CR 1.3), batal aplikasi, serta penyelesaian sisa lewat offset hutang FIFO dan/atau refund kas dari
 * vendor (DR Kas-Bank / CR 1.3).
 *
 * <p>Pembayaran uang muka (DR 1.3 / CR Kas) dibuat di rute pembayaran (paymentType VendorDeposit).
 * Saldo mitra lewat {@link ErpPartyBalanceService#incrementParty}; outstanding mitra dihitung ulang
 * {@link ErpAccountBalanceService#recalcPartyOutstanding}, sama seperti sumber.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpVendorDepositService {

    private static final String BALANCE_COLUMN = "deposit_balance";

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
     * jurnal sendiri; pembalikan parsial tidak didukung). Jurnal pembayaran dibatalkan (saldo dibalik,
     * ledger di-soft-cancel tanpa JV [BATAL]); impor lama tanpa jurnal dibalik langsung
     * CR Uang Muka Pembelian / DR Ekuitas Saldo Awal.
     */
    @ErpTx
    public Map<String, Object> cancel(String id) {
        Payment deposit = em.find(Payment.class, id);
        if (deposit == null) {
            throw ErpException.business("Deposit tidak ditemukan.");
        }
        if (deposit.getPaymentType() != PaymentType.VendorDeposit) {
            throw ErpException.business("Bukan tipe uang muka supplier.");
        }
        if (deposit.getStatus() == DocumentStatus.Cancelled) {
            throw ErpException.business("Deposit sudah dibatalkan.");
        }
        if (queries.activeApplicationCount(DepositKind.VENDOR, id) > 0) {
            throw ErpException.business("Deposit sudah digunakan untuk invoice. Batalkan penggunaan terlebih dahulu.");
        }
        DepositSupport.assertNotRefunded(deposit.getRefundedAmount());

        BigDecimal depositAmount = Money.nz(deposit.getAmount());
        JournalEntry journal = deposit.getJournalEntryId() == null ? null
                : em.find(JournalEntry.class, deposit.getJournalEntryId());
        if (journal != null) {
            gl.cancelJournal(journal.getId());
        } else {
            // Tanpa jurnal tertaut (impor lama) — balik saldo akun langsung.
            String vendorDepositAccount = systemAccounts.getAccountId(SystemAccountRole.VENDOR_DEPOSIT);
            String openingEquityAccount = systemAccounts.getAccountId(SystemAccountRole.OPENING_EQUITY);
            accountBalances.updateAccountBalance(vendorDepositAccount, BigDecimal.ZERO, depositAmount);
            accountBalances.updateAccountBalance(openingEquityAccount, depositAmount, BigDecimal.ZERO);
            DepositSupport.cancelLegacyLedgerRows(em, deposit.getPaymentNumber());
        }

        partyBalances.incrementParty(deposit.getPartyId(), BALANCE_COLUMN, depositAmount.negate());

        deposit.setStatus(DocumentStatus.Cancelled);
        deposit.setCancelledAt(Instant.now());
        return Map.of("message", "Uang muka supplier dibatalkan.");
    }

    // ───────────────────────────── aplikasi ke invoice ─────────────────────────────

    /** POST /apply — DR Hutang Usaha / CR Uang Muka Pembelian, kurangi outstanding invoice. Status 201. */
    @ErpTx
    public Map<String, Object> apply(DepositRequests.ApplyVendor body, String userId) {
        DepositSupport.lockRow(em, "payments", body.depositPaymentId());
        DepositSupport.lockRow(em, "purchase_invoices", body.purchaseInvoiceId());

        Payment deposit = em.find(Payment.class, body.depositPaymentId());
        if (deposit == null) {
            throw ErpException.business("Deposit tidak ditemukan.");
        }
        if (deposit.getPaymentType() != PaymentType.VendorDeposit) {
            throw ErpException.business("Pembayaran bukan tipe Uang Muka Supplier.");
        }
        if (deposit.getStatus() != DocumentStatus.Submitted) {
            throw ErpException.business("Deposit sudah dibatalkan.");
        }

        PurchaseInvoice invoice = em.find(PurchaseInvoice.class, body.purchaseInvoiceId());
        if (invoice == null) {
            throw ErpException.business("Invoice pembelian tidak ditemukan.");
        }
        if (invoice.getStatus() == InvoiceStatus.Cancelled) {
            throw ErpException.business("Invoice sudah dibatalkan.");
        }
        if (invoice.getStatus() == InvoiceStatus.Paid) {
            throw ErpException.business("Invoice sudah lunas.");
        }
        if (!deposit.getPartyId().equals(invoice.getPartyId())) {
            throw ErpException.business("Deposit dan invoice harus dari supplier yang sama.");
        }

        BigDecimal totalApplied = queries.activeAppliedTotal(DepositKind.VENDOR, deposit.getId());
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
        MappedAccount apAccount = systemAccounts.getAccount(SystemAccountRole.AP);
        MappedAccount depositAccount = systemAccounts.getAccount(SystemAccountRole.VENDOR_DEPOSIT);

        String description = "Aplikasi UM: " + deposit.getPaymentNumber();
        JournalEntry journal = gl.journal()
                .date(parsedDate)
                .narration("Aplikasi Uang Muka: " + deposit.getPaymentNumber() + " → " + invoice.getInvoiceNumber())
                .fiscalYear(fiscalYear.getId())
                .createdBy(userId)
                .line(apAccount.id(), applyAmount, BigDecimal.ZERO, description, invoice.getPartyId())
                .line(depositAccount.id(), BigDecimal.ZERO, applyAmount, description, invoice.getPartyId())
                .post();

        BigDecimal newOutstanding = invoiceOutstanding.subtract(applyAmount);
        invoice.setOutstanding(Money.round2(newOutstanding));
        invoice.setStatus(newOutstanding.compareTo(CENT) <= 0 ? InvoiceStatus.Paid : InvoiceStatus.PartiallyPaid);
        em.flush();

        partyBalances.incrementParty(invoice.getPartyId(), BALANCE_COLUMN, applyAmount.negate());
        accountBalances.recalcPartyOutstanding(invoice.getPartyId());

        VendorDepositApplication application = new VendorDepositApplication();
        application.setDepositPaymentId(deposit.getId());
        application.setPurchaseInvoiceId(invoice.getId());
        application.setAppliedAmount(Money.round2(applyAmount));
        application.setJournalEntryId(journal.getId());
        application.setCreatedBy(userId);
        em.persist(application);
        em.flush();
        return applicationJson(application);
    }

    /** POST /apply/:id/cancel (capability doc.cancel dicek controller) — kebalikan {@link #apply}. */
    @ErpTx
    public Map<String, Object> cancelApplication(String id) {
        VendorDepositApplication application = em.find(VendorDepositApplication.class, id);
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

        // Kunci faktur sebelum menulis outstanding absolut.
        DepositSupport.lockRow(em, "purchase_invoices", application.getPurchaseInvoiceId());
        PurchaseInvoice invoice = em.find(PurchaseInvoice.class, application.getPurchaseInvoiceId());
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
     * POST /:id/refund — selesaikan sisa uang muka: offset ke hutang (FIFO, invoice pembelian terlama
     * dulu) dan/atau vendor mengembalikan kas DP ke rekening kita (DR Kas-Bank / CR Uang Muka Pembelian).
     */
    @ErpTx
    public Map<String, Object> refund(String depositId, DepositRequests.Refund body, String userId) {
        DepositSupport.lockRow(em, "payments", depositId);

        Payment deposit = em.find(Payment.class, depositId);
        if (deposit == null) {
            throw ErpException.business("Uang muka tidak ditemukan.");
        }
        if (deposit.getPaymentType() != PaymentType.VendorDeposit) {
            throw ErpException.business("Bukan tipe uang muka supplier.");
        }
        if (deposit.getStatus() != DocumentStatus.Submitted) {
            throw ErpException.business("Uang muka sudah dibatalkan.");
        }

        BigDecimal totalApplied = queries.activeAppliedTotal(DepositKind.VENDOR, deposit.getId());
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

        MappedAccount depositAccount = systemAccounts.getAccount(SystemAccountRole.VENDOR_DEPOSIT);
        MappedAccount apAccount = systemAccounts.getAccount(SystemAccountRole.AP);
        String partyId = deposit.getPartyId();
        String paymentNumber = deposit.getPaymentNumber();

        List<String> createdApplications = new ArrayList<>();
        String cashJournalId = null;

        // Offset ke hutang (FIFO, terlama dulu).
        if (offsetReq.signum() > 0) {
            DepositSupport.lockOpenInvoices(em, "purchase_invoices", partyId);
            List<PurchaseInvoice> invoices = em.createQuery(
                            "select i from PurchaseInvoice i where i.partyId = :partyId and i.status in :statuses "
                                    + "order by i.date asc, i.invoiceNumber asc", PurchaseInvoice.class)
                    .setParameter("partyId", partyId)
                    .setParameter("statuses", List.of(InvoiceStatus.Submitted, InvoiceStatus.PartiallyPaid, InvoiceStatus.Overdue))
                    .getResultList();

            BigDecimal remainingOffset = offsetReq;
            for (PurchaseInvoice inv : invoices) {
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
                        .narration("Offset UM Vendor: " + paymentNumber + " → " + inv.getInvoiceNumber())
                        .fiscalYear(fiscalYear.getId())
                        .createdBy(userId)
                        .line(apAccount.id(), apply, BigDecimal.ZERO, "Offset hutang: " + inv.getInvoiceNumber(), partyId)
                        .line(depositAccount.id(), BigDecimal.ZERO, apply, "Offset UM Vendor: " + paymentNumber, partyId)
                        .post();

                BigDecimal newOut = invOut.subtract(apply);
                inv.setOutstanding(Money.round2(newOut));
                inv.setStatus(newOut.compareTo(CENT) <= 0 ? InvoiceStatus.Paid : InvoiceStatus.PartiallyPaid);

                VendorDepositApplication app = new VendorDepositApplication();
                app.setDepositPaymentId(deposit.getId());
                app.setPurchaseInvoiceId(inv.getId());
                app.setAppliedAmount(Money.round2(apply));
                app.setJournalEntryId(je.getId());
                app.setCreatedBy(userId);
                em.persist(app);
                createdApplications.add(app.getId());

                partyBalances.incrementParty(partyId, BALANCE_COLUMN, apply.negate());
                remainingOffset = remainingOffset.subtract(apply);
            }

            if (remainingOffset.compareTo(CENT) > 0) {
                throw ErpException.business("Hutang supplier tidak cukup untuk offset. Sisa offset yang tidak teralokasi: Rp "
                        + fixed2(remainingOffset) + ".");
            }
            em.flush();
            accountBalances.recalcPartyOutstanding(partyId);
        }

        // Vendor mengembalikan kas DP ke rekening kita: DR Bank/Kas / CR Uang Muka Pembelian.
        if (cashReq.signum() > 0) {
            Account cashAccount = em.find(Account.class, body.cashAccountId());
            if (cashAccount == null) {
                throw ErpException.business("Akun kas/bank tidak ditemukan.");
            }
            if (!systemAccounts.isCashAccount(cashAccount.getAccountNumber())) {
                throw ErpException.business("Akun yang dipilih bukan kas/bank.");
            }
            Party party = em.find(Party.class, partyId);
            String description = "Refund UM Vendor: " + paymentNumber;
            JournalEntry je = gl.journal()
                    .date(parsedDate)
                    .narration("Refund UM Vendor: " + paymentNumber + " - " + party.getName())
                    .fiscalYear(fiscalYear.getId())
                    .createdBy(userId)
                    .line(cashAccount.getId(), cashReq, BigDecimal.ZERO, description)
                    .line(depositAccount.id(), BigDecimal.ZERO, cashReq, description, partyId)
                    .post();
            cashJournalId = je.getId();

            partyBalances.incrementParty(partyId, BALANCE_COLUMN, cashReq.negate());
            DepositSupport.incrementRefunded(em, deposit.getId(), cashReq);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Uang muka supplier diselesaikan.");
        out.put("offsetApplied", offsetReq);
        out.put("cashRefunded", cashReq);
        out.put("applications", createdApplications);
        out.put("cashJournalId", cashJournalId);
        return out;
    }

    private static Map<String, Object> applicationJson(VendorDepositApplication a) {
        return DepositSupport.applicationJson(a.getId(), a.getDepositPaymentId(), "purchaseInvoiceId",
                a.getPurchaseInvoiceId(), a.getAppliedAmount(), a.getJournalEntryId(), a.getIsCancelled(),
                a.getAppliedAt(), a.getCancelledAt(), a.getCreatedBy());
    }
}
