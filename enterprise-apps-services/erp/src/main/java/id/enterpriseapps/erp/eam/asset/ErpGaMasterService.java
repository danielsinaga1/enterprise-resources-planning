package id.enterpriseapps.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.GaAssetCategory;
import id.enterpriseapps.erp.entity.GaBranch;
import id.enterpriseapps.erp.entity.GaCostCenter;
import id.enterpriseapps.erp.entity.GaDocumentType;
import id.enterpriseapps.erp.entity.GaInventoryCategory;
import id.enterpriseapps.erp.entity.GaInventoryType;
import id.enterpriseapps.erp.entity.GaLocation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Master data GA (cabang, cost center, kategori aset, jenis dokumen, kategori inventory, lokasi) — port
 * {@code routes/ga/master.ts}. Skema Zod {@code CodeNameSchema} & turunannya dicek berurutan; update memakai
 * {@code .partial()} sehingga field yang tidak dikirim dibiarkan (kecuali {@code category} jenis dokumen yang
 * di sumber memang dikosongkan bila absen).
 */
@Slf4j
@Service
public class ErpGaMasterService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Bagian {@code CodeNameSchema}: code, name, isActive (opsional). */
    private record CodeName(String code, String name, Boolean isActive) {
    }

    private static CodeName codeName(GaZod z, boolean withActive) {
        String code = z.requiredString("code", 1, "Kode wajib diisi.", 50);
        String name = z.requiredString("name", 1, "Nama wajib diisi.", 200);
        Boolean isActive = withActive ? z.bool("isActive", true) : null;
        return new CodeName(code, name, isActive);
    }

    private static ArrayNode rows(Iterable<?> entities) {
        ArrayNode out = ErpJson.arrayNode();
        entities.forEach(e -> out.add(GaJs.scalars(e)));
        return out;
    }

    private <T> T require(Class<T> type, String id) {
        T e = em.find(type, id);
        if (e == null) {
            throw GaJs.p2025();
        }
        return e;
    }

    private void deleteById(Class<?> type, String id) {
        int n = em.createQuery("delete from " + type.getSimpleName() + " x where x.id = :id")
                .setParameter("id", id).executeUpdate();
        if (n == 0) {
            throw GaJs.p2025();
        }
    }

    // ───────────────────────────── Cabang ─────────────────────────────

    @ErpTx(readOnly = true)
    public ArrayNode branches() {
        return rows(em.createQuery("select b from GaBranch b order by b.code asc", GaBranch.class).getResultList());
    }

    @ErpTx
    public ObjectNode saveBranch(String id, JsonNode body) {
        GaZod z = GaZod.of(body, id != null);
        CodeName cn = codeName(z, true);
        String address = z.optString("address", null);
        z.throwIfInvalid();
        GaBranch b = id == null ? new GaBranch() : require(GaBranch.class, id);
        if (cn.code() != null) {
            b.setCode(cn.code());
        }
        if (cn.name() != null) {
            b.setName(cn.name());
        }
        if (cn.isActive() != null) {
            b.setIsActive(cn.isActive());
        }
        if (id == null || z.has("address")) {
            b.setAddress(address);
        }
        if (id == null) {
            em.persist(b);
        }
        em.flush();
        return GaJs.scalars(b);
    }

    @ErpTx
    public Map<String, Object> deleteBranch(String id) {
        deleteById(GaBranch.class, id);
        return ErpJson.map("message", "Cabang dihapus.");
    }

    // ───────────────────────────── Cost center ─────────────────────────────

    @ErpTx(readOnly = true)
    public ArrayNode costCenters() {
        ArrayNode out = ErpJson.arrayNode();
        for (GaCostCenter c : em.createQuery("select c from GaCostCenter c left join fetch c.branch order by c.code asc",
                GaCostCenter.class).getResultList()) {
            ObjectNode o = GaJs.scalars(c);
            o.set("branch", GaJs.pick(c.getBranch(), "code", "name"));
            out.add(o);
        }
        return out;
    }

    @ErpTx
    public ObjectNode saveCostCenter(String id, JsonNode body) {
        GaZod z = GaZod.of(body, id != null);
        CodeName cn = codeName(z, true);
        String branchId = z.requiredString("branchId", 1, "Cabang wajib dipilih.", null);
        z.throwIfInvalid();
        GaCostCenter c = id == null ? new GaCostCenter() : require(GaCostCenter.class, id);
        if (cn.code() != null) {
            c.setCode(cn.code());
        }
        if (cn.name() != null) {
            c.setName(cn.name());
        }
        if (cn.isActive() != null) {
            c.setIsActive(cn.isActive());
        }
        if (branchId != null) {
            c.setBranchId(branchId);
        }
        if (id == null) {
            em.persist(c);
        }
        em.flush();
        return GaJs.scalars(c);
    }

    @ErpTx
    public Map<String, Object> deleteCostCenter(String id) {
        deleteById(GaCostCenter.class, id);
        return ErpJson.map("message", "Cost center dihapus.");
    }

    // ───────────────────────────── Kategori aset ─────────────────────────────

    @ErpTx(readOnly = true)
    public ArrayNode assetCategories() {
        return rows(em.createQuery("select c from GaAssetCategory c order by c.code asc", GaAssetCategory.class)
                .getResultList());
    }

    /** Skema JSON (customFieldsSchema / maintenanceChecklistSchema) null/absen = tidak diubah. */
    @ErpTx
    public ObjectNode saveAssetCategory(String id, JsonNode body) {
        GaZod z = GaZod.of(body, id != null);
        CodeName cn = codeName(z, true);
        JsonNode customFieldsSchema = z.record("customFieldsSchema");
        JsonNode checklistSchema = z.record("maintenanceChecklistSchema");
        z.throwIfInvalid();
        GaAssetCategory c = id == null ? new GaAssetCategory() : require(GaAssetCategory.class, id);
        if (cn.code() != null) {
            c.setCode(cn.code());
        }
        if (cn.name() != null) {
            c.setName(cn.name());
        }
        if (cn.isActive() != null) {
            c.setIsActive(cn.isActive());
        }
        if (customFieldsSchema != null) {
            c.setCustomFieldsSchema(customFieldsSchema);
        }
        if (checklistSchema != null) {
            c.setMaintenanceChecklistSchema(checklistSchema);
        }
        if (id == null) {
            em.persist(c);
        }
        em.flush();
        return GaJs.scalars(c);
    }

    @ErpTx
    public Map<String, Object> deleteAssetCategory(String id) {
        deleteById(GaAssetCategory.class, id);
        return ErpJson.map("message", "Kategori aset dihapus.");
    }

    // ───────────────────────────── Jenis dokumen ─────────────────────────────

    @ErpTx(readOnly = true)
    public ArrayNode documentTypes() {
        return rows(em.createQuery("select t from GaDocumentType t order by t.code asc", GaDocumentType.class)
                .getResultList());
    }

    /** {@code category: input.category ?? null} — juga pada update (absen → dikosongkan), sesuai sumber. */
    @ErpTx
    public ObjectNode saveDocumentType(String id, JsonNode body) {
        GaZod z = GaZod.of(body, id != null);
        CodeName cn = codeName(z, true);
        String category = z.optString("category", null);
        JsonNode customFieldsSchema = z.record("customFieldsSchema");
        z.throwIfInvalid();
        GaDocumentType t = id == null ? new GaDocumentType() : require(GaDocumentType.class, id);
        if (cn.code() != null) {
            t.setCode(cn.code());
        }
        if (cn.name() != null) {
            t.setName(cn.name());
        }
        t.setCategory(category);
        if (cn.isActive() != null) {
            t.setIsActive(cn.isActive());
        }
        if (customFieldsSchema != null) {
            t.setCustomFieldsSchema(customFieldsSchema);
        }
        if (id == null) {
            em.persist(t);
        }
        em.flush();
        return GaJs.scalars(t);
    }

    @ErpTx
    public Map<String, Object> deleteDocumentType(String id) {
        deleteById(GaDocumentType.class, id);
        return ErpJson.map("message", "Jenis dokumen dihapus.");
    }

    // ───────────────────────────── Kategori inventory ─────────────────────────────

    @ErpTx(readOnly = true)
    public ArrayNode inventoryCategories() {
        return rows(em.createQuery("select c from GaInventoryCategory c order by c.code asc", GaInventoryCategory.class)
                .getResultList());
    }

    @ErpTx
    public ObjectNode saveInventoryCategory(String id, JsonNode body) {
        GaZod z = GaZod.of(body, id != null);
        CodeName cn = codeName(z, false);
        String type = z.enumValue("type", null, false, false, "SPAREPART", "CONSUMABLE");
        z.throwIfInvalid();
        GaInventoryCategory c = id == null ? new GaInventoryCategory() : require(GaInventoryCategory.class, id);
        if (cn.code() != null) {
            c.setCode(cn.code());
        }
        if (cn.name() != null) {
            c.setName(cn.name());
        }
        if (type != null) {
            c.setType(GaInventoryType.valueOf(type));
        }
        if (id == null) {
            em.persist(c);
        }
        em.flush();
        return GaJs.scalars(c);
    }

    @ErpTx
    public Map<String, Object> deleteInventoryCategory(String id) {
        deleteById(GaInventoryCategory.class, id);
        return ErpJson.map("message", "Kategori inventory dihapus.");
    }

    // ───────────────────────────── Lokasi ─────────────────────────────

    @ErpTx(readOnly = true)
    public ArrayNode locations() {
        ArrayNode out = ErpJson.arrayNode();
        for (GaLocation l : em.createQuery("select l from GaLocation l left join fetch l.branch order by l.code asc",
                GaLocation.class).getResultList()) {
            ObjectNode o = GaJs.scalars(l);
            o.set("branch", GaJs.pick(l.getBranch(), "code", "name"));
            out.add(o);
        }
        return out;
    }

    @ErpTx
    public ObjectNode saveLocation(String id, JsonNode body) {
        GaZod z = GaZod.of(body, id != null);
        CodeName cn = codeName(z, false);
        String branchId = z.requiredString("branchId", 1, "Cabang wajib dipilih.", null);
        z.throwIfInvalid();
        GaLocation l = id == null ? new GaLocation() : require(GaLocation.class, id);
        if (cn.code() != null) {
            l.setCode(cn.code());
        }
        if (cn.name() != null) {
            l.setName(cn.name());
        }
        if (branchId != null) {
            l.setBranchId(branchId);
        }
        if (id == null) {
            em.persist(l);
        }
        em.flush();
        return GaJs.scalars(l);
    }

    @ErpTx
    public Map<String, Object> deleteLocation(String id) {
        deleteById(GaLocation.class, id);
        return ErpJson.map("message", "Lokasi dihapus.");
    }
}
