package id.enterpriseapps.erp.paymentrequest;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.security.ErpAccess;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.GaCostCenter;
import id.enterpriseapps.erp.entity.JournalEntry;
import id.enterpriseapps.erp.entity.Party;
import id.enterpriseapps.erp.entity.PaymentRequest;
import id.enterpriseapps.erp.entity.PaymentRequestAttachment;
import id.enterpriseapps.erp.entity.PaymentRequestHistory;
import id.enterpriseapps.erp.entity.PaymentRequestLine;
import id.enterpriseapps.erp.entity.PaymentRequestSettlementLine;
import id.enterpriseapps.erp.entity.PaymentRequestType;
import id.enterpriseapps.erp.entity.User;
import id.enterpriseapps.erp.paymentrequest.PrRules.Decide;
import id.enterpriseapps.erp.paymentrequest.PrRules.Ref;
import id.enterpriseapps.erp.paymentrequest.PrRules.Scope;
import id.enterpriseapps.erp.paymentrequest.PrSettlementRules.AdvanceRow;
import id.enterpriseapps.erp.paymentrequest.PrSettlementRules.AdvanceSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bacaan Pengajuan Pembayaran — bagian GET di {@code routes/paymentRequests.ts} + {@code services/paymentRequestList.ts}.
 * Scope visibilitas (mine / approvals / payable / advances / all) dari {@link PrRules#scopeWhere}; filter hanya
 * mempersempitnya, sehingga daftar, kartu KPI dan ekspor Excel selalu menggambarkan baris yang sama.
 */
@Service
@RequiredArgsConstructor
public class ErpPaymentRequestQueryService {

    static final List<String> SORT_FIELDS = List.of("createdAt", "number", "neededBy", "totalAmount", "requester",
            "status", "paymentDate");
    private static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("createdAt", true);

    private final ErpPaymentRequestSupport support;
    private final ErpAccess access;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── where daftar ─────────────────────────────

    private record Where(List<String> conds, Map<String, Object> params) {
        String jpql() {
            return conds.isEmpty() ? "" : " where " + String.join(" and ", conds);
        }

        <T> TypedQuery<T> bind(TypedQuery<T> q) {
            params.forEach(q::setParameter);
            return q;
        }
    }

    private static Where scopeOnly(Scope scope) {
        List<String> conds = new ArrayList<>();
        if (scope.jpql() != null) {
            conds.add("(" + scope.jpql() + ")");
        }
        return new Where(conds, new LinkedHashMap<>(scope.params()));
    }

    /** {@code buildPaymentRequestWhere}: status, settlementStatus, typeId, startDate/endDate (createdAt), search. */
    private static Where buildWhere(Map<String, String> query, Scope scope) {
        Where w = scopeOnly(scope);
        String status = ListQuery.str(query, "status");
        if (status != null) {
            if (!PrRules.PR_STATUSES.contains(status)) {
                throw ErpException.business("Status pengajuan tidak dikenal.");
            }
            w.conds().add("p.status = :fStatus");
            w.params().put("fStatus", status);
        }
        String settlementStatus = ListQuery.str(query, "settlementStatus");
        if (settlementStatus != null) {
            if (!PrSettlementRules.SETTLEMENT_STATUSES.contains(settlementStatus)) {
                throw ErpException.business("Status pertanggungjawaban tidak dikenal.");
            }
            w.conds().add("p.settlementStatus = :fSettlement");
            w.params().put("fSettlement", settlementStatus);
        }
        String typeId = ListQuery.str(query, "typeId");
        if (typeId != null) {
            w.conds().add("p.typeId = :fType");
            w.params().put("fType", typeId);
        }
        String start = ListQuery.str(query, "startDate");
        String end = ListQuery.str(query, "endDate");
        if ((start != null && !ErpDates.isIsoDate(start)) || (end != null && !ErpDates.isIsoDate(end))) {
            throw ErpException.business("Format tanggal harus YYYY-MM-DD.");
        }
        if (start != null && end != null && start.compareTo(end) > 0) {
            throw ErpException.business("Tanggal awal tidak boleh setelah tanggal akhir.");
        }
        if (start != null || end != null) {
            Instant[] range = ErpDates.localDayRange(start, end);
            if (range[0] != null) {
                w.conds().add("p.createdAt >= :fFrom");
                w.params().put("fFrom", range[0]);
            }
            if (range[1] != null) {
                w.conds().add("p.createdAt < :fTo");
                w.params().put("fTo", range[1]);
            }
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            w.conds().add("(lower(p.number) like :fSearch or lower(p.purpose) like :fSearch "
                    + "or lower(p.payeeName) like :fSearch or lower(r.fullName) like :fSearch)");
            w.params().put("fSearch", ErpQuery.contains(search));
        }
        return w;
    }

    private static String parseScope(Map<String, String> query) {
        String s = ListQuery.str(query, "scope");
        return s != null && PrRules.PR_SCOPES.contains(s) ? s : "mine";
    }

    private static Scope requireScope(String scope, PrViewer ctx) {
        Scope s = PrRules.scopeWhere(scope, ctx);
        if (s == null) {
            throw ErpException.forbidden();
        }
        return s;
    }

    private static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        String primary = switch (sort.field()) {
            case "requester" -> "r.fullName " + dir;
            case "neededBy" -> "p.neededBy " + dir + " nulls last";
            case "paymentDate" -> "p.paymentDate " + dir + " nulls last";
            default -> "p." + sort.field() + " " + dir;
        };
        return primary + ", p.number desc";
    }

    private long count(Where w) {
        return w.bind(em.createQuery("select count(p) from PaymentRequest p join p.requester r" + w.jpql(), Long.class))
                .getSingleResult();
    }

    private Map<String, Object> badgeCounts(PrViewer ctx) {
        long approvals = count(scopeOnly(PrRules.scopeWhere("approvals", ctx)));
        Scope payable = PrRules.scopeWhere("payable", ctx);
        long pay = payable != null ? count(scopeOnly(payable)) : 0;
        return PrJson.ordered("approvals", approvals, "payable", pay);
    }

    private List<AdvanceRow> advanceRows(Where w) {
        List<Object[]> rows = w.bind(em.createQuery("select p.requesterId, r.fullName, p.totalAmount, p.paymentDate, "
                + "p.settlementDeadline from PaymentRequest p join p.requester r" + w.jpql(), Object[].class)).getResultList();
        List<AdvanceRow> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(new AdvanceRow((String) r[0], (String) r[1], (BigDecimal) r[2], (Instant) r[3], (Instant) r[4]));
        }
        return out;
    }

    // ───────────────────────────── endpoint ─────────────────────────────

    /** GET /meta — konteks pengguna untuk formulir & tab. */
    @ErpTx(readOnly = true)
    public Map<String, Object> meta(PrViewer ctx) {
        User u = em.find(User.class, ctx.id());
        Map<String, Object> me = null;
        if (u != null) {
            User sup = u.getSupervisorId() == null ? null : em.find(User.class, u.getSupervisorId());
            me = PrJson.ordered("id", u.getId(), "fullName", u.getFullName(), "supervisor", sup == null ? null
                    : PrJson.ordered("id", sup.getId(), "fullName", sup.getFullName(), "isActive", sup.getIsActive()));
        }
        List<PaymentRequest> last = em.createQuery("select p from PaymentRequest p where p.requesterId = :me "
                        + "and p.payeeKind = 'EMPLOYEE' and p.bankAccountNo is not null order by p.createdAt desc",
                        PaymentRequest.class)
                .setParameter("me", ctx.id()).setMaxResults(1).getResultList();
        Map<String, Object> lastPayee = last.isEmpty() ? null : PrJson.ordered(
                "bankName", last.get(0).getBankName(),
                "bankAccountNo", last.get(0).getBankAccountNo(),
                "bankAccountHolder", last.get(0).getBankAccountHolder(),
                "department", last.get(0).getDepartment());

        Map<String, Object> mineParams = new LinkedHashMap<>();
        mineParams.put("me", ctx.id());
        mineParams.put("paid", "PAID");
        mineParams.put("out", PrSettlementRules.OUTSTANDING_SETTLEMENT_STATUSES);
        Where mine = new Where(new ArrayList<>(List.of("p.requesterId = :me", "p.status = :paid",
                "p.settlementStatus in :out")), mineParams);
        AdvanceSummary s = PrSettlementRules.summarizeAdvances(advanceRows(mine), Instant.now());

        Map<String, Object> open = PrJson.ordered(
                "count", s.count(),
                "amount", PrJson.num(s.amount()),
                "overdueCount", s.overdueCount(),
                "overdueAmount", PrJson.num(s.overdueAmount()),
                "oldestDays", s.byEmployee().isEmpty() ? 0 : s.byEmployee().get(0).oldestDays());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("openAdvances", open);
        out.put("overdueDays", PrSettlementRules.SETTLEMENT_OVERDUE_DAYS);
        out.put("me", me);
        out.put("role", ctx.role());
        out.put("isFinance", ctx.isFinance());
        out.put("canViewAll", ctx.canViewAll());
        out.put("isSuperAdmin", ctx.isSuperAdmin());
        out.put("lastPayee", lastPayee);
        out.put("badges", badgeCounts(ctx));
        return out;
    }

    /** GET / — daftar berpaginasi untuk satu scope. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(PrViewer ctx, Map<String, String> query) {
        Scope scope = requireScope(parseScope(query), ctx);
        Where w = buildWhere(query, scope);
        ListQuery.Paging paging = ListQuery.paging(query, 25, 500);
        ListQuery.Sort sort = ListQuery.sort(query == null ? null : query.get("sort"), SORT_FIELDS, DEFAULT_SORT);
        List<PaymentRequest> rows = w.bind(em.createQuery("select p from PaymentRequest p join fetch p.requester r "
                        + "join fetch p.type t" + w.jpql() + " order by " + orderBy(sort), PaymentRequest.class))
                .setFirstResult(paging.skip()).setMaxResults(paging.limit()).getResultList();
        List<Map<String, Object>> data = new ArrayList<>();
        for (PaymentRequest p : rows) {
            data.add(listRow(p));
        }
        return ListQuery.result(data, count(w), paging);
    }

    /** {@code LIST_SELECT}. */
    private static Map<String, Object> listRow(PaymentRequest p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("number", p.getNumber());
        m.put("status", p.getStatus());
        m.put("purpose", p.getPurpose());
        m.put("payeeKind", p.getPayeeKind());
        m.put("payeeName", p.getPayeeName());
        m.put("totalAmount", p.getTotalAmount());
        m.put("neededBy", p.getNeededBy());
        m.put("createdAt", p.getCreatedAt());
        m.put("submittedAt", p.getSubmittedAt());
        m.put("paidAt", p.getPaidAt());
        m.put("paymentDate", p.getPaymentDate());
        m.put("settlementStatus", p.getSettlementStatus());
        m.put("settlementDeadline", p.getSettlementDeadline());
        m.put("directorRole", p.getDirectorRole());
        PaymentRequestType t = p.getType();
        m.put("type", t == null ? null : PrJson.ordered("id", t.getId(), "name", t.getName(), "kind", t.getKind()));
        m.put("requester", PrJson.userRef(p.getRequester()));
        return m;
    }

    /** GET /summary — KPI per status untuk scope + filter yang sama, plus badge tab. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(PrViewer ctx, Map<String, String> query) {
        Scope scope = requireScope(parseScope(query), ctx);
        Where w = buildWhere(query, scope);
        List<Object[]> groups = w.bind(em.createQuery("select p.status, count(p), sum(p.totalAmount) "
                + "from PaymentRequest p join p.requester r" + w.jpql() + " group by p.status", Object[].class)).getResultList();
        Map<String, Object> byStatus = new LinkedHashMap<>();
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] g : groups) {
            String status = (String) g[0];
            long c = ((Number) g[1]).longValue();
            BigDecimal amount = Money.nz((BigDecimal) g[2]);
            byStatus.put(status, PrJson.ordered("count", c, "amount", PrJson.num(amount)));
            count += c;
            if (!"CANCELLED".equals(status) && !"REJECTED".equals(status)) {
                total = total.add(amount);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", count);
        out.put("totalAmount", PrJson.num(total));
        out.put("byStatus", byStatus);
        out.put("badges", badgeCounts(ctx));
        return out;
    }

    /** GET /advances/summary — uang muka karyawan terbuka (visibilitas = tab "Belum Dipertanggungjawabkan"). */
    @ErpTx(readOnly = true)
    public Map<String, Object> advancesSummary(PrViewer ctx) {
        Where w = scopeOnly(PrRules.scopeWhere("advances", ctx));
        return PrSettlementRules.summarizeAdvances(advanceRows(w), Instant.now()).toJson();
    }

    /** GET /:id — detail + aksi yang boleh dilakukan pengguna saat ini. */
    @ErpTx(readOnly = true)
    public ObjectNode detail(PrViewer ctx, String id) {
        PaymentRequest pr = support.visibleRequest(id, ctx);
        ObjectNode o = PrJson.scalars(pr);
        PaymentRequestType type = em.find(PaymentRequestType.class, pr.getTypeId());
        o.set("type", type == null ? null : PrJson.scalars(type));
        o.set("requester", ErpJson.tree(PrJson.userRefWithEmail(em.find(User.class, pr.getRequesterId()))));
        o.set("supervisor", ErpJson.tree(PrJson.userRef(find(User.class, pr.getSupervisorId()))));
        Party party = find(Party.class, pr.getPartyId());
        o.set("party", ErpJson.tree(party == null ? null : PrJson.ordered("id", party.getId(), "name", party.getName())));
        GaCostCenter cc = find(GaCostCenter.class, pr.getCostCenterId());
        o.set("costCenter", ErpJson.tree(cc == null ? null
                : PrJson.ordered("id", cc.getId(), "code", cc.getCode(), "name", cc.getName())));
        o.set("cashAccount", ErpJson.tree(PrJson.accountRef(find(Account.class, pr.getCashAccountId()))));
        o.set("paidBy", ErpJson.tree(PrJson.userRef(find(User.class, pr.getPaidById()))));
        o.set("journalEntry", ErpJson.tree(PrJson.journalRef(find(JournalEntry.class, pr.getJournalEntryId()))));

        ArrayNode lines = ErpJson.arrayNode();
        em.createQuery("select l from PaymentRequestLine l left join fetch l.expenseAccount "
                        + "where l.paymentRequestId = :id order by l.lineNo asc", PaymentRequestLine.class)
                .setParameter("id", id).getResultList()
                .forEach(l -> lines.add(PrJson.scalars(l).set("expenseAccount", ErpJson.tree(PrJson.accountRef(l.getExpenseAccount())))));
        o.set("lines", lines);

        o.set("settlementSupervisor", ErpJson.tree(PrJson.userRef(find(User.class, pr.getSettlementSupervisorId()))));
        o.set("settlementCashAccount", ErpJson.tree(PrJson.accountRef(find(Account.class, pr.getSettlementCashAccountId()))));
        o.set("settlementJournal", ErpJson.tree(PrJson.journalRef(find(JournalEntry.class, pr.getSettlementJournalId()))));

        ArrayNode settlementLines = ErpJson.arrayNode();
        em.createQuery("select l from PaymentRequestSettlementLine l left join fetch l.expenseAccount "
                        + "where l.paymentRequestId = :id order by l.lineNo asc", PaymentRequestSettlementLine.class)
                .setParameter("id", id).getResultList()
                .forEach(l -> settlementLines.add(PrJson.scalars(l)
                        .set("expenseAccount", ErpJson.tree(PrJson.accountRef(l.getExpenseAccount())))));
        o.set("settlementLines", settlementLines);

        ArrayNode attachments = ErpJson.arrayNode();
        em.createQuery("select a from PaymentRequestAttachment a where a.paymentRequestId = :id order by a.createdAt asc",
                        PaymentRequestAttachment.class)
                .setParameter("id", id).getResultList()
                .forEach(a -> attachments.add(PrJson.scalars(a)));
        o.set("attachments", attachments);

        ArrayNode history = ErpJson.arrayNode();
        em.createQuery("select h from PaymentRequestHistory h left join fetch h.actor "
                        + "where h.paymentRequestId = :id order by h.createdAt asc", PaymentRequestHistory.class)
                .setParameter("id", id).getResultList()
                .forEach(h -> history.add(PrJson.scalars(h).set("actor", ErpJson.tree(PrJson.userRef(h.getActor())))));
        o.set("history", history);

        Ref ref = Ref.of(pr);
        boolean isRequester = pr.getRequesterId().equals(ctx.id());
        Decide decide = PrRules.checkCanDecide(ref, ctx);
        Decide settleDecide = PrSettlementRules.checkSettlementDecide(ref, ctx);
        Map<String, Object> settlement = PrJson.ordered(
                "edit", PrSettlementRules.checkSettlementEditable(ref, ctx.id()).ok(),
                "decide", settleDecide.ok(),
                "step", settleDecide.ok() ? settleDecide.step() : null,
                "post", settleDecide.ok() && "FINANCE".equals(settleDecide.step()),
                "cancelPosting", PrSettlementRules.checkCancelSettlementPosting(ref, ctx).ok());
        Map<String, Object> actions = PrJson.ordered(
                "edit", PrRules.checkEditable(ref, ctx.id()).ok(),
                "submit", PrRules.checkEditable(ref, ctx.id()).ok(),
                "decide", decide.ok(),
                "step", decide.ok() ? decide.step() : null,
                "verify", PrRules.checkFinanceVerify(ref, ctx).ok(),
                "pay", PrRules.checkPayable(ref, ctx).ok(),
                "cancel", isRequester && PrRules.checkRequesterCancel(ref, ctx.id()).ok(),
                "cancelPayment", PrRules.checkCancelPayment(ref, ctx).ok(),
                "viewJournal", access.can("journal", "view"),
                "settlement", settlement);
        boolean outstanding = "PAID".equals(pr.getStatus())
                && pr.getSettlementStatus() != null
                && PrSettlementRules.OUTSTANDING_SETTLEMENT_STATUSES.contains(pr.getSettlementStatus());
        Instant now = Instant.now();
        Map<String, Object> advanceAge = outstanding ? PrJson.ordered(
                "days", PrSettlementRules.advanceAgeDays(pr.getPaymentDate(), now),
                "overdue", PrSettlementRules.isAdvanceOverdue(pr.getPaymentDate(), pr.getSettlementDeadline(), now,
                        PrSettlementRules.SETTLEMENT_OVERDUE_DAYS),
                "overdueDays", PrSettlementRules.SETTLEMENT_OVERDUE_DAYS) : null;
        o.set("advanceAge", ErpJson.tree(advanceAge));
        o.set("actions", ErpJson.tree(actions));
        return o;
    }

    private <T> T find(Class<T> type, String id) {
        return id == null ? null : em.find(type, id);
    }
}
