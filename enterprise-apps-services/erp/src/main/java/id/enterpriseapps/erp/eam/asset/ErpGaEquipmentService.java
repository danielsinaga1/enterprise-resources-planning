package id.enterpriseapps.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.GaCalibrationLog;
import id.enterpriseapps.erp.entity.GaCalibrationMode;
import id.enterpriseapps.erp.entity.GaCalibrationResult;
import id.enterpriseapps.erp.entity.GaEquipmentData;
import id.enterpriseapps.erp.entity.GaWeighingUnit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Peralatan (timbangan): data alat (1-1 per aset) dan log kalibrasi — port {@code routes/ga/equipment.ts}.
 * Log kalibrasi baru menyinkronkan lastCalibratedAt & nextCalibrationDue (= validUntil) di data alat.
 */
@Service
public class ErpGaEquipmentService {

    static final String[] WEIGHING_UNITS = {"KG", "GRAM", "TON", "LBS"};
    static final String[] CALIB_RESULTS = {"PASS", "FAIL", "CONDITIONAL"};
    static final String[] CALIB_MODES = {"ON_SITE", "SHIPPED"};

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private List<GaEquipmentData> findData(String assetId) {
        return em.createQuery("select e from GaEquipmentData e where e.assetId = :id", GaEquipmentData.class)
                .setParameter("id", assetId).getResultList();
    }

    @ErpTx(readOnly = true)
    public JsonNode equipmentData(String assetId) {
        List<GaEquipmentData> rows = findData(assetId);
        return rows.isEmpty() ? NullNode.getInstance() : GaJs.scalars(rows.get(0));
    }

    /** Upsert; nextCalibrationDue = lastCalibratedAt + intervalMonths (setMonth JavaScript). */
    @ErpTx
    public ObjectNode saveEquipmentData(String assetId, JsonNode body) {
        GaZod z = GaZod.of(body, false);
        BigDecimal maxCapacity = z.number("maxCapacity", true, false);
        String unit = z.enumValue("unit", null, true, true, WEIGHING_UNITS);
        BigDecimal accuracy = z.number("accuracy", true, false);
        String classLabel = z.optString("classLabel", 50);
        String defaultCalibrator = z.optString("defaultCalibrator", 200);
        Integer intervalMonths = z.has("intervalMonths")
                ? z.integer("intervalMonths", true, null, null, true) : Integer.valueOf(12);
        String lastCalibratedRaw = z.optString("lastCalibratedAt", null);
        String notes = z.optString("notes", null);
        z.throwIfInvalid();

        Instant lastCalibratedAt = lastCalibratedRaw == null || lastCalibratedRaw.isEmpty()
                ? null : ErpDates.toInstant(lastCalibratedRaw);
        Instant nextDue = lastCalibratedAt != null && intervalMonths != null && intervalMonths != 0
                ? GaJs.addMonthsJs(lastCalibratedAt, intervalMonths) : null;

        List<GaEquipmentData> rows = findData(assetId);
        GaEquipmentData e = rows.isEmpty() ? new GaEquipmentData() : rows.get(0);
        e.setAssetId(assetId);
        e.setMaxCapacity(maxCapacity);
        e.setUnit(unit == null ? null : GaWeighingUnit.valueOf(unit));
        e.setAccuracy(accuracy);
        e.setClassLabel(classLabel);
        e.setDefaultCalibrator(defaultCalibrator);
        e.setIntervalMonths(intervalMonths);
        e.setLastCalibratedAt(lastCalibratedAt);
        e.setNextCalibrationDue(nextDue);
        e.setNotes(notes);
        if (rows.isEmpty()) {
            em.persist(e);
        }
        em.flush();
        return GaJs.scalars(e);
    }

    @ErpTx(readOnly = true)
    public ArrayNode calibrationLogs(String assetId) {
        ArrayNode out = ErpJson.arrayNode();
        for (GaCalibrationLog l : em.createQuery("select l from GaCalibrationLog l left join fetch l.document "
                        + "left join fetch l.workOrder where l.assetId = :id order by l.tanggal desc", GaCalibrationLog.class)
                .setParameter("id", assetId).getResultList()) {
            ObjectNode o = GaJs.scalars(l);
            o.set("document", GaJs.pick(l.getDocument(), "id", "code", "title"));
            o.set("workOrder", GaJs.pick(l.getWorkOrder(), "id", "code"));
            out.add(o);
        }
        return out;
    }

    @ErpTx
    public ObjectNode createCalibrationLog(String assetId, JsonNode body, String userId) {
        GaZod z = GaZod.of(body, false);
        String tanggal = z.requiredString("tanggal", 1, null, null);
        String lembaga = z.requiredString("lembaga", 1, null, 200);
        String noSertifikat = z.requiredString("noSertifikat", 1, null, 100);
        String mode = z.enumValue("mode", "ON_SITE", false, false, CALIB_MODES);
        BigDecimal biaya = z.number("biaya", true, false);
        String hasil = z.enumValue("hasil", null, false, false, CALIB_RESULTS);
        String deviasi = z.optString("deviasi", 500);
        String rangeTested = z.optString("rangeTested", 100);
        String validUntil = z.requiredString("validUntil", 1, null, null);
        String documentId = z.optString("documentId", null);
        String workOrderId = z.optString("workOrderId", null);
        z.throwIfInvalid();

        GaCalibrationLog l = new GaCalibrationLog();
        l.setAssetId(assetId);
        l.setTanggal(ErpDates.toInstant(tanggal));
        l.setLembaga(lembaga);
        l.setNoSertifikat(noSertifikat);
        l.setMode(GaCalibrationMode.valueOf(mode));
        l.setBiaya(biaya);
        l.setHasil(GaCalibrationResult.valueOf(hasil));
        l.setDeviasi(deviasi);
        l.setRangeTested(rangeTested);
        l.setValidUntil(ErpDates.toInstant(validUntil));
        l.setDocumentId(documentId);
        l.setWorkOrderId(workOrderId);
        l.setCreatedById(userId);
        em.persist(l);

        // Sinkron data alat: kalibrasi terakhir + jatuh tempo berikutnya (= validUntil).
        List<GaEquipmentData> rows = findData(assetId);
        if (!rows.isEmpty()) {
            rows.get(0).setLastCalibratedAt(l.getTanggal());
            rows.get(0).setNextCalibrationDue(l.getValidUntil());
        }
        em.flush();
        return GaJs.scalars(l);
    }

    @ErpTx
    public void deleteCalibrationLog(String logId) {
        GaCalibrationLog l = em.find(GaCalibrationLog.class, logId);
        if (l == null) {
            throw GaJs.p2025();
        }
        em.remove(l);
        em.flush();
    }
}
