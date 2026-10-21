package id.enterpriseapps.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.accounting.SystemAccountRole;
import id.enterpriseapps.erp.entity.AccountType;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.RootType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
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
 * Laporan keuangan dari buku besar — port {@code routes/reports.ts}: neraca saldo, laba rugi,
 * neraca, arus kas, aging, detail buku besar, buku besar lengkap, dan ekspor Excel.
 *
 * <p>Sumber kebenaran = {@code accounting_ledger_entries} aktif ({@code is_cancelled = false});
 * {@code accounts.balance} tidak dipakai di sini. Arah saldo per root type mengikuti sumber.
 */
@Service
@RequiredArgsConstructor
public class ErpFinancialReportService {

    private static final List<RootType> PROFIT_AND_LOSS = List.of(RootType.REVENUE, RootType.EXPENSE);
    private static final List<RootType> BALANCE_SHEET = List.of(RootType.ASSET, RootType.LIABILITY, RootType.EQUITY);
    private static final List<InvoiceStatus> AGING_STATUSES =
            List.of(InvoiceStatus.Submitted, InvoiceStatus.PartiallyPaid, InvoiceStatus.Overdue);
    private static final int CASH_FLOW_TAKE = 50_000;
    private static final int REF_CHUNK = 1_000;

    private final ErpSystemAccountService systemAccounts;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Rentang tanggal inklusif [gte, lte]; elemen null bila parameternya kosong. */
    record Range(Instant gte, Instant lte) {
    }

    record Export(String fileName, byte[] content) {
    }

    // ─────────────────────────── parameter tanggal ───────────────────────────

    /** {@code parseQueryDate(value, endOfDay)}: null bila kosong/tak valid; endOfDay = 23:59:59.999 lokal. */
    static Instant queryDate(String raw, boolean endOfDay) {
        Instant d = ReportDates.parse(raw);
        if (d == null) {
            return null;
        }
        return endOfDay ? ReportDates.endOfLocalDay(d) : d;
    }

    /** startDate/endDate dengan validasi 400 (pesan disalin dari sumber). */
    static Range validatedRange(Map<String, String> query) {
        String rawStart = query.get("startDate");
        String rawEnd = query.get("endDate");
        Instant start = queryDate(rawStart, false);
        Instant end = queryDate(rawEnd, true);
        if (rawStart != null && !rawStart.isEmpty() && start == null) {
            throw ErpException.business("Parameter startDate tidak valid.");
        }
        if (rawEnd != null && !rawEnd.isEmpty() && end == null) {
            throw ErpException.business("Parameter endDate tidak valid.");
        }
        return new Range(start, end);
    }

    private static String dateCond(String alias, Range r, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        if (r.gte() != null) {
            sb.append(" and ").append(alias).append(".date >= :dGte");
            params.put("dGte", r.gte());
        }
        if (r.lte() != null) {
            sb.append(" and ").append(alias).append(".date <= :dLte");
            params.put("dLte", r.lte());
        }
        return sb.toString();
    }

    private List<Object[]> list(String jpql, Map<String, Object> params) {
        TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        params.forEach(q::setParameter);
        return q.getResultList();
    }

    /** accountId → [Σdebit, Σkredit]. */
    private static Map<String, BigDecimal[]> sumMap(List<Object[]> rows) {
        Map<String, BigDecimal[]> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put((String) r[0], new BigDecimal[]{Money.nz((BigDecimal) r[1]), Money.nz((BigDecimal) r[2])});
        }
        return m;
    }

    private List<AccountTree.Node> nodes(List<RootType> rootTypes) {
        List<AccountTree.Node> out = new ArrayList<>();
        for (Object[] r : em.createQuery("select a.id, a.name, a.accountNumber, a.isGroup, a.parentId, a.rootType "
                        + "from Account a where a.rootType in :rt", Object[].class)
                .setParameter("rt", rootTypes).getResultList()) {
            out.add(new AccountTree.Node((String) r[0], (String) r[1], (String) r[2], Boolean.TRUE.equals(r[3]),
                    (String) r[4], (RootType) r[5]));
        }
        return ReportAccounts.sorted(out, AccountTree.Node::accountNumber);
    }

    // ─────────────────────────────── neraca saldo ───────────────────────────────

    /** GET /reports/trial-balance — semua akun (termasuk grup) yang punya posting dalam periode. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> trialBalance(Map<String, String> query) {
        Range r = validatedRange(query);
        Map<String, Object> p = new HashMap<>();
        List<Object[]> sums = list("select e.accountId, sum(e.debit), sum(e.credit) from AccountingLedgerEntry e "
                + "where e.isCancelled = false" + dateCond("e", r, p) + " group by e.accountId", p);
        List<TrialBalance.Account> accounts = new ArrayList<>();
        for (Object[] a : em.createQuery("select a.id, a.name, a.accountNumber, a.accountType, a.rootType from Account a",
                Object[].class).getResultList()) {
            accounts.add(new TrialBalance.Account((String) a[0], (String) a[1], (String) a[2], (AccountType) a[3],
                    (RootType) a[4]));
        }
        List<TrialBalance.Sums> s = new ArrayList<>();
        for (Object[] row : sums) {
            s.add(new TrialBalance.Sums((String) row[0], (BigDecimal) row[1], (BigDecimal) row[2]));
        }
        return TrialBalance.build(accounts, s);
    }

    // ─────────────────────────────── laba rugi ───────────────────────────────

    /** GET /reports/profit-loss — hierarki pendapatan & beban per periode + laba bersih. */
    @ErpTx(readOnly = true)
    public Map<String, Object> profitLoss(Map<String, String> query) {
        Range r = validatedRange(query);
        Map<String, Object> p = new HashMap<>();
        p.put("rt", PROFIT_AND_LOSS);
        Map<String, BigDecimal[]> sums = sumMap(list("select e.accountId, sum(e.debit), sum(e.credit) "
                + "from AccountingLedgerEntry e join e.account a where e.isCancelled = false and a.rootType in :rt"
                + dateCond("e", r, p) + " group by e.accountId", p));
        // REVENUE = kredit − debit, selainnya (EXPENSE) = debit − kredit.
        AccountTree tree = new AccountTree(nodes(PROFIT_AND_LOSS), sums, rt -> rt != RootType.REVENUE);
        List<Map<String, Object>> revenue = plRoots(tree, RootType.REVENUE);
        List<Map<String, Object>> expense = plRoots(tree, RootType.EXPENSE);
        BigDecimal totalRevenue = AccountTree.total(revenue);
        BigDecimal totalExpense = AccountTree.total(expense);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("revenue", revenue);
        out.put("expense", expense);
        out.put("totalRevenue", totalRevenue);
        out.put("totalExpense", totalExpense);
        out.put("netProfit", totalRevenue.subtract(totalExpense));
        return out;
    }

    /** {@code buildRootItems(rootType)}: akun puncak root type itu beserta anak-anaknya. */
    private static List<Map<String, Object>> plRoots(AccountTree tree, RootType rootType) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AccountTree.Node root : tree.children(null)) {
            if (root.rootType() != rootType) {
                continue;
            }
            List<Map<String, Object>> kids = root.isGroup() ? tree.hierarchy(root.id(), null) : new ArrayList<>();
            Map<String, Object> m = tree.item(root, kids);
            if (((BigDecimal) m.get("balance")).signum() != 0 || root.isGroup()) {
                out.add(m);
            }
        }
        return out;
    }

    // ─────────────────────────────── neraca ───────────────────────────────

    /**
     * GET /reports/balance-sheet — posisi per akhir {@code date} (default sekarang). Laba tahun
     * berjalan dihitung dari ledger pendapatan/beban s.d. tanggal itu dan menggantikan akun
     * CURRENT_PROFIT asli (agar tidak terhitung ganda).
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> balanceSheet(Map<String, String> query) {
        String raw = query.get("date");
        Instant parsed = queryDate(raw, true);
        if (raw != null && !raw.isEmpty() && parsed == null) {
            throw ErpException.business("Parameter date tidak valid.");
        }
        Instant target = parsed != null ? parsed : Instant.now();

        Map<String, BigDecimal[]> sums = sumMap(em.createQuery("select e.accountId, sum(e.debit), sum(e.credit) "
                        + "from AccountingLedgerEntry e where e.date <= :t and e.isCancelled = false group by e.accountId",
                Object[].class).setParameter("t", target).getResultList());
        BigDecimal[] rev = rootTypeSums(RootType.REVENUE, target);
        BigDecimal[] exp = rootTypeSums(RootType.EXPENSE, target);

        AccountTree tree = new AccountTree(nodes(BALANCE_SHEET), sums, rt -> rt == RootType.ASSET);
        List<Map<String, Object>> assets = tree.hierarchy(null, RootType.ASSET);
        List<Map<String, Object>> liabilities = tree.hierarchy(null, RootType.LIABILITY);
        List<Map<String, Object>> equity = tree.hierarchy(null, RootType.EQUITY);

        BigDecimal currentProfit = rev[1].subtract(rev[0]).subtract(exp[0].subtract(exp[1]));
        ErpSystemAccountService.MappedAccount cp = systemAccounts.getAccount(SystemAccountRole.CURRENT_PROFIT);
        List<Map<String, Object>> filteredEquity = withoutAccount(equity, cp.accountNumber());
        Map<String, Object> synthetic = new LinkedHashMap<>();
        synthetic.put("id", "current-profit");
        synthetic.put("name", "Laba Tahun Berjalan");
        synthetic.put("accountNumber", cp.accountNumber());
        synthetic.put("isGroup", false);
        synthetic.put("balance", currentProfit);
        synthetic.put("children", List.of());
        filteredEquity.add(synthetic);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("assets", assets);
        out.put("liabilities", liabilities);
        out.put("equity", filteredEquity);
        out.put("totalAssets", AccountTree.total(assets));
        out.put("totalLiabilities", AccountTree.total(liabilities));
        out.put("totalEquity", AccountTree.total(filteredEquity));
        return out;
    }

    /** [Σdebit, Σkredit] ledger aktif s.d. tanggal untuk satu root type. */
    private BigDecimal[] rootTypeSums(RootType rootType, Instant target) {
        Object[] r = em.createQuery("select coalesce(sum(e.debit), 0), coalesce(sum(e.credit), 0) "
                        + "from AccountingLedgerEntry e join e.account a "
                        + "where e.date <= :t and e.isCancelled = false and a.rootType = :rt", Object[].class)
                .setParameter("t", target).setParameter("rt", rootType).getSingleResult();
        return new BigDecimal[]{ReportRows.dec(r[0]), ReportRows.dec(r[1])};
    }

    /** {@code filterOutCurrentProfit}: buang akun bernomor itu di semua tingkat (saldo induk tidak dihitung ulang). */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> withoutAccount(List<Map<String, Object>> items, String accountNumber) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> copy = new LinkedHashMap<>(item);
            Object children = item.get("children");
            copy.put("children", children instanceof List<?> l
                    ? withoutAccount((List<Map<String, Object>>) l, accountNumber) : new ArrayList<>());
            if (!accountNumber.equals(item.get("accountNumber"))) {
                out.add(copy);
            }
        }
        return out;
    }

    // ─────────────────────────────── arus kas ───────────────────────────────

    /**
     * GET /reports/cash-flow (metode langsung): lawan transaksi setiap mutasi kas/bank diklasifikasi
     * operasi (pendapatan/beban/piutang/hutang/lainnya), investasi (1.6/1.7), pendanaan (ekuitas/2.3).
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> cashFlow(Map<String, String> query) {
        Range r = validatedRange(query);
        Instant now = Instant.now();
        Instant start = r.gte() != null ? r.gte() : ReportDates.startOfLocalYear(now);
        Instant end = r.lte() != null ? r.lte() : now;

        List<String> cashIds = systemAccounts.getAccountIds(SystemAccountRole.CASH);
        List<Object[]> offsetting = new ArrayList<>();
        if (!cashIds.isEmpty()) {
            List<String> refs = em.createQuery("select e.referenceId from AccountingLedgerEntry e "
                            + "where e.accountId in :cash and e.date >= :s and e.date <= :e and e.isCancelled = false",
                            String.class)
                    .setParameter("cash", cashIds).setParameter("s", start).setParameter("e", end)
                    .setMaxResults(CASH_FLOW_TAKE).getResultList();
            Set<String> refIds = new LinkedHashSet<>();
            for (String ref : refs) {
                if (ref != null && !ref.isEmpty()) {
                    refIds.add(ref);
                }
            }
            // Dipecah per 1000 id agar tidak melewati batas parameter bind PostgreSQL.
            List<String> refList = new ArrayList<>(refIds);
            for (int i = 0; i < refList.size() && offsetting.size() < CASH_FLOW_TAKE; i += REF_CHUNK) {
                List<String> chunk = refList.subList(i, Math.min(i + REF_CHUNK, refList.size()));
                offsetting.addAll(em.createQuery("select a.name, a.rootType, a.accountNumber, e.debit, e.credit "
                                + "from AccountingLedgerEntry e join e.account a where e.referenceId in :refs "
                                + "and e.accountId not in :cash and e.isCancelled = false", Object[].class)
                        .setParameter("refs", chunk).setParameter("cash", cashIds)
                        .setMaxResults(CASH_FLOW_TAKE - offsetting.size()).getResultList());
            }
        }

        String ar = systemAccounts.getAccount(SystemAccountRole.AR).accountNumber();
        String ap = systemAccounts.getAccount(SystemAccountRole.AP).accountNumber();
        BigDecimal operating = BigDecimal.ZERO;
        BigDecimal investing = BigDecimal.ZERO;
        BigDecimal financing = BigDecimal.ZERO;
        List<Map<String, Object>> operatingDetails = new ArrayList<>();
        List<Map<String, Object>> investingDetails = new ArrayList<>();
        List<Map<String, Object>> financingDetails = new ArrayList<>();
        for (Object[] e : offsetting) {
            String name = (String) e[0];
            RootType rootType = (RootType) e[1];
            String accNum = (String) e[2];
            BigDecimal amount = Money.nz((BigDecimal) e[4]).subtract(Money.nz((BigDecimal) e[3]));
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", name);
            detail.put("amount", amount);
            if (rootType == RootType.REVENUE || rootType == RootType.EXPENSE || accNum.startsWith(ar) || accNum.startsWith(ap)) {
                operating = operating.add(amount);
                operatingDetails.add(detail);
            } else if (accNum.startsWith("1.6") || accNum.startsWith("1.7")) {
                investing = investing.add(amount);
                investingDetails.add(detail);
            } else if (rootType == RootType.EQUITY || accNum.startsWith("2.3")) {
                financing = financing.add(amount);
                financingDetails.add(detail);
            } else {
                operating = operating.add(amount);
                operatingDetails.add(detail);
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("operating", operatingDetails);
        details.put("investing", investingDetails);
        details.put("financing", financingDetails);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("operating", operating);
        out.put("investing", investing);
        out.put("financing", financing);
        out.put("netChange", operating.add(investing).add(financing));
        out.put("details", details);
        return out;
    }

    // ─────────────────────────────── aging ───────────────────────────────

    /**
     * GET /reports/aging?type=Customer|Supplier — outstanding faktur terbuka per mitra dalam
     * ember umur jatuh tempo (hari = ceil(sekarang − jatuh tempo); tanpa jatuh tempo pakai tanggal faktur).
     */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> aging(Map<String, String> query) {
        boolean supplier = "Supplier".equals(query.get("type"));
        long now = Instant.now().toEpochMilli();
        String jpql = supplier
                ? "select p.name, i.dueDate, i.date, i.outstanding from PurchaseInvoice i join i.supplier p "
                + "where i.status in :st order by i.date asc"
                : "select p.name, i.dueDate, i.date, i.outstanding from SalesInvoice i join i.customer p "
                + "where i.status in :st order by i.date asc";
        List<Object[]> rows = em.createQuery(jpql, Object[].class).setParameter("st", AGING_STATUSES)
                .setMaxResults(10_000).getResultList();
        // [current, 1-30, 31-60, 61-90, 91+, total] per nama mitra (urutan kemunculan pertama).
        Map<String, BigDecimal[]> aging = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String name = (String) r[0];
            Instant due = r[1] != null ? (Instant) r[1] : (Instant) r[2];
            long diffDays = ReportDates.ceilDays(now - due.toEpochMilli());
            BigDecimal amt = Money.nz((BigDecimal) r[3]);
            BigDecimal[] b = aging.computeIfAbsent(name, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            b[5] = b[5].add(amt);
            int idx = diffDays <= 0 ? 0 : diffDays <= 30 ? 1 : diffDays <= 60 ? 2 : diffDays <= 90 ? 3 : 4;
            b[idx] = b[idx].add(amt);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        aging.forEach((name, b) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("1", b[1]);
            m.put("31", b[2]);
            m.put("61", b[3]);
            m.put("91", b[4]);
            m.put("current", b[0]);
            m.put("total", b[5]);
            out.add(m);
        });
        return out;
    }

    // ─────────────────────────────── buku besar ───────────────────────────────

    /** GET /reports/ledger-detail?accountId=… — mutasi satu akun + saldo berjalan (arah saldo normal akun). */
    @ErpTx(readOnly = true)
    public Map<String, Object> ledgerDetail(Map<String, String> query) {
        String accountId = query.get("accountId");
        if (accountId == null || accountId.isEmpty()) {
            throw ErpException.business("Parameter accountId wajib diisi.");
        }
        Range r = validatedRange(query);
        List<Object[]> acc = em.createQuery("select a.id, a.name, a.accountNumber, a.rootType from Account a where a.id = :id",
                Object[].class).setParameter("id", accountId).getResultList();
        if (acc.isEmpty()) {
            throw ErpException.notFound("Akun tidak ditemukan.");
        }
        Object[] account = acc.get(0);
        RootType rootType = (RootType) account[3];
        boolean debitNormal = rootType == RootType.ASSET || rootType == RootType.EXPENSE;

        Map<String, Object> p = new HashMap<>();
        p.put("id", accountId);
        TypedQuery<Object[]> q = em.createQuery("select e.id, e.date, e.debit, e.credit, e.description, e.referenceType, "
                + "e.referenceId from AccountingLedgerEntry e where e.accountId = :id and e.isCancelled = false"
                + dateCond("e", r, p) + " order by e.date asc, e.createdAt asc, e.id asc", Object[].class);
        p.forEach(q::setParameter);
        BigDecimal running = BigDecimal.ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] e : q.setMaxResults(10_000).getResultList()) {
            BigDecimal debit = Money.nz((BigDecimal) e[2]);
            BigDecimal credit = Money.nz((BigDecimal) e[3]);
            running = running.add(debitNormal ? debit.subtract(credit) : credit.subtract(debit));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e[0]);
            m.put("date", e[1]);
            m.put("debit", debit);
            m.put("credit", credit);
            m.put("description", e[4]);
            m.put("referenceType", e[5]);
            m.put("referenceId", e[6]);
            m.put("runningBalance", running);
            rows.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accountId", account[0]);
        out.put("accountName", account[1]);
        out.put("accountNumber", account[2]);
        out.put("entries", rows);
        return out;
    }

    /**
     * GET /reports/ledger-book — buku besar semua akun detail aktif: saldo awal (sebelum startDate),
     * mutasi periode dengan saldo berjalan, total debit/kredit dan saldo akhir.
     */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> ledgerBook(Map<String, String> query) {
        Instant start = queryDate(query.get("startDate"), false);
        Instant end = queryDate(query.get("endDate"), true);

        record Acc(String id, String name, String accountNumber, RootType rootType) {
            boolean debitNormal() {
                return rootType == RootType.ASSET || rootType == RootType.EXPENSE;
            }
        }
        List<Acc> accounts = new ArrayList<>();
        for (Object[] a : em.createQuery("select a.id, a.name, a.accountNumber, a.rootType from Account a "
                + "where a.isActive = true and a.isGroup = false order by a.accountNumber asc", Object[].class).getResultList()) {
            accounts.add(new Acc((String) a[0], (String) a[1], (String) a[2], (RootType) a[3]));
        }
        accounts = ReportAccounts.sorted(accounts, Acc::accountNumber);
        Map<String, Acc> byId = new HashMap<>();
        accounts.forEach(a -> byId.put(a.id(), a));

        Map<String, BigDecimal> opening = new HashMap<>();
        if (start != null) {
            for (Object[] e : em.createQuery("select e.accountId, sum(e.debit), sum(e.credit) from AccountingLedgerEntry e "
                            + "where e.isCancelled = false and e.date < :s group by e.accountId", Object[].class)
                    .setParameter("s", start).getResultList()) {
                Acc a = byId.get((String) e[0]);
                if (a == null) {
                    continue;
                }
                BigDecimal dr = Money.nz((BigDecimal) e[1]);
                BigDecimal cr = Money.nz((BigDecimal) e[2]);
                opening.put(a.id(), a.debitNormal() ? dr.subtract(cr) : cr.subtract(dr));
            }
        }

        Map<String, Object> p = new HashMap<>();
        TypedQuery<Object[]> q = em.createQuery("select e.id, e.accountId, e.date, e.debit, e.credit, e.description, "
                + "e.referenceType from AccountingLedgerEntry e where e.isCancelled = false"
                + dateCond("e", new Range(start, end), p) + " order by e.date asc, e.createdAt asc", Object[].class);
        p.forEach(q::setParameter);
        Map<String, List<Object[]>> byAccount = new HashMap<>();
        for (Object[] e : q.setMaxResults(100_000).getResultList()) {
            byAccount.computeIfAbsent((String) e[1], k -> new ArrayList<>()).add(e);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Acc account : accounts) {
            BigDecimal openingBalance = opening.getOrDefault(account.id(), BigDecimal.ZERO);
            if (!byAccount.containsKey(account.id()) && openingBalance.signum() == 0) {
                continue;
            }
            BigDecimal running = openingBalance;
            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Object[] e : byAccount.getOrDefault(account.id(), List.of())) {
                BigDecimal debit = Money.nz((BigDecimal) e[3]);
                BigDecimal credit = Money.nz((BigDecimal) e[4]);
                running = running.add(account.debitNormal() ? debit.subtract(credit) : credit.subtract(debit));
                totalDebit = totalDebit.add(debit);
                totalCredit = totalCredit.add(credit);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e[0]);
                m.put("date", e[2]);
                m.put("debit", debit);
                m.put("credit", credit);
                m.put("description", e[5]);
                m.put("referenceType", e[6]);
                m.put("runningBalance", running);
                entries.add(m);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accountId", account.id());
            row.put("accountNumber", account.accountNumber());
            row.put("accountName", account.name());
            row.put("rootType", account.rootType());
            row.put("openingBalance", openingBalance);
            row.put("entries", entries);
            row.put("totalDebit", totalDebit);
            row.put("totalCredit", totalCredit);
            row.put("closingBalance", running);
            result.add(row);
        }
        return result;
    }

    // ─────────────────────────────── ekspor ───────────────────────────────

    /** POST /reports/export — body {@code { filename, data: object[] }} → berkas .xlsx. */
    public Export export(JsonNode body) {
        JsonNode data = body == null ? null : body.get("data");
        if (data == null || !data.isArray()) {
            throw ErpException.business("Data tidak valid untuk ekspor.");
        }
        if (data.size() > 50_000) {
            throw ErpException.business("Data terlalu banyak untuk ekspor (maks. 50.000 baris).");
        }
        byte[] content = ReportExcel.write(data);
        return new Export(ReportExcel.safeName(body.get("filename")) + ".xlsx", content);
    }
}
