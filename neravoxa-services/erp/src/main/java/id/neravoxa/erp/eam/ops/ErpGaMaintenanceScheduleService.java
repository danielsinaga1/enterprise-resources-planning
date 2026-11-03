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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jadwal pemeliharaan preventif GA — bagian {@code /schedules} dari {@code routes/ga/maintenance.ts}.
 * Jadwal tidak membuat WO otomatis: WO preventif dibuat pengguna (POST /work-orders dengan scheduleId),
 * dan penyelesaian WO itu menggulirkan {@code nextDueAt}/{@code nextDueAtKm}/{@code nextDueAtHours} (lihat
 * ErpGaWorkOrderService.complete). Jadwal ber-interval km memakai odometer terakhir kendaraan ({@code ga_vehicle_data}),
 * jadwal ber-interval jam memakai pembacaan hour meter terakhir ({@link ErpGaMeterReadingService}); aturan jatuh
 * tempo ada di {@link MaintenanceDue}.
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
        List<String> assetIds = rows.stream().map(GaMaintenanceSchedule::getAssetId).distinct().toList();
        Map<String, Integer> odometers = odometers(assetIds);
        Map<String, Integer> meters = ErpGaMeterReadingService.currentHours(em, assetIds);
        return rows.stream().map(s -> {
            ObjectNode o = GaOpsJson.scalars(s);
            o.set("asset", ErpJson.tree(GaOpsJson.assetWithPic(s.getAsset())));
            putInt(o, "currentKm", odometers.get(s.getAssetId()));
            putInt(o, "currentHours", meters.get(s.getAssetId()));
            return o;
        }).toList();
    }

    private static void putInt(ObjectNode o, String field, Integer v) {
        if (v == null) {
            o.putNull(field);
        } else {
            o.put(field, v);
        }
    }

    /** Odometer terakhir per aset (hanya aset yang punya data kendaraan dengan odometer). */
    private Map<String, Integer> odometers(List<String> assetIds) {
        Map<String, Integer> out = new HashMap<>();
        if (assetIds.isEmpty()) {
            return out;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT asset_id, odometer_terakhir FROM ga_vehicle_data "
                        + "WHERE asset_id IN (:ids) AND odometer_terakhir IS NOT NULL")
                .setParameter("ids", assetIds)
                .getResultList();
        rows.forEach(r -> out.put((String) r[0], ((Number) r[1]).intValue()));
        return out;
    }

    /** Odometer terakhir satu aset, null bila belum tercatat. */
    static Integer currentKm(EntityManager em, String assetId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("SELECT odometer_terakhir FROM ga_vehicle_data "
                        + "WHERE asset_id = :id AND odometer_terakhir IS NOT NULL")
                .setParameter("id", assetId)
                .getResultList();
        return rows.isEmpty() ? null : ((Number) rows.get(0)).intValue();
    }

    /** Km jatuh tempo awal: odometer terakhir + interval, null bila odometer belum tercatat. */
    private Integer initialDueKm(String assetId, Integer intervalKm) {
        Integer km = intervalKm == null ? null : currentKm(em, assetId);
        return km == null ? null : km + intervalKm;
    }

    /** Jam jatuh tempo awal: hour meter terakhir + interval, null bila meter belum pernah dicatat. */
    private Integer initialDueHours(String assetId, Integer intervalHours) {
        Integer h = intervalHours == null ? null : ErpGaMeterReadingService.currentHours(em, assetId);
        return h == null ? null : h + intervalHours;
    }

    record CreateInput(String assetId, Integer intervalDays, Integer intervalHours, Integer intervalKm,
                       Integer nextDueAtKm, Integer nextDueAtHours, String nextDueAt, String notes, boolean isActive) {
    }

    /** {@code MaintenanceScheduleSchema} (refine: salah satu interval wajib). */
    static CreateInput parseCreate(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String assetId = GaZod.reqString(GaZod.field(b, "assetId"), 1, "assetId wajib diisi.", 0);
        Integer days = GaZod.optPositiveInt(GaZod.field(b, "intervalDays"));
        Integer hours = GaZod.optPositiveInt(GaZod.field(b, "intervalHours"));
        Integer km = GaZod.optPositiveInt(GaZod.field(b, "intervalKm"));
        Integer dueKm = GaZod.optPositiveInt(GaZod.field(b, "nextDueAtKm"));
        Integer dueHours = GaZod.optPositiveInt(GaZod.field(b, "nextDueAtHours"));
        String nextDueAt = GaZod.reqString(GaZod.field(b, "nextDueAt"), 1, "nextDueAt wajib diisi.", 0);
        String notes = GaZod.optString(GaZod.field(b, "notes"), 0, null, 0);
        boolean isActive = GaZod.boolDefault(GaZod.field(b, "isActive"), true);
        if (days == null && hours == null && km == null) {
            throw ErpException.business("Isi salah satu interval (hari, jam, atau km).");
        }
        return new CreateInput(assetId, days, hours, km, km == null ? null : dueKm, hours == null ? null : dueHours,
                nextDueAt, notes, isActive);
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
        s.setNextDueAtKm(in.nextDueAtKm() != null ? in.nextDueAtKm() : initialDueKm(in.assetId(), in.intervalKm()));
        s.setNextDueAtHours(in.nextDueAtHours() != null ? in.nextDueAtHours() : initialDueHours(in.assetId(), in.intervalHours()));
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
                       Integer nextDueAtKm, Integer nextDueAtHours, String nextDueAt, String notes, Boolean isActive) {
        boolean has(String f) {
            return !GaZod.absent(GaZod.field(body, f));
        }
    }

    static UpdateInput parseUpdate(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        Integer days = GaZod.optPositiveInt(GaZod.field(b, "intervalDays"));
        Integer hours = GaZod.optPositiveInt(GaZod.field(b, "intervalHours"));
        Integer km = GaZod.optPositiveInt(GaZod.field(b, "intervalKm"));
        Integer dueKm = GaZod.optPositiveInt(GaZod.field(b, "nextDueAtKm"));
        Integer dueHours = GaZod.optPositiveInt(GaZod.field(b, "nextDueAtHours"));
        String nextDueAt = GaZod.optStringNotNull(GaZod.field(b, "nextDueAt"), 1, null, 0);
        String notes = GaZod.optString(GaZod.field(b, "notes"), 0, null, 0);
        Boolean isActive = GaZod.optBool(GaZod.field(b, "isActive"));
        return new UpdateInput(b, days, hours, km, dueKm, dueHours, nextDueAt, notes, isActive);
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
        if (s.getIntervalKm() == null) {
            s.setNextDueAtKm(null);
            s.setLastDoneAtKm(null);
        } else if (in.has("nextDueAtKm") && in.nextDueAtKm() != null) {
            s.setNextDueAtKm(in.nextDueAtKm());
        } else if (s.getNextDueAtKm() == null) {
            s.setNextDueAtKm(initialDueKm(s.getAssetId(), s.getIntervalKm()));
        }
        if (s.getIntervalHours() == null) {
            s.setNextDueAtHours(null);
            s.setLastDoneAtHours(null);
        } else if (in.has("nextDueAtHours") && in.nextDueAtHours() != null) {
            s.setNextDueAtHours(in.nextDueAtHours());
        } else if (s.getNextDueAtHours() == null) {
            s.setNextDueAtHours(initialDueHours(s.getAssetId(), s.getIntervalHours()));
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
