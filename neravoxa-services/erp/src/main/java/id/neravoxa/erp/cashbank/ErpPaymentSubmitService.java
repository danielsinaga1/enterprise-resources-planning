package id.neravoxa.erp.cashbank;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.cashbank.CashBankRequests.CreatePayment;
import id.neravoxa.erp.cashbank.CashBankRequests.PaymentSplitInput;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpAccountBalanceService;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpPartyBalanceService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.Party;
import id.neravoxa.erp.entity.PartyType;
import id.neravoxa.erp.entity.Payment;
import id.neravoxa.erp.entity.PaymentAllocation;
import id.neravoxa.erp.entity.PaymentSplit;
import id.neravoxa.erp.entity.PaymentType;
import id.neravoxa.erp.entity.PurchaseInvoice;
import id.neravoxa.erp.entity.SalesInvoice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Simpan pembayaran — port {@code services/paymentSubmit.ts}. Jurnal per paymentType:
 * <ul>
 *   <li>VendorDeposit: DR Uang Muka Vendor / CR Kas (atau Ekuitas Saldo Awal bila saldo awal)</li>
 *   <li>CustomerDeposit: DR Kas (atau Ekuitas Saldo Awal) / CR Uang Muka Pelanggan</li>
 *   <li>VendorRefund: DR Bank / CR Hutang Usaha — opsional mengurangi alokasi pembayaran Pay asal</li>
 *   <li>CustomerRefund: DR Piutang Usaha / CR Bank</li>
 *   <li>Receive: DR Kas (multi-split) / CR Piutang Usaha — alokasi otomatis ke invoice penjualan terlama</li>
 *   <li>Pay: DR Hutang Usaha / CR Kas (multi-split) — alokasi otomatis ke invoice pembelian terlama</li>
 * </ul>
 * Receive/Pay mengembalikan sisa yang tidak teralokasi (kelebihan dicatat di log dan mengurangi saldo awal mitra).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpPaymentSubmitService {

    private static final List<InvoiceStatus> OPEN_INVOICE = List.of(
            InvoiceStatus.Submitted, InvoiceStatus.PartiallyPaid, InvoiceStatus.Overdue);

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpSystemAccountService systemAccounts;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpPartyBalanceService partyBalances;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * Hasil simpan: {@code payment} = baris pembayaran seperti saat dibuat (sama dengan
     * {@code tx.payment.create} di sumber, journalEntryId masih null), {@code party} = mitra sebelum
     * saldo berubah, dan sisa tak teralokasi.
     */
    public record Submitted(ObjectNode payment, ObjectNode party, BigDecimal unallocatedAmount) {

        /** Body respons {@code { ...payment, party, unallocatedAmount }}. */
        public ObjectNode toJson() {
            ObjectNode o = payment.deepCopy();
            o.set("party", party);
            o.set("unallocatedAmount", ErpJson.tree(unallocatedAmount));
            return o;
        }
    }

    @ErpTx
    public Submitted submit(CreatePayment body, String userId) {
        Instant parsedDate = ErpDates.tryInstant(body.date());
        if (parsedDate == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(parsedDate);

        // Deposit saldo awal tidak memakai akun kas — lawannya Ekuitas Saldo Awal.
        boolean isOpeningDeposit = Boolean.TRUE.equals(body.isOpeningBalance())
                && (body.paymentType() == PaymentType.VendorDeposit || body.paymentType() == PaymentType.CustomerDeposit);
        if (!isOpeningDeposit) {
            Account bankAccount = em.find(Account.class, body.accountId());
            if (bankAccount == null) {
                throw ErpException.business("Akun kas/bank tidak ditemukan.");
            }
            if (!systemAccounts.isCashAccount(bankAccount.getAccountNumber())) {
                throw ErpException.business("Akun yang dipilih bukan akun kas/bank.");
            }
        }

        Party party = em.find(Party.class, body.partyId());
        if (party == null) {
            throw ErpException.business("Data mitra tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(party.getIsActive())) {
            throw ErpException.business("Mitra sudah tidak aktif.");
        }
        ObjectNode partyJson = CashBankJson.scalars(party);

        String paymentNumber = numbers.generate("PAY", parsedDate, fiscalYear.getId());
        BigDecimal numAmount = body.amount();
        String resolvedAccountId = isOpeningDeposit
                ? systemAccounts.getAccount(SystemAccountRole.OPENING_EQUITY).id()
                : body.accountId();

        Payment payment = new Payment();
        payment.setPaymentNumber(paymentNumber);
        payment.setDate(parsedDate);
        payment.setPartyId(body.partyId());
        payment.setPaymentType(body.paymentType());
        payment.setAccountId(resolvedAccountId);
        payment.setAmount(Money.round2(numAmount));
        payment.setStatus(DocumentStatus.Submitted);
        payment.setReferenceNo(blankToNull(body.referenceNo()));
        payment.setNotes(blankToNull(body.notes()));
        // Tag SO hanya untuk CustomerDeposit (uang muka pesanan).
        payment.setSalesOrderId(body.paymentType() == PaymentType.CustomerDeposit ? body.salesOrderId() : null);
        payment.setFiscalYearId(fiscalYear.getId());
        payment.setCreatedBy(userId);
        payment.setSubmittedAt(Instant.now());
        em.persist(payment);
        em.flush();
        ObjectNode paymentJson = CashBankJson.scalars(payment);

        return switch (body.paymentType()) {
            case VendorDeposit -> vendorDeposit(body, payment, party, fiscalYear, userId, paymentJson, partyJson);
            case CustomerDeposit -> customerDeposit(body, payment, party, fiscalYear, userId, paymentJson, partyJson);
            case VendorRefund, CustomerRefund -> refund(body, payment, party, fiscalYear, userId, paymentJson, partyJson);
            case Receive, Pay -> receiveOrPay(body, payment, party, fiscalYear, userId, paymentJson, partyJson);
        };
    }

    private Submitted vendorDeposit(CreatePayment body, Payment payment, Party party, FiscalYear fy, String userId,
                                    ObjectNode paymentJson, ObjectNode partyJson) {
        if (party.getPartyType() != PartyType.Supplier && party.getPartyType() != PartyType.Both) {
            throw ErpException.business("Uang muka hanya dapat dibuat untuk Supplier.");
        }
        String depositAccountId = systemAccounts.getAccount(SystemAccountRole.VENDOR_DEPOSIT).id();
        boolean isOpening = Boolean.TRUE.equals(body.isOpeningBalance());
        String creditAccountId = isOpening
                ? systemAccounts.getAccount(SystemAccountRole.OPENING_EQUITY).id()
                : body.accountId();
        String prefix = isOpening ? "Saldo Awal Uang Muka Vendor" : "Uang Muka Vendor";
        String number = payment.getPaymentNumber();
        String jvNumber = ensureFreeJournalNumber(number);
        BigDecimal amount = body.amount();
        String desc = prefix + ": " + number;

        JournalEntry je = gl.journal()
                .number(jvNumber).date(payment.getDate()).fiscalYear(fy.getId()).createdBy(userId)
                .narration(prefix + ": " + number + " - " + party.getName())
                .line(depositAccountId, amount, Money.ZERO, desc, body.partyId())
                .line(creditAccountId, Money.ZERO, amount, desc)
                .post();

        partyBalances.incrementParty(body.partyId(), "deposit_balance", Money.round2(amount));
        payment.setJournalEntryId(je.getId());
        return new Submitted(paymentJson, partyJson, BigDecimal.ZERO);
    }

    private Submitted customerDeposit(CreatePayment body, Payment payment, Party party, FiscalYear fy, String userId,
                                      ObjectNode paymentJson, ObjectNode partyJson) {
        if (party.getPartyType() != PartyType.Customer && party.getPartyType() != PartyType.Both) {
            throw ErpException.business("Uang muka customer hanya dapat dibuat untuk Customer.");
        }
        String depositAccountId = systemAccounts.getAccount(SystemAccountRole.CUSTOMER_DEPOSIT).id();
        boolean isOpening = Boolean.TRUE.equals(body.isOpeningBalance());
        String debitAccountId = isOpening
                ? systemAccounts.getAccount(SystemAccountRole.OPENING_EQUITY).id()
                : body.accountId();
        String prefix = isOpening ? "Saldo Awal Uang Muka Pelanggan" : "Uang Muka Pelanggan";
        String number = payment.getPaymentNumber();
        String jvNumber = ensureFreeJournalNumber(number);
        BigDecimal amount = body.amount();
        String desc = prefix + ": " + number;

        JournalEntry je = gl.journal()
                .number(jvNumber).date(payment.getDate()).fiscalYear(fy.getId()).createdBy(userId)
                .narration(prefix + ": " + number + " - " + party.getName())
                .line(debitAccountId, amount, Money.ZERO, desc)
                .line(depositAccountId, Money.ZERO, amount, desc, body.partyId())
                .post();

        partyBalances.incrementParty(body.partyId(), "customer_deposit_balance", Money.round2(amount));
        payment.setJournalEntryId(je.getId());
        return new Submitted(paymentJson, partyJson, BigDecimal.ZERO);
    }

    private Submitted refund(CreatePayment body, Payment payment, Party party, FiscalYear fy, String userId,
                             ObjectNode paymentJson, ObjectNode partyJson) {
        boolean isVendorRefund = body.paymentType() == PaymentType.VendorRefund;
        if (isVendorRefund && party.getPartyType() != PartyType.Supplier && party.getPartyType() != PartyType.Both) {
            throw ErpException.business("Refund supplier hanya berlaku untuk Supplier.");
        }
        if (!isVendorRefund && party.getPartyType() != PartyType.Customer && party.getPartyType() != PartyType.Both) {
            throw ErpException.business("Refund customer hanya berlaku untuk Customer.");
        }
        String liabAssetAccountId = systemAccounts.getAccount(isVendorRefund ? SystemAccountRole.AP : SystemAccountRole.AR).id();
        String number = payment.getPaymentNumber();
        BigDecimal amount = body.amount();
        String who = isVendorRefund ? "Vendor" : "Pelanggan";

        // Vendor mengembalikan uang: DR Bank / CR Hutang. Kita mengembalikan ke pelanggan: DR Piutang / CR Bank.
        ErpGlPostingService.Line bankItem = new ErpGlPostingService.Line(body.accountId(),
                isVendorRefund ? amount : Money.ZERO, isVendorRefund ? Money.ZERO : amount,
                "Refund " + who + ": " + number, null, null);
        ErpGlPostingService.Line liabAssetItem = new ErpGlPostingService.Line(liabAssetAccountId,
                isVendorRefund ? Money.ZERO : amount, isVendorRefund ? amount : Money.ZERO,
                "Refund " + who + ": " + number + " (restore " + (isVendorRefund ? "hutang" : "piutang") + ")",
                body.partyId(), body.partyId());

        String jvNumber = ensureFreeJournalNumber(number);
        JournalEntry je = gl.journal()
                .number(jvNumber).date(payment.getDate()).fiscalYear(fy.getId()).createdBy(userId)
                .narration("Refund " + who + ": " + number + " - " + party.getName())
                .line(bankItem)
                .line(liabAssetItem)
                .post();

        // Tautkan ke pembayaran asal: kurangi alokasinya dan pulihkan outstanding invoice
        // (alokasi terbesar dulu). Gagal bila refund melebihi alokasi yang tersedia.
        if (body.refundSourcePaymentId() != null) {
            reverseSourceAllocations(body, isVendorRefund, amount);
        }

        payment.setJournalEntryId(je.getId());
        em.flush();
        balances.recalcPartyOutstanding(body.partyId());
        return new Submitted(paymentJson, partyJson, BigDecimal.ZERO);
    }

    private void reverseSourceAllocations(CreatePayment body, boolean isVendorRefund, BigDecimal amount) {
        PaymentType expectedType = isVendorRefund ? PaymentType.Pay : PaymentType.Receive;
        Payment source = em.find(Payment.class, body.refundSourcePaymentId());
        if (source == null) {
            throw ErpException.business("Pembayaran asal tidak ditemukan.");
        }
        if (!source.getPartyId().equals(body.partyId())) {
            throw ErpException.business("Pembayaran asal bukan dari pihak yang sama.");
        }
        if (source.getPaymentType() != expectedType) {
            throw ErpException.business("Pembayaran asal harus bertipe " + expectedType.name() + ".");
        }
        if (source.getStatus() != DocumentStatus.Submitted) {
            throw ErpException.business("Pembayaran asal sudah dibatalkan.");
        }

        String expectedInvoiceType = isVendorRefund ? "PurchaseInvoice" : "SalesInvoice";
        List<PaymentAllocation> sorted = new ArrayList<>(em.createQuery(
                        "select a from PaymentAllocation a where a.paymentId = :id", PaymentAllocation.class)
                .setParameter("id", source.getId()).getResultList()
                .stream().filter(a -> expectedInvoiceType.equals(a.getInvoiceType())).toList());
        sorted.sort(Comparator.comparing(PaymentAllocation::getAllocatedAmount).reversed());

        BigDecimal remaining = amount;
        for (PaymentAllocation alloc : sorted) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal allocAmount = Money.nz(alloc.getAllocatedAmount());
            BigDecimal reverseAmt = remaining.min(allocAmount);
            remaining = remaining.subtract(reverseAmt);
            BigDecimal newAlloc = allocAmount.subtract(reverseAmt);
            if (newAlloc.compareTo(Money.CENT) <= 0) {
                em.remove(alloc);
            } else {
                alloc.setAllocatedAmount(Money.round2(newAlloc));
            }
            restoreInvoiceOutstanding(isVendorRefund ? "PurchaseInvoice" : "SalesInvoice", alloc.getInvoiceId(), reverseAmt);
        }
        if (remaining.compareTo(Money.CENT) > 0) {
            throw ErpException.business("Nilai refund (" + CashBankFormat.jsNumber(amount)
                    + ") melebihi total alokasi pembayaran asal yang tersedia.");
        }
    }

    /**
     * Tambah kembali outstanding invoice; status Submitted bila kembali penuh, selain itu PartiallyPaid.
     * Dipakai refund dan pembatalan pembayaran; wajib dipanggil di dalam transaksi pemanggil.
     */
    public void restoreInvoiceOutstanding(String invoiceType, String invoiceId, BigDecimal amount) {
        if ("SalesInvoice".equals(invoiceType)) {
            SalesInvoice inv = em.find(SalesInvoice.class, invoiceId);
            if (inv != null) {
                BigDecimal newOutstanding = Money.nz(inv.getOutstanding()).add(amount);
                inv.setOutstanding(Money.round2(newOutstanding));
                inv.setStatus(newOutstanding.compareTo(Money.nz(inv.getGrandTotal())) >= 0
                        ? InvoiceStatus.Submitted : InvoiceStatus.PartiallyPaid);
            }
        } else if ("PurchaseInvoice".equals(invoiceType)) {
            PurchaseInvoice inv = em.find(PurchaseInvoice.class, invoiceId);
            if (inv != null) {
                BigDecimal newOutstanding = Money.nz(inv.getOutstanding()).add(amount);
                inv.setOutstanding(Money.round2(newOutstanding));
                inv.setStatus(newOutstanding.compareTo(Money.nz(inv.getGrandTotal())) >= 0
                        ? InvoiceStatus.Submitted : InvoiceStatus.PartiallyPaid);
            }
        }
    }

    private Submitted receiveOrPay(CreatePayment body, Payment payment, Party party, FiscalYear fy, String userId,
                                   ObjectNode paymentJson, ObjectNode partyJson) {
        boolean receive = body.paymentType() == PaymentType.Receive;
        String arAccountId = systemAccounts.getAccount(SystemAccountRole.AR).id();
        String apAccountId = systemAccounts.getAccount(SystemAccountRole.AP).id();
        BigDecimal amount = body.amount();
        String number = payment.getPaymentNumber();

        // Multi-akun (mis. invoice 280jt → BCA 270jt + Petty 7,5jt + Beban Komisi 2,5jt) vs satu akun.
        boolean useSplits = body.splits() != null && !body.splits().isEmpty();
        List<PaymentSplitInput> splits = useSplits
                ? body.splits()
                : List.of(new PaymentSplitInput(body.accountId(), amount, null));

        BigDecimal splitsSum = Money.sum(splits, PaymentSplitInput::amount);
        if (splitsSum.subtract(amount).abs().compareTo(Money.CENT) > 0) {
            throw ErpException.business("Total split (" + CashBankFormat.toFixed2(splitsSum)
                    + ") tidak sama dengan jumlah pembayaran (" + CashBankFormat.toFixed2(amount) + ").");
        }

        if (useSplits) {
            for (PaymentSplitInput sp : splits) {
                PaymentSplit row = new PaymentSplit();
                row.setPaymentId(payment.getId());
                row.setAccountId(sp.accountId());
                row.setAmount(Money.round2(sp.amount()));
                row.setNotes(sp.notes());
                em.persist(row);
            }
        }

        String jvNumber = ensureFreeJournalNumber(number);
        ErpGlPostingService.Builder journal = gl.journal()
                .number(jvNumber).date(payment.getDate()).fiscalYear(fy.getId()).createdBy(userId)
                .narration("Pembayaran " + body.paymentType().name() + ": " + number + " - " + party.getName());
        for (PaymentSplitInput sp : splits) {
            String splitParty = receive ? null : body.partyId();
            String desc = "Pembayaran: " + number + (sp.notes() != null && !sp.notes().isEmpty() ? " (" + sp.notes() + ")" : "");
            journal.line(new ErpGlPostingService.Line(sp.accountId(),
                    receive ? sp.amount() : Money.ZERO, receive ? Money.ZERO : sp.amount(), desc, splitParty, splitParty));
        }
        if (receive) {
            journal.line(arAccountId, Money.ZERO, amount, "Pembayaran: " + number, body.partyId());
        } else {
            journal.line(apAccountId, amount, Money.ZERO, "Pembayaran: " + number, body.partyId());
        }
        JournalEntry je = journal.post();
        payment.setJournalEntryId(je.getId());

        BigDecimal unallocated = autoAllocate(payment.getId(), body.partyId(), receive, amount);

        // Sisa tak teralokasi mengurangi saldo awal (piutang/hutang sebelum aplikasi yang tidak ada di tabel invoice).
        if (unallocated.compareTo(Money.CENT) > 0) {
            BigDecimal openingBal = Money.nz(em.createQuery(
                            "select p.openingOutstanding from Party p where p.id = :id", BigDecimal.class)
                    .setParameter("id", body.partyId()).getSingleResult());
            if (openingBal.signum() > 0) {
                BigDecimal reduction = unallocated.min(openingBal);
                em.createNativeQuery("UPDATE parties SET opening_outstanding = opening_outstanding - :v, updated_at = now() WHERE id = :id")
                        .setParameter("v", Money.round2(reduction))
                        .setParameter("id", body.partyId())
                        .executeUpdate();
                BigDecimal finalUnallocated = unallocated.subtract(reduction);
                if (finalUnallocated.compareTo(Money.CENT) > 0) {
                    log.warn("Overpayment: sisa pembayaran tidak teralokasi paymentId={} unallocatedAmount={}",
                            payment.getId(), finalUnallocated);
                }
            } else {
                log.warn("Overpayment: sisa pembayaran tidak teralokasi paymentId={} unallocatedAmount={}",
                        payment.getId(), unallocated);
            }
        }

        balances.recalcPartyOutstanding(body.partyId());
        return new Submitted(paymentJson, partyJson, unallocated);
    }

    /**
     * Alokasi otomatis Receive/Pay ke invoice terbuka (terlama dulu). Baris invoice dikunci
     * FOR UPDATE agar alokasi paralel tidak saling menimpa. Mengembalikan sisa tak teralokasi.
     */
    private BigDecimal autoAllocate(String paymentId, String partyId, boolean receive, BigDecimal amount) {
        BigDecimal remaining = amount;
        String table = receive ? "sales_invoices" : "purchase_invoices";
        em.createNativeQuery("SELECT id FROM " + table + " WHERE party_id = :p "
                        + "AND status IN ('Submitted', 'PartiallyPaid', 'Overdue') FOR UPDATE")
                .setParameter("p", partyId)
                .getResultList();
        if (receive) {
            List<SalesInvoice> invoices = em.createQuery(
                            "select i from SalesInvoice i where i.partyId = :p and i.status in :st order by i.date asc",
                            SalesInvoice.class)
                    .setParameter("p", partyId).setParameter("st", OPEN_INVOICE).getResultList();
            for (SalesInvoice inv : invoices) {
                if (remaining.signum() <= 0) {
                    break;
                }
                BigDecimal outstanding = Money.nz(inv.getOutstanding());
                BigDecimal allocate = remaining.min(outstanding);
                remaining = remaining.subtract(allocate);
                BigDecimal newOutstanding = outstanding.subtract(allocate);
                inv.setOutstanding(Money.round2(newOutstanding));
                inv.setStatus(newOutstanding.compareTo(Money.CENT) <= 0 ? InvoiceStatus.Paid : InvoiceStatus.PartiallyPaid);
                persistAllocation(paymentId, "SalesInvoice", inv.getId(), allocate);
            }
        } else {
            List<PurchaseInvoice> invoices = em.createQuery(
                            "select i from PurchaseInvoice i where i.partyId = :p and i.status in :st order by i.date asc",
                            PurchaseInvoice.class)
                    .setParameter("p", partyId).setParameter("st", OPEN_INVOICE).getResultList();
            for (PurchaseInvoice inv : invoices) {
                if (remaining.signum() <= 0) {
                    break;
                }
                BigDecimal outstanding = Money.nz(inv.getOutstanding());
                BigDecimal allocate = remaining.min(outstanding);
                remaining = remaining.subtract(allocate);
                BigDecimal newOutstanding = outstanding.subtract(allocate);
                inv.setOutstanding(Money.round2(newOutstanding));
                inv.setStatus(newOutstanding.compareTo(Money.CENT) <= 0 ? InvoiceStatus.Paid : InvoiceStatus.PartiallyPaid);
                persistAllocation(paymentId, "PurchaseInvoice", inv.getId(), allocate);
            }
        }
        em.flush();
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }

    private void persistAllocation(String paymentId, String invoiceType, String invoiceId, BigDecimal amount) {
        PaymentAllocation a = new PaymentAllocation();
        a.setPaymentId(paymentId);
        a.setInvoiceType(invoiceType);
        a.setInvoiceId(invoiceId);
        a.setAllocatedAmount(Money.round2(amount));
        em.persist(a);
    }

    /** Nomor jurnal {@code JV-<nomor dokumen>}; 400 bila sudah terpakai (sama dengan sumber). */
    private String ensureFreeJournalNumber(String documentNumber) {
        String jvNumber = "JV-" + documentNumber;
        Long taken = em.createQuery("select count(j) from JournalEntry j where j.entryNumber = :n", Long.class)
                .setParameter("n", jvNumber).getSingleResult();
        if (taken > 0) {
            throw ErpException.business("Nomor jurnal " + jvNumber + " sudah ada. Silakan coba lagi.");
        }
        return jvNumber;
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
