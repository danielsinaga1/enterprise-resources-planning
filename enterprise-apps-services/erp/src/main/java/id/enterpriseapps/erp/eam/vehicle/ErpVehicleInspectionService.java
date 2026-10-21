package id.enterpriseapps.erp.eam.vehicle;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.storage.ErpFileStorage;
import id.enterpriseapps.erp.entity.GaVehicleFuelLevel;
import id.enterpriseapps.erp.entity.GaVehicleInspection;
import id.enterpriseapps.erp.entity.GaVehicleInspectionPhoto;
import id.enterpriseapps.erp.entity.GaVehicleInspectionResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Inspeksi berkala kendaraan (checklist ban/rem/oli, terpisah dari log servis) + foto bukti per kategori
 * checklist. Foto disimpan sebagai berkas publik ({@code erp/ga/inspections/...}) tetapi UI membacanya
 * lewat endpoint ber-RBAC {@code .../photos/:pid/file}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpVehicleInspectionService {

    /** Batas ukuran foto inspeksi (multer 5MB). */
    static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    static final List<String> PHOTO_MIME = List.of("image/jpeg", "image/png", "image/webp");

    private final ErpVehicleOdometerService odometer;
    private final ErpFileStorage storage;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private static ObjectNode withPhotos(GaVehicleInspection i, List<GaVehicleInspectionPhoto> photos) {
        ObjectNode o = ErpJson.obj(i);
        o.remove(List.of("asset", "photos"));
        o.set("photos", ErpJson.array(photos.stream().map(p -> {
            ObjectNode po = ErpJson.obj(p);
            po.remove("inspection");
            return po;
        }).toList()));
        return o;
    }

    @ErpTx(readOnly = true)
    public List<ObjectNode> list(String assetId) {
        List<GaVehicleInspection> rows = em.createQuery("select distinct i from GaVehicleInspection i "
                        + "left join fetch i.photos where i.assetId = :a", GaVehicleInspection.class)
                .setParameter("a", assetId).getResultList();
        return rows.stream()
                .sorted(Comparator.comparing(GaVehicleInspection::getTanggal, Comparator.reverseOrder()))
                .map(i -> withPhotos(i, i.getPhotos().stream()
                        .sorted(Comparator.comparing(GaVehicleInspectionPhoto::getCreatedAt)).toList()))
                .toList();
    }

    @ErpTx
    public ObjectNode create(String assetId, VehicleRequests.Inspection in, String userId) {
        Instant tanggal = ErpDates.toInstant(in.tanggal());
        // Jadwal inspeksi berikutnya: tanggal eksplisit bila dikirim (dan valid), selain itu
        // tanggal + interval (default 30 hari).
        Instant nextDueDate = in.nextDueDate() != null && !in.nextDueDate().isEmpty()
                ? ErpDates.tryInstant(in.nextDueDate()) : null;
        if (nextDueDate == null && tanggal != null) {
            int days = in.intervalDays() != null ? in.intervalDays() : VehicleInspectionCatalog.DEFAULT_INTERVAL_DAYS;
            nextDueDate = tanggal.plus(Duration.ofDays(days));
        }
        GaVehicleInspection i = new GaVehicleInspection();
        i.setAssetId(assetId);
        i.setTanggal(tanggal);
        i.setOdometer(in.odometer());
        i.setChecklist(ErpJson.tree(in.checklist()));
        i.setOverallResult(GaVehicleInspectionResult.valueOf(in.overallResult()));
        i.setNotes(in.notes());
        i.setInspectorName(in.inspectorName());
        i.setInspectorUserId(in.inspectorUserId());
        i.setFuelLevel(in.fuelLevel() == null ? null : GaVehicleFuelLevel.valueOf(in.fuelLevel()));
        i.setNextDueDate(nextDueDate);
        i.setInspectedById(userId);
        em.persist(i);
        em.flush();
        odometer.bump(assetId, in.odometer());
        String id = i.getId();
        em.flush();
        em.clear();
        return withPhotos(em.find(GaVehicleInspection.class, id), List.of());
    }

    /** Hapus berkas foto fisik dulu; barisnya ikut terhapus lewat cascade FK. */
    @ErpTx
    public void delete(String inspectionId) {
        List<String> paths = em.createQuery("select p.filePath from GaVehicleInspectionPhoto p where p.inspection.id = :i",
                String.class).setParameter("i", inspectionId).getResultList();
        paths.forEach(this::deleteFile);
        int n = em.createQuery("delete from GaVehicleInspection i where i.id = :id")
                .setParameter("id", inspectionId).executeUpdate();
        if (n == 0) {
            throw new EntityNotFoundException();
        }
    }

    private void deleteFile(String filePath) {
        try {
            storage.deletePublic(filePath);
        } catch (RuntimeException e) {
            // unlink(...).catch(() => undefined): kegagalan hapus berkas diabaikan.
            log.warn("ERP: gagal menghapus foto inspeksi {}: {}", filePath, e.getMessage());
        }
    }

    /** Simpan foto bukti satu kategori checklist. */
    @ErpTx
    public GaVehicleInspectionPhoto addPhoto(String assetId, String inspectionId, byte[] bytes, String mimeType,
                                             String category, String caption) {
        GaVehicleInspection inspection = em.find(GaVehicleInspection.class, inspectionId);
        if (inspection == null || !inspection.getAssetId().equals(assetId)) {
            throw ErpException.notFound("Data inspeksi tidak ditemukan.");
        }
        ErpFileStorage.SaveResult saved = storage.savePublic(bytes, null, mimeType, "ga/inspections");
        GaVehicleInspectionPhoto photo = new GaVehicleInspectionPhoto();
        photo.setInspectionId(inspection.getId());
        photo.setCategory(category);
        photo.setFilePath(saved.filePath());
        photo.setCaption(caption != null && !caption.isEmpty() ? caption : null);
        em.persist(photo);
        em.flush();
        String id = photo.getId();
        em.clear();
        return em.find(GaVehicleInspectionPhoto.class, id);
    }

    /** Berkas foto (path-traversal dijaga ErpFileStorage) + MIME dari ekstensi. */
    public record PhotoFile(Path path, String mime) {
    }

    @ErpTx(readOnly = true)
    public PhotoFile photoFile(String assetId, String inspectionId, String photoId) {
        List<Object[]> rows = em.createQuery("select p.filePath, i.assetId from GaVehicleInspectionPhoto p "
                        + "join p.inspection i where p.id = :p and p.inspectionId = :i", Object[].class)
                .setParameter("p", photoId).setParameter("i", inspectionId).setMaxResults(1).getResultList();
        if (rows.isEmpty() || !assetId.equals(rows.get(0)[1])) {
            throw ErpException.notFound("Foto tidak ditemukan.");
        }
        String filePath = (String) rows.get(0)[0];
        Path full = storage.resolvePublic(filePath);
        if (!Files.isRegularFile(full)) {
            // sendFile ENOENT di sumber → 404.
            throw ErpException.notFound("Foto tidak ditemukan.");
        }
        String lower = full.getFileName().toString().toLowerCase();
        String mime = lower.endsWith(".png") ? "image/png" : lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return new PhotoFile(full, mime);
    }

    @ErpTx
    public void deletePhoto(String photoId) {
        GaVehicleInspectionPhoto photo = em.find(GaVehicleInspectionPhoto.class, photoId);
        if (photo != null) {
            deleteFile(photo.getFilePath());
            em.remove(photo);
            em.flush();
        }
    }
}
