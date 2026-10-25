package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.GaAsset;
import id.neravoxa.erp.entity.GaMaintenanceSchedule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Jadwal pemeliharaan preventif GA — bagian {@code /schedules} dari {@code routes/ga/maintenance.ts}.
 * Jadwal tidak membuat WO otomatis: WO preventif dibuat pengguna (POST /work-orders dengan scheduleId),
 * dan penyelesaian WO itu menggulirkan {@code nextDueAt} (lihat ErpGaWorkOrderService.complete).
 */
@Service
public class ErpGaMaintenanceScheduleService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET /schedules — filter assetId / active, urut nextDueAt naik, dengan aset ringkas. */
    @ErpTx(readOnly = true)
    public List<ObjectNode> list(Map<String, String> query) {
        String assetId = query.get("assetId");
        String active = query.get("active");
        GaWhere w = new GaWhere()
                .andIf(assetId != null && !assetId.isEmpty(), "s.assetId = :assetId", "assetId", assetId)
                .andIf("true".equals(active), "s.isActive = true")
                .andIf("false".equals(active), "s.isActive = false");
        List<GaMaintenanceSchedule> rows = w.bind(em.createQuery("select s from GaMaintenanceSchedule s "
                + "left join fetch s.asset" + w.clause() + " order by s.nextDueAt asc", GaMaintenanceSchedule.class))
                .getResultList();
        return rows.stream().map(s -> {
            ObjectNode o = GaOpsJson.scalars(s);
            o.set("asset", ErpJson.tree(GaOpsJson.assetWithPic(s.getAsset())));
            return o;
        }).toList();
    }

    record CreateInput(String assetId, Integer intervalDays, Integer intervalHours, Integer intervalKm,
                       String nextDueAt, String notes, boolean isActive) {
    }

    /** {@code MaintenanceScheduleSchema} (refine: salah satu interval wajib). */
    static CreateInput parseCreate(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String assetId = GaZod.reqString(GaZod.field(b, "assetId"), 1, "assetId wajib diisi.", 0);
        Integer days = GaZod.optPositiveInt(GaZod.field(b, "intervalDays"));
        Integer hours = GaZod.optPositiveInt(GaZod.field(b, "intervalHours"));
        Integer km = GaZod.optPositiveInt(GaZod.field(b, "intervalKm"));
        String nextDueAt = GaZod.reqString(GaZod.field(b, "nextDueAt"), 1, "nextDueAt wajib diisi.", 0);
        String notes = GaZod.optString(GaZod.field(b, "notes"), 0, null, 0);
        boolean isActive = GaZod.boolDefault(GaZod.field(b, "isActive"), true);
        if (days == null && hours == null && km == null) {
            throw ErpException.business("Isi salah satu interval (hari, jam, atau km).");
        }
        return new CreateInput(assetId, days, hours, km, nextDueAt, notes, isActive);
    }

    /** {@code new Date(str)} tak valid → galat Prisma → 500 dengan pesan cadangan rute. */
    static Instant date(String raw, String fallbackMessage) {
        Instant v = ErpDates.tryInstant(raw);
        if (v == null) {
            throw ErpException.of(500, fallbackMessage);
        }
        return v;
    }

    /** POST /schedules → 201 {@code { id }}. */
    @ErpTx
    public Map<String, Object> create(CreateInput in, String userId) {
        if (em.find(GaAsset.class, in.assetId()) == null) {
            throw ErpException.business("Aset tidak ditemukan.");
        }
        GaMaintenanceSchedule s = new GaMaintenanceSchedule();
        s.setAssetId(in.assetId());
        s.setIntervalDays(in.intervalDays());
        s.setIntervalHours(in.intervalHours());
        s.setIntervalKm(in.intervalKm());
        s.setNextDueAt(date(in.nextDueAt(), "Gagal membuat jadwal maintenance."));
        s.setNotes(in.notes());
        s.setIsActive(in.isActive());
        s.setCreatedBy(userId);
        em.persist(s);
        em.flush();
        return GaOpsJson.map("id", s.getId());
    }

    /** {@code UpdateScheduleSchema}: semua opsional; field absen tidak diubah, null = kosongkan. */
    record UpdateInput(JsonNode body, Integer intervalDays, Integer intervalHours, Integer intervalKm,
                       String nextDueAt, String notes, Boolean isActive) {
        boolean has(String f) {
            return !GaZod.absent(GaZod.field(body, f));
        }
    }

    static UpdateInput parseUpdate(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        Integer days = GaZod.optPositiveInt(GaZod.field(b, "intervalDays"));
        Integer hours = GaZod.optPositiveInt(GaZod.field(b, "intervalHours"));
        Integer km = GaZod.optPositiveInt(GaZod.field(b, "intervalKm"));
        String nextDueAt = GaZod.optStringNotNull(GaZod.field(b, "nextDueAt"), 1, null, 0);
        String notes = GaZod.optString(GaZod.field(b, "notes"), 0, null, 0);
        Boolean isActive = GaZod.optBool(GaZod.field(b, "isActive"));
        return new UpdateInput(b, days, hours, km, nextDueAt, notes, isActive);
    }

    /** PUT /schedules/:id → {@code { id }}; id tak dikenal = P2025. */
    @ErpTx
    public Map<String, Object> update(String id, UpdateInput in) {
        GaMaintenanceSchedule s = em.find(GaMaintenanceSchedule.class, id);
        if (s == null) {
            throw ErpException.business("Data terkait tidak ditemukan. Periksa referensi yang dipilih.");
        }
        if (in.has("intervalDays")) {
            s.setIntervalDays(in.intervalDays());
        }
        if (in.has("intervalHours")) {
            s.setIntervalHours(in.intervalHours());
        }
        if (in.has("intervalKm")) {
            s.setIntervalKm(in.intervalKm());
        }
        if (in.nextDueAt() != null && !in.nextDueAt().isEmpty()) {
            s.setNextDueAt(date(in.nextDueAt(), "Gagal update jadwal maintenance."));
        }
        if (in.has("notes")) {
            s.setNotes(in.notes());
        }
        if (in.isActive() != null) {
            s.setIsActive(in.isActive());
        }
        s.setUpdatedAt(Instant.now());
        em.flush();
        return GaOpsJson.map("id", s.getId());
    }
}
