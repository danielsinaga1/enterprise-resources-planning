package id.enterpriseapps.erp.report;

import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.accounting.SystemAccountRole;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.RootType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Widget finansial dashboard — port {@code routes/dashboard.ts} (metrik, grafik, invoice jatuh
 * tempo, beban, arus kas harian, rasio, profit bulanan, aging, posisi kas, top pelanggan/vendor).
 * Gerbang modul {@code reports} dijalankan ErpRouteGuard.
 */
@Service
@RequiredArgsConstructor
public class ErpFinanceDashboardService {

    private static final List<InvoiceStatus> NOT_ACTIVE = List.of(InvoiceStatus.Draft, InvoiceStatus.Cancelled);
    private static final List<InvoiceStatus> NOT_OPEN =
            List.of(InvoiceStatus.Draft, InvoiceStatus.Cancelled, InvoiceStatus.Paid);

    private final ErpSystemAccountService systemAccounts;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    record Period(Instant start, Instant end) {
    }

    /**
     * {@code parseRange}: startDate = {@code new Date(s)}, endDate = {@code new Date(e + 'T23:59:59.999')}
     * (waktu lokal); kosong atau tak valid → default.
     */
    static Period parseRange(Map<String, String> query, Instant defaultStart, Instant defaultEnd) {
        String s = query.get("startDate");
        String e = query.get("endDate");
        Instant start = s == null || s.isEmpty() ? null : ReportDates.parse(s);
        Instant end = e == null || e.isEmpty() ? null : ReportDates.parse(e + "T23:59:59.999");
        return new Period(start != null ? start : defaultStart, end != null ? end : defaultEnd);
    }

    private BigDecimal scalar(String jpql, Map<String, Object> params) {
        var q = em.createQuery(jpql, BigDecimal.class);
        params.forEach(q::setParameter);
        return Money.nz(q.getSingleResult());
    }

    /** [Σkredit, Σdebit] ledger aktif satu root type dalam periode. */
    private BigDecimal[] ledgerSums(RootType rootType, Period p) {
        Object[] r = em.createQuery("select coalesce(sum(e.credit), 0), coalesce(sum(e.debit), 0) "
                        + "from AccountingLedgerEntry e join e.account a where e.date >= :s and e.date <= :e "
                        + "and e.isCancelled = false and a.rootType = :rt", Object[].class)
                .setParameter("s", p.start()).setParameter("e", p.end()).setParameter("rt", rootType).getSingleResult();
        return new BigDecimal[]{ReportRows.dec(r[0]), ReportRows.dec(r[1])};
    }

    // ─────────────────────────────── metrik ───────────────────────────────

    /** GET /dashboard/metrics — kas, piutang, hutang, uang muka vendor, nilai persediaan, laba periode. */
    @ErpTx(readOnly = true)
    public Map<String, Object> metrics(Map<String, String> query) {
        List<String> cashIds = systemAccounts.getAccountIds(SystemAccountRole.CASH);
        BigDecimal cashBalance = cashIds.isEmpty() ? BigDecimal.ZERO
                : scalar("select sum(a.balance) from Account a where a.id in :ids and a.isGroup = false and a.isActive = true",
                Map.of("ids", cashIds));
        String arId = systemAccounts.getAccount(SystemAccountRole.AR).id();
        BigDecimal inventoryValue = scalar("select sum(a.balance) from Account a where a.accountNumber like '1.4.%' "
                + "and a.isGroup = false and a.isActive = true", Map.of());
        BigDecimal totalLiabilities = scalar("select sum(a.balance) from Account a where a.rootType = :rt "
                + "and a.isGroup = false and a.isActive = true", Map.of("rt", RootType.LIABILITY));
        // Uang muka vendor = Σ saldo per mitra (sumber kebenaran, bukan akun GL yang bisa drift).
        BigDecimal vendorDeposit = scalar("select sum(p.depositBalance) from Party p where p.isActive = true", Map.of());
        List<BigDecimal> ar = em.createQuery("select a.balance from Account a where a.id = :id", BigDecimal.class)
                .setParameter("id", arId).getResultList();

        Instant now = Instant.now();
        Period p = parseRange(query, ReportDates.startOfLocalMonth(now, 0), now);
        BigDecimal[] rev = ledgerSums(RootType.REVENUE, p);
        BigDecimal[] exp = ledgerSums(RootType.EXPENSE, p);
        BigDecimal netRevenue = rev[0].subtract(rev[1]);
        BigDecimal netExpense = exp[1].subtract(exp[0]);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cashBalance", cashBalance);
        out.put("accountsReceivable", Money.max(BigDecimal.ZERO, ar.isEmpty() ? BigDecimal.ZERO : Money.nz(ar.get(0))));
        out.put("accountsPayable", Money.max(BigDecimal.ZERO, totalLiabilities));
        out.put("vendorDeposit", Money.max(BigDecimal.ZERO, vendorDeposit));
        // Saldo riil (boleh negatif): negatif = anomali pencatatan stok yang harus terlihat pemilik.
        out.put("inventoryValue", inventoryValue);
        out.put("netProfit", netRevenue.subtract(netExpense));
        return out;
    }

    // ─────────────────────────── grafik bulanan ───────────────────────────

    /** Pendapatan & beban per bulan ("YYYY-MM" jam dinding UTC) dalam periode. */
    private Map<String, BigDecimal[]> monthlyRevenueExpense(Period p) {
        Query q = em.createNativeQuery("""
                SELECT to_char(date_trunc('month', ale.date), 'YYYY-MM'),
                       COALESCE(SUM(CASE WHEN a.root_type = 'REVENUE' THEN ale.credit - ale.debit ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN a.root_type = 'EXPENSE' THEN ale.debit - ale.credit ELSE 0 END), 0)
                  FROM accounting_ledger_entries ale
                  JOIN accounts a ON ale.account_id = a.id
                 WHERE ale.date >= CAST(:start AS timestamp)
                   AND ale.date <= CAST(:end AS timestamp)
                   AND ale.is_cancelled = false
                   AND a.root_type IN ('REVENUE', 'EXPENSE')
                 GROUP BY date_trunc('month', ale.date)
                 ORDER BY 1
                """)
                .setParameter("start", ReportDates.sqlTs(p.start()))
                .setParameter("end", ReportDates.sqlTs(p.end()));
        Map<String, BigDecimal[]> m = new HashMap<>();
        for (Object[] r : ReportRows.rows(q)) {
            m.put((String) r[0], new BigDecimal[]{Money.max(BigDecimal.ZERO, ReportRows.dec(r[1])),
                    Money.max(BigDecimal.ZERO, ReportRows.dec(r[2]))});
        }
        return m;
    }

    /** Bulan lokal dari awal periode s.d. bulan akhir periode: [kunci, label]. */
    private static List<String[]> months(Period p) {
        List<String[]> out = new ArrayList<>();
        LocalDate cursor = ReportDates.localDay(p.start()).withDayOfMonth(1);
        LocalDate endCap = ReportDates.localDay(p.end()).withDayOfMonth(1);
        while (!cursor.isAfter(endCap)) {
            out.add(new String[]{ReportDates.monthKey(cursor.getYear(), cursor.getMonthValue()),
                    ReportDates.monthLabel(cursor.getYear(), cursor.getMonthValue())});
            cursor = cursor.plusMonths(1);
        }
        return out;
    }

    /** GET /dashboard/charts — pendapatan vs beban per bulan (default 6 bulan). */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> charts(Map<String, String> query) {
        Instant now = Instant.now();
        Period p = parseRange(query, ReportDates.startOfLocalMonth(now, -5), now);
        Map<String, BigDecimal[]> rows = monthlyRevenueExpense(p);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String[] month : months(p)) {
            BigDecimal[] v = rows.get(month[0]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", month[1]);
            m.put("pendapatan", v == null ? BigDecimal.ZERO : v[0]);
            m.put("beban", v == null ? BigDecimal.ZERO : v[1]);
            out.add(m);
        }
        return out;
    }

    /** GET /dashboard/monthly-profit — tren laba per bulan (default 12 bulan). */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> monthlyProfit(Map<String, String> query) {
        Instant now = Instant.now();
        Period p = parseRange(query, ReportDates.startOfLocalMonth(now, -11), now);
        Map<String, BigDecimal[]> rows = monthlyRevenueExpense(p);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String[] month : months(p)) {
            BigDecimal[] v = rows.get(month[0]);
            BigDecimal revenue = v == null ? BigDecimal.ZERO : v[0];
            BigDecimal expense = v == null ? BigDecimal.ZERO : v[1];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", month[1]);
            m.put("revenue", revenue);
            m.put("expense", expense);
            m.put("profit", revenue.subtract(expense));
            out.add(m);
        }
        return out;
    }

    // ─────────────────────────── top pelanggan / vendor ───────────────────────────

    private List<Map<String, Object>> topParties(boolean purchase) {
        String entity = purchase ? "PurchaseInvoice" : "SalesInvoice";
        List<Object[]> results = em.createQuery("select i.partyId, sum(i.grandTotal) from " + entity + " i "
                        + "where i.status not in :st group by i.partyId order by sum(i.grandTotal) desc", Object[].class)
                .setParameter("st", NOT_ACTIVE).setMaxResults(5).getResultList();
        Map<String, String> names = new HashMap<>();
        if (!results.isEmpty()) {
            em.createQuery("select p.id, p.name from Party p where p.id in :ids", Object[].class)
                    .setParameter("ids", results.stream().map(r -> (String) r[0]).toList()).getResultList()
                    .forEach(r -> names.put((String) r[0], (String) r[1]));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int rank = 1;
        for (Object[] r : results) {
            String name = names.get((String) r[0]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", rank++);
            m.put("partyId", r[0]);
            m.put("partyName", name == null || name.isEmpty() ? "Unknown" : name);
            m.put("total", ReportRows.dec(r[1]));
            out.add(m);
        }
        return out;
    }

    /** GET /dashboard/top-customers — 5 pelanggan dengan total penjualan terbesar. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> topCustomers() {
        return topParties(false);
    }

    /** GET /dashboard/top-vendors — 5 vendor dengan total pembelian terbesar. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> topVendors() {
        return topParties(true);
    }

    // ─────────────────────────── invoice jatuh tempo ───────────────────────────

    private List<Object[]> openInvoices(boolean purchase, String dueCondition, Map<String, Object> params, int take) {
        String jpql = purchase
                ? "select i.invoiceNumber, p.name, i.outstanding, i.dueDate from PurchaseInvoice i join i.supplier p "
                : "select i.invoiceNumber, p.name, i.outstanding, i.dueDate from SalesInvoice i join i.customer p ";
        var q = em.createQuery(jpql + "where " + dueCondition + " and i.outstanding > 0 and i.status not in :st "
                + "order by i.dueDate asc", Object[].class);
        params.forEach(q::setParameter);
        return q.setParameter("st", NOT_OPEN).setMaxResults(take).getResultList();
    }

    private static Map<String, Object> dueRow(String type, Object[] r, String daysKey, long days) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("invoiceNumber", r[0]);
        m.put("partyName", r[1]);
        m.put("amount", Money.nz((BigDecimal) r[2]));
        m.put("dueDate", r[3]);
        m.put(daysKey, days);
        return m;
    }

    /** GET /dashboard/overdue — 10 invoice lewat jatuh tempo paling lama (penjualan + pembelian). */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> overdue() {
        Instant now = Instant.now();
        Map<String, Object> p = Map.of("now", now);
        List<Map<String, Object>> data = new ArrayList<>();
        for (Object[] r : openInvoices(false, "i.dueDate < :now", p, 10)) {
            data.add(dueRow("sales", r, "daysOverdue", ReportDates.ceilDays(now.toEpochMilli() - ((Instant) r[3]).toEpochMilli())));
        }
        for (Object[] r : openInvoices(true, "i.dueDate < :now", p, 10)) {
            data.add(dueRow("purchase", r, "daysOverdue", ReportDates.ceilDays(now.toEpochMilli() - ((Instant) r[3]).toEpochMilli())));
        }
        data.sort(Comparator.comparing((Map<String, Object> m) -> (Long) m.get("daysOverdue")).reversed());
        return new ArrayList<>(data.subList(0, Math.min(10, data.size())));
    }

    /** GET /dashboard/upcoming-due — invoice belum jatuh tempo yang jatuh tempo dalam 14 hari. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> upcomingDue() {
        Instant now = Instant.now();
        Map<String, Object> p = Map.of("now", now, "horizon", now.plus(Duration.ofDays(14)));
        String cond = "i.dueDate >= :now and i.dueDate <= :horizon";
        List<Map<String, Object>> data = new ArrayList<>();
        for (Object[] r : openInvoices(false, cond, p, 8)) {
            data.add(dueRow("sales", r, "daysLeft", daysLeft(now, (Instant) r[3])));
        }
        for (Object[] r : openInvoices(true, cond, p, 8)) {
            data.add(dueRow("purchase", r, "daysLeft", daysLeft(now, (Instant) r[3])));
        }
        data.sort(Comparator.comparing((Map<String, Object> m) -> (Long) m.get("daysLeft")));
        return new ArrayList<>(data.subList(0, Math.min(8, data.size())));
    }

    private static long daysLeft(Instant now, Instant due) {
        return Math.max(0, ReportDates.ceilDays(due.toEpochMilli() - now.toEpochMilli()));
    }

    // ─────────────────────────────── beban ───────────────────────────────

    /** GET /dashboard/expense-breakdown — beban per akun dalam periode (default bulan ini). */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> expenseBreakdown(Map<String, String> query) {
        Instant now = Instant.now();
        Period p = parseRange(query, ReportDates.startOfLocalMonth(now, 0), now);
        List<Object[]> rows = em.createQuery("select e.accountId, a.name, a.accountNumber, sum(e.debit), sum(e.credit) "
                        + "from AccountingLedgerEntry e join e.account a where e.date >= :s and e.date <= :e "
                        + "and e.isCancelled = false and a.rootType = :rt group by e.accountId, a.name, a.accountNumber",
                        Object[].class)
                .setParameter("s", p.start()).setParameter("e", p.end()).setParameter("rt", RootType.EXPENSE)
                .getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            BigDecimal amount = Money.max(BigDecimal.ZERO, ReportRows.dec(r[3]).subtract(ReportRows.dec(r[4])));
            if (amount.signum() <= 0) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("accountId", r[0]);
            m.put("accountName", r[1] == null || ((String) r[1]).isEmpty() ? "Unknown" : r[1]);
            m.put("accountNumber", r[2] == null ? "" : r[2]);
            m.put("amount", amount);
            out.add(m);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("amount")).reversed());
        return out;
    }

    // ─────────────────────────── arus kas harian ───────────────────────────

    /** GET /dashboard/cash-flow — kas masuk (Receive) / keluar (Pay) per hari (default bulan ini). */
    @ErpTx(readOnly = true)
    public Map<String, Object> cashFlow(Map<String, String> query) {
        Instant now = Instant.now();
        Period p = parseRange(query, ReportDates.startOfLocalMonth(now, 0), now);
        Query q = em.createNativeQuery("SELECT " + ReportRows.epochMs("date_trunc('day', p.date)") + ", "
                        + "COALESCE(SUM(CASE WHEN p.payment_type = 'Receive' THEN p.amount ELSE 0 END), 0), "
                        + "COALESCE(SUM(CASE WHEN p.payment_type = 'Pay' THEN p.amount ELSE 0 END), 0) "
                        + "FROM payments p WHERE p.date >= CAST(:start AS timestamp) AND p.date <= CAST(:end AS timestamp) "
                        + "AND p.status = 'Submitted' GROUP BY date_trunc('day', p.date) ORDER BY 1")
                .setParameter("start", ReportDates.sqlTs(p.start()))
                .setParameter("end", ReportDates.sqlTs(p.end()));
        List<Map<String, Object>> days = new ArrayList<>();
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        for (Object[] r : ReportRows.rows(q)) {
            BigDecimal in = ReportRows.dec(r[1]);
            BigDecimal outAmt = ReportRows.dec(r[2]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", ReportRows.instantMs(r[0]));
            m.put("cashIn", in);
            m.put("cashOut", outAmt);
            days.add(m);
            totalIn = totalIn.add(in);
            totalOut = totalOut.add(outAmt);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("totalIn", totalIn);
        out.put("totalOut", totalOut);
        out.put("net", totalIn.subtract(totalOut));
        return out;
    }

    // ─────────────────────────────── rasio ───────────────────────────────

    private BigDecimal balanceSum(RootType rootType, String numberPrefix) {
        Map<String, Object> params = new HashMap<>();
        params.put("rt", rootType);
        String extra = "";
        if (numberPrefix != null) {
            extra = " and a.accountNumber like :prefix";
            params.put("prefix", numberPrefix + "%");
        }
        return scalar("select sum(a.balance) from Account a where a.rootType = :rt and a.isGroup = false "
                + "and a.isActive = true" + extra, params);
    }

    /** GET /dashboard/financial-ratios — rasio lancar, DER, DAR dari saldo akun detail aktif. */
    @ErpTx(readOnly = true)
    public Map<String, Object> financialRatios() {
        BigDecimal ca = balanceSum(RootType.ASSET, "1.");
        BigDecimal cl = balanceSum(RootType.LIABILITY, "2.");
        BigDecimal ta = balanceSum(RootType.ASSET, null);
        BigDecimal tl = balanceSum(RootType.LIABILITY, null);
        BigDecimal te = balanceSum(RootType.EQUITY, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("currentRatio", cl.signum() > 0 ? ReportRows.ratio(ca, cl, 2) : BigDecimal.ZERO);
        out.put("debtToEquity", te.signum() > 0 ? ReportRows.ratio(tl, te, 2) : BigDecimal.ZERO);
        out.put("debtToAsset", ta.signum() > 0 ? ReportRows.ratio(tl, ta, 2) : BigDecimal.ZERO);
        out.put("totalAssets", ta);
        out.put("totalLiabilities", tl);
        out.put("totalEquity", te);
        return out;
    }

    // ─────────────────────────────── aging ───────────────────────────────

    private BigDecimal[] agingBuckets(String entity, Instant now) {
        BigDecimal[] b = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (Object[] r : em.createQuery("select i.outstanding, i.dueDate from " + entity + " i "
                + "where i.outstanding > 0 and i.status not in :st", Object[].class)
                .setParameter("st", NOT_ACTIVE).getResultList()) {
            BigDecimal amt = Money.nz((BigDecimal) r[0]);
            Instant due = (Instant) r[1];
            if (due == null || !due.isBefore(now)) {
                b[0] = b[0].add(amt);
                continue;
            }
            long days = ReportDates.ceilDays(now.toEpochMilli() - due.toEpochMilli());
            int i = days <= 30 ? 1 : days <= 60 ? 2 : days <= 90 ? 3 : 4;
            b[i] = b[i].add(amt);
        }
        return b;
    }

    private static List<Map<String, Object>> bucketArray(BigDecimal[] b) {
        String[] labels = {"Belum JT", "1-30 hari", "31-60 hari", "61-90 hari", ">90 hari"};
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", labels[i]);
            m.put("amount", b[i]);
            out.add(m);
        }
        return out;
    }

    /** GET /dashboard/aging-summary — ember umur piutang & hutang. */
    @ErpTx(readOnly = true)
    public Map<String, Object> agingSummary() {
        Instant now = Instant.now();
        BigDecimal[] ar = agingBuckets("SalesInvoice", now);
        BigDecimal[] ap = agingBuckets("PurchaseInvoice", now);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("receivable", bucketArray(ar));
        out.put("payable", bucketArray(ap));
        out.put("totalReceivable", Money.sum(List.of(ar)));
        out.put("totalPayable", Money.sum(List.of(ap)));
        return out;
    }

    // ─────────────────────────────── posisi kas ───────────────────────────────

    /** GET /dashboard/cash-position — saldo per akun kas/bank aktif. */
    @ErpTx(readOnly = true)
    public Map<String, Object> cashPosition() {
        List<String> ids = systemAccounts.getAccountIds(SystemAccountRole.CASH);
        Map<String, Object> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            out.put("accounts", List.of());
            out.put("total", 0);
            return out;
        }
        List<Map<String, Object>> accounts = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] a : em.createQuery("select a.id, a.accountNumber, a.name, a.balance from Account a "
                        + "where a.id in :ids and a.isActive = true and a.isGroup = false order by a.accountNumber asc",
                Object[].class).setParameter("ids", ids).getResultList()) {
            BigDecimal balance = Money.nz((BigDecimal) a[3]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("accountId", a[0]);
            m.put("accountNumber", a[1]);
            m.put("name", a[2]);
            m.put("balance", balance);
            accounts.add(m);
            total = total.add(balance);
        }
        out.put("accounts", accounts);
        out.put("total", total);
        return out;
    }
}
