package id.neravoxa.erp.deposit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bacaan uang muka (pelanggan & supplier): daftar dua mode, ringkasan KPI, saldo per mitra, dan
 * total aplikasi aktif untuk validasi penulisan. Bentuk baris daftar = Payment + {@code party} penuh
 * + {@code account} (id, name, accountNumber) + aplikasi aktif + angka turunan, persis
 * {@code DEPOSIT_LIST_INCLUDE} + {@code enrichDeposit} di sumber.
 */
@Service
public class ErpDepositQueryService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── daftar ─────────────────────────────

    /**
     * GET daftar. Tanpa {@code page} (mode lama — PaymentModal dsb.): semua uang muka, filter
     * {@code partyId} dan {@code status} mentah (DocumentStatus), urut tanggal turun → {@code { data }}.
     * Dengan {@code page}: filter/sort/paging state turunan → {@code { data, total, page, limit }} dan
     * setiap baris membawa {@code state}.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(DepositKind kind, Map<String, String> query) {
        if (query.containsKey("page")) {
            return listPaged(kind, query);
        }
        StringBuilder jpql = new StringBuilder(
                "select p from Payment p join fetch p.party join fetch p.account where p.paymentType = :type");
        Map<String, Object> params = new HashMap<>();
        params.put("type", kind.paymentType);
        String partyId = query.get("partyId");
        if (partyId != null && !partyId.isEmpty()) {
            jpql.append(" and p.partyId = :partyId");
            params.put("partyId", partyId);
        }
        String status = query.get("status");
        if (status != null && !status.isEmpty()) {
            DocumentStatus st = DepositList.documentStatus(status);
            if (st == null) {
                // Prisma menolak nilai enum asing → handler sumber membalas 500 dengan pesan ini.
                throw ErpException.of(500, kind.listErrorMessage);
            }
            jpql.append(" and p.status = :status");
            params.put("status", st);
        }
        jpql.append(" order by p.date desc");
        TypedQuery<Payment> q = em.createQuery(jpql.toString(), Payment.class);
        params.forEach(q::setParameter);
        List<ObjectNode> data = enrich(kind, q.getResultList(), null);
        return ErpJson.map("data", data);
    }

    private Map<String, Object> listPaged(DepositKind kind, Map<String, String> query) {
        ListQuery.Paging paging = ListQuery.paging(query, 25, 200);
        DepositList.Filter filter = DepositList.buildWhere(query);
        ListQuery.Sort sort = ListQuery.sort(query.get("sort"), DepositList.sortFields(kind), DepositList.DEFAULT_SORT);

        List<DepositList.LightRow> light = lightRows(kind, filter);
        DepositList.Page page = DepositList.page(light, filter.usage(), sort, paging.skip(), paging.limit(), kind);
        Map<String, String> stateById = new HashMap<>();
        light.forEach(r -> stateById.put(r.id(), r.state()));

        List<ObjectNode> data = new ArrayList<>();
        if (!page.ids().isEmpty()) {
            List<Payment> rows = em.createQuery(
                            "select p from Payment p join fetch p.party join fetch p.account where p.id in :ids",
                            Payment.class)
                    .setParameter("ids", page.ids())
                    .getResultList();
            Map<String, ObjectNode> byId = new HashMap<>();
            for (ObjectNode o : enrich(kind, rows, stateById)) {
                byId.put(o.get("id").asText(), o);
            }
            for (String id : page.ids()) {
                ObjectNode row = byId.get(id);
                if (row != null) {
                    data.add(row);
                }
            }
        }
        return ListQuery.result(data, page.total(), paging);
    }

    /** GET /summary — KPI untuk filter yang sama dengan daftar berhalaman. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(DepositKind kind, Map<String, String> query) {
        DepositList.Filter filter = DepositList.buildWhere(query);
        return DepositList.summarize(DepositList.filterByUsage(lightRows(kind, filter), filter.usage()));
    }

    /** Proyeksi ringan (tanpa relasi penuh) — total aplikasi aktif dihitung di SQL. */
    List<DepositList.LightRow> lightRows(DepositKind kind, DepositList.Filter f) {
        StringBuilder jpql = new StringBuilder(
                "select p.id, p.paymentNumber, p.date, p.status, p.amount, p.refundedAmount, pa.name, "
                        + "(select coalesce(sum(a.appliedAmount), 0) from " + kind.applicationEntity + " a "
                        + "where a.depositPaymentId = p.id and a.isCancelled = false) "
                        + "from Payment p join p.party pa where p.paymentType = :type");
        Map<String, Object> params = new HashMap<>();
        params.put("type", kind.paymentType);
        if (f.excludeCancelled()) {
            jpql.append(" and p.status <> :cancelled");
            params.put("cancelled", DocumentStatus.Cancelled);
        }
        if (f.status() != null) {
            jpql.append(" and p.status = :status");
            params.put("status", f.status());
        }
        if (f.partyId() != null) {
            jpql.append(" and p.partyId = :partyId");
            params.put("partyId", f.partyId());
        }
        if (f.dateGte() != null) {
            jpql.append(" and p.date >= :gte");
            params.put("gte", f.dateGte());
        }
        if (f.dateLt() != null) {
            jpql.append(" and p.date < :lt");
            params.put("lt", f.dateLt());
        }
        if (f.search() != null) {
            jpql.append(" and (lower(p.paymentNumber) like :q or lower(pa.name) like :q)");
            params.put("q", ErpQuery.contains(f.search()));
        }
        TypedQuery<Object[]> q = em.createQuery(jpql.toString(), Object[].class);
        params.forEach(q::setParameter);
        List<DepositList.LightRow> rows = new ArrayList<>();
        for (Object[] r : q.getResultList()) {
            rows.add(DepositList.LightRow.of((String) r[0], (String) r[1], (Instant) r[2], (DocumentStatus) r[3],
                    (BigDecimal) r[4], (BigDecimal) r[5], (String) r[6], Money.of(r[7])));
        }
        return rows;
    }

    /**
     * {@code { ...payment, party, account: {id,name,accountNumber}, <aplikasi aktif>, totalApplied,
     * totalRefunded, remaining }} (+ {@code state} bila {@code stateById} diberikan).
     */
    private List<ObjectNode> enrich(DepositKind kind, List<Payment> payments, Map<String, String> stateById) {
        Map<String, List<Map<String, Object>>> apps = activeApplications(kind, payments.stream().map(Payment::getId).toList());
        List<ObjectNode> out = new ArrayList<>(payments.size());
        for (Payment p : payments) {
            List<Map<String, Object>> list = apps.getOrDefault(p.getId(), List.of());
            BigDecimal applied = Money.sum(list, m -> (BigDecimal) m.get("appliedAmount"));
            ObjectNode o = ErpJson.obj(p);
            o.set("account", ErpJson.tree(accountSelect(p.getAccount())));
            o.set(kind.applicationsKey, ErpJson.tree(list));
            DepositList.figures(p.getAmount(), p.getRefundedAmount(), applied).toMap()
                    .forEach((k, v) -> o.set(k, ErpJson.tree(v)));
            if (stateById != null) {
                o.put("state", stateById.get(p.getId()));
            }
            out.add(o);
        }
        return out;
    }

    /** Aplikasi aktif per pembayaran: {@code { id, appliedAmount, salesInvoiceId|purchaseInvoiceId, appliedAt }}. */
    private Map<String, List<Map<String, Object>>> activeApplications(DepositKind kind, Collection<String> paymentIds) {
        Map<String, List<Map<String, Object>>> byPayment = new HashMap<>();
        if (paymentIds.isEmpty()) {
            return byPayment;
        }
        List<Object[]> rows = em.createQuery(
                        "select a.depositPaymentId, a.id, a.appliedAmount, a." + kind.invoiceIdField + ", a.appliedAt from "
                                + kind.applicationEntity + " a where a.depositPaymentId in :ids and a.isCancelled = false "
                                + "order by a.appliedAt asc, a.id asc", Object[].class)
                .setParameter("ids", paymentIds)
                .getResultList();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[1]);
            m.put("appliedAmount", r[2]);
            m.put(kind.invoiceIdField, r[3]);
            m.put("appliedAt", r[4]);
            byPayment.computeIfAbsent((String) r[0], k -> new ArrayList<>()).add(m);
        }
        return byPayment;
    }

    private static Map<String, Object> accountSelect(Account a) {
        if (a == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("accountNumber", a.getAccountNumber());
        return m;
    }

    // ───────────────────────────── saldo per mitra ─────────────────────────────

    /**
     * GET /balance/:partyId — uang muka Submitted milik mitra dengan sisa &gt; 0,01, urut tanggal naik,
     * plus {@code totalBalance}. Versi supplier juga mengirim {@code totalRefunded}.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> balance(DepositKind kind, String partyId) {
        List<Payment> deposits = em.createQuery(
                        "select p from Payment p join fetch p.account where p.partyId = :partyId "
                                + "and p.paymentType = :type and p.status = :status order by p.date asc", Payment.class)
                .setParameter("partyId", partyId)
                .setParameter("type", kind.paymentType)
                .setParameter("status", DocumentStatus.Submitted)
                .getResultList();
        Map<String, List<Map<String, Object>>> apps =
                activeApplications(kind, deposits.stream().map(Payment::getId).toList());

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalBalance = BigDecimal.ZERO;
        for (Payment d : deposits) {
            BigDecimal totalApplied = Money.sum(apps.getOrDefault(d.getId(), List.of()),
                    m -> (BigDecimal) m.get("appliedAmount"));
            BigDecimal refunded = Money.nz(d.getRefundedAmount());
            BigDecimal remaining = Money.nz(d.getAmount()).subtract(totalApplied).subtract(refunded);
            if (remaining.compareTo(DepositList.REMAINING_EPSILON) <= 0) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("paymentNumber", d.getPaymentNumber());
            m.put("date", d.getDate());
            m.put("amount", d.getAmount());
            m.put("totalApplied", totalApplied);
            if (kind == DepositKind.VENDOR) {
                m.put("totalRefunded", refunded);
            }
            m.put("remaining", remaining);
            m.put("referenceNo", d.getReferenceNo());
            m.put("account", accountSelect(d.getAccount()));
            items.add(m);
            totalBalance = totalBalance.add(remaining);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", items);
        out.put("totalBalance", totalBalance);
        return out;
    }

    // ───────────────────────────── bantu penulisan ─────────────────────────────

    /** Σ applied_amount aplikasi aktif satu uang muka (di dalam transaksi pemanggil). */
    public BigDecimal activeAppliedTotal(DepositKind kind, String paymentId) {
        Object v = em.createQuery("select coalesce(sum(a.appliedAmount), 0) from " + kind.applicationEntity
                        + " a where a.depositPaymentId = :id and a.isCancelled = false")
                .setParameter("id", paymentId)
                .getSingleResult();
        return Money.of(v);
    }

    /** Jumlah aplikasi aktif satu uang muka. */
    public long activeApplicationCount(DepositKind kind, String paymentId) {
        return em.createQuery("select count(a) from " + kind.applicationEntity
                        + " a where a.depositPaymentId = :id and a.isCancelled = false", Long.class)
                .setParameter("id", paymentId)
                .getSingleResult();
    }
}
