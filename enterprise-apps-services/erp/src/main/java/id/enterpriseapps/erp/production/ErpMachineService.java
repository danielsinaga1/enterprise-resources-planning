package id.enterpriseapps.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.Machine;
import id.enterpriseapps.erp.production.ProductionRequests.MachineCreateRequest;
import id.enterpriseapps.erp.production.ProductionRequests.MachineUpdateRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Master mesin untuk OEE — port {@code routes/inventory/machines.ts}. */
@Service
public class ErpMachineService {

    static final String NOT_FOUND = "Mesin tidak ditemukan.";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET / — {@code ?active=true|false} (tanpa parameter = semua), aktif dulu lalu nama. */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(String active) {
        TypedQuery<Machine> q = em.createQuery("select m from Machine m"
                + (active != null ? " where m.isActive = :a" : "") + " order by m.isActive desc, m.name asc", Machine.class);
        if (active != null) {
            q.setParameter("a", "true".equals(active));
        }
        List<Map<String, Object>> data = q.getResultList().stream().map(ProdJson::scalars).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        return out;
    }

    @ErpTx(readOnly = true)
    public Map<String, Object> get(String id) {
        Machine m = em.find(Machine.class, id);
        if (m == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        return ProdJson.scalars(m);
    }

    /** POST / — kode & nama di-trim; kode ganda → 409 (pesan memakai kode mentah body). */
    @ErpTx
    public Map<String, Object> create(MachineCreateRequest body, JsonNode raw) {
        Machine m = new Machine();
        m.setCode(body.code().trim());
        m.setName(body.name().trim());
        m.setIdealRatePerHour(body.idealRatePerHour());
        m.setUnit(body.unit() != null ? body.unit() : "kg");
        m.setIsActive(body.isActive() != null ? body.isActive() : Boolean.TRUE);
        m.setNotes(body.notes());
        em.persist(m);
        ProdDb.flushOrConflict(em, "Kode mesin \"" + ProdJson.jsText(raw, "code") + "\" sudah ada.");
        return ProdJson.scalars(m);
    }

    /** PUT /:id — hanya field yang dikirim yang diubah. */
    @ErpTx
    public Map<String, Object> update(String id, MachineUpdateRequest body, JsonNode raw) {
        Machine m = em.find(Machine.class, id);
        if (m == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        if (raw.has("code")) {
            m.setCode(body.code().trim());
        }
        if (raw.has("name")) {
            m.setName(body.name().trim());
        }
        if (raw.has("idealRatePerHour")) {
            m.setIdealRatePerHour(body.idealRatePerHour());
        }
        if (raw.has("unit")) {
            m.setUnit(body.unit());
        }
        if (raw.has("isActive")) {
            m.setIsActive(body.isActive());
        }
        if (raw.has("notes")) {
            m.setNotes(body.notes());
        }
        ProdDb.flushOrConflict(em, "Kode mesin \"" + ProdJson.jsText(raw, "code") + "\" sudah ada.");
        return ProdJson.scalars(m);
    }

    /**
     * DELETE /:id — ditolak (409) bila mesin sudah dipakai run atau downtime agar histori OEE tetap
     * bisa ditelusuri; sarankan nonaktifkan.
     */
    @ErpTx
    public Map<String, Object> delete(String id) {
        long runCount = em.createQuery("select count(r) from ProductionRun r where r.machineId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        long downtimeCount = em.createQuery("select count(d) from ProductionDowntime d where d.machineId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (runCount > 0 || downtimeCount > 0) {
            throw ErpException.conflict("Mesin tidak bisa dihapus karena sudah dipakai di " + runCount + " proses produksi"
                    + (downtimeCount > 0 ? " dan " + downtimeCount + " catatan downtime" : "")
                    + ". Nonaktifkan mesin saja agar histori OEE tetap utuh.");
        }
        Machine m = em.find(Machine.class, id);
        if (m == null) {
            throw ErpException.notFound(NOT_FOUND);
        }
        em.remove(m);
        em.flush();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Mesin dihapus.");
        out.put("deleted", true);
        return out;
    }
}
