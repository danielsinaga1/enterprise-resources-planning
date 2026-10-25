package id.neravoxa.erp.eam.vehicle;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.GaVehicleFuelLog;
import id.neravoxa.erp.entity.GaVehicleIncident;
import id.neravoxa.erp.entity.GaVehicleIncidentSeverity;
import id.neravoxa.erp.entity.GaVehicleIncidentStatus;
import id.neravoxa.erp.entity.GaVehicleOilLog;
import id.neravoxa.erp.entity.GaVehicleTire;
import id.neravoxa.erp.entity.GaVehicleTirePosition;
import id.neravoxa.erp.entity.GaVehicleTireStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Log operasional kendaraan: BBM (satu baris per isi ulang), insiden/kecelakaan, ban (master per posisi
 * roda, menjaga satu ban aktif per posisi) dan ganti oli (target odometer berikutnya). Log BBM dan oli
 * menaikkan odometer terakhir.
 */
@Service
@RequiredArgsConstructor
public class ErpVehicleLogService {

    private final ErpVehicleOdometerService odometer;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private static ObjectNode plain(Object entity) {
        ObjectNode o = ErpJson.obj(entity);
        o.remove("asset");
        return o;
    }

    private <T> ObjectNode reload(Class<T> type, String id) {
        em.flush();
        em.clear();
        return plain(em.find(type, id));
    }

    private <T> void remove(Class<T> type, String id) {
        T row = em.find(type, id);
        if (row == null) {
            throw new EntityNotFoundException();
        }
        em.remove(row);
        em.flush();
    }

    // BBM

    @ErpTx(readOnly = true)
    public List<GaVehicleFuelLog> fuelLogs(String assetId) {
        return em.createQuery("select f from GaVehicleFuelLog f where f.assetId = :a order by f.tanggal desc",
                GaVehicleFuelLog.class).setParameter("a", assetId).getResultList();
    }

    @ErpTx
    public ObjectNode createFuelLog(String assetId, VehicleRequests.FuelLog in, String userId) {
        GaVehicleFuelLog f = new GaVehicleFuelLog();
        f.setAssetId(assetId);
        f.setTanggal(ErpDates.toInstant(in.tanggal()));
        f.setOdometer(in.odometer());
        f.setLiters(in.liters());
        f.setPricePerLiter(in.pricePerLiter());
        f.setTotalCost(in.totalCost());
        f.setSpbu(in.spbu());
        f.setDriverName(in.driverName());
        f.setNotes(in.notes());
        f.setCreatedById(userId);
        em.persist(f);
        em.flush();
        odometer.bump(assetId, in.odometer());
        return reload(GaVehicleFuelLog.class, f.getId());
    }

    @ErpTx
    public void deleteFuelLog(String id) {
        remove(GaVehicleFuelLog.class, id);
    }

    // Insiden

    @ErpTx(readOnly = true)
    public List<GaVehicleIncident> incidents(String assetId) {
        return em.createQuery("select i from GaVehicleIncident i where i.assetId = :a order by i.tanggal desc",
                GaVehicleIncident.class).setParameter("a", assetId).getResultList();
    }

    @ErpTx
    public ObjectNode createIncident(String assetId, VehicleRequests.Incident in, String userId) {
        GaVehicleIncident i = new GaVehicleIncident();
        i.setAssetId(assetId);
        i.setTanggal(ErpDates.toInstant(in.tanggal()));
        i.setLocation(in.location());
        i.setDriverName(in.driverName());
        i.setDescription(in.description());
        i.setSeverity(GaVehicleIncidentSeverity.valueOf(in.severity()));
        i.setStatus(GaVehicleIncidentStatus.valueOf(in.status()));
        i.setEstimatedCost(in.estimatedCost());
        i.setActualCost(in.actualCost());
        i.setInsuranceClaimNo(in.insuranceClaimNo());
        i.setReportedById(userId);
        em.persist(i);
        return reload(GaVehicleIncident.class, i.getId());
    }

    @ErpTx
    public ObjectNode updateIncident(String id, VehicleRequests.Incident in) {
        GaVehicleIncident i = em.find(GaVehicleIncident.class, id);
        if (i == null) {
            throw new EntityNotFoundException();
        }
        if (in.tanggal() != null) {
            i.setTanggal(ErpDates.toInstant(in.tanggal()));
        }
        if (in.present().test("location")) {
            i.setLocation(in.location());
        }
        if (in.present().test("driverName")) {
            i.setDriverName(in.driverName());
        }
        if (in.description() != null) {
            i.setDescription(in.description());
        }
        if (in.severity() != null) {
            i.setSeverity(GaVehicleIncidentSeverity.valueOf(in.severity()));
        }
        // Zod 4: default 'DILAPORKAN' tetap terisi walau skemanya partial.
        if (in.status() != null) {
            i.setStatus(GaVehicleIncidentStatus.valueOf(in.status()));
        }
        if (in.present().test("estimatedCost")) {
            i.setEstimatedCost(in.estimatedCost());
        }
        if (in.present().test("actualCost")) {
            i.setActualCost(in.actualCost());
        }
        if (in.present().test("insuranceClaimNo")) {
            i.setInsuranceClaimNo(in.insuranceClaimNo());
        }
        return reload(GaVehicleIncident.class, id);
    }

    @ErpTx
    public void deleteIncident(String id) {
        remove(GaVehicleIncident.class, id);
    }

    // Ban

    @ErpTx(readOnly = true)
    public List<GaVehicleTire> tires(String assetId) {
        List<GaVehicleTire> rows = em.createQuery("select t from GaVehicleTire t where t.assetId = :a", GaVehicleTire.class)
                .setParameter("a", assetId).getResultList();
        // orderBy position asc = urutan deklarasi enum (enum PostgreSQL di ENTARIS).
        return rows.stream().sorted(Comparator.comparing((GaVehicleTire t) -> t.getPosition().ordinal())).toList();
    }

    private GaVehicleTire activeAt(String assetId, GaVehicleTirePosition position) {
        List<GaVehicleTire> rows = em.createQuery("select t from GaVehicleTire t where t.assetId = :a and t.position = :p "
                        + "and t.status = :st", GaVehicleTire.class)
                .setParameter("a", assetId).setParameter("p", position).setParameter("st", GaVehicleTireStatus.ACTIVE)
                .setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private GaVehicleTire activeAtExcept(String assetId, GaVehicleTirePosition position, String exceptId) {
        List<GaVehicleTire> rows = em.createQuery("select t from GaVehicleTire t where t.assetId = :a and t.position = :p "
                        + "and t.status = :st and t.id <> :x", GaVehicleTire.class)
                .setParameter("a", assetId).setParameter("p", position).setParameter("st", GaVehicleTireStatus.ACTIVE)
                .setParameter("x", exceptId).setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @ErpTx
    public ObjectNode createTire(String assetId, VehicleRequests.Tire in, String userId) {
        GaVehicleTirePosition position = GaVehicleTirePosition.valueOf(in.position());
        // Satu posisi hanya boleh diisi satu ban aktif.
        if ("ACTIVE".equals(in.status())) {
            GaVehicleTire existing = activeAt(assetId, position);
            if (existing != null) {
                throw ErpException.business("Posisi " + in.position() + " sudah diisi oleh ban aktif (SN: "
                        + existing.getSerialNumber() + "). Ganti status ban lama terlebih dahulu.");
            }
        }
        GaVehicleTire t = new GaVehicleTire();
        t.setAssetId(assetId);
        t.setSerialNumber(in.serialNumber());
        t.setBrand(in.brand());
        t.setModel(in.model());
        t.setSize(in.size());
        t.setPosition(position);
        t.setInstalledAt(ErpDates.toInstant(in.installedAt()));
        t.setInstalledOdometer(in.installedOdometer());
        t.setNotes(in.notes());
        t.setStatus(GaVehicleTireStatus.valueOf(in.status()));
        t.setCreatedById(userId);
        em.persist(t);
        return reload(GaVehicleTire.class, t.getId());
    }

    @ErpTx
    public ObjectNode updateTire(String assetId, String tireId, VehicleRequests.Tire in) {
        // Zod 4 mengisi default status 'ACTIVE' walau partial, jadi cek ini hampir selalu berjalan.
        if ("ACTIVE".equals(in.status()) || in.position() != null) {
            GaVehicleTire current = em.find(GaVehicleTire.class, tireId);
            if (current == null) {
                throw ErpException.notFound("Ban tidak ditemukan.");
            }
            GaVehicleTirePosition targetPos = in.position() != null
                    ? GaVehicleTirePosition.valueOf(in.position()) : current.getPosition();
            String targetStatus = in.status() != null ? in.status() : current.getStatus().name();
            if ("ACTIVE".equals(targetStatus)) {
                GaVehicleTire existing = activeAtExcept(assetId, targetPos, tireId);
                if (existing != null) {
                    throw ErpException.business("Posisi " + targetPos.name() + " sudah diisi oleh ban aktif lain (SN: "
                            + existing.getSerialNumber() + ").");
                }
            }
        }
        GaVehicleTire t = em.find(GaVehicleTire.class, tireId);
        if (t == null) {
            throw new EntityNotFoundException();
        }
        if (in.serialNumber() != null) {
            t.setSerialNumber(in.serialNumber());
        }
        if (in.brand() != null) {
            t.setBrand(in.brand());
        }
        if (in.present().test("model")) {
            t.setModel(in.model());
        }
        if (in.present().test("size")) {
            t.setSize(in.size());
        }
        if (in.position() != null) {
            t.setPosition(GaVehicleTirePosition.valueOf(in.position()));
        }
        if (in.installedAt() != null) {
            t.setInstalledAt(ErpDates.toInstant(in.installedAt()));
        }
        if (in.present().test("installedOdometer")) {
            t.setInstalledOdometer(in.installedOdometer());
        }
        if (in.present().test("notes")) {
            t.setNotes(in.notes());
        }
        if (in.status() != null) {
            t.setStatus(GaVehicleTireStatus.valueOf(in.status()));
        }
        return reload(GaVehicleTire.class, tireId);
    }

    @ErpTx
    public void deleteTire(String id) {
        remove(GaVehicleTire.class, id);
    }

    // Oli

    @ErpTx(readOnly = true)
    public List<GaVehicleOilLog> oilLogs(String assetId) {
        return em.createQuery("select o from GaVehicleOilLog o where o.assetId = :a order by o.tanggal desc",
                GaVehicleOilLog.class).setParameter("a", assetId).getResultList();
    }

    @ErpTx
    public ObjectNode createOilLog(String assetId, VehicleRequests.OilLog in, String userId) {
        GaVehicleOilLog o = new GaVehicleOilLog();
        o.setAssetId(assetId);
        o.setTanggal(ErpDates.toInstant(in.tanggal()));
        o.setOdometer(in.odometer());
        o.setNextOdometerDue(in.nextOdometerDue());
        o.setBrand(in.brand());
        o.setVolume(in.volume());
        o.setNotes(in.notes());
        o.setCreatedById(userId);
        em.persist(o);
        em.flush();
        odometer.bump(assetId, in.odometer());
        return reload(GaVehicleOilLog.class, o.getId());
    }

    @ErpTx
    public void deleteOilLog(String id) {
        remove(GaVehicleOilLog.class, id);
    }
}
