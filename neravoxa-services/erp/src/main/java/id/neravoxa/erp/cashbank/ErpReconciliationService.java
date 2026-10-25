package id.neravoxa.erp.cashbank;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.cashbank.CashBankRequests.CreateReconciliation;
import id.neravoxa.erp.cashbank.CashBankRequests.MatchItem;
import id.neravoxa.erp.cashbank.CashBankRequests.StatementItemInput;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.AccountingLedgerEntry;
import id.neravoxa.erp.entity.BankReconciliation;
import id.neravoxa.erp.entity.BankReconciliationItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rekonsiliasi bank — logika {@code routes/reconciliation.ts} + {@code services/ledgerEntryLabels.ts}.
 * Sesi berstatus Draft → baris rekening koran ditambahkan, dicocokkan ke ledger akun kas/bank, lalu Completed.
 */
@Service
@RequiredArgsConstructor
public class ErpReconciliationService {

    private static final String DRAFT = "Draft";
    private static final String COMPLETED = "Completed";

    private final ErpSystemAccountService systemAccounts;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET / — daftar berpaginasi (default 50, maks. 200) + {@code _count.items} dan {@code matchedCount} per baris. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        ErpQuery<BankReconciliation> q = ReconciliationList.query(em, query);
        ListQuery.Paging paging = ListQuery.paging(query, 50, 200);
        ListQuery.Sort sort = ListQuery.sort(ListQuery.str(query, "sort"), ReconciliationList.SORT_FIELDS,
                ReconciliationList.DEFAULT_SORT);
        q.orderBy(ReconciliationList.orderBy(sort));
        long total = q.count();
        List<BankReconciliation> rows = q.list(paging.skip(), paging.limit());

        Map<String, Long> itemCounts = new HashMap<>();
        Map<String, Long> matchedCounts = new HashMap<>();
        List<String> ids = rows.stream().map(BankReconciliation::getId).toList();
        if (!ids.isEmpty()) {
            em.createQuery("select i.reconciliationId, count(i), sum(case when i.isMatched = true then 1 else 0 end) "
                            + "from BankReconciliationItem i where i.reconciliationId in :ids group by i.reconciliationId",
                            Object[].class)
                    .setParameter("ids", ids).getResultList()
                    .forEach(r -> {
                        itemCounts.put((String) r[0], ((Number) r[1]).longValue());
                        matchedCounts.put((String) r[0], r[2] == null ? 0L : ((Number) r[2]).longValue());
                    });
        }

        List<ObjectNode> data = new ArrayList<>();
        for (BankReconciliation r : rows) {
            ObjectNode o = CashBankJson.scalars(r);
            o.set("account", CashBankJson.pick(r.getAccount(), "id", "name", "accountNumber", "balance"));
            ObjectNode count = ErpJson.object();
            count.put("items", itemCounts.getOrDefault(r.getId(), 0L));
            o.set("_count", count);
            o.put("matchedCount", matchedCounts.getOrDefault(r.getId(), 0L));
            data.add(o);
        }
        return ListQuery.result(data, total, paging);
    }

    /** POST / — sesi baru; saldo buku diambil dari saldo akun saat ini. */
    @ErpTx
    public ObjectNode create(CreateReconciliation body, String userId) {
        Account account = em.find(Account.class, body.accountId());
        if (account == null) {
            throw ErpException.business("Akun tidak ditemukan.");
        }
        if (!systemAccounts.isCashAccount(account.getAccountNumber())) {
            throw ErpException.business("Akun yang dipilih bukan akun kas/bank.");
        }
        BankReconciliation r = new BankReconciliation();
        r.setAccountId(body.accountId());
        r.setStatementDate(ErpDates.toInstant(body.statementDate()));
        r.setStatementBalance(Money.round2(body.statementBalance()));
        r.setBookBalance(Money.nz(account.getBalance()));
        r.setNotes(body.notes() == null || body.notes().isEmpty() ? null : body.notes());
        r.setCreatedBy(userId);
        em.persist(r);
        em.flush();
        ObjectNode o = CashBankJson.scalars(r);
        o.set("account", CashBankJson.pick(account, "id", "name", "accountNumber", "balance"));
        return o;
    }

    /** GET /:id — sesi + item statement + ledger akun yang belum dicocokkan (200 terlama). */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<BankReconciliation> found = em.createQuery(
                        "select r from BankReconciliation r left join fetch r.account where r.id = :id", BankReconciliation.class)
                .setParameter("id", id).getResultList();
        if (found.isEmpty()) {
            throw ErpException.notFound("Rekonsiliasi tidak ditemukan.");
        }
        BankReconciliation r = found.get(0);
        List<BankReconciliationItem> items = items(r.getId());

        List<String> matchedLedgerIds = items.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsMatched()) && i.getLedgerEntryId() != null)
                .map(BankReconciliationItem::getLedgerEntryId)
                .toList();
        TypedQuery<AccountingLedgerEntry> lq = em.createQuery("select e from AccountingLedgerEntry e "
                        + "where e.accountId = :acc and e.isCancelled = false"
                        + (matchedLedgerIds.isEmpty() ? "" : " and e.id not in :matched")
                        + " order by e.date asc, e.createdAt asc, e.id asc", AccountingLedgerEntry.class)
                .setParameter("acc", r.getAccountId());
        if (!matchedLedgerIds.isEmpty()) {
            lq.setParameter("matched", matchedLedgerIds);
        }
        List<AccountingLedgerEntry> unmatched = lq.setMaxResults(200).getResultList();

        ObjectNode o = CashBankJson.scalars(r);
        o.set("account", CashBankJson.pick(r.getAccount(), "id", "name", "accountNumber", "balance"));
        o.set("items", CashBankJson.scalarsList(items));
        o.set("unmatchedLedgerEntries", withJournalLabels(unmatched));
        return o;
    }

    private List<BankReconciliationItem> items(String reconciliationId) {
        return em.createQuery("select i from BankReconciliationItem i where i.reconciliationId = :id "
                        + "order by i.createdAt asc, i.id asc", BankReconciliationItem.class)
                .setParameter("id", reconciliationId).getResultList();
    }

    /**
     * Ledger yang diposting langsung dari jurnal bisa tanpa deskripsi sendiri (mutasi stok lama tidak
     * menulisnya). Pakai narasi jurnal dan tampilkan nomor jurnal agar layar pencocokan tidak pernah
     * menampilkan tipe referensi mentah — port {@code withJournalLabels}.
     */
    private ArrayNode withJournalLabels(List<AccountingLedgerEntry> entries) {
        Set<String> journalIds = new LinkedHashSet<>();
        entries.forEach(e -> {
            if ("JournalEntry".equals(e.getReferenceType())) {
                journalIds.add(e.getReferenceId());
            }
        });
        Map<String, String[]> journals = new HashMap<>();
        if (!journalIds.isEmpty()) {
            em.createQuery("select j.id, j.entryNumber, j.narration from JournalEntry j where j.id in :ids", Object[].class)
                    .setParameter("ids", journalIds).getResultList()
                    .forEach(j -> journals.put((String) j[0], new String[]{(String) j[1], (String) j[2]}));
        }
        ArrayNode out = ErpJson.arrayNode();
        for (AccountingLedgerEntry e : entries) {
            String[] je = "JournalEntry".equals(e.getReferenceType()) ? journals.get(e.getReferenceId()) : null;
            ObjectNode o = CashBankJson.scalars(e);
            String description = e.getDescription() != null && !e.getDescription().isEmpty()
                    ? e.getDescription()
                    : (je != null && je[1] != null && !je[1].isEmpty() ? je[1] : null);
            o.put("description", description);
            o.put("referenceNumber", je == null ? null : je[0]);
            out.add(o);
        }
        return out;
    }

    /** POST /:id/items — tambah baris rekening koran (hanya sesi Draft). */
    @ErpTx
    public ArrayNode addItems(String id, List<StatementItemInput> items) {
        BankReconciliation r = em.find(BankReconciliation.class, id);
        if (r == null) {
            throw ErpException.business("Rekonsiliasi tidak ditemukan.");
        }
        if (!DRAFT.equals(r.getStatus())) {
            throw ErpException.business("Rekonsiliasi sudah selesai.");
        }
        List<BankReconciliationItem> created = new ArrayList<>();
        for (StatementItemInput in : items) {
            BankReconciliationItem item = new BankReconciliationItem();
            item.setReconciliationId(r.getId());
            item.setStatementAmount(Money.round2(in.statementAmount()));
            item.setStatementDesc(in.statementDesc() == null || in.statementDesc().isEmpty() ? null : in.statementDesc());
            item.setStatementDate(in.statementDate() == null || in.statementDate().isEmpty()
                    ? null : ErpDates.toInstant(in.statementDate()));
            em.persist(item);
            created.add(item);
        }
        em.flush();
        return CashBankJson.scalarsList(created);
    }

    /** PATCH /:id/match — cocokkan satu baris statement ke ledger akun yang sama. */
    @ErpTx
    public ObjectNode match(String id, MatchItem body) {
        BankReconciliation r = draft(id);
        BankReconciliationItem item = item(r.getId(), body.itemId());
        if (Boolean.TRUE.equals(item.getIsMatched())) {
            throw ErpException.business("Item sudah dicocokkan.");
        }
        Long ok = em.createQuery("select count(e) from AccountingLedgerEntry e where e.id = :id "
                        + "and e.accountId = :acc and e.isCancelled = false", Long.class)
                .setParameter("id", body.ledgerEntryId())
                .setParameter("acc", r.getAccountId())
                .getSingleResult();
        if (ok == 0) {
            throw ErpException.business("Ledger entry tidak ditemukan atau bukan milik akun ini.");
        }
        item.setLedgerEntryId(body.ledgerEntryId());
        item.setIsMatched(true);
        em.flush();
        return CashBankJson.scalars(item);
    }

    /** PATCH /:id/unmatch — lepas pencocokan satu baris. */
    @ErpTx
    public ObjectNode unmatch(String id, String itemId) {
        BankReconciliation r = draft(id);
        BankReconciliationItem item = item(r.getId(), itemId);
        item.setLedgerEntryId(null);
        item.setIsMatched(false);
        em.flush();
        return CashBankJson.scalars(item);
    }

    /** POST /:id/complete — tandai Completed (wajib ada item statement). */
    @ErpTx
    public ObjectNode complete(String id) {
        BankReconciliation r = draft(id);
        List<BankReconciliationItem> items = items(r.getId());
        if (items.isEmpty()) {
            throw ErpException.business("Tidak ada item statement. Tambahkan item terlebih dahulu.");
        }
        r.setStatus(COMPLETED);
        em.flush();
        Account account = em.find(Account.class, r.getAccountId());
        ObjectNode o = CashBankJson.scalars(r);
        o.set("account", CashBankJson.pick(account, "id", "name", "accountNumber"));
        o.set("items", CashBankJson.scalarsList(items));
        return o;
    }

    /** DELETE /:id — hanya Draft; item ikut terhapus (FK ON DELETE CASCADE). */
    @ErpTx
    public Map<String, Object> delete(String id) {
        BankReconciliation r = em.find(BankReconciliation.class, id);
        if (r == null) {
            throw ErpException.business("Rekonsiliasi tidak ditemukan.");
        }
        if (!DRAFT.equals(r.getStatus())) {
            throw ErpException.business("Hanya rekonsiliasi berstatus Draft yang dapat dihapus.");
        }
        em.remove(r);
        em.flush();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Rekonsiliasi dihapus.");
        return out;
    }

    private BankReconciliation draft(String id) {
        BankReconciliation r = em.find(BankReconciliation.class, id);
        if (r == null) {
            throw ErpException.business("Rekonsiliasi tidak ditemukan.");
        }
        if (!DRAFT.equals(r.getStatus())) {
            throw ErpException.business("Rekonsiliasi sudah selesai.");
        }
        return r;
    }

    private BankReconciliationItem item(String reconciliationId, String itemId) {
        List<BankReconciliationItem> rows = em.createQuery("select i from BankReconciliationItem i "
                        + "where i.id = :id and i.reconciliationId = :rid", BankReconciliationItem.class)
                .setParameter("id", itemId).setParameter("rid", reconciliationId).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.business("Item tidak ditemukan.");
        }
        return rows.get(0);
    }
}
