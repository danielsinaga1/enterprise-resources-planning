package id.neravoxa.erp.cashbook;

import id.neravoxa.erp.cashbook.ImportRows.CoaRow;
import id.neravoxa.erp.cashbook.ImportRows.ItemRow;
import id.neravoxa.erp.cashbook.ImportRows.JournalGroup;
import id.neravoxa.erp.cashbook.ImportRows.JournalLine;
import id.neravoxa.erp.cashbook.ImportRows.PartyRow;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpAccountBalanceService;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.AccountType;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.InventoryItem;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.JournalItem;
import id.neravoxa.erp.entity.Party;
import id.neravoxa.erp.entity.PartyType;
import id.neravoxa.erp.entity.Payment;
import id.neravoxa.erp.entity.PaymentType;
import id.neravoxa.erp.entity.RootType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Langkah tulis impor master/saldo awal — port bagian {@code prisma.$transaction} / tulisan langsung di
 * {@code routes/import/{coa,journals,parties,inventory}.ts}. Setiap method = satu transaksi, sehingga
 * seperti sumber kegagalan satu langkah (mis. jurnal saldo awal) tidak membatalkan langkah sebelumnya
 * (upsert master) dan baris lain tetap diproses.
 */
@Service
@RequiredArgsConstructor
public class ErpImportWriter {

    private static final Pattern YMD_LOOSE = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$");
    private static final Pattern MDY = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$");
    private static final Pattern YMD_SPACE_TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d.*$");

    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpDocumentNumberService numbers;
    private final ErpFiscalYearService fiscalYears;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private Account accountByNumber(String number) {
        List<Account> rows = em.createQuery("select a from Account a where a.accountNumber = :n", Account.class)
                .setParameter("n", number).setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static BigDecimal dec(double v) {
        return BigDecimal.valueOf(v);
    }

    /** Hapus permanen jurnal saldo awal lama + ledger-nya, saldo akun dibalik (re-import tidak menggandakan). */
    private void deleteJournalInTx(String journalId) {
        List<JournalItem> items = em.createQuery("select i from JournalItem i where i.journalEntryId = :id", JournalItem.class)
                .setParameter("id", journalId).getResultList();
        for (JournalItem item : items) {
            balances.updateAccountBalance(item.getAccountId(), item.getCredit(), item.getDebit());
        }
        em.createQuery("delete from AccountingLedgerEntry e where e.referenceId = :id").setParameter("id", journalId).executeUpdate();
        em.createQuery("delete from JournalItem i where i.journalEntryId = :id").setParameter("id", journalId).executeUpdate();
        em.createQuery("delete from JournalEntry j where j.id = :id").setParameter("id", journalId).executeUpdate();
    }

    // ───────────────────────────── COA ─────────────────────────────

    /** Upsert akun berdasarkan nomor; induk dicari dari {@code parentNumber}. Mengembalikan id akun. */
    @ErpTx
    public String upsertAccount(CoaRow row, String effectiveParentNumber) {
        String parentId = null;
        if (effectiveParentNumber != null && !effectiveParentNumber.isEmpty()) {
            Account parent = accountByNumber(effectiveParentNumber);
            if (parent != null) {
                parentId = parent.getId();
            }
        }
        AccountType accountType;
        try {
            accountType = AccountType.valueOf(row.accountType);
        } catch (IllegalArgumentException e) {
            // accountType tidak divalidasi di sumber; Prisma menolaknya saat upsert.
            throw ErpException.business("Invalid value for argument `accountType`. Expected AccountType.");
        }
        Account acc = accountByNumber(row.accountNumber);
        if (acc == null) {
            acc = new Account();
            acc.setAccountNumber(row.accountNumber);
            acc.setName(row.name);
            acc.setRootType(RootType.valueOf(row.rootType));
            acc.setAccountType(accountType);
            acc.setParentId(parentId);
            acc.setIsGroup(row.isGroup);
            em.persist(acc);
        } else {
            acc.setName(row.name);
            acc.setRootType(RootType.valueOf(row.rootType));
            acc.setAccountType(accountType);
            acc.setParentId(parentId);
            acc.setIsGroup(row.isGroup);
        }
        em.flush();
        return acc.getId();
    }

    /**
     * Jurnal saldo awal akun: saldo positif pada akun debit-normal (ASSET/EXPENSE) → DR akun / CR Ekuitas
     * Saldo Awal; pada akun kredit-normal → CR akun / DR ekuitas (negatif kebalikannya).
     */
    @ErpTx
    public void postCoaOpeningBalance(String accountId, CoaRow row, String openingEquityId, String fiscalYearId,
                                      Instant now, String userId) {
        double abs = Math.abs(row.openingBalance);
        boolean isDebitNormal = "ASSET".equals(row.rootType) || "EXPENSE".equals(row.rootType);
        boolean isPositive = row.openingBalance > 0;
        BigDecimal accountDebit = isDebitNormal == isPositive ? dec(abs) : BigDecimal.ZERO;
        BigDecimal accountCredit = isDebitNormal == isPositive ? BigDecimal.ZERO : dec(abs);

        List<String> existing = em.createQuery("select j.id from JournalEntry j where j.narration like :p and j.status = :st",
                        String.class)
                .setParameter("p", ErpQuery.startsWith("Saldo awal akun: " + row.accountNumber))
                .setParameter("st", DocumentStatus.Submitted).getResultList();
        for (String id : existing) {
            deleteJournalInTx(id);
        }
        String desc = "Saldo awal: " + row.accountNumber + " - " + row.name;
        gl.journal().date(now).narration("Saldo awal akun: " + row.accountNumber + " - " + row.name)
                .fiscalYear(fiscalYearId).createdBy(userId).submittedAt(now)
                .line(accountId, accountDebit, accountCredit, desc)
                .line(openingEquityId, accountCredit, accountDebit, desc)
                .post();
    }

    // ───────────────────────────── jurnal ─────────────────────────────

    /**
     * {@code new Date(str)} untuk kolom tanggal impor: ISO (tanggal saja = tengah malam UTC), tanggal
     * tanpa nol di depan, {@code M/D/YYYY}, atau tanggal-waktu dengan spasi. Null bila tidak valid.
     */
    static Instant parseJsDate(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (YMD_SPACE_TIME.matcher(s).matches()) {
            s = s.replaceFirst(" ", "T");
        }
        Instant iso = id.neravoxa.erp.core.ErpDates.tryInstant(s);
        if (iso != null) {
            return iso;
        }
        try {
            Matcher m = YMD_LOOSE.matcher(s);
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))
                        .atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            m = MDY.matcher(s);
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)))
                        .atStartOfDay(ZoneOffset.UTC).toInstant();
            }
        } catch (java.time.DateTimeException e) {
            return null;
        }
        return null;
    }

    /** Satu kelompok (tanggal + narasi) menjadi satu jurnal Submitted + ledger + saldo. */
    @ErpTx
    public String postImportedJournal(JournalGroup group, String userId) {
        Instant parsedDate = parseJsDate(group.date());
        if (parsedDate == null) {
            throw ErpException.business("Tanggal \"" + group.date() + "\" tidak valid.");
        }
        FiscalYear fy = fiscalYears.getOpenFiscalYear(parsedDate);
        String entryNumber = numbers.generate("JV", parsedDate, fy.getId());
        ErpGlPostingService.Builder b = gl.journal().number(entryNumber).date(parsedDate).narration(group.narration())
                .fiscalYear(fy.getId()).createdBy(userId).submittedAt(Instant.now());
        for (JournalLine item : group.items()) {
            Account account = accountByNumber(item.accountNumber());
            if (account == null) {
                throw ErpException.business("Akun \"" + item.accountNumber() + "\" tidak ditemukan.");
            }
            String desc = item.description() == null || item.description().isEmpty() ? group.narration() : item.description();
            b.line(account.getId(), dec(item.debit()), dec(item.credit()), desc);
        }
        return b.post().getId();
    }

    // ───────────────────────────── mitra ─────────────────────────────

    /**
     * Cari mitra (nama + tipe) atau buat baru; saldo outstanding/deposit DITETAPKAN (bukan ditambah) dari
     * file seperti sumber — lewat UPDATE SQL, bukan setter entity.
     */
    @ErpTx
    public String upsertParty(PartyRow row, BigDecimal outstanding, BigDecimal deposit, BigDecimal customerDeposit) {
        PartyType type = PartyType.valueOf(row.partyType());
        List<Party> found = em.createQuery("select p from Party p where p.name = :n and p.partyType = :t order by p.createdAt asc",
                        Party.class)
                .setParameter("n", row.name()).setParameter("t", type).setMaxResults(1).getResultList();
        if (found.isEmpty()) {
            Party p = new Party();
            p.setName(row.name());
            p.setPartyType(type);
            p.setPhone(row.phone());
            p.setEmail(row.email());
            p.setAddress(row.address());
            p.setTaxId(row.taxId());
            p.setOutstandingAmount(Money.round2(outstanding));
            p.setDepositBalance(Money.round2(deposit));
            p.setCustomerDepositBalance(Money.round2(customerDeposit));
            em.persist(p);
            em.flush();
            return p.getId();
        }
        Party p = found.get(0);
        p.setName(row.name());
        p.setPartyType(type);
        if (row.phone() != null) {
            p.setPhone(row.phone());
        }
        if (row.email() != null) {
            p.setEmail(row.email());
        }
        if (row.address() != null) {
            p.setAddress(row.address());
        }
        if (row.taxId() != null) {
            p.setTaxId(row.taxId());
        }
        em.flush();
        em.createNativeQuery("UPDATE parties SET outstanding_amount = :o, deposit_balance = :d, "
                        + "customer_deposit_balance = :c, updated_at = now() WHERE id = :id")
                .setParameter("o", Money.round2(outstanding))
                .setParameter("d", Money.round2(deposit))
                .setParameter("c", Money.round2(customerDeposit))
                .setParameter("id", p.getId())
                .executeUpdate();
        return p.getId();
    }

    /** Jurnal saldo awal mitra yang sudah ada (Submitted, narasi memuat nama, salah satu awalan saldo awal). */
    @ErpTx(readOnly = true)
    public List<String> partyOpeningJournalIds(String name) {
        return em.createQuery("select j.id from JournalEntry j where j.narration like :c and j.status = :st and ("
                        + "j.narration like :p1 or j.narration like :p2 or j.narration like :p3 or j.narration like :p4)",
                        String.class)
                .setParameter("c", "%" + ErpQuery.escapeLike(name) + "%")
                .setParameter("st", DocumentStatus.Submitted)
                .setParameter("p1", ErpQuery.startsWith("Saldo awal piutang:"))
                .setParameter("p2", ErpQuery.startsWith("Saldo awal hutang:"))
                .setParameter("p3", ErpQuery.startsWith("Saldo awal uang muka vendor:"))
                .setParameter("p4", ErpQuery.startsWith("Saldo awal uang muka pelanggan:"))
                .getResultList();
    }

    @ErpTx
    public void deleteJournal(String journalId) {
        deleteJournalInTx(journalId);
    }

    /** Payment uang muka saldo awal hasil impor sebelumnya untuk mitra ini. */
    @ErpTx(readOnly = true)
    public List<String> openingDepositPaymentIds(String partyId) {
        return em.createQuery("select p.id from Payment p where p.partyId = :id and p.notes like :n", String.class)
                .setParameter("id", partyId)
                .setParameter("n", "%" + ErpQuery.escapeLike("Saldo awal uang muka") + "%")
                .getResultList();
    }

    @ErpTx
    public void deletePayment(String paymentId) {
        em.createQuery("delete from Payment p where p.id = :id").setParameter("id", paymentId).executeUpdate();
    }

    /** Saldo awal piutang (DR Piutang [mitra] / CR Ekuitas) atau hutang (DR Ekuitas / CR Hutang [mitra]). */
    @ErpTx
    public void postPartyOpeningBalance(String partyId, String name, boolean isCustomer, String receivableAccountId,
                                        String openingEquityId, BigDecimal amount, String fiscalYearId, Instant now,
                                        String userId) {
        String narration = "Saldo awal " + (isCustomer ? "piutang" : "hutang") + ": " + name;
        ErpGlPostingService.Builder b = gl.journal().date(now).narration(narration).fiscalYear(fiscalYearId)
                .createdBy(userId).submittedAt(now);
        if (isCustomer) {
            b.line(receivableAccountId, amount, BigDecimal.ZERO, narration, partyId)
                    .line(openingEquityId, BigDecimal.ZERO, amount, narration);
        } else {
            b.line(openingEquityId, amount, BigDecimal.ZERO, narration)
                    .line(receivableAccountId, BigDecimal.ZERO, amount, narration, partyId);
        }
        b.post();
    }

    /**
     * Saldo awal uang muka: vendor → DR Uang Muka Pembelian [mitra] / CR Ekuitas; pelanggan → DR Ekuitas /
     * CR Uang Muka Pelanggan [mitra]. Ditambah record Payment agar tampil di modul deposit.
     */
    @ErpTx
    public void postPartyDeposit(boolean vendor, String partyId, String name, String depositAccountId,
                                 String openingEquityId, BigDecimal amount, String fiscalYearId, Instant now, String userId) {
        String payNumber = numbers.generate("PAY", now, fiscalYearId);
        String narration = vendor ? "Saldo awal uang muka vendor: " + name : "Saldo awal uang muka pelanggan: " + name;
        ErpGlPostingService.Builder b = gl.journal().date(now).narration(narration).fiscalYear(fiscalYearId)
                .createdBy(userId).submittedAt(now);
        if (vendor) {
            b.line(depositAccountId, amount, BigDecimal.ZERO, narration, partyId)
                    .line(openingEquityId, BigDecimal.ZERO, amount, narration);
        } else {
            b.line(openingEquityId, amount, BigDecimal.ZERO, narration)
                    .line(depositAccountId, BigDecimal.ZERO, amount, narration, partyId);
        }
        JournalEntry journal = b.post();

        Payment pay = new Payment();
        pay.setPaymentNumber(payNumber);
        pay.setDate(now);
        pay.setPaymentType(vendor ? PaymentType.VendorDeposit : PaymentType.CustomerDeposit);
        pay.setPartyId(partyId);
        pay.setAccountId(depositAccountId);
        pay.setAmount(Money.round2(amount));
        pay.setStatus(DocumentStatus.Submitted);
        pay.setNotes(vendor ? "Saldo awal uang muka vendor dari import: " + name
                : "Saldo awal uang muka pelanggan dari import: " + name);
        pay.setCreatedBy(userId);
        pay.setJournalEntryId(journal.getId());
        pay.setFiscalYearId(fiscalYearId);
        pay.setSubmittedAt(now);
        em.persist(pay);
    }

    // ───────────────────────────── persediaan ─────────────────────────────

    /** Upsert barang berdasarkan kode; field opsional yang kosong tidak mengubah data lama. */
    @ErpTx
    public String upsertItem(ItemRow row) {
        List<InventoryItem> found = em.createQuery("select i from InventoryItem i where i.code = :c", InventoryItem.class)
                .setParameter("c", row.code()).setMaxResults(1).getResultList();
        InventoryItem item;
        if (found.isEmpty()) {
            item = new InventoryItem();
            item.setCode(row.code());
            item.setName(row.name());
            item.setUnit(row.unit());
            item.setCategory(row.category());
            item.setDescription(row.description());
            if (row.minimumStock() != null) {
                item.setMinimumStock(Money.round(dec(row.minimumStock()), 3));
            }
            em.persist(item);
        } else {
            item = found.get(0);
            item.setName(row.name());
            item.setUnit(row.unit());
            if (row.category() != null) {
                item.setCategory(row.category());
            }
            if (row.description() != null) {
                item.setDescription(row.description());
            }
            if (row.minimumStock() != null) {
                item.setMinimumStock(Money.round(dec(row.minimumStock()), 3));
            }
        }
        em.flush();
        return item.getId();
    }
}
