package id.neravoxa.erp.adminsetup;

import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.InvoiceStatus;
import id.neravoxa.erp.entity.PartyType;
import id.neravoxa.erp.entity.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab analisis Pelanggan & Vendor — port {@code GET /parties/dashboard}: total piutang/utang (saldo
 * positif mitra aktif non-dummy), uang muka, 10 mitra teratas, aging piutang/utang dari invoice
 * Submitted/PartiallyPaid yang masih outstanding, dan 10 pembayaran terakhir.
 */
@Service
public class ErpPartyDashboardService {

    private static final long DAY_MS = 86_400_000L;
    private static final List<InvoiceStatus> OPEN = List.of(InvoiceStatus.Submitted, InvoiceStatus.PartiallyPaid);

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private record PartyBal(String id, String name, PartyType type, BigDecimal outstanding,
                            BigDecimal deposit, BigDecimal customerDeposit) {
    }

    @ErpTx(readOnly = true)
    public Map<String, Object> dashboard() {
        Instant now = Instant.now();
        List<PartyBal> parties = em.createQuery("select p.id, p.name, p.partyType, p.outstandingAmount, p.depositBalance,"
                        + " p.customerDepositBalance from Party p where p.isActive = true and p.isDummy = false", Object[].class)
                .getResultList().stream()
                .map(r -> new PartyBal((String) r[0], (String) r[1], (PartyType) r[2], Money.nz((BigDecimal) r[3]),
                        Money.nz((BigDecimal) r[4]), Money.nz((BigDecimal) r[5])))
                .toList();
        List<PartyBal> customers = parties.stream()
                .filter(p -> p.type() == PartyType.Customer || p.type() == PartyType.Both).toList();
        List<PartyBal> suppliers = parties.stream()
                .filter(p -> p.type() == PartyType.Supplier || p.type() == PartyType.Both).toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCustomers", customers.size());
        summary.put("totalSuppliers", suppliers.size());
        summary.put("totalPiutang", sumPositive(customers));
        summary.put("totalHutang", sumPositive(suppliers));
        summary.put("totalCustomerDeposit", customers.stream().map(PartyBal::customerDeposit).reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.put("totalVendorDeposit", suppliers.stream().map(PartyBal::deposit).reduce(BigDecimal.ZERO, BigDecimal::add));

        Aging piutang = aging(em.createQuery("select i.id, i.invoiceNumber, i.date, i.dueDate, i.outstanding, c.name"
                + " from SalesInvoice i join i.customer c where i.status in :open and i.outstanding > 0", Object[].class)
                .setParameter("open", OPEN).getResultList(), now, "customerName");
        Aging hutang = aging(em.createQuery("select i.id, i.invoiceNumber, i.date, i.dueDate, i.outstanding, s.name"
                + " from PurchaseInvoice i join i.supplier s where i.status in :open and i.outstanding > 0", Object[].class)
                .setParameter("open", OPEN).getResultList(), now, "supplierName");

        List<Payment> payments = em.createQuery("select p from Payment p join fetch p.party join fetch p.account"
                        + " where p.status = :submitted order by p.date desc", Payment.class)
                .setParameter("submitted", DocumentStatus.Submitted).setMaxResults(10).getResultList();
        List<Map<String, Object>> recent = new ArrayList<>();
        for (Payment p : payments) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("paymentNumber", p.getPaymentNumber());
            m.put("date", p.getDate());
            m.put("paymentType", p.getPaymentType());
            m.put("amount", p.getAmount());
            m.put("partyName", p.getParty().getName());
            m.put("accountName", p.getAccount().getName());
            recent.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("topCustomers", top(customers));
        out.put("topVendors", top(suppliers));
        out.put("agingPiutang", piutang.buckets());
        out.put("agingPiutangInvoices", piutang.invoices());
        out.put("agingHutang", hutang.buckets());
        out.put("agingHutangInvoices", hutang.invoices());
        out.put("recentPayments", recent);
        return out;
    }

    private static BigDecimal sumPositive(List<PartyBal> rows) {
        return rows.stream().map(p -> p.outstanding().max(BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 10 mitra dengan outstanding positif terbesar. */
    private static List<Map<String, Object>> top(List<PartyBal> rows) {
        return rows.stream()
                .filter(p -> p.outstanding().signum() > 0)
                .sorted(Comparator.comparing(PartyBal::outstanding).reversed())
                .limit(10)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.id());
                    m.put("name", p.name());
                    m.put("outstanding", p.outstanding());
                    return m;
                })
                .toList();
    }

    private record Aging(Map<String, Object> buckets, List<Map<String, Object>> invoices) {
    }

    /**
     * Ember umur dari jatuh tempo (atau tanggal invoice bila tanpa jatuh tempo):
     * ≤0 hari current, 1–30, 31–60, 61–90, >90. Daftar invoice lewat jatuh tempo, terlama dulu, maks 10.
     */
    private static Aging aging(List<Object[]> rows, Instant now, String partyField) {
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal d30 = BigDecimal.ZERO;
        BigDecimal d60 = BigDecimal.ZERO;
        BigDecimal d90 = BigDecimal.ZERO;
        BigDecimal over90 = BigDecimal.ZERO;
        List<Map<String, Object>> overdue = new ArrayList<>();
        for (Object[] r : rows) {
            Instant date = (Instant) r[2];
            Instant due = (Instant) r[3];
            Instant ref = due != null ? due : date;
            long days = Math.floorDiv(now.toEpochMilli() - ref.toEpochMilli(), DAY_MS);
            BigDecimal amt = Money.nz((BigDecimal) r[4]);
            if (days <= 0) {
                current = current.add(amt);
            } else if (days <= 30) {
                d30 = d30.add(amt);
            } else if (days <= 60) {
                d60 = d60.add(amt);
            } else if (days <= 90) {
                d90 = d90.add(amt);
            } else {
                over90 = over90.add(amt);
            }
            if (days > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r[0]);
                m.put("invoiceNumber", r[1]);
                m.put(partyField, r[5]);
                m.put("outstanding", amt);
                m.put("daysOverdue", days);
                m.put("dueDate", ref);
                overdue.add(m);
            }
        }
        overdue.sort(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("daysOverdue")).reversed());
        Map<String, Object> buckets = new LinkedHashMap<>();
        buckets.put("current", current);
        buckets.put("d30", d30);
        buckets.put("d60", d60);
        buckets.put("d90", d90);
        buckets.put("over90", over90);
        return new Aging(buckets, overdue.size() > 10 ? List.copyOf(overdue.subList(0, 10)) : overdue);
    }
}
