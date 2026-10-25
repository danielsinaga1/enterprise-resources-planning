package id.neravoxa.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.cashbank.ProjectGuaranteeList.Figures;
import id.neravoxa.erp.cashbank.ProjectGuaranteeList.LightRow;
import id.neravoxa.erp.cashbank.ProjectGuaranteeList.SettlementAmounts;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.ProjectGuarantee;
import id.neravoxa.erp.entity.ProjectGuaranteeSettlement;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bacaan uang jaminan proyek — GET list (mode lama &amp; berhalaman), GET /summary, GET /:id
 * ({@code routes/projectGuarantees.ts} + {@code services/projectGuaranteeList.ts}).
 */
@Service
public class ErpProjectGuaranteeQueryService {

    private static final int IN_CHUNK = 1000;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * Dua mode (kompatibel ke belakang):
     * <ul>
     *   <li>tanpa {@code page}: semua jaminan, filter mentah partyId/status/guaranteeType, urut tanggal terbaru,
     *       respons {@code { data, summary }}.</li>
     *   <li>dengan {@code page}: page, limit (≤200), status, due, partyId, guaranteeType, startDate/endDate,
     *       search, sort → {@code { data, total, page, limit }}; tiap baris membawa {@code dueState}.</li>
     * </ul>
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        if (query.containsKey("page")) {
            return paged(query);
        }
        List<String> conds = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        for (String f : List.of("partyId", "status", "guaranteeType")) {
            String v = query.get(f);
            if (v != null && !v.isEmpty()) {
                conds.add("g." + f + " = :" + f);
                params.put(f, v);
            }
        }
        TypedQuery<ProjectGuarantee> q = em.createQuery("select g from ProjectGuarantee g left join fetch g.party"
                + (conds.isEmpty() ? "" : " where " + String.join(" and ", conds))
                + " order by g.date desc, g.createdAt desc, g.id desc", ProjectGuarantee.class);
        params.forEach(q::setParameter);
        List<ProjectGuarantee> guarantees = q.getResultList();
        Map<String, List<ProjectGuaranteeSettlement>> settlements = settlementsOf(ids(guarantees));
        Map<String, Map<String, Object>> accounts = accountMap(guarantees, settlements);

        BigDecimal totalActive = BigDecimal.ZERO;
        BigDecimal totalReturned = BigDecimal.ZERO;
        BigDecimal totalForfeited = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        int activeCount = 0;
        List<ObjectNode> data = new ArrayList<>();
        for (ProjectGuarantee g : guarantees) {
            List<ProjectGuaranteeSettlement> ss = settlements.getOrDefault(g.getId(), List.of());
            Figures fig = figuresOf(g, ss);
            data.add(enrich(g, ss, accounts, fig));
            if (!ProjectGuaranteeList.CANCELLED.equals(g.getStatus())) {
                totalActive = totalActive.add(Money.nz(g.getAmount()));
                totalReturned = totalReturned.add(fig.totalReturned());
                totalForfeited = totalForfeited.add(fig.totalForfeited());
                totalOutstanding = totalOutstanding.add(fig.outstanding());
            }
            if (ProjectGuaranteeList.OPEN.equals(g.getStatus())) {
                activeCount++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalDeposited", totalActive);
        summary.put("totalReturned", totalReturned);
        summary.put("totalForfeited", totalForfeited);
        summary.put("totalOutstanding", totalOutstanding);
        summary.put("activeCount", activeCount);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        out.put("summary", summary);
        return out;
    }

    private Map<String, Object> paged(Map<String, String> query) {
        Instant now = Instant.now();
        ListQuery.Paging paging = ListQuery.paging(query, 25, 200);
        ProjectGuaranteeList.Where where = ProjectGuaranteeList.where(query, now);
        ListQuery.Sort sort = ListQuery.sort(ListQuery.str(query, "sort"), ProjectGuaranteeList.SORT_FIELDS,
                ProjectGuaranteeList.DEFAULT_SORT);

        List<LightRow> light = lightRows(where, now);
        List<LightRow> sorted = ProjectGuaranteeList.sort(light, sort);
        List<String> pageIds = sorted.stream().skip(paging.skip()).limit(paging.limit()).map(LightRow::id).toList();
        Map<String, String> dueById = new HashMap<>();
        light.forEach(r -> dueById.put(r.id(), r.dueState()));

        List<ProjectGuarantee> rows = pageIds.isEmpty() ? List.of() : em.createQuery(
                        "select g from ProjectGuarantee g left join fetch g.party where g.id in :ids", ProjectGuarantee.class)
                .setParameter("ids", pageIds).getResultList();
        Map<String, List<ProjectGuaranteeSettlement>> settlements = settlementsOf(ids(rows));
        Map<String, Map<String, Object>> accounts = accountMap(rows, settlements);
        Map<String, ProjectGuarantee> byId = new HashMap<>();
        rows.forEach(g -> byId.put(g.getId(), g));

        List<ObjectNode> data = new ArrayList<>();
        for (String id : pageIds) {
            ProjectGuarantee g = byId.get(id);
            if (g == null) {
                continue;
            }
            List<ProjectGuaranteeSettlement> ss = settlements.getOrDefault(id, List.of());
            ObjectNode o = enrich(g, ss, accounts, figuresOf(g, ss));
            String due = dueById.get(id);
            o.put("dueState", due);
            data.add(o);
        }
        return ListQuery.result(data, sorted.size(), paging);
    }

    /** GET /summary — KPI untuk filter yang sama dengan daftar berhalaman. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        Instant now = Instant.now();
        return ProjectGuaranteeList.summarize(lightRows(ProjectGuaranteeList.where(query, now), now));
    }

    /** GET /:id — detail + settlement (termasuk yang dibatalkan) + sisa dari settlement aktif. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<ProjectGuarantee> found = em.createQuery(
                        "select g from ProjectGuarantee g left join fetch g.party where g.id = :id", ProjectGuarantee.class)
                .setParameter("id", id).getResultList();
        if (found.isEmpty()) {
            throw ErpException.notFound("Uang jaminan tidak ditemukan.");
        }
        ProjectGuarantee g = found.get(0);
        List<ProjectGuaranteeSettlement> ss = settlementsOf(List.of(g.getId())).getOrDefault(g.getId(), List.of());
        BigDecimal settled = BigDecimal.ZERO;
        for (ProjectGuaranteeSettlement s : ss) {
            if (!Boolean.TRUE.equals(s.getIsCancelled())) {
                settled = settled.add(Money.nz(s.getReturnAmount())).add(Money.nz(s.getForfeitAmount()));
            }
        }
        ObjectNode o = CashBankJson.scalars(g);
        o.set("party", CashBankJson.pick(g.getParty(), "id", "name"));
        o.set("settlements", CashBankJson.scalarsList(ss));
        o.set("outstanding", ErpJson.tree(Money.nz(g.getAmount()).subtract(settled)));
        return o;
    }

    // ─────────────────────────────── helper ───────────────────────────────

    private static List<String> ids(List<ProjectGuarantee> guarantees) {
        return guarantees.stream().map(ProjectGuarantee::getId).toList();
    }

    /** Settlement per jaminan, urut dibuat (lama → baru); IN dipotong per 1000 id. */
    private Map<String, List<ProjectGuaranteeSettlement>> settlementsOf(Collection<String> guaranteeIds) {
        Map<String, List<ProjectGuaranteeSettlement>> out = new HashMap<>();
        List<String> all = new ArrayList<>(guaranteeIds);
        for (int i = 0; i < all.size(); i += IN_CHUNK) {
            em.createQuery("select s from ProjectGuaranteeSettlement s where s.guaranteeId in :ids "
                            + "order by s.createdAt asc, s.id asc", ProjectGuaranteeSettlement.class)
                    .setParameter("ids", all.subList(i, Math.min(all.size(), i + IN_CHUNK)))
                    .getResultList()
                    .forEach(s -> out.computeIfAbsent(s.getGuaranteeId(), k -> new ArrayList<>()).add(s));
        }
        return out;
    }

    private static Figures figuresOf(ProjectGuarantee g, List<ProjectGuaranteeSettlement> ss) {
        return ProjectGuaranteeList.figures(g.getAmount(), g.getStatus(), ss.stream()
                .map(s -> new SettlementAmounts(s.getReturnAmount(), s.getForfeitAmount(), Boolean.TRUE.equals(s.getIsCancelled())))
                .toList());
    }

    /** Nama akun (FK skalar tanpa relasi) untuk jaminan & settlement-nya dalam satu query. */
    private Map<String, Map<String, Object>> accountMap(List<ProjectGuarantee> guarantees,
                                                        Map<String, List<ProjectGuaranteeSettlement>> settlements) {
        Set<String> accountIds = new LinkedHashSet<>();
        guarantees.forEach(g -> accountIds.add(g.getAccountId()));
        settlements.values().forEach(list -> list.forEach(s -> {
            if (s.getReturnAccountId() != null) {
                accountIds.add(s.getReturnAccountId());
            }
            if (s.getForfeitAccountId() != null) {
                accountIds.add(s.getForfeitAccountId());
            }
        }));
        accountIds.remove(null);
        Map<String, Map<String, Object>> out = new HashMap<>();
        if (accountIds.isEmpty()) {
            return out;
        }
        em.createQuery("select a.id, a.accountNumber, a.name from Account a where a.id in :ids", Object[].class)
                .setParameter("ids", accountIds).getResultList()
                .forEach(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r[0]);
                    m.put("accountNumber", r[1]);
                    m.put("name", r[2]);
                    out.put((String) r[0], m);
                });
        return out;
    }

    private static JsonNode accountJson(Map<String, Map<String, Object>> accounts, String id) {
        Map<String, Object> m = id == null ? null : accounts.get(id);
        return m == null ? NullNode.getInstance() : ErpJson.tree(m);
    }

    /** Bentuk baris kedua mode daftar: jaminan + nama akun + angka turunan. */
    private static ObjectNode enrich(ProjectGuarantee g, List<ProjectGuaranteeSettlement> ss,
                                     Map<String, Map<String, Object>> accounts, Figures fig) {
        ObjectNode o = CashBankJson.scalars(g);
        o.set("party", CashBankJson.pick(g.getParty(), "id", "name"));
        ArrayNode settlements = ErpJson.arrayNode();
        for (ProjectGuaranteeSettlement s : ss) {
            ObjectNode so = CashBankJson.scalars(s);
            so.set("returnAccount", accountJson(accounts, s.getReturnAccountId()));
            so.set("forfeitAccount", accountJson(accounts, s.getForfeitAccountId()));
            settlements.add(so);
        }
        o.set("settlements", settlements);
        o.set("account", accountJson(accounts, g.getAccountId()));
        o.set("totalReturned", ErpJson.tree(fig.totalReturned()));
        o.set("totalForfeited", ErpJson.tree(fig.totalForfeited()));
        o.set("outstanding", ErpJson.tree(fig.outstanding()));
        return o;
    }

    /** Proyeksi ringan seluruh jaminan yang cocok filter (untuk urut, potong halaman, ringkasan). */
    private List<LightRow> lightRows(ProjectGuaranteeList.Where where, Instant now) {
        TypedQuery<Object[]> q = em.createQuery("select g.id, g.guaranteeNumber, g.date, g.expectedReturnDate, g.status, "
                + "g.amount, g.projectName, p.name from ProjectGuarantee g left join g.party p" + where.jpql(), Object[].class);
        where.params().forEach(q::setParameter);
        List<Object[]> rows = q.getResultList();
        Map<String, List<SettlementAmounts>> amounts = new HashMap<>();
        List<String> ids = rows.stream().map(r -> (String) r[0]).toList();
        for (int i = 0; i < ids.size(); i += IN_CHUNK) {
            em.createQuery("select s.guaranteeId, s.returnAmount, s.forfeitAmount, s.isCancelled "
                            + "from ProjectGuaranteeSettlement s where s.guaranteeId in :ids", Object[].class)
                    .setParameter("ids", ids.subList(i, Math.min(ids.size(), i + IN_CHUNK)))
                    .getResultList()
                    .forEach(s -> amounts.computeIfAbsent((String) s[0], k -> new ArrayList<>())
                            .add(new SettlementAmounts((BigDecimal) s[1], (BigDecimal) s[2], Boolean.TRUE.equals(s[3]))));
        }
        Instant[] bounds = ErpDates.dueBoundaries(now);
        List<LightRow> out = new ArrayList<>();
        for (Object[] r : rows) {
            String id = (String) r[0];
            String status = (String) r[4];
            BigDecimal amount = Money.nz((BigDecimal) r[5]);
            Instant expected = (Instant) r[3];
            out.add(new LightRow(id, (String) r[1], (Instant) r[2], expected, status, amount,
                    r[7] == null ? "" : (String) r[7], (String) r[6],
                    ProjectGuaranteeList.figures(amount, status, amounts.getOrDefault(id, List.of())),
                    ProjectGuaranteeList.dueState(status, expected, bounds)));
        }
        return out;
    }
}
