package id.neravoxa.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.ProductionCostStandard;
import id.neravoxa.erp.production.ProductionRequests.CostStandardCreateRequest;
import id.neravoxa.erp.production.ProductionRequests.CostStandardUpdateRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standar biaya produksi (master rate komponen biaya) — port
 * {@code routes/inventory/productionCostStandards.ts}. Akun biaya hanya tag informatif: modul
 * produksi TIDAK memposting ke akun ini.
 */
@Service
public class ErpProductionCostStandardService {

    static final String NOT_FOUND = "Standar biaya produksi tidak ditemukan.";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * GET / — filter active, costSide, itemId, warehouseId. Filter item/gudang juga mencocokkan
     * standar global (NULL); bila keduanya dikirim, tiap dimensi dicocokkan sendiri-sendiri.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (query.containsKey("active")) {
            where.add("s.isActive = :active");
            params.put("active", "true".equals(query.get("active")));
        }
        if (query.get("costSide") != null) {
            where.add("s.costSide = :side");
            params.put("side", query.get("costSide"));
        }
        String itemId = query.get("itemId");
        if (itemId != null && !itemId.isEmpty()) {
            where.add("(s.itemId = :item or s.itemId is null)");
            params.put("item", itemId);
        }
        String warehouseId = query.get("warehouseId");
        if (warehouseId != null && !warehouseId.isEmpty()) {
            where.add("(s.warehouseId = :wh or s.warehouseId is null)");
            params.put("wh", warehouseId);
        }
        TypedQuery<ProductionCostStandard> q = em.createQuery("select s from ProductionCostStandard s "
                + "left join fetch s.costAccount left join fetch s.item left join fetch s.warehouse"
                + (where.isEmpty() ? "" : " where " + String.join(" and ", where))
                + " order by s.costSide asc, s.name asc", ProductionCostStandard.class);
        params.forEach(q::setParameter);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", q.getResultList().stream().map(ErpProductionCostStandardService::toJson).toList());
        return out;
    }

    @ErpTx
    public Map<String, Object> create(CostStandardCreateRequest body) {
        ProductionCostStandard s = new ProductionCostStandard();
        s.setName(body.name());
        s.setDescription(body.description());
        s.setCostSide(body.costSide());
        s.setBasis(body.basis());
        s.setDefaultRate(body.defaultRate());
        s.setCostAccountId(body.costAccountId());
        s.setItemId(body.itemId());
        s.setWarehouseId(body.warehouseId());
        em.persist(s);
        em.flush();
        return reload(s.getId());
    }

    /** PUT /:id — hanya field yang dikirim yang diubah. */
    @ErpTx
    public Map<String, Object> update(String id, CostStandardUpdateRequest body, JsonNode raw) {
        ProductionCostStandard s = em.find(ProductionCostStandard.class, id);
        if (s == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        if (raw.has("name")) {
            s.setName(body.name());
        }
        if (raw.has("description")) {
            s.setDescription(body.description());
        }
        if (raw.has("costSide")) {
            s.setCostSide(body.costSide());
        }
        if (raw.has("basis")) {
            s.setBasis(body.basis());
        }
        if (raw.has("defaultRate")) {
            s.setDefaultRate(body.defaultRate());
        }
        if (raw.has("costAccountId")) {
            s.setCostAccountId(body.costAccountId());
        }
        if (raw.has("itemId")) {
            s.setItemId(body.itemId());
        }
        if (raw.has("warehouseId")) {
            s.setWarehouseId(body.warehouseId());
        }
        if (raw.has("isActive")) {
            s.setIsActive(body.isActive());
        }
        em.flush();
        return reload(id);
    }

    /** DELETE /:id — standar yang sudah dipakai run dinonaktifkan, selain itu dihapus permanen. */
    @ErpTx
    public Map<String, Object> delete(String id) {
        long usage = em.createQuery("select count(c) from ProductionRunCost c where c.standardId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        ProductionCostStandard s = em.find(ProductionCostStandard.class, id);
        if (s == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (usage > 0) {
            s.setIsActive(false);
            em.flush();
            out.put("message", "Standar dinonaktifkan karena sudah dipakai di " + usage + " produksi.");
            out.put("deactivated", true);
            return out;
        }
        em.remove(s);
        em.flush();
        out.put("message", "Standar biaya produksi dihapus.");
        out.put("deleted", true);
        return out;
    }

    /** Muat ulang dengan include costAccount/item/warehouse (select ringkas). */
    private Map<String, Object> reload(String id) {
        em.clear();
        ProductionCostStandard s = em.createQuery("select s from ProductionCostStandard s left join fetch s.costAccount "
                        + "left join fetch s.item left join fetch s.warehouse where s.id = :id", ProductionCostStandard.class)
                .setParameter("id", id).getSingleResult();
        return toJson(s);
    }

    static Map<String, Object> toJson(ProductionCostStandard s) {
        Map<String, Object> m = ProdJson.scalars(s);
        m.put("costAccount", ProdJson.pick(s.getCostAccount(), "id", "accountNumber", "name"));
        m.put("item", ProdJson.pick(s.getItem(), "id", "code", "name", "unit"));
        m.put("warehouse", ProdJson.pick(s.getWarehouse(), "id", "code", "name"));
        return m;
    }
}
