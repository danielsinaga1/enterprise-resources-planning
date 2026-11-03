package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.GaAsset;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Pembacaan hour meter (jam operasi) mesin — {@code /meter-readings} di bawah pemeliharaan GA. Pembacaan
 * terakhir aset menjadi "jam sekarang" untuk jadwal preventif ber-interval jam (lihat {@link MaintenanceDue}),
 * seperti odometer untuk jadwal km. Angka meter tidak boleh mundur terhadap pembacaan sebelum/sesudahnya.
 */
@Service
public class ErpGaMeterReadingService {

    static final int LIST_LIMIT = 50;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    record ReadingInput(String assetId, int hours, Instant readingAt, String notes) {
    }

    static ReadingInput parse(JsonNode raw) {
        JsonNode b = GaZod.object(raw);
        String assetId = GaZod.reqString(GaZod.field(b, "assetId"), 1, "Aset wajib dipilih.", 0);
        BigDecimal hours = GaZod.reqNumber(GaZod.field(b, "hours"), GaZod.Sign.NONNEGATIVE, "Jam meter tidak boleh negatif.");
        if (hours.stripTrailingZeros().scale() > 0) {
            throw ErpException.business("Jam meter harus bilangan bulat.");
        }
        String rawAt = GaZod.optString(GaZod.field(b, "readingAt"), 0, null, 0);
        Instant readingAt = rawAt == null || rawAt.isEmpty() ? null : ErpDates.tryInstant(rawAt);
        if (rawAt != null && !rawAt.isEmpty() && readingAt == null) {
            throw ErpException.business("Tanggal pembacaan tidak valid.");
        }
        String notes = GaZod.optString(GaZod.field(b, "notes"), 0, null, 500);
        return new ReadingInput(assetId, hours.intValueExact(), readingAt, notes);
    }

    /** Pembacaan terakhir satu aset, null bila belum pernah dicatat. */
    static Integer currentHours(EntityManager em, String assetId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("SELECT hours FROM ga_meter_readings WHERE asset_id = :id "
                        + "ORDER BY reading_at DESC, created_at DESC LIMIT 1")
                .setParameter("id", assetId)
                .getResultList();
        return rows.isEmpty() ? null : ((Number) rows.get(0)).intValue();
    }

    /** Pembacaan terakhir per aset (hanya aset yang pernah dicatat). */
    static Map<String, Integer> currentHours(EntityManager em, List<String> assetIds) {
        Map<String, Integer> out = new HashMap<>();
        if (assetIds.isEmpty()) {
            return out;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT DISTINCT ON (asset_id) asset_id, hours FROM ga_meter_readings "
                        + "WHERE asset_id IN (:ids) ORDER BY asset_id, reading_at DESC, created_at DESC")
                .setParameter("ids", assetIds)
                .getResultList();
        rows.forEach(r -> out.put((String) r[0], ((Number) r[1]).intValue()));
        return out;
    }

    /**
     * Simpan satu pembacaan (dipakai juga saat WO selesai). Ditolak bila asetnya tidak ada, tanggalnya di masa
     * depan, atau angkanya lebih kecil dari pembacaan sebelumnya / lebih besar dari pembacaan sesudahnya.
     */
    static String record(EntityManager em, ReadingInput in, String workOrderId, String userId) {
        if (em.find(GaAsset.class, in.assetId()) == null) {
            throw ErpException.business("Aset tidak ditemukan.");
        }
        Instant now = Instant.now();
        Instant at = in.readingAt() == null ? now : in.readingAt();
        if (at.isAfter(now.plus(Duration.ofDays(1)))) {
            throw ErpException.business("Tanggal pembacaan tidak boleh di masa depan.");
        }
        LocalDateTime atUtc = LocalDateTime.ofInstant(at, ZoneOffset.UTC);
        Integer before = neighbour(em, in.assetId(), "reading_at <= :at ORDER BY reading_at DESC, created_at DESC", atUtc);
        if (before != null && in.hours() < before) {
            throw ErpException.business("Jam meter tidak boleh lebih kecil dari pembacaan sebelumnya ("
                    + fmt(before) + " jam).");
        }
        Integer after = neighbour(em, in.assetId(), "reading_at > :at ORDER BY reading_at ASC, created_at ASC", atUtc);
        if (after != null && in.hours() > after) {
            throw ErpException.business("Jam meter tidak boleh lebih besar dari pembacaan sesudahnya ("
                    + fmt(after) + " jam).");
        }
        String id = UUID.randomUUID().toString();
        em.createNativeQuery("INSERT INTO ga_meter_readings (id, asset_id, reading_at, hours, notes, work_order_id, "
                        + "created_by_id, created_at) VALUES (:id, :asset, :at, :hours, :notes, :wo, :by, :created)")
                .setParameter("id", id)
                .setParameter("asset", in.assetId())
                .setParameter("at", atUtc)
                .setParameter("hours", in.hours())
                .setParameter("notes", in.notes())
                .setParameter("wo", workOrderId)
                .setParameter("by", userId)
                .setParameter("created", LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                .executeUpdate();
        return id;
    }

    private static Integer neighbour(EntityManager em, String assetId, String whereOrder, LocalDateTime at) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("SELECT hours FROM ga_meter_readings WHERE asset_id = :id AND "
                        + whereOrder + " LIMIT 1")
                .setParameter("id", assetId)
                .setParameter("at", at)
                .getResultList();
        return rows.isEmpty() ? null : ((Number) rows.get(0)).intValue();
    }

    /** GET /meter-readings?assetId= — {@value #LIST_LIMIT} pembacaan terbaru beserta nama pencatat & kode WO. */
    @ErpTx(readOnly = true)
    public List<JsonNode> list(String assetId) {
        if (assetId == null || assetId.isEmpty()) {
            throw ErpException.business("assetId wajib diisi.");
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT r.id, r.reading_at, r.hours, r.notes, r.work_order_id, w.code, "
                        + "coalesce(u.full_name, u.username), r.created_at FROM ga_meter_readings r "
                        + "LEFT JOIN ga_work_orders w ON w.id = r.work_order_id LEFT JOIN users u ON u.id = r.created_by_id "
                        + "WHERE r.asset_id = :id ORDER BY r.reading_at DESC, r.created_at DESC LIMIT " + LIST_LIMIT)
                .setParameter("id", assetId)
                .getResultList();
        List<JsonNode> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(ErpJson.tree(GaOpsJson.map("id", r[0], "readingAt", utcInstant(r[1]), "hours", ((Number) r[2]).intValue(),
                    "notes", r[3], "workOrderId", r[4], "workOrderCode", r[5], "createdByName", r[6],
                    "createdAt", utcInstant(r[7]))));
        }
        return out;
    }

    /** POST /meter-readings → 201 {@code { id, hours }}. */
    @ErpTx
    public Map<String, Object> create(ReadingInput in, String userId) {
        String id = record(em, in, null, userId);
        return GaOpsJson.map("id", id, "hours", in.hours());
    }

    /** DELETE /meter-readings/:id — koreksi salah input; jadwal tidak digulir ulang. */
    @ErpTx
    public Map<String, Object> delete(String id) {
        int n = em.createNativeQuery("DELETE FROM ga_meter_readings WHERE id = :id").setParameter("id", id).executeUpdate();
        if (n == 0) {
            throw ErpException.business("Pembacaan meter tidak ditemukan.");
        }
        return GaOpsJson.map("id", id);
    }

    private static String fmt(int v) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")).format(v);
    }

    private static Instant utcInstant(Object v) {
        return v instanceof Timestamp t ? t.toLocalDateTime().toInstant(ZoneOffset.UTC) : null;
    }
}
