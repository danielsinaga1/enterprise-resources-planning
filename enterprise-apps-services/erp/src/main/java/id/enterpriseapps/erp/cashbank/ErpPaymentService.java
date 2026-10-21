package id.enterpriseapps.erp.cashbank;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpAccountBalanceService;
import id.enterpriseapps.erp.core.accounting.ErpAllocationService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.accounting.SystemAccountRole;
import id.enterpriseapps.erp.entity.DocumentStatus;
import id.enterpriseapps.erp.entity.JournalEntry;
import id.enterpriseapps.erp.entity.JournalItem;
import id.enterpriseapps.erp.entity.Payment;
import id.enterpriseapps.erp.entity.PaymentAllocation;
import id.enterpriseapps.erp.entity.PaymentSplit;
import id.enterpriseapps.erp.entity.PaymentType;
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
 * Daftar, detail, jurnal kas, dan pembatalan pembayaran — padanan handler di {@code routes/payments.ts}.
 * Logika simpan ada di {@link ErpPaymentSubmitService}; daftar kas &amp; bank gabungan di {@link ErpCashLedgerService}.
 */
@Service
@RequiredArgsConstructor
public class ErpPaymentService {

    private final ErpAllocationService allocations;
    private final ErpAccountBalanceService balances;
    private final ErpSystemAccountService systemAccounts;
    private final ErpPaymentSubmitService submitter;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * GET /payments — {@code page} (default 1), {@code limit} (default 50, maks. 200), urut tanggal terbaru;
     * include party + splits.account.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        BigDecimal pageNum = CashBankZod.jsNumber(query.getOrDefault("page", "1"));
        BigDecimal limitNum = CashBankZod.jsNumber(query.getOrDefault("limit", "50"));
        // Number(limit) || 50 → NaN/0 jatuh ke 50.
        BigDecimal take = limitNum == null || limitNum.signum() == 0 ? BigDecimal.valueOf(50)
                : limitNum.min(BigDecimal.valueOf(200));
        BigDecimal skip = pageNum == null ? null : pageNum.subtract(BigDecimal.ONE).multiply(take);
        // Prisma menolak skip/take yang bukan bilangan bulat non-negatif → 500 di sumber.
        if (skip == null || !isNonNegativeInt(skip) || !isNonNegativeInt(take) || take.signum() == 0) {
            throw ErpException.of(500, "Gagal mengambil data pembayaran.");
        }

        ErpQuery<Payment> q = ErpQuery.of(em, Payment.class, "p")
                .fetch("left join fetch p.party")
                .orderBy("p.date desc, p.createdAt desc, p.id desc");
        long total = q.count();
        List<Payment> rows = q.list(skip.intValueExact(), take.intValueExact());
        Map<String, List<PaymentSplit>> splitsByPayment = splitsWithAccount(rows.stream().map(Payment::getId).toList());

        List<ObjectNode> data = new ArrayList<>();
        for (Payment p : rows) {
            ObjectNode o = CashBankJson.scalars(p);
            o.set("party", CashBankJson.scalars(p.getParty()));
            o.set("splits", splitsJson(splitsByPayment.getOrDefault(p.getId(), List.of())));
            data.add(o);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        out.put("total", total);
        // Number(page) apa adanya (tanpa notasi eksponen).
        out.put("page", new BigDecimal(pageNum.stripTrailingZeros().toPlainString()));
        out.put("limit", take.intValueExact());
        return out;
    }

    private static boolean isNonNegativeInt(BigDecimal v) {
        return v.signum() >= 0 && v.stripTrailingZeros().scale() <= 0 && v.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
    }

    private Map<String, List<PaymentSplit>> splitsWithAccount(List<String> paymentIds) {
        Map<String, List<PaymentSplit>> out = new HashMap<>();
        if (paymentIds.isEmpty()) {
            return out;
        }
        em.createQuery("select s from PaymentSplit s left join fetch s.account where s.paymentId in :ids order by s.id",
                        PaymentSplit.class)
                .setParameter("ids", paymentIds).getResultList()
                .forEach(s -> out.computeIfAbsent(s.getPaymentId(), k -> new ArrayList<>()).add(s));
        return out;
    }

    private static ArrayNode splitsJson(List<PaymentSplit> splits) {
        ArrayNode arr = ErpJson.arrayNode();
        for (PaymentSplit s : splits) {
            ObjectNode so = CashBankJson.scalars(s);
            so.set("account", CashBankJson.scalars(s.getAccount()));
            arr.add(so);
        }
        return arr;
    }

    /**
     * GET /payments/cash-journals — baris jurnal yang menyentuh akun kas/bank (1.1.x detail) dan bukan
     * jurnal pembayaran (JV-PAY…), 100 terbaru.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> cashJournals() {
        List<JournalItem> items = em.createQuery(
                        "select i from JournalItem i join fetch i.journalEntry je join fetch i.account a "
                                + "where a.accountNumber like '1.1%' and a.isGroup = false "
                                + "and je.status <> :cancelled and je.entryNumber not like 'JV-PAY%' "
                                + "order by je.date desc", JournalItem.class)
                .setParameter("cancelled", DocumentStatus.Cancelled)
                .setMaxResults(100)
                .getResultList();

        Set<String> partyIds = new LinkedHashSet<>();
        items.forEach(i -> {
            if (i.getPartyId() != null) {
                partyIds.add(i.getPartyId());
            }
        });
        Map<String, String> partyNames = new HashMap<>();
        if (!partyIds.isEmpty()) {
            em.createQuery("select p.id, p.name from Party p where p.id in :ids", Object[].class)
                    .setParameter("ids", partyIds).getResultList()
                    .forEach(r -> partyNames.put((String) r[0], (String) r[1]));
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (JournalItem item : items) {
            JournalEntry je = item.getJournalEntry();
            boolean isCredit = Money.nz(item.getCredit()).signum() > 0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId());
            m.put("journalEntryId", item.getJournalEntryId());
            m.put("date", je.getDate());
            m.put("entryNumber", je.getEntryNumber());
            m.put("narration", je.getNarration());
            m.put("partyName", item.getPartyId() != null ? partyNames.get(item.getPartyId()) : null);
            m.put("amount", isCredit ? item.getCredit() : item.getDebit());
            m.put("isCredit", isCredit);
            m.put("status", je.getStatus());
            data.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        return out;
    }

    /** GET /payments/:id — party, account, splits.account, allocations (+invoiceNumber), journalEntry.items.account. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<Payment> rows = em.createQuery("select p from Payment p left join fetch p.party left join fetch p.account "
                        + "left join fetch p.journalEntry where p.id = :id", Payment.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("Pembayaran tidak ditemukan.");
        }
        Payment p = rows.get(0);
        ObjectNode o = CashBankJson.scalars(p);
        o.set("party", CashBankJson.scalars(p.getParty()));
        o.set("account", CashBankJson.scalars(p.getAccount()));
        o.set("splits", splitsJson(splitsWithAccount(List.of(p.getId())).getOrDefault(p.getId(), List.of())));

        List<PaymentAllocation> allocs = em.createQuery(
                        "select a from PaymentAllocation a where a.paymentId = :id order by a.id", PaymentAllocation.class)
                .setParameter("id", p.getId()).getResultList();
        o.set("allocations", allocations.withAllocationInvoiceNumbers(
                allocs.stream().map(CashBankJson::scalars).toList()));

        JournalEntry je = p.getJournalEntry();
        if (je == null) {
            o.set("journalEntry", NullNode.getInstance());
        } else {
            ObjectNode jo = CashBankJson.scalars(je);
            ArrayNode items = ErpJson.arrayNode();
            em.createQuery("select i from JournalItem i left join fetch i.account where i.journalEntryId = :id order by i.id",
                            JournalItem.class)
                    .setParameter("id", je.getId()).getResultList()
                    .forEach(i -> {
                        ObjectNode io = CashBankJson.scalars(i);
                        io.set("account", CashBankJson.scalars(i.getAccount()));
                        items.add(io);
                    });
            jo.set("items", items);
            o.set("journalEntry", jo);
        }
        return o;
    }

    /**
     * POST /payments/:id/cancel — batalkan Receive/Pay: pulihkan outstanding invoice, hapus alokasi,
     * batalkan jurnal JV-&lt;nomor&gt; (status + ledger), balik saldo akun per split, hitung ulang outstanding mitra.
     * Uang muka & refund ditolak (punya jalur pembatalan sendiri).
     */
    @ErpTx
    public Map<String, Object> cancel(String id) {
        // Kunci baris agar dua pembatalan paralel tidak membalik saldo dua kali.
        em.createNativeQuery("SELECT id FROM payments WHERE id = :id FOR UPDATE").setParameter("id", id).getResultList();
        Payment payment = em.find(Payment.class, id);
        if (payment == null) {
            throw ErpException.business("Pembayaran tidak ditemukan.");
        }
        if (payment.getStatus() == DocumentStatus.Cancelled) {
            throw ErpException.business("Pembayaran sudah dibatalkan.");
        }
        BigDecimal amount = Money.nz(payment.getAmount());

        // Deposit dibatalkan lewat endpoint masing-masing; membatalkan di sini juga = batal ganda & GL drift.
        if (payment.getPaymentType() == PaymentType.VendorDeposit) {
            throw ErpException.business("Gunakan endpoint Uang Muka Supplier untuk membatalkan deposit.");
        }
        if (payment.getPaymentType() == PaymentType.CustomerDeposit) {
            throw ErpException.business("Gunakan endpoint Uang Muka Customer untuk membatalkan deposit.");
        }
        if (payment.getPaymentType() == PaymentType.VendorRefund || payment.getPaymentType() == PaymentType.CustomerRefund) {
            throw ErpException.business(
                    "Refund belum mendukung cancel otomatis. Buat jurnal koreksi manual untuk membatalkan refund ini.");
        }

        List<PaymentAllocation> allocs = em.createQuery(
                        "select a from PaymentAllocation a where a.paymentId = :id", PaymentAllocation.class)
                .setParameter("id", payment.getId()).getResultList();
        for (PaymentAllocation alloc : allocs) {
            submitter.restoreInvoiceOutstanding(alloc.getInvoiceType(), alloc.getInvoiceId(), Money.nz(alloc.getAllocatedAmount()));
        }
        em.flush();
        em.createQuery("delete from PaymentAllocation a where a.paymentId = :id")
                .setParameter("id", payment.getId()).executeUpdate();

        // Jurnal dibatalkan dengan nomor yang sama seperti saat dibuat.
        List<JournalEntry> journals = em.createQuery("select j from JournalEntry j where j.entryNumber = :n", JournalEntry.class)
                .setParameter("n", "JV-" + payment.getPaymentNumber()).getResultList();
        if (!journals.isEmpty()) {
            JournalEntry journal = journals.get(0);
            journal.setStatus(DocumentStatus.Cancelled);
            journal.setCancelledAt(Instant.now());
            em.flush();
            em.createQuery("update AccountingLedgerEntry e set e.isCancelled = true where e.referenceId = :id")
                    .setParameter("id", journal.getId()).executeUpdate();
        }

        // Balik saldo akun: per split bila ada, selain itu akun tunggal pembayaran.
        String arAccountId = systemAccounts.getAccount(SystemAccountRole.AR).id();
        String apAccountId = systemAccounts.getAccount(SystemAccountRole.AP).id();
        List<PaymentSplit> splits = em.createQuery("select s from PaymentSplit s where s.paymentId = :id", PaymentSplit.class)
                .setParameter("id", payment.getId()).getResultList();
        List<Object[]> reverse = new ArrayList<>();
        if (splits.isEmpty()) {
            reverse.add(new Object[]{payment.getAccountId(), amount});
        } else {
            splits.forEach(s -> reverse.add(new Object[]{s.getAccountId(), Money.nz(s.getAmount())}));
        }
        if (payment.getPaymentType() == PaymentType.Receive) {
            // Asal: split DR, Piutang CR. Balik: split CR, Piutang DR.
            for (Object[] sp : reverse) {
                balances.updateAccountBalance((String) sp[0], Money.ZERO, (BigDecimal) sp[1]);
            }
            balances.updateAccountBalance(arAccountId, amount, Money.ZERO);
        } else {
            // Asal: split CR, Hutang DR. Balik: split DR, Hutang CR.
            for (Object[] sp : reverse) {
                balances.updateAccountBalance((String) sp[0], (BigDecimal) sp[1], Money.ZERO);
            }
            balances.updateAccountBalance(apAccountId, Money.ZERO, amount);
        }

        // Outstanding mitra dihitung ulang dari data invoice (sumber kebenaran).
        balances.recalcPartyOutstanding(payment.getPartyId());

        payment.setStatus(DocumentStatus.Cancelled);
        payment.setCancelledAt(Instant.now());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", payment.getId());
        out.put("status", "Cancelled");
        return out;
    }
}
