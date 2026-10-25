package id.neravoxa.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.ProductionProcess;
import id.neravoxa.erp.production.ProductionRequests.ProcessCreateRequest;
import id.neravoxa.erp.production.ProductionRequests.ProcessUpdateRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Master proses produksi (traceability) — port {@code routes/inventory/productionProcesses.ts}. */
@Service
public class ErpProductionProcessService {

    static final String NOT_FOUND = "Proses produksi tidak ditemukan.";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET / — {@code ?active=true|false}; urut sortOrder lalu nama. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(String active) {
        TypedQuery<ProductionProcess> q = em.createQuery("select p from ProductionProcess p"
                + (active != null ? " where p.isActive = :a" : "") + " order by p.sortOrder asc, p.name asc",
                ProductionProcess.class);
        if (active != null) {
            q.setParameter("a", "true".equals(active));
        }
        List<Map<String, Object>> data = q.getResultList().stream().map(ProdJson::scalars).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        return out;
    }

    /** POST / — nama di-trim; nama ganda → 409 (pesan memakai nama mentah body). */
    @ErpTx
    public Map<String, Object> create(ProcessCreateRequest body, JsonNode raw) {
        ProductionProcess p = new ProductionProcess();
        p.setName(body.name().trim());
        p.setDescription(body.description());
        p.setSortOrder(body.sortOrder() != null ? body.sortOrder().intValueExact() : 0);
        em.persist(p);
        ProdDb.flushOrConflict(em, "Proses \"" + ProdJson.jsText(raw, "name") + "\" sudah ada.");
        return ProdJson.scalars(p);
    }

    /** PUT /:id — hanya field yang dikirim yang diubah. */
    @ErpTx
    public Map<String, Object> update(String id, ProcessUpdateRequest body, JsonNode raw) {
        ProductionProcess p = em.find(ProductionProcess.class, id);
        if (p == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        if (raw.has("name")) {
            p.setName(body.name().trim());
        }
        if (raw.has("description")) {
            p.setDescription(body.description());
        }
        if (raw.has("sortOrder")) {
            p.setSortOrder(body.sortOrder() != null ? body.sortOrder().intValueExact() : 0);
        }
        if (raw.has("isActive")) {
            p.setIsActive(body.isActive());
        }
        ProdDb.flushOrConflict(em, "Proses \"" + ProdJson.jsText(raw, "name") + "\" sudah ada.");
        return ProdJson.scalars(p);
    }

    /**
     * DELETE /:id — proses yang sudah dipakai run dinonaktifkan (histori tetap tertelusur), selain
     * itu dihapus permanen.
     */
    @ErpTx
    public Map<String, Object> delete(String id) {
        long usage = em.createQuery("select count(x) from ProductionRunProcess x where x.processId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        ProductionProcess p = em.find(ProductionProcess.class, id);
        if (p == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (usage > 0) {
            p.setIsActive(false);
            em.flush();
            out.put("message", "Proses dinonaktifkan karena sudah dipakai di " + usage + " run produksi.");
            out.put("deactivated", true);
            return out;
        }
        em.remove(p);
        em.flush();
        out.put("message", "Proses produksi dihapus.");
        out.put("deleted", true);
        return out;
    }
}
