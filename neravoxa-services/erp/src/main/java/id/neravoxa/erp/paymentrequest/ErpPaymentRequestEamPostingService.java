package id.neravoxa.erp.paymentrequest;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpAccountBalanceService;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.GaAsset;
import id.neravoxa.erp.entity.GaMaterialRequest;
import id.neravoxa.erp.entity.GaMaterialRequestItem;
import id.neravoxa.erp.entity.GaMaterialRequestStatus;
import id.neravoxa.erp.entity.GaVehicleServiceLog;
import id.neravoxa.erp.entity.GaVehicleTax;
import id.neravoxa.erp.entity.GaVehicleTaxStatus;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.Party;
import id.neravoxa.erp.entity.PurchaseInvoice;
import id.neravoxa.erp.entity.PurchaseInvoiceItem;
import id.neravoxa.erp.paymentrequest.PrRequests.EamPay;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Posting tagihan GA/EAM (pajak kendaraan, service log EAM, MR sparepart) — port {@code services/gaFinance.ts},
 * satu-satunya sumber kebenaran GL untuk antrian Tagihan EAM. Dua jalur:
 * <ul>
 *   <li>Jurnal (dibayar sekarang): DR beban / CR kas-bank</li>
 *   <li>Invoice pembelian (ditagih): DR beban / CR 2.1.1 Hutang Usaha, dilunasi lewat Pembayaran</li>
 * </ul>
 * Setiap dokumen GA paling banyak satu jurnal; batalkan dulu sebelum posting ulang.
 */
@Service
@RequiredArgsConstructor
public class ErpPaymentRequestEamPostingService {

    /** Beban pajak & perizinan kendaraan (6.17 = beban pajak penghasilan, bukan pajak kendaraan). */
    static final String ACC_VEHICLE_TAX = "6.41";
    /** Beban Pemeliharaan Kendaraan. */
    static final String ACC_VEHICLE_MAINTENANCE = "6.7";
    /** Hutang Usaha. */
    static final String ACC_AP = "2.1.1";
    static final String ACC_CASH_FALLBACK = "1.1.1";

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── helper akun & jurnal ─────────────────────────────

    private Account accountByNumber(String accountNumber) {
        List<Account> rows = em.createQuery("select a from Account a where a.accountNumber = :n and a.isGroup = false",
                        Account.class)
                .setParameter("n", accountNumber).setMaxResults(1).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.business("Akun " + accountNumber + " tidak ditemukan di COA. Pilih akun secara manual.");
        }
        return rows.get(0);
    }

    /** Versi gaFinance: cukup akun detail (bukan grup) — status aktif tidak diperiksa di sumber. */
    private Account assertDetailAccount(String accountId, String label) {
        Account acc = em.find(Account.class, accountId);
        if (acc == null || Boolean.TRUE.equals(acc.getIsGroup())) {
            throw ErpException.business(label + " tidak valid (harus akun detail, bukan grup).");
        }
        return acc;
    }

    private Account resolveCashAccount(String creditAccountId) {
        if (creditAccountId != null) {
            return assertDetailAccount(creditAccountId, "Akun sumber dana");
        }
        List<String> mapped = em.createQuery("select m.accountId from SystemAccountMapping m where m.role = 'CASH' "
                        + "order by m.sortOrder asc", String.class)
                .setMaxResults(1).getResultList();
        if (!mapped.isEmpty()) {
            return assertDetailAccount(mapped.get(0), "Akun sumber dana");
        }
        return accountByNumber(ACC_CASH_FALLBACK);
    }

    /** {@code new Date(s)} JavaScript; null bila tidak valid. */
    private static Instant jsDate(String s) {
        return ErpDates.tryInstant(s);
    }

    private static Instant requiredDate(String s) {
        Instant d = jsDate(s);
        if (d == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        return d;
    }

    /** JE 2 baris + ledger + saldo (jalur JOURNAL). */
    private JournalEntry createSimpleJournal(Instant date, String narration, String debitAccountId, String creditAccountId,
                                             BigDecimal amount, String partyId, String userId) {
        if (debitAccountId.equals(creditAccountId)) {
            throw ErpException.business("Akun beban dan akun sumber dana tidak boleh sama.");
        }
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
        JournalEntry je = gl.journal()
                .date(date).narration(narration).fiscalYear(fy.getId()).createdBy(userId)
                .line(debitAccountId, amount, BigDecimal.ZERO, narration)
                .line(creditAccountId, BigDecimal.ZERO, amount, narration, partyId)
                .post();
        if (partyId != null) {
            balances.recalcPartyOutstanding(partyId);
        }
        return je;
    }

    private record ExpenseInvoice(PurchaseInvoice invoice, JournalEntry journal) {
    }

    /**
     * Invoice pembelian beban non-persediaan + JE. Sengaja BUKAN submit invoice pembelian biasa (yang memaksa
     * DR Persediaan): tagihan GA adalah beban langsung, bukan stok gudang.
     */
    private ExpenseInvoice createExpensePurchaseInvoice(Instant date, Instant dueDate, String partyId,
                                                        String expenseAccountId, BigDecimal amount, String itemName,
                                                        String itemType, String narration, String notes, String userId) {
        Party party = em.find(Party.class, partyId);
        if (party == null) {
            throw ErpException.business("Data supplier tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(party.getIsActive())) {
            throw ErpException.business("Supplier sudah tidak aktif.");
        }
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
        Account ap = accountByNumber(ACC_AP);
        String invoiceNumber = numbers.generate("PI", date, fy.getId());
        BigDecimal total = Money.round2(amount);
        Instant now = Instant.now();

        PurchaseInvoice inv = new PurchaseInvoice();
        inv.setInvoiceNumber(invoiceNumber);
        inv.setDate(date);
        inv.setDueDate(dueDate);
        inv.setPartyId(partyId);
        inv.setGrandTotal(total);
        inv.setOutstanding(total);
        inv.setTaxPct(BigDecimal.ZERO);
        inv.setPotongan(BigDecimal.ZERO);
        inv.setBiayaLain(BigDecimal.ZERO);
        inv.setStatus(InvoiceStatus.Submitted);
        inv.setNotes(notes == null || notes.isEmpty() ? null : notes);
        inv.setFiscalYearId(fy.getId());
        inv.setCreatedBy(userId);
        inv.setSubmittedAt(now);
        em.persist(inv);

        PurchaseInvoiceItem item = new PurchaseInvoiceItem();
        item.setPurchaseInvoiceId(inv.getId());
        item.setItemName(itemName);
        item.setItemType(itemType);
        item.setQuantity(BigDecimal.ONE);
        item.setUnit("ls");
        item.setRate(total);
        item.setDiscount(BigDecimal.ZERO);
        item.setAmount(total);
        item.setTaxPct(BigDecimal.ZERO);
        item.setPphPct(BigDecimal.ZERO);
        item.setPotonganItem(BigDecimal.ZERO);
        item.setAccountId(expenseAccountId);
        item.setDescription(narration);
        em.persist(item);

        JournalEntry je = gl.journal()
                .number("JV-" + invoiceNumber)
                .date(date).narration(narration + " - " + party.getName()).fiscalYear(fy.getId()).createdBy(userId)
                .submittedAt(now)
                .line(expenseAccountId, total, BigDecimal.ZERO, narration + ": " + invoiceNumber)
                .line(ap.getId(), BigDecimal.ZERO, total, "Hutang: " + invoiceNumber, partyId)
                .post();
        balances.recalcPartyOutstanding(partyId);
        return new ExpenseInvoice(inv, je);
    }

    /** Invoice tagihan dibatalkan dulu (hanya bila belum ada pembayaran) sebelum jurnalnya dibatalkan. */
    private void cancelLinkedInvoice(String purchaseInvoiceId) {
        if (purchaseInvoiceId == null) {
            return;
        }
        PurchaseInvoice inv = em.find(PurchaseInvoice.class, purchaseInvoiceId);
        if (inv != null && inv.getStatus() != InvoiceStatus.Cancelled) {
            if (Money.nz(inv.getOutstanding()).compareTo(Money.nz(inv.getGrandTotal())) != 0) {
                throw ErpException.business("Invoice " + inv.getInvoiceNumber()
                        + " sudah dibayar sebagian/lunas. Batalkan pembayarannya dulu di modul Pembayaran.");
            }
            inv.setStatus(InvoiceStatus.Cancelled);
            inv.setCancelledAt(Instant.now());
            inv.setOutstanding(BigDecimal.ZERO);
            em.flush();
        }
    }

    private static String plate(GaAsset asset) {
        if (asset == null) {
            return "-";
        }
        return asset.getName() != null ? asset.getName() : asset.getCode() != null ? asset.getCode() : "-";
    }

    // ───────────────────────────── pajak / STNK / KIR ─────────────────────────────

    @ErpTx
    public GaVehicleTax payVehicleTax(String id, EamPay args, String userId) {
        GaVehicleTax tax = id == null ? null : em.find(GaVehicleTax.class, id);
        if (tax == null) {
            throw ErpException.business("Data pajak kendaraan tidak ditemukan.");
        }
        if (tax.getJournalEntryId() != null) {
            throw ErpException.business("Pajak ini sudah dibayar/terjurnal. Batalkan dulu sebelum posting ulang.");
        }
        BigDecimal amount = args.amount() != null ? args.amount() : Money.nz(tax.getAmount());
        if (!(amount.signum() > 0)) {
            throw ErpException.business("Nominal pajak harus lebih besar dari 0.");
        }
        Instant date = args.date() != null ? jsDate(args.date()) : tax.getDueDate();
        if (date == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        Account debit = args.accountId() != null ? assertDetailAccount(args.accountId(), "Akun beban")
                : accountByNumber(ACC_VEHICLE_TAX);
        GaAsset asset = tax.getAssetId() == null ? null : em.find(GaAsset.class, tax.getAssetId());
        String label = tax.getType().name().replace('_', ' ') + " " + plate(asset) + " TA " + tax.getPeriodYear();
        String narration = "Pembayaran " + label;

        String journalEntryId;
        String purchaseInvoiceId = null;
        String creditAccountId;
        if ("PURCHASE_INVOICE".equals(args.method())) {
            if (args.partyId() == null) {
                throw ErpException.business("Supplier wajib dipilih untuk jalur Invoice Pembelian.");
            }
            ExpenseInvoice out = createExpensePurchaseInvoice(date, args.dueDate() != null ? ErpDates.toInstant(args.dueDate()) : null,
                    args.partyId(), debit.getId(), amount, label, "service", narration,
                    args.notes() != null ? args.notes() : tax.getNotes(), userId);
            journalEntryId = out.journal().getId();
            purchaseInvoiceId = out.invoice().getId();
            creditAccountId = accountByNumber(ACC_AP).getId();
        } else {
            Account cash = resolveCashAccount(args.creditAccountId());
            JournalEntry je = createSimpleJournal(date, narration, debit.getId(), cash.getId(), amount, null, userId);
            journalEntryId = je.getId();
            creditAccountId = cash.getId();
        }
        tax.setStatus(GaVehicleTaxStatus.PAID);
        tax.setPaidAt(date);
        tax.setPaidAmount(Money.round2(amount));
        tax.setPaidById(userId);
        tax.setJournalEntryId(journalEntryId);
        tax.setPurchaseInvoiceId(purchaseInvoiceId);
        tax.setAccountId(debit.getId());
        tax.setCreditAccountId(creditAccountId);
        em.flush();
        return tax;
    }

    @ErpTx
    public GaVehicleTax cancelVehicleTaxPayment(String id) {
        GaVehicleTax tax = id == null ? null : em.find(GaVehicleTax.class, id);
        if (tax == null) {
            throw ErpException.business("Data pajak kendaraan tidak ditemukan.");
        }
        if (tax.getJournalEntryId() == null) {
            throw ErpException.business("Pajak ini belum memiliki jurnal.");
        }
        cancelLinkedInvoice(tax.getPurchaseInvoiceId());
        gl.cancelJournalAndRecalcParties(tax.getJournalEntryId());
        tax.setStatus(GaVehicleTaxStatus.IN_PROCESS);
        tax.setPaidAt(null);
        tax.setPaidAmount(null);
        tax.setPaidById(null);
        tax.setJournalEntryId(null);
        tax.setPurchaseInvoiceId(null);
        em.flush();
        return tax;
    }

    // ───────────────────────────── service log (jalur invoice; jalur jurnal = kernel) ─────────────────────────────

    @ErpTx
    public GaVehicleServiceLog payServiceLogViaInvoice(String id, EamPay args, String userId) {
        GaVehicleServiceLog log = id == null ? null : em.find(GaVehicleServiceLog.class, id);
        if (log == null) {
            throw ErpException.business("Service log tidak ditemukan.");
        }
        if (log.getJournalEntryId() != null) {
            throw ErpException.business("Service log ini sudah terjurnal. Batalkan dulu sebelum posting ulang.");
        }
        if (!"EAM".equals(log.getSource())) {
            throw ErpException.business("Service log bersumber \"" + log.getSource()
                    + "\" (data historis) tidak dapat diposting karena nilainya sudah tercatat di GL lewat input manual.");
        }
        if (args.partyId() == null) {
            throw ErpException.business("Supplier/bengkel wajib dipilih untuk jalur Invoice Pembelian.");
        }
        BigDecimal amount = args.amount() != null ? args.amount() : Money.nz(log.getBiaya());
        if (!(amount.signum() > 0)) {
            throw ErpException.business("Biaya service harus lebih besar dari 0.");
        }
        Instant date = args.date() != null ? requiredDate(args.date()) : log.getTanggal();
        Account debit = args.accountId() != null ? assertDetailAccount(args.accountId(), "Akun beban")
                : accountByNumber(ACC_VEHICLE_MAINTENANCE);
        GaAsset asset = log.getAssetId() == null ? null : em.find(GaAsset.class, log.getAssetId());
        String plat = plate(asset);
        String narration = "Biaya service kendaraan " + plat + ": " + log.getDeskripsi();
        ExpenseInvoice out = createExpensePurchaseInvoice(date, args.dueDate() != null ? ErpDates.toInstant(args.dueDate()) : null,
                args.partyId(), debit.getId(), amount, PrRules.slice("Service " + plat + ": " + log.getDeskripsi(), 200),
                "service", narration, args.notes(), userId);
        Account ap = accountByNumber(ACC_AP);
        log.setJournalEntryId(out.journal().getId());
        log.setPurchaseInvoiceId(out.invoice().getId());
        log.setAccountId(debit.getId());
        log.setCreditAccountId(ap.getId());
        em.flush();
        return log;
    }

    /** Membatalkan posting service log (jalur jurnal maupun invoice). */
    @ErpTx
    public GaVehicleServiceLog cancelServiceLog(String id) {
        GaVehicleServiceLog log = id == null ? null : em.find(GaVehicleServiceLog.class, id);
        if (log == null) {
            throw ErpException.business("Service log tidak ditemukan.");
        }
        if (log.getJournalEntryId() == null) {
            throw ErpException.business("Service log ini belum memiliki jurnal.");
        }
        cancelLinkedInvoice(log.getPurchaseInvoiceId());
        gl.cancelJournalAndRecalcParties(log.getJournalEntryId());
        log.setJournalEntryId(null);
        log.setPurchaseInvoiceId(null);
        em.flush();
        return log;
    }

    // ───────────────────────────── Material Request (sparepart) ─────────────────────────────

    private List<GaMaterialRequestItem> mrItems(String id) {
        return em.createQuery("select i from GaMaterialRequestItem i where i.materialRequestId = :id "
                        + "order by i.createdAt asc, i.id asc", GaMaterialRequestItem.class)
                .setParameter("id", id).getResultList();
    }

    @ErpTx
    public GaMaterialRequest payMaterialRequest(String id, EamPay args, String userId) {
        GaMaterialRequest mr = id == null ? null : em.find(GaMaterialRequest.class, id);
        if (mr == null) {
            throw ErpException.business("Material Request tidak ditemukan.");
        }
        if (mr.getJournalEntryId() != null) {
            throw ErpException.business("MR ini sudah terjurnal. Batalkan dulu sebelum posting ulang.");
        }
        if (mr.getStatus() != GaMaterialRequestStatus.APPROVED && mr.getStatus() != GaMaterialRequestStatus.FULFILLED) {
            throw ErpException.business("MR berstatus " + mr.getStatus()
                    + " belum bisa dibayar. Hanya MR APPROVED atau FULFILLED yang masuk antrian keuangan.");
        }
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (GaMaterialRequestItem it : mrItems(id)) {
            itemsTotal = itemsTotal.add(Money.nz(it.getUnitPrice()).multiply(Money.nz(it.getQuantity())));
        }
        BigDecimal amount = args.amount() != null ? args.amount() : itemsTotal;
        if (!(amount.signum() > 0)) {
            throw ErpException.business("Total nilai MR harus lebih besar dari 0. Lengkapi harga satuan di item MR dulu.");
        }
        Instant date = args.date() != null ? requiredDate(args.date())
                : (mr.getApprovedAt() != null ? mr.getApprovedAt() : mr.getRequestedAt());
        Account debit = args.accountId() != null ? assertDetailAccount(args.accountId(), "Akun beban")
                : accountByNumber(ACC_VEHICLE_MAINTENANCE);
        GaAsset asset = mr.getAssetId() == null ? null : em.find(GaAsset.class, mr.getAssetId());
        String target = asset != null && asset.getName() != null ? asset.getName()
                : mr.getMachineTarget() != null ? mr.getMachineTarget() : "-";
        String narration = "Pengadaan sparepart " + mr.getCode() + ": " + target;

        String journalEntryId;
        String purchaseInvoiceId = null;
        String creditAccountId;
        if ("PURCHASE_INVOICE".equals(args.method())) {
            if (args.partyId() == null) {
                throw ErpException.business("Supplier wajib dipilih untuk jalur Invoice Pembelian.");
            }
            ExpenseInvoice out = createExpensePurchaseInvoice(date, args.dueDate() != null ? ErpDates.toInstant(args.dueDate()) : null,
                    args.partyId(), debit.getId(), amount, "Sparepart " + mr.getCode(), "product", narration,
                    args.notes() != null ? args.notes() : mr.getNotes(), userId);
            journalEntryId = out.journal().getId();
            purchaseInvoiceId = out.invoice().getId();
            creditAccountId = accountByNumber(ACC_AP).getId();
        } else {
            Account cash = resolveCashAccount(args.creditAccountId());
            JournalEntry je = createSimpleJournal(date, narration, debit.getId(), cash.getId(), amount, null, userId);
            journalEntryId = je.getId();
            creditAccountId = cash.getId();
        }
        mr.setStatus(GaMaterialRequestStatus.PAID);
        mr.setPaidAt(date);
        mr.setPaidAmount(Money.round2(amount));
        mr.setPaidById(userId);
        mr.setJournalEntryId(journalEntryId);
        mr.setPurchaseInvoiceId(purchaseInvoiceId);
        mr.setGlAccountId(debit.getId());
        mr.setCreditAccountId(creditAccountId);
        em.flush();
        return mr;
    }

    @ErpTx
    public GaMaterialRequest cancelMaterialRequestPayment(String id) {
        GaMaterialRequest mr = id == null ? null : em.find(GaMaterialRequest.class, id);
        if (mr == null) {
            throw ErpException.business("Material Request tidak ditemukan.");
        }
        if (mr.getJournalEntryId() == null) {
            throw ErpException.business("MR ini belum memiliki jurnal.");
        }
        cancelLinkedInvoice(mr.getPurchaseInvoiceId());
        gl.cancelJournalAndRecalcParties(mr.getJournalEntryId());
        // Kembali ke status sebelum bayar: FULFILLED bila semua item sudah diterima.
        List<GaMaterialRequestItem> items = mrItems(id);
        boolean allReceived = !items.isEmpty() && items.stream()
                .allMatch(it -> Money.nz(it.getReceivedQty()).compareTo(Money.nz(it.getQuantity())) >= 0);
        mr.setStatus(allReceived ? GaMaterialRequestStatus.FULFILLED : GaMaterialRequestStatus.APPROVED);
        mr.setPaidAt(null);
        mr.setPaidAmount(null);
        mr.setPaidById(null);
        mr.setJournalEntryId(null);
        mr.setPurchaseInvoiceId(null);
        em.flush();
        return mr;
    }
}
