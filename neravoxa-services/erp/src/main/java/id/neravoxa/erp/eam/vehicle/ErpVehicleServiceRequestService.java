package id.neravoxa.erp.eam.vehicle;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.GaAsset;
import id.neravoxa.erp.entity.GaVehicleData;
import id.neravoxa.erp.entity.GaVehicleServiceRequest;
import id.neravoxa.erp.entity.GaVehicleServiceRequestStatus;
import id.neravoxa.erp.entity.GaVehicleServiceType;
import id.neravoxa.erp.entity.GaWorkOrder;
import id.neravoxa.erp.entity.GaWorkOrderPriority;
import id.neravoxa.erp.entity.GaWorkOrderStatus;
import id.neravoxa.erp.entity.GaWorkOrderType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pengajuan servis kendaraan dengan alur approval. Approve membuat Work Order otomatis (sekali saja —
 * idempoten bila WO sudah tertaut): servis rutin = PREVENTIVE, sisanya CORRECTIVE.
 */
@Service
public class ErpVehicleServiceRequestService {

    static final Map<String, String> SERVICE_TYPE_LABEL = Map.of(
            "OLI", "Ganti Oli",
            "TUNE_UP", "Tune Up",
            "SERVIS_BERKALA", "Servis Berkala",
            "PERBAIKAN", "Perbaikan",
            "GANTI_BAN", "Ganti Ban",
            "ELEKTRIKAL", "Perbaikan Elektrikal",
            "AC", "Servis AC",
            "LAINNYA", "Servis Lainnya");

    private static final Set<String> PREVENTIVE = Set.of("OLI", "TUNE_UP", "SERVIS_BERKALA");

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx
    public Map<String, Object> create(String assetId, VehicleRequests.ServiceRequest in, String userId) {
        GaVehicleServiceRequest sr = new GaVehicleServiceRequest();
        sr.setAssetId(assetId);
        sr.setRequestedById(userId);
        sr.setJenisService(GaVehicleServiceType.valueOf(in.jenisService()));
        sr.setOdometer(in.odometer());
        sr.setDeskripsi(in.deskripsi());
        sr.setUrgensi(GaWorkOrderPriority.valueOf(in.urgensi()));
        sr.setStatus(GaVehicleServiceRequestStatus.SUBMITTED);
        em.persist(sr);
        em.flush();
        return idStatus(sr.getId(), sr.getStatus());
    }

    static Map<String, Object> idStatus(String id, Object status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("status", status);
        return m;
    }

    /** Status di query tidak dikenal = error validasi Prisma → 500 dengan pesan fallback rute. */
    static <E extends Enum<E>> E statusParam(Class<E> type, String raw, String fallbackMessage) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            throw ErpException.of(500, fallbackMessage);
        }
    }

    /** Urutan status asc, urgensi desc (urutan deklarasi enum = urutan enum PostgreSQL ENTARIS), terbaru dulu. */
    static final Comparator<GaVehicleServiceRequest> ORDER = Comparator
            .comparing((GaVehicleServiceRequest r) -> r.getStatus().ordinal())
            .thenComparing(r -> -r.getUrgensi().ordinal())
            .thenComparing(GaVehicleServiceRequest::getCreatedAt, Comparator.reverseOrder());

    @ErpTx(readOnly = true)
    public List<ObjectNode> list(String status, String assetId, String mineUserId) {
        GaVehicleServiceRequestStatus st = statusParam(GaVehicleServiceRequestStatus.class, status, "Gagal mengambil pengajuan.");
        StringBuilder jpql = new StringBuilder("select r from GaVehicleServiceRequest r left join fetch r.asset "
                + "left join fetch r.workOrder where 1=1");
        if (st != null) {
            jpql.append(" and r.status = :st");
        }
        if (assetId != null) {
            jpql.append(" and r.assetId = :asset");
        }
        if (mineUserId != null) {
            jpql.append(" and r.requestedById = :me");
        }
        var q = em.createQuery(jpql.toString(), GaVehicleServiceRequest.class);
        if (st != null) {
            q.setParameter("st", st);
        }
        if (assetId != null) {
            q.setParameter("asset", assetId);
        }
        if (mineUserId != null) {
            q.setParameter("me", mineUserId);
        }
        return q.getResultList().stream().sorted(ORDER).map(r -> {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("asset", assetRef(r.getAsset()));
            GaWorkOrder w = r.getWorkOrder();
            extra.put("workOrder", w == null ? null : ErpJson.map("id", w.getId(), "code", w.getCode(), "status", w.getStatus()));
            return ErpJson.merge(plain(r), extra);
        }).toList();
    }

    static Map<String, Object> assetRef(GaAsset a) {
        return a == null ? null : ErpJson.map("id", a.getId(), "code", a.getCode(), "name", a.getName());
    }

    private static ObjectNode plain(GaVehicleServiceRequest r) {
        ObjectNode o = ErpJson.obj(r);
        o.remove(List.of("asset", "workOrder", "serviceLog"));
        return o;
    }

    /** Aset + vehicleData {noPlat, brand, tipe} (select Prisma detail pengajuan). */
    static Map<String, Object> assetWithVehicle(EntityManager em, GaAsset a) {
        if (a == null) {
            return null;
        }
        List<GaVehicleData> vd = em.createQuery("select v from GaVehicleData v where v.assetId = :a", GaVehicleData.class)
                .setParameter("a", a.getId()).getResultList();
        Map<String, Object> m = assetRef(a);
        m.put("vehicleData", vd.isEmpty() ? null
                : ErpJson.map("noPlat", vd.get(0).getNoPlat(), "brand", vd.get(0).getBrand(), "tipe", vd.get(0).getTipe()));
        return m;
    }

    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        List<GaVehicleServiceRequest> rows = em.createQuery("select r from GaVehicleServiceRequest r "
                        + "left join fetch r.asset left join fetch r.workOrder left join fetch r.serviceLog where r.id = :id",
                GaVehicleServiceRequest.class).setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("Pengajuan tidak ditemukan.");
        }
        GaVehicleServiceRequest r = rows.get(0);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("asset", assetWithVehicle(em, r.getAsset()));
        extra.put("workOrder", r.getWorkOrder() == null ? null : scalarOnly(r.getWorkOrder()));
        extra.put("serviceLog", r.getServiceLog() == null ? null : scalarOnly(r.getServiceLog()));
        return ErpJson.merge(plain(r), extra);
    }

    /** Hanya kolom skalar entity relasi ({@code include: { x: true }} tanpa relasi bersarang). */
    static ObjectNode scalarOnly(Object entity) {
        ObjectNode o = ErpJson.obj(entity);
        List<String> drop = new java.util.ArrayList<>();
        o.fields().forEachRemaining(e -> {
            if (e.getValue().isObject() || (e.getValue().isArray() && !"checklist".equals(e.getKey()))) {
                drop.add(e.getKey());
            }
        });
        // Kolom JSON (checklist) tetap; relasi objek/daftar yang kebetulan termuat dibuang.
        drop.removeIf(k -> "checklist".equals(k));
        o.remove(drop);
        return o;
    }

    /** Kode WO-YYYY-NNNN berikutnya (advisory lock sama dengan maintenance.ts). */
    private String nextWorkOrderCode() {
        int year = Instant.now().atZone(ZoneOffset.UTC).getYear();
        String prefix = "WO-" + year + "-";
        long lockKey = 0xa70000L + (year - 2000);
        em.createNativeQuery("SELECT pg_advisory_xact_lock(:k)").setParameter("k", lockKey).getSingleResult();
        List<String> latest = em.createQuery(
                        "select w.code from GaWorkOrder w where w.code like :p order by w.code desc", String.class)
                .setParameter("p", prefix + "%").setMaxResults(1).getResultList();
        int next = 1;
        if (!latest.isEmpty()) {
            try {
                next = Integer.parseInt(latest.get(0).substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                next = 1;
            }
        }
        return prefix + String.format("%04d", next);
    }

    @ErpTx
    public Map<String, Object> approve(String id, String userId) {
        GaVehicleServiceRequest sr = em.find(GaVehicleServiceRequest.class, id);
        if (sr == null) {
            throw ErpException.notFound("Pengajuan tidak ditemukan.");
        }
        if (sr.getStatus() != GaVehicleServiceRequestStatus.SUBMITTED) {
            throw ErpException.business("Tidak bisa approve dari status " + sr.getStatus() + ".");
        }
        // WO dibuat sekali saja — kalau sudah ada, pakai yang lama (idempoten).
        String workOrderId = sr.getWorkOrderId();
        String workOrderCode = null;
        if (workOrderId != null) {
            GaWorkOrder existing = em.find(GaWorkOrder.class, workOrderId);
            workOrderCode = existing == null ? null : existing.getCode();
        } else {
            GaAsset asset = em.find(GaAsset.class, sr.getAssetId());
            String plat = "";
            if (asset != null) {
                List<String> plates = em.createQuery("select v.noPlat from GaVehicleData v where v.assetId = :a", String.class)
                        .setParameter("a", asset.getId()).getResultList();
                String noPlat = plates.isEmpty() ? null : plates.get(0);
                plat = noPlat != null && !noPlat.isEmpty() ? noPlat : (asset.getCode() != null ? asset.getCode() : "");
            }
            String jenis = sr.getJenisService().name();
            String label = SERVICE_TYPE_LABEL.getOrDefault(jenis, "Servis Kendaraan");
            GaWorkOrder wo = new GaWorkOrder();
            wo.setCode(nextWorkOrderCode());
            wo.setAssetId(sr.getAssetId());
            wo.setType(PREVENTIVE.contains(jenis) ? GaWorkOrderType.PREVENTIVE : GaWorkOrderType.CORRECTIVE);
            wo.setStatus(GaWorkOrderStatus.OPEN);
            wo.setPriority(sr.getUrgensi());
            wo.setTitle(!plat.isEmpty() ? label + " - " + plat : label);
            wo.setDescription(sr.getDeskripsi() + "\n\n[Dibuat otomatis dari pengajuan servis"
                    + (sr.getOdometer() != null ? " | Odometer: " + sr.getOdometer() + " km" : "") + "]");
            wo.setReportedById(sr.getRequestedById());
            wo.setReportedAt(sr.getCreatedAt());
            em.persist(wo);
            em.flush();
            workOrderId = wo.getId();
            workOrderCode = wo.getCode();
        }
        sr.setStatus(GaVehicleServiceRequestStatus.APPROVED);
        sr.setApprovedById(userId);
        sr.setApprovedAt(Instant.now());
        sr.setWorkOrderId(workOrderId);
        em.flush();
        Map<String, Object> out = idStatus(sr.getId(), sr.getStatus());
        out.put("workOrderId", workOrderId);
        out.put("workOrderCode", workOrderCode);
        return out;
    }

    @ErpTx
    public Map<String, Object> reject(String id, String reason, String userId) {
        GaVehicleServiceRequest sr = em.find(GaVehicleServiceRequest.class, id);
        if (sr == null) {
            throw ErpException.notFound("Pengajuan tidak ditemukan.");
        }
        if (sr.getStatus() != GaVehicleServiceRequestStatus.SUBMITTED) {
            throw ErpException.business("Tidak bisa reject dari status " + sr.getStatus() + ".");
        }
        sr.setStatus(GaVehicleServiceRequestStatus.REJECTED);
        sr.setApprovedById(userId);
        sr.setApprovedAt(Instant.now());
        sr.setRejectedReason(reason);
        em.flush();
        return idStatus(sr.getId(), sr.getStatus());
    }

    /** Batal oleh pengaju selama masih SUBMITTED. */
    @ErpTx
    public Map<String, Object> cancel(String id, String userId) {
        GaVehicleServiceRequest sr = em.find(GaVehicleServiceRequest.class, id);
        if (sr == null) {
            throw ErpException.notFound("Pengajuan tidak ditemukan.");
        }
        if (!sr.getRequestedById().equals(userId)) {
            throw ErpException.forbidden("Hanya pengaju yang bisa membatalkan.");
        }
        if (sr.getStatus() != GaVehicleServiceRequestStatus.SUBMITTED) {
            throw ErpException.business("Tidak bisa cancel dari status " + sr.getStatus() + ".");
        }
        sr.setStatus(GaVehicleServiceRequestStatus.CANCELLED);
        em.flush();
        return idStatus(sr.getId(), sr.getStatus());
    }
}
