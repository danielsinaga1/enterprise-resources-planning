package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpAccountBalanceService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpPartyBalanceService;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.CustomerDepositApplication;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.JournalItem;
import id.neravoxa.erp.entity.SalesInvoice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jurnal umum (Buku Besar) — port {@code routes/journals.ts} + {@code services/journalList.ts}:
 * daftar berpaginasi, detail, jurnal manual (posting GL + saldo mitra), dan pembatalan dengan
 * pembalikan saldo akun/mitra serta efek samping aplikasi & refund Uang Muka Pelanggan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpJournalService {

    /** Jurnal otomatis dari dokumen sumber: JV-PAY-* (pembayaran), JV-SI-*, JV-PI-*, JV-COGS-* (HPP). */
    private static final Pattern AUTO_JOURNAL = Pattern.compile("^JV-(PAY|SI|PI|COGS)");
    private static final Pattern DEPOSIT_APPLICATION = Pattern.compile("^Aplikasi Uang Muka:");
    private static final Pattern CUSTOMER_REFUND = Pattern.compile("(?U)^Refund UM Pelanggan:\\s*(\\S+)");

    private final ErpFiscalYearService fiscalYears;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpPartyBalanceService partyBalances;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── daftar & detail ─────────────────────────────

    /** GET /journals — {@code { data, total, page, limit }}; baris + akun (nomor, nama) + pembuat. */
    @ErpTx(readOnly = true)
    public ObjectNode list(Map<String, String> query) {
        // maxLimit 200: batas yang sama dengan endpoint asli.
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        ErpQuery<JournalEntry> q = ErpQuery.of(em, JournalEntry.class, "e");
        JournalListFilter.apply(q, query);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), JournalListFilter.SORT_FIELDS,
                JournalListFilter.DEFAULT_SORT);
        q.orderBy(JournalListFilter.orderBy(sort));

        List<JournalEntry> entries = q.list(paging.skip(), paging.limit());
        long total = q.count();
        ArrayNode data = render(entries, new String[]{"accountNumber", "name"});
        return AccountingJson.listResult(data, total, paging);
    }

    /** GET /journals/:id — baris + akun (id, nomor, nama, rootType) + pembuat. */
    @ErpTx(readOnly = true)
    public ObjectNode get(String id) {
        JournalEntry entry = em.find(JournalEntry.class, id);
        if (entry == null) {
            throw ErpException.of(404, "Jurnal tidak ditemukan.");
        }
        return (ObjectNode) render(List.of(entry), new String[]{"id", "accountNumber", "name", "rootType"}).get(0);
    }

    /** Header + {@code items} (akun parsial) + {@code user: { fullName }} untuk tiap jurnal. */
    private ArrayNode render(List<JournalEntry> entries, String[] accountFields) {
        ArrayNode out = ErpJson.arrayNode();
        if (entries.isEmpty()) {
            return out;
        }
        List<String> ids = entries.stream().map(JournalEntry::getId).toList();
        Map<String, List<JournalItem>> items = new HashMap<>();
        for (JournalItem it : em.createQuery(
                        "select i from JournalItem i join fetch i.account where i.journalEntryId in :ids", JournalItem.class)
                .setParameter("ids", ids).getResultList()) {
            items.computeIfAbsent(it.getJournalEntryId(), k -> new ArrayList<>()).add(it);
        }
        Map<String, String> userNames = userNames(entries.stream().map(JournalEntry::getCreatedBy).toList());
        for (JournalEntry e : entries) {
            ObjectNode n = AccountingJson.pick(e, AccountingJson.JOURNAL_ENTRY);
            ArrayNode lines = n.putArray("items");
            for (JournalItem it : items.getOrDefault(e.getId(), List.of())) {
                ObjectNode line = AccountingJson.pick(it, AccountingJson.JOURNAL_ITEM);
                line.set("account", AccountingJson.pick(it.getAccount(), accountFields));
                lines.add(line);
            }
            n.set("user", userJson(userNames, e.getCreatedBy()));
            out.add(n);
        }
        return out;
    }

    private Map<String, String> userNames(List<String> userIds) {
        Map<String, String> names = new HashMap<>();
        Set<String> unique = new HashSet<>(userIds);
        unique.remove(null);
        if (unique.isEmpty()) {
            return names;
        }
        for (Object[] r : em.createQuery("select u.id, u.fullName from User u where u.id in :ids", Object[].class)
                .setParameter("ids", unique).getResultList()) {
            names.put((String) r[0], (String) r[1]);
        }
        return names;
    }

    static com.fasterxml.jackson.databind.JsonNode userJson(Map<String, String> names, String userId) {
        if (userId == null || !names.containsKey(userId)) {
            return com.fasterxml.jackson.databind.node.NullNode.getInstance();
        }
        ObjectNode u = ErpJson.object();
        u.put("fullName", names.get(userId));
        return u;
    }

    // ───────────────────────────── jurnal manual ─────────────────────────────

    /** Baris jurnal manual yang sudah dikoersi. */
    private record Line(String accountId, String partyId, BigDecimal debit, BigDecimal credit, String description) {
    }

    /**
     * POST /journals — validasi (debit XOR kredit per baris, total seimbang), lalu posting jurnal
     * Submitted bernomor JV ke ledger, saldo akun, dan saldo mitra (akun-aware: baris UM 2.1.2/1.3
     * mengenai saldo uang muka, bukan outstanding).
     */
    @ErpTx
    public ObjectNode create(AccountingRequests.JournalCreate body, String userId) {
        List<Line> lines = new ArrayList<>();
        for (AccountingRequests.JournalLine item : body.items()) {
            if (item == null) {
                throw ErpException.business(ZodLite.invalidType("object", null));
            }
            BigDecimal debit = nonNegative(item.debit());
            BigDecimal credit = nonNegative(item.credit());
            lines.add(new Line(item.accountId(), blankToNull(item.partyId()), debit, credit,
                    item.description() == null || item.description().isEmpty() ? body.narration() : item.description()));
        }

        // Satu baris tidak boleh memiliki debit dan kredit sekaligus.
        for (Line l : lines) {
            if (l.debit().signum() > 0 && l.credit().signum() > 0) {
                throw ErpException.business("Satu baris jurnal tidak boleh memiliki debit dan kredit sekaligus.");
            }
        }
        // Total dihitung seperti sumber (angka JS) agar pesan & toleransinya identik.
        double totalDebit = 0;
        double totalCredit = 0;
        for (Line l : lines) {
            totalDebit += l.debit().doubleValue();
            totalCredit += l.credit().doubleValue();
        }
        if (Math.abs(totalDebit - totalCredit) > 0.01) {
            throw ErpException.business("Jurnal tidak seimbang. Total Debit (" + JsNumbers.toJsString(totalDebit)
                    + ") ≠ Total Kredit (" + JsNumbers.toJsString(totalCredit) + ").");
        }

        Instant date = ErpDates.toInstant(body.date());
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
        Map<String, Account> accounts = accountsById(lines.stream().map(Line::accountId).toList());
        assertParties(lines.stream().map(Line::partyId).toList());

        ErpGlPostingService.Builder journal = gl.journal()
                .date(date)
                .narration(body.narration())
                .fiscalYear(fy.getId())
                .createdBy(userId)
                .submittedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        for (Line l : lines) {
            journal.line(l.accountId(), l.debit(), l.credit(), l.description(), l.partyId());
        }
        JournalEntry je = journal.post();
        List<JournalItem> posted = new ArrayList<>(je.getItems());

        // Saldo denormalisasi mitra — akun-aware (UM 2.1.2/1.3 ke saldo uang muka).
        List<ErpPartyBalanceService.PartyImpactItem> impacts = new ArrayList<>();
        for (JournalItem it : posted) {
            Account acc = accounts.get(it.getAccountId());
            impacts.add(new ErpPartyBalanceService.PartyImpactItem(it.getPartyId(), it.getDebit(), it.getCredit(),
                    acc.getAccountNumber(), acc.getRootType()));
        }
        partyBalances.applyPartyImpact(impacts, ErpPartyBalanceService.Direction.APPLY);

        em.flush();
        em.refresh(je);
        ObjectNode out = AccountingJson.pick(je, AccountingJson.JOURNAL_ENTRY);
        ArrayNode arr = out.putArray("items");
        for (JournalItem it : posted) {
            ObjectNode line = AccountingJson.pick(it, AccountingJson.JOURNAL_ITEM);
            line.set("account", AccountingJson.pick(accounts.get(it.getAccountId()), "accountNumber", "rootType"));
            arr.add(line);
        }
        return out;
    }

    /** z.coerce.number().min(0) untuk debit/kredit baris. */
    private static BigDecimal nonNegative(com.fasterxml.jackson.databind.JsonNode raw) {
        BigDecimal v = ZodLite.requireNumber(raw);
        if (v.signum() < 0) {
            throw ErpException.business(ZodLite.tooSmall(true, "0"));
        }
        return v;
    }

    /** Akun baris jurnal; id yang tidak ada = pelanggaran FK (P2003) di sumber. */
    private Map<String, Account> accountsById(List<String> ids) {
        Set<String> unique = new LinkedHashSet<>(ids);
        Map<String, Account> map = new LinkedHashMap<>();
        for (Account a : em.createQuery("select a from Account a where a.id in :ids", Account.class)
                .setParameter("ids", unique).getResultList()) {
            map.put(a.getId(), a);
        }
        if (map.size() != unique.size()) {
            throw ErpException.business(AccountingDb.INVALID_REFERENCE_MESSAGE);
        }
        return map;
    }

    /** Mitra pada baris (ledger ber-FK ke parties); id yang tidak ada = P2003 di sumber. */
    private void assertParties(List<String> partyIds) {
        Set<String> unique = new HashSet<>(partyIds);
        unique.remove(null);
        if (unique.isEmpty()) {
            return;
        }
        long found = em.createQuery("select count(p) from Party p where p.id in :ids", Long.class)
                .setParameter("ids", unique).getSingleResult();
        if (found != unique.size()) {
            throw ErpException.business(AccountingDb.INVALID_REFERENCE_MESSAGE);
        }
    }

    // ───────────────────────────── pembatalan ─────────────────────────────

    /**
     * PATCH /journals/:id/cancel — balik saldo akun & mitra, batalkan aplikasi uang muka pelanggan
     * (kembalikan outstanding invoice), kembalikan refundedAmount pembayaran untuk JV refund, tandai
     * jurnal Cancelled dan soft-cancel baris ledger aslinya (bukan baris [BATAL] tambahan — itu akan
     * menggandakan total ALE vs JournalItem dan menggagalkan health check).
     */
    @ErpTx
    public ObjectNode cancel(String id) {
        JournalEntry entry = em.find(JournalEntry.class, id);
        if (entry == null) {
            throw ErpException.of(404, "Jurnal tidak ditemukan.");
        }
        if (entry.getStatus() == DocumentStatus.Cancelled) {
            throw ErpException.business("Jurnal sudah dibatalkan.");
        }
        long payments = em.createQuery("select count(p) from Payment p where p.journalEntryId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (payments > 0) {
            throw ErpException.business("Jurnal ini terkait pembayaran. Batalkan dari modul Pembayaran.");
        }
        // Jaring pengaman: jurnal otomatis dari dokumen sumber dibatalkan di modul asalnya.
        if (AUTO_JOURNAL.matcher(entry.getEntryNumber()).find()) {
            throw ErpException.business("Jurnal ini dibuat otomatis dari transaksi sumber (Pembayaran/Invoice/HPP). "
                    + "Batalkan dari modul asalnya, bukan dari Buku Besar.");
        }
        // Jurnal aplikasi uang muka (pelanggan & vendor) bisa tanpa prefix JV-PAY; membatalkannya di sini
        // tidak mengembalikan aplikasi & outstanding invoice, jadi dicocokkan juga lewat narasi.
        String narration = entry.getNarration() == null ? "" : entry.getNarration();
        if (DEPOSIT_APPLICATION.matcher(narration).find()) {
            throw ErpException.business("Jurnal ini adalah aplikasi Uang Muka ke invoice. Batalkan dari modul Uang "
                    + "Muka Customer/Supplier (tombol batalkan aplikasi), bukan dari Buku Besar.");
        }

        // Klaim atomik: permintaan pembatalan paralel tidak boleh membalik saldo dua kali.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        int claimed = em.createQuery("update JournalEntry j set j.status = :cancelled, j.cancelledAt = :now, "
                        + "j.updatedAt = :now where j.id = :id and j.status <> :cancelled")
                .setParameter("cancelled", DocumentStatus.Cancelled)
                .setParameter("now", now)
                .setParameter("id", id)
                .executeUpdate();
        if (claimed == 0) {
            throw ErpException.business("Jurnal sudah dibatalkan.");
        }

        List<JournalItem> items = em.createQuery(
                        "select i from JournalItem i join fetch i.account where i.journalEntryId = :id", JournalItem.class)
                .setParameter("id", id).getResultList();

        // Balik saldo akun (tukar debit↔kredit).
        for (JournalItem it : items) {
            balances.updateAccountBalance(it.getAccountId(), it.getCredit(), it.getDebit());
        }
        // Balik saldo mitra — klasifikasi simetris dengan posting.
        List<ErpPartyBalanceService.PartyImpactItem> impacts = new ArrayList<>();
        for (JournalItem it : items) {
            impacts.add(new ErpPartyBalanceService.PartyImpactItem(it.getPartyId(), it.getDebit(), it.getCredit(),
                    it.getAccount().getAccountNumber(), it.getAccount().getRootType()));
        }
        partyBalances.applyPartyImpact(impacts, ErpPartyBalanceService.Direction.REVERSE);

        // JV offset dari customerDeposits.ts: aplikasi UM ditandai batal, outstanding + status invoice dipulihkan.
        List<CustomerDepositApplication> apps = em.createQuery(
                        "select a from CustomerDepositApplication a where a.journalEntryId = :id and a.isCancelled = false",
                        CustomerDepositApplication.class)
                .setParameter("id", id).getResultList();
        for (CustomerDepositApplication app : apps) {
            app.setIsCancelled(true);
            app.setCancelledAt(now);
            SalesInvoice inv = em.find(SalesInvoice.class, app.getSalesInvoiceId(), LockModeType.PESSIMISTIC_WRITE);
            if (inv != null) {
                BigDecimal restored = Money.nz(inv.getOutstanding()).add(Money.nz(app.getAppliedAmount()));
                inv.setOutstanding(restored);
                inv.setStatus(restored.compareTo(Money.nz(inv.getGrandTotal())) >= 0
                        ? InvoiceStatus.Submitted : InvoiceStatus.PartiallyPaid);
            }
        }

        // JV refund ("Refund UM Pelanggan: {paymentNumber} - ..."): kembalikan refundedAmount sebesar
        // debit baris 2.1.2.
        Matcher refund = CUSTOMER_REFUND.matcher(narration);
        if (refund.find()) {
            String paymentNumber = refund.group(1);
            items.stream()
                    .filter(i -> ErpPartyBalanceService.CUSTOMER_DEPOSIT_ACCOUNT.equals(i.getAccount().getAccountNumber())
                            && Money.nz(i.getDebit()).signum() > 0)
                    .findFirst()
                    .ifPresent(line -> em.createQuery("update Payment p set p.refundedAmount = p.refundedAmount - :amt, "
                                    + "p.updatedAt = :now where p.paymentNumber = :no")
                            .setParameter("amt", line.getDebit())
                            .setParameter("now", now)
                            .setParameter("no", paymentNumber)
                            .executeUpdate());
        }

        em.createQuery("update AccountingLedgerEntry l set l.isCancelled = true "
                        + "where l.referenceId = :id and l.isCancelled = false")
                .setParameter("id", id)
                .executeUpdate();
        log.info("Jurnal {} dibatalkan", entry.getEntryNumber());
        return AccountingJson.message("Jurnal dibatalkan.");
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
