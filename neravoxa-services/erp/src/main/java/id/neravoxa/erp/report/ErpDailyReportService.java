package id.neravoxa.erp.report;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.core.inventory.ErpStockSnapshotService;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.PaymentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laporan harian terkonsolidasi — port {@code GET /reports/daily} (routes/reports/operational.ts):
 * penjualan, pembelian, produksi, kas/bank, umur piutang/hutang per akhir hari, sisa uang muka vendor,
 * dan snapshot stok per gudang (kernel {@link ErpStockSnapshotService}).
 */
@Service
@RequiredArgsConstructor
public class ErpDailyReportService {

    private static final List<InvoiceStatus> NOT_ACTIVE = List.of(InvoiceStatus.Draft, InvoiceStatus.Cancelled);
    private static final String DASH = "—";

    private final ErpSystemAccountService systemAccounts;
    private final ErpStockSnapshotService snapshots;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private static String orDash(Object v) {
        return v == null || v.toString().isEmpty() ? DASH : v.toString();
    }

    @ErpTx(readOnly = true)
    public Map<String, Object> daily(Map<String, String> query) {
        String raw = query.get("date");
        String dateStr = raw != null && !raw.isEmpty() ? raw : ErpDates.isoDay(Instant.now());
        Instant dayStart = ReportDates.parse(dateStr);
        if (dayStart == null) {
            throw ErpException.business("Parameter date tidak valid.");
        }
        Instant dayEnd = ReportDates.endOfLocalDay(dayStart);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", dateStr);
        out.put("sales", invoices(false, dayStart, dayEnd));
        out.put("purchases", invoices(true, dayStart, dayEnd));
        out.put("production", production(dayStart, dayEnd));
        out.put("finance", finance(dayStart, dayEnd));
        out.put("receivables", aging(false, dayEnd));
        out.put("payables", aging(true, dayEnd));
        out.put("vendorDeposits", vendorDeposits());
        // Snapshot kernel butuh YYYY-MM-DD; tanggal-waktu lain dipetakan ke hari lokalnya.
        out.put("stock", stock(ErpDates.isIsoDate(dateStr) ? dateStr : ReportDates.localDay(dayStart).toString()));
        return out;
    }

    // ─────────────────────────── penjualan / pembelian ───────────────────────────

    private Map<String, Object> invoices(boolean purchase, Instant start, Instant end) {
        String jpql = purchase
                ? "select i.id, i.invoiceNumber, p.name, i.grandTotal, i.outstanding from PurchaseInvoice i "
                + "left join i.supplier p where i.date >= :s and i.date <= :e and i.status not in :st order by i.date asc"
                : "select i.id, i.invoiceNumber, p.name, i.grandTotal, i.outstanding from SalesInvoice i "
                + "left join i.customer p where i.date >= :s and i.date <= :e and i.status not in :st order by i.date asc";
        List<Object[]> rows = em.createQuery(jpql, Object[].class)
                .setParameter("s", start).setParameter("e", end).setParameter("st", NOT_ACTIVE).getResultList();
        Map<String, List<Object[]>> items = new HashMap<>();
        if (!rows.isEmpty()) {
            String table = purchase ? "purchase_invoice_items" : "sales_invoice_items";
            String fk = purchase ? "purchase_invoice_id" : "sales_invoice_id";
            // ORDER BY ctid ≈ urutan sisip (Prisma tanpa orderBy mengikuti urutan fisik).
            ReportRows.rows(em.createNativeQuery("SELECT " + fk + ", item_name, quantity, unit, amount FROM " + table
                            + " WHERE " + fk + " IN (:ids) ORDER BY ctid")
                    .setParameter("ids", rows.stream().map(r -> (String) r[0]).toList()))
                    .forEach(r -> items.computeIfAbsent((String) r[0], k -> new ArrayList<>()).add(r));
        }
        List<Map<String, Object>> list = new ArrayList<>();
        BigDecimal totalGrand = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalQty = BigDecimal.ZERO;
        for (Object[] r : rows) {
            List<Map<String, Object>> its = new ArrayList<>();
            BigDecimal qty = BigDecimal.ZERO;
            for (Object[] it : items.getOrDefault((String) r[0], List.of())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemName", it[1]);
                m.put("quantity", ReportRows.dec(it[2]));
                m.put("unit", it[3]);
                m.put("amount", ReportRows.dec(it[4]));
                its.add(m);
                qty = qty.add(ReportRows.dec(it[2]));
            }
            BigDecimal grand = Money.nz((BigDecimal) r[3]);
            BigDecimal outstanding = Money.nz((BigDecimal) r[4]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("invoiceNumber", r[1]);
            m.put(purchase ? "supplierName" : "customerName", orDash(r[2]));
            m.put("items", its);
            m.put("totalQty", qty);
            m.put("grandTotal", grand);
            m.put("outstanding", outstanding);
            list.add(m);
            totalGrand = totalGrand.add(grand);
            totalOutstanding = totalOutstanding.add(outstanding);
            totalQty = totalQty.add(qty);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", rows.size());
        summary.put(purchase ? "totalSpend" : "totalRevenue", totalGrand);
        summary.put(purchase ? "totalNewPayables" : "totalNewReceivables", totalOutstanding);
        summary.put("totalQty", totalQty);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("invoices", list);
        out.put("summary", summary);
        return out;
    }

    // ─────────────────────────────── produksi ───────────────────────────────

    private Map<String, Object> production(Instant start, Instant end) {
        List<Object[]> runs = em.createQuery("select pr.id, pr.runNumber, pr.date, pr.yieldPct from ProductionRun pr "
                        + "where pr.date >= :s and pr.date <= :e and pr.isCancelled = false order by pr.date asc", Object[].class)
                .setParameter("s", start).setParameter("e", end).getResultList();
        Map<String, List<Object[]>> items = new HashMap<>();
        if (!runs.isEmpty()) {
            em.createQuery("select i.productionRunId, i.lineType, i.quantity, i.isByProduct, it.name, it.unit "
                            + "from ProductionRunItem i join i.item it where i.productionRunId in :ids "
                            + "order by i.createdAt asc, i.id asc", Object[].class)
                    .setParameter("ids", runs.stream().map(r -> (String) r[0]).toList()).getResultList()
                    .forEach(r -> items.computeIfAbsent((String) r[0], k -> new ArrayList<>()).add(r));
        }
        List<Map<String, Object>> list = new ArrayList<>();
        BigDecimal totalInput = BigDecimal.ZERO;
        BigDecimal totalOutput = BigDecimal.ZERO;
        BigDecimal yieldSum = BigDecimal.ZERO;
        for (Object[] r : runs) {
            List<Map<String, Object>> inputs = new ArrayList<>();
            List<Map<String, Object>> outputs = new ArrayList<>();
            for (Object[] it : items.getOrDefault((String) r[0], List.of())) {
                BigDecimal qty = ReportRows.dec(it[2]);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemName", it[4]);
                m.put("quantity", qty);
                m.put("unit", it[5]);
                if ("Input".equals(it[1])) {
                    inputs.add(m);
                    totalInput = totalInput.add(qty);
                } else if ("Output".equals(it[1])) {
                    m.put("isByProduct", it[3]);
                    outputs.add(m);
                    totalOutput = totalOutput.add(qty);
                }
            }
            BigDecimal yieldPct = (BigDecimal) r[3];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("runNumber", r[1]);
            m.put("date", r[2]);
            m.put("inputs", inputs);
            m.put("outputs", outputs);
            m.put("yieldPct", yieldPct);
            list.add(m);
            yieldSum = yieldSum.add(Money.nz(yieldPct));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", runs.size());
        summary.put("totalInputKg", totalInput);
        summary.put("totalOutputKg", totalOutput);
        summary.put("avgYield", runs.isEmpty() ? BigDecimal.ZERO
                : yieldSum.divide(BigDecimal.valueOf(runs.size()), 1, RoundingMode.HALF_UP));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runs", list);
        out.put("summary", summary);
        return out;
    }

    // ─────────────────────────────── keuangan ───────────────────────────────

    private Map<String, Object> finance(Instant start, Instant end) {
        List<Object[]> payments = em.createQuery("select p.paymentNumber, p.paymentType, pa.name, p.amount, ac.name "
                        + "from Payment p left join p.party pa left join p.account ac "
                        + "where p.date >= :s and p.date <= :e and p.status = :sub order by p.date asc", Object[].class)
                .setParameter("s", start).setParameter("e", end).setParameter("sub", DocumentStatus.Submitted)
                .getResultList();
        long manualJournals = em.createQuery("select count(j) from JournalEntry j "
                        + "where j.date >= :s and j.date <= :e and j.status = :sub", Long.class)
                .setParameter("s", start).setParameter("e", end).setParameter("sub", DocumentStatus.Submitted)
                .getSingleResult();

        List<String> cashIds = systemAccounts.getAccountIds(SystemAccountRole.CASH);
        List<Map<String, Object>> cashBalances = new ArrayList<>();
        if (!cashIds.isEmpty()) {
            for (Object[] a : em.createQuery("select a.name, a.accountNumber, a.balance from Account a where a.id in :ids",
                    Object[].class).setParameter("ids", cashIds).getResultList()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("accountName", a[0]);
                m.put("accountNumber", a[1]);
                m.put("balance", Money.nz((BigDecimal) a[2]));
                cashBalances.add(m);
            }
        }

        List<Map<String, Object>> in = new ArrayList<>();
        List<Map<String, Object>> outList = new ArrayList<>();
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        for (Object[] p : payments) {
            PaymentType type = (PaymentType) p[1];
            boolean isIn = type == PaymentType.Receive || type == PaymentType.CustomerDeposit;
            boolean isOut = type == PaymentType.Pay || type == PaymentType.VendorDeposit;
            if (!isIn && !isOut) {
                continue;
            }
            BigDecimal amount = Money.nz((BigDecimal) p[3]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("paymentNumber", p[0]);
            m.put("partyName", orDash(p[2]));
            m.put("amount", amount);
            m.put("accountName", orDash(p[4]));
            if (isIn) {
                in.add(m);
                totalIn = totalIn.add(amount);
            } else {
                outList.add(m);
                totalOut = totalOut.add(amount);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalIn", totalIn);
        summary.put("totalOut", totalOut);
        summary.put("netCashFlow", totalIn.subtract(totalOut));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("paymentsIn", in);
        out.put("paymentsOut", outList);
        out.put("manualJournals", manualJournals);
        out.put("cashBankBalances", cashBalances);
        out.put("summary", summary);
        return out;
    }

    // ─────────────────────────── umur piutang / hutang ───────────────────────────

    /** Ember umur terhadap akhir hari laporan: floor((akhir hari − jatuh tempo) / hari). */
    private static int bucket(Instant due, Instant ref) {
        if (due == null) {
            return 0;
        }
        long diff = ReportDates.floorDays(ref.toEpochMilli() - due.toEpochMilli());
        return diff <= 0 ? 0 : diff <= 30 ? 1 : diff <= 60 ? 2 : diff <= 90 ? 3 : 4;
    }

    private Map<String, Object> aging(boolean payable, Instant dayEnd) {
        String jpql = payable
                ? "select p.name, i.dueDate, i.outstanding from PurchaseInvoice i left join i.supplier p "
                + "where i.outstanding > 0 and i.status not in :st order by i.dueDate asc"
                : "select p.name, i.dueDate, i.outstanding from SalesInvoice i left join i.customer p "
                + "where i.outstanding > 0 and i.status not in :st order by i.dueDate asc";
        List<Object[]> rows = em.createQuery(jpql, Object[].class).setParameter("st", NOT_ACTIVE).getResultList();
        String[] keys = {"current", "d1_30", "d31_60", "d61_90", "d91_plus"};
        Map<String, BigDecimal[]> byParty = new LinkedHashMap<>();
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        for (Object[] r : rows) {
            String name = orDash(r[0]);
            Instant due = (Instant) r[1];
            BigDecimal amt = Money.nz((BigDecimal) r[2]);
            BigDecimal[] e = byParty.computeIfAbsent(name, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            int b = bucket(due, dayEnd);
            e[b] = e[b].add(amt);
            e[5] = e[5].add(amt);
            totalOutstanding = totalOutstanding.add(amt);
            if (due != null && due.isBefore(dayEnd)) {
                totalOverdue = totalOverdue.add(amt);
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        byParty.forEach((name, e) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(payable ? "supplierName" : "customerName", name);
            for (int i = 0; i < keys.length; i++) {
                m.put(keys[i], e[i]);
            }
            m.put("total", e[5]);
            list.add(m);
        });
        list.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("total")).reversed());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOutstanding", totalOutstanding);
        summary.put("totalOverdue", totalOverdue);
        summary.put(payable ? "totalSuppliers" : "totalCustomers", byParty.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(payable ? "bySupplier" : "byCustomer", list);
        out.put("summary", summary);
        return out;
    }

    // ─────────────────────────── uang muka vendor ───────────────────────────

    /** Uang muka vendor Submitted dengan sisa > 0 (aplikasi dijumlah seperti sumber, termasuk yang batal). */
    private Map<String, Object> vendorDeposits() {
        List<Object[]> deposits = em.createQuery("select p.id, p.paymentNumber, pa.name, p.amount, p.date from Payment p "
                        + "left join p.party pa where p.paymentType = :vd and p.status = :sub order by p.date desc", Object[].class)
                .setParameter("vd", PaymentType.VendorDeposit).setParameter("sub", DocumentStatus.Submitted).getResultList();
        Map<String, BigDecimal> applied = new HashMap<>();
        em.createQuery("select a.depositPaymentId, sum(a.appliedAmount) from VendorDepositApplication a "
                        + "join a.depositPayment p where p.paymentType = :vd and p.status = :sub group by a.depositPaymentId",
                        Object[].class)
                .setParameter("vd", PaymentType.VendorDeposit).setParameter("sub", DocumentStatus.Submitted).getResultList()
                .forEach(r -> applied.put((String) r[0], Money.nz((BigDecimal) r[1])));
        List<Map<String, Object>> list = new ArrayList<>();
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalApplied = BigDecimal.ZERO;
        BigDecimal totalRemaining = BigDecimal.ZERO;
        for (Object[] p : deposits) {
            BigDecimal amount = Money.nz((BigDecimal) p[3]);
            BigDecimal used = applied.getOrDefault((String) p[0], BigDecimal.ZERO);
            BigDecimal remaining = amount.subtract(used);
            totalDeposits = totalDeposits.add(amount);
            totalApplied = totalApplied.add(used);
            if (remaining.signum() > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("paymentNumber", p[1]);
                m.put("supplierName", orDash(p[2]));
                m.put("amount", amount);
                m.put("totalApplied", used);
                m.put("remaining", remaining);
                m.put("date", p[4]);
                list.add(m);
                totalRemaining = totalRemaining.add(remaining);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalDeposits", totalDeposits);
        summary.put("totalApplied", totalApplied);
        summary.put("totalRemaining", totalRemaining);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deposits", list);
        out.put("summary", summary);
        return out;
    }

    // ─────────────────────────────── stok ───────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> stock(String isoDate) {
        Map<String, Object> snapshot = snapshots.build(isoDate, null);
        List<Map<String, Object>> warehouses = (List<Map<String, Object>>) snapshot.get("warehouses");
        List<Map<String, Object>> list = new ArrayList<>();
        long totalLines = 0;
        long totalNegative = 0;
        for (Map<String, Object> w : warehouses) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> it : (List<Map<String, Object>>) w.get("items")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", it.get("code"));
                m.put("name", it.get("name"));
                m.put("unit", it.get("unit"));
                m.put("qty", it.get("qty"));
                m.put("averageCost", it.get("averageCost"));
                m.put("value", it.get("value"));
                items.add(m);
                if (((BigDecimal) it.get("qty")).signum() < 0) {
                    totalNegative++;
                }
            }
            totalLines += ((Number) w.get("itemCount")).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", w.get("code"));
            m.put("name", w.get("name"));
            m.put("location", w.get("location"));
            m.put("isDefault", w.get("isDefault"));
            m.put("itemCount", w.get("itemCount"));
            m.put("totalValue", w.get("totalValue"));
            m.put("items", items);
            list.add(m);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalWarehouses", warehouses.size());
        summary.put("totalItemLines", totalLines);
        summary.put("totalNegative", totalNegative);
        summary.put("grandTotalValue", snapshot.get("grandTotalValue"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOfDate", snapshot.get("asOfDate"));
        out.put("valuationNote", snapshot.get("valuationNote"));
        out.put("warehouses", list);
        out.put("summary", summary);
        return out;
    }
}
