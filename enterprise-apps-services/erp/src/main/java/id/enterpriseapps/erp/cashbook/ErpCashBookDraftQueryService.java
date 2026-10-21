package id.enterpriseapps.erp.cashbook;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.ListQuery;
import id.enterpriseapps.erp.entity.AccountType;
import id.enterpriseapps.erp.entity.CashBookDraft;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read model Draft Buku Kas — port {@code services/cashBookDraftList.ts}: daftar berpaginasi (dengan
 * jumlah per status), katalog akun lawan ringkas (UI) dan daftar peta terpelajari.
 */
@Service
@RequiredArgsConstructor
public class ErpCashBookDraftQueryService {

    private static final List<String> RULE_ROLES = List.of("EMPLOYEE_ADVANCE", "OTHER_EXPENSE", "OTHER_INCOME");

    private final ErpCashBookDraftService drafts;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET / — {@code { data, total, page, limit, counts }}. Query: page, limit, status, search, startDate, endDate. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        String status = ListQuery.str(query, "status");
        if (status != null && !ErpCashBookDraftService.DRAFT_STATUSES.contains(status)) {
            throw ErpException.business("Status draft tidak dikenal.");
        }
        String startDate = ListQuery.dateParam(query, "startDate", "Format tanggal harus YYYY-MM-DD.");
        String endDate = ListQuery.dateParam(query, "endDate", "Format tanggal harus YYYY-MM-DD.");
        ListQuery.Paging paging = ListQuery.paging(query, 25, 200);

        // Draft yang dibuang hanya tampil bila diminta eksplisit.
        ErpQuery<CashBookDraft> q = ErpQuery.of(em, CashBookDraft.class, "d")
                .whereIf(status != null, "d.status = :st", "st", status)
                .whereIf(status == null, "d.status <> 'Discarded'");
        Instant[] range = ErpDates.localDayRange(startDate, endDate);
        q.whereIf(range[0] != null, "d.entryDate >= :from", "from", range[0])
                .whereIf(range[1] != null, "d.entryDate < :to", "to", range[1]);
        String search = ListQuery.str(query, "search");
        if (search != null) {
            q.where("(lower(d.draftNumber) like :s or lower(d.title) like :s or lower(d.sourceNote) like :s)",
                    "s", ErpQuery.contains(search));
        }
        q.orderBy("d.createdAt desc");
        long total = q.count();
        List<CashBookDraft> rows = q.list(paging.skip(), paging.limit());

        Map<String, Object> counts = new LinkedHashMap<>();
        for (Object[] g : em.createQuery("select d.status, count(d) from CashBookDraft d where d.status <> 'Discarded' "
                + "group by d.status order by d.status", Object[].class).getResultList()) {
            counts.put((String) g[0], ((Number) g[1]).longValue());
        }

        List<String> ids = rows.stream().map(CashBookDraft::getId).toList();
        Map<String, Map<String, BigDecimal>> sums = new HashMap<>();
        Map<String, Long> rowCounts = new HashMap<>();
        Map<String, Long> review = new HashMap<>();
        Map<String, Map<String, Object>> cashMap = new HashMap<>();
        Map<String, String> userMap = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] s : em.createQuery("select r.draftId, r.direction, sum(r.amount), count(r) from CashBookDraftRow r "
                            + "where r.draftId in :ids and r.skip = false group by r.draftId, r.direction", Object[].class)
                    .setParameter("ids", ids).getResultList()) {
                String draftId = (String) s[0];
                sums.computeIfAbsent(draftId, k -> new HashMap<>()).put((String) s[1], (BigDecimal) s[2]);
                rowCounts.merge(draftId, ((Number) s[3]).longValue(), Long::sum);
            }
            for (Object[] s : em.createQuery("select r.draftId, count(r) from CashBookDraftRow r where r.draftId in :ids "
                            + "and r.skip = false and r.needsReview = true and r.journalEntryId is null group by r.draftId",
                    Object[].class).setParameter("ids", ids).getResultList()) {
                review.put((String) s[0], ((Number) s[1]).longValue());
            }
            Set<String> cashIds = new HashSet<>(rows.stream().map(CashBookDraft::getCashAccountId).toList());
            for (Object[] a : em.createQuery("select a.id, a.accountNumber, a.name from Account a where a.id in :ids", Object[].class)
                    .setParameter("ids", cashIds).getResultList()) {
                cashMap.put((String) a[0], ErpJson.map("id", a[0], "accountNumber", a[1], "name", a[2]));
            }
            Set<String> userIds = new HashSet<>(rows.stream().map(CashBookDraft::getCreatedById).toList());
            for (Object[] u : em.createQuery("select u.id, u.fullName from User u where u.id in :ids", Object[].class)
                    .setParameter("ids", userIds).getResultList()) {
                userMap.put((String) u[0], (String) u[1]);
            }
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (CashBookDraft r : rows) {
            Map<String, BigDecimal> mine = sums.getOrDefault(r.getId(), Map.of());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("draftNumber", r.getDraftNumber());
            m.put("status", r.getStatus());
            m.put("title", r.getTitle());
            m.put("entryDate", ErpDates.isoDay(r.getEntryDate()));
            m.put("source", r.getSource());
            m.put("createdVia", r.getCreatedVia());
            m.put("cashAccount", cashMap.get(r.getCashAccountId()));
            m.put("createdBy", ErpJson.map("id", r.getCreatedById(), "fullName", userMap.get(r.getCreatedById())));
            m.put("rowCount", rowCounts.getOrDefault(r.getId(), 0L));
            m.put("totalMasuk", JsCompat.jsonNumber(mine.getOrDefault("masuk", BigDecimal.ZERO)));
            m.put("totalKeluar", JsCompat.jsonNumber(mine.getOrDefault("keluar", BigDecimal.ZERO)));
            m.put("needsReviewCount", review.getOrDefault(r.getId(), 0L));
            m.put("lastError", r.getLastError());
            m.put("createdAt", r.getCreatedAt());
            m.put("postedAt", r.getPostedAt());
            data.add(m);
        }
        Map<String, Object> out = ListQuery.result(data, total, paging);
        out.put("counts", counts);
        return out;
    }

    /** Akun detail aktif (bukan grup) + penanda kas + peran aturan; diurutkan nomor akun. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> counterAccounts() {
        List<Object[]> accounts = em.createQuery("select a.id, a.accountNumber, a.name, a.accountType from Account a "
                + "where a.isGroup = false and a.isActive = true", Object[].class).getResultList();
        Set<String> cashIds = new HashSet<>(em.createQuery(
                "select m.accountId from SystemAccountMapping m where m.role = 'CASH'", String.class).getResultList());
        Map<String, String> roleOf = new HashMap<>();
        for (Object[] m : em.createQuery("select m.role, m.accountId from SystemAccountMapping m where m.role in :roles "
                + "order by m.sortOrder asc", Object[].class).setParameter("roles", RULE_ROLES).getResultList()) {
            roleOf.putIfAbsent((String) m[1], (String) m[0]);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] a : accounts) {
            String id = (String) a[0];
            String number = (String) a[1];
            AccountType type = (AccountType) a[3];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("code", number);
            m.put("name", a[2]);
            m.put("type", type.name());
            m.put("isCash", type == AccountType.ASSET && (cashIds.contains(id) || number.startsWith("1.1")));
            m.put("role", roleOf.get(id));
            out.add(m);
        }
        return AccountSort.sorted(out, m -> (String) m.get("code"));
    }

    /** Peta terpelajari (keterangan ternormalisasi → akun), terbanyak dipakai dulu. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> learnedMaps(int limit) {
        List<Object[]> maps = em.createQuery("select m.id, m.pattern, m.txnType, m.accountId, m.hits, m.updatedAt "
                + "from CashBookLearnedMap m order by m.hits desc, m.updatedAt desc", Object[].class)
                .setMaxResults(limit).getResultList();
        Set<String> accIds = new LinkedHashSet<>();
        maps.forEach(m -> accIds.add((String) m[3]));
        Map<String, Object[]> accMap = new HashMap<>();
        if (!accIds.isEmpty()) {
            for (Object[] a : em.createQuery("select a.id, a.accountNumber, a.name from Account a where a.id in :ids", Object[].class)
                    .setParameter("ids", accIds).getResultList()) {
                accMap.put((String) a[0], a);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] m : maps) {
            Object[] acc = accMap.get((String) m[3]);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m[0]);
            row.put("pattern", m[1]);
            row.put("txnType", m[2]);
            row.put("accountId", m[3]);
            row.put("accountCode", acc != null ? acc[1] : null);
            row.put("accountName", acc != null ? acc[2] : null);
            row.put("hits", m[4]);
            row.put("updatedAt", m[5]);
            out.add(row);
        }
        return out;
    }

    /** GET /accounts — katalog akun lawan + akun kas + aturan jenis transaksi + batas + pola terpelajari. */
    @ErpTx(readOnly = true)
    public Map<String, Object> accountsCatalog() {
        List<Map<String, Object>> accounts = counterAccounts();
        ErpCashBookDraftService.RuleAccounts rules = drafts.resolveRuleAccounts();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accounts", accounts);
        out.put("cashAccounts", accounts.stream().filter(a -> Boolean.TRUE.equals(a.get("isCash"))).toList());
        out.put("rules", ErpJson.map("advanceAccountId", rules.advanceAccountId(),
                "defaultExpenseAccountId", rules.defaultExpenseAccountId(),
                "defaultIncomeAccountId", rules.defaultIncomeAccountId()));
        out.put("txnTypes", CashBookRules.txnTypeGuide());
        out.put("limits", ErpJson.map("maxRows", CashBookRules.ROW_LIMIT, "reviewThreshold", CashBookRules.REVIEW_THRESHOLD));
        out.put("learnedMaps", learnedMaps(300));
        return out;
    }
}
