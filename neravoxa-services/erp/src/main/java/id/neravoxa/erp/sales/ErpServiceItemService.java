package id.neravoxa.erp.sales;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.ServiceItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Layanan/jasa (service item) — port {@code routes/serviceItems.ts} + {@code services/serviceItemList.ts}.
 *
 * <p>Endpoint daftar historis mengembalikan {@code { data }} berisi semua baris (picker di modal
 * invoice/penawaran bergantung padanya); mode berhalaman {@code { data, total, page, limit }} dengan
 * sort whitelist hanya berlaku bila {@code page} dikirim.
 */
@Service
public class ErpServiceItemService {

    static final List<String> SORT_FIELDS = List.of("code", "name", "defaultRate");
    static final ListQuery.Sort DEFAULT_SORT = new ListQuery.Sort("code", false);
    /** Pesan P2025 Prisma (update baris yang tidak ada) di handleRouteError. */
    static final String NOT_FOUND = "Data terkait tidak ditemukan. Periksa referensi yang dipilih.";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET /service-items — mode lama (tanpa page) atau berhalaman (page dikirim). */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        if (query.containsKey("page")) {
            JpqlFilter where = pagedWhere(query);
            ListQuery.Paging paging = ListQuery.paging(query, 25, 200);
            ListQuery.Sort sort = ListQuery.sort(query.get("sort"), SORT_FIELDS, DEFAULT_SORT);
            ErpQuery<ServiceItem> q = where.applyTo(ErpQuery.of(em, ServiceItem.class, "s")).orderBy(orderBy(sort));
            long total = q.count();
            List<ServiceItem> rows = q.list(paging.skip(), paging.limit());
            return ListQuery.result(withAccounts(rows), total, paging);
        }

        // Mode lama: isActive dikirim → === 'true'; search apa adanya (tanpa trim).
        JpqlFilter where = new JpqlFilter("ServiceItem", "s");
        if (query.containsKey("isActive")) {
            where.and("s.isActive = :isActive", "isActive", "true".equals(query.get("isActive")));
        }
        String search = query.get("search");
        if (search != null && !search.isEmpty()) {
            where.and("lower(s.code) like :search or lower(s.name) like :search",
                    "search", "%" + ErpQuery.escapeLike(search.toLowerCase(Locale.ROOT)) + "%");
        }
        List<ServiceItem> rows = where.applyTo(ErpQuery.of(em, ServiceItem.class, "s")).orderBy("s.code asc").list();
        return Map.of("data", withAccounts(rows));
    }

    /** Port {@code buildServiceItemWhere}: isActive (true|false) dan search (kode/nama). */
    static JpqlFilter pagedWhere(Map<String, String> query) {
        JpqlFilter where = new JpqlFilter("ServiceItem", "s");
        String isActive = ListQuery.str(query, "isActive");
        if (isActive != null) {
            if (!isActive.equals("true") && !isActive.equals("false")) {
                throw ErpException.business("Filter isActive harus true atau false.");
            }
            where.and("s.isActive = :isActive", "isActive", isActive.equals("true"));
        }
        String search = ListQuery.str(query, "search");
        if (search != null) {
            where.and("lower(s.code) like :search or lower(s.name) like :search", "search", ErpQuery.contains(search));
        }
        return where;
    }

    /** Port {@code serviceItemOrderBy}: kode saja, atau kolom lain + tie-breaker kode menaik. */
    static String orderBy(ListQuery.Sort sort) {
        String dir = sort.dir();
        return switch (sort.field()) {
            case "code" -> "s.code " + dir;
            case "defaultRate" -> "s.defaultRate " + dir + " nulls last, s.code asc";
            default -> "s." + sort.field() + " " + dir + ", s.code asc";
        };
    }

    /** Baris + {@code account: { select: { id, accountNumber, name } }}. */
    private List<ObjectNode> withAccounts(List<ServiceItem> rows) {
        Map<String, Map<String, Object>> accounts = SalesJson.briefs(em,
                "select a.id, a.accountNumber, a.name from Account a where a.id in :ids",
                rows.stream().map(ServiceItem::getAccountId).toList(), "id", "accountNumber", "name");
        List<ObjectNode> out = new ArrayList<>();
        for (ServiceItem s : rows) {
            ObjectNode n = SalesJson.obj(s);
            n.set("account", SalesJson.relation(accounts, s.getAccountId()));
            out.add(n);
        }
        return out;
    }

    /** POST /service-items — kode unik (duplikat → 400 seperti P2002). */
    @ErpTx
    public ObjectNode create(ServiceItemRequests.Create body) {
        ServiceItem s = new ServiceItem();
        s.setCode(body.code());
        s.setName(body.name());
        s.setUnit(body.unit() != null ? body.unit() : "Jasa");
        s.setDefaultRate(body.defaultRate());
        s.setAccountId(body.accountId());
        s.setDescription(body.description());
        em.persist(s);
        SalesSupport.flush(em);
        return withAccounts(List.of(s)).get(0);
    }

    /** PATCH /service-items/:id — hanya field yang dikirim. */
    @ErpTx
    public ObjectNode update(String id, ServiceItemRequests.Patch body) {
        ServiceItem s = em.find(ServiceItem.class, id);
        if (s == null) {
            throw ErpException.business(NOT_FOUND);
        }
        if (body.has("code")) {
            s.setCode(body.code());
        }
        if (body.has("name")) {
            s.setName(body.name());
        }
        if (body.has("unit")) {
            s.setUnit(body.unit());
        }
        if (body.has("defaultRate")) {
            // z.coerce.number(): null → 0.
            s.setDefaultRate(body.defaultRate() == null ? BigDecimal.ZERO : body.defaultRate());
        }
        if (body.has("accountId")) {
            s.setAccountId(body.accountId());
        }
        if (body.has("description")) {
            s.setDescription(body.description());
        }
        if (body.has("isActive")) {
            s.setIsActive(body.isActive());
        }
        s.setUpdatedAt(Instant.now());
        SalesSupport.flush(em);
        return withAccounts(List.of(s)).get(0);
    }

    /** DELETE /service-items/:id — soft delete (isActive = false). */
    @ErpTx
    public Map<String, Object> deactivate(String id) {
        ServiceItem s = em.find(ServiceItem.class, id);
        if (s == null) {
            throw ErpException.business(NOT_FOUND);
        }
        s.setIsActive(false);
        s.setUpdatedAt(Instant.now());
        SalesSupport.flush(em);
        return Map.of("success", true);
    }
}
