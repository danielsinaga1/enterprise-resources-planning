package id.enterpriseapps.erp.eam.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.security.ErpAccess;
import id.enterpriseapps.erp.entity.GaVehicleFuelLog;
import id.enterpriseapps.erp.entity.GaVehicleIncident;
import id.enterpriseapps.erp.entity.GaVehicleInspectionPhoto;
import id.enterpriseapps.erp.entity.GaVehicleOilLog;
import id.enterpriseapps.erp.entity.GaVehicleTire;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Log operasional kendaraan — port {@code routes/ga/vehicle.ts} bagian BBM, inspeksi berkala + foto,
 * insiden, ban dan oli (mount {@code /api/ga/vehicle}, gerbang modul {@code ga_vehicle} oleh ErpRouteGuard).
 */
@RestController
@RequestMapping("/api/erp/ga/vehicle")
@RequiredArgsConstructor
public class ErpVehicleLogController {

    private final ErpAccess access;
    private final ErpVehicleLogService logs;
    private final ErpVehicleInspectionService inspections;

    private static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    // BBM

    @GetMapping("/assets/{id}/fuel-logs")
    public List<GaVehicleFuelLog> fuelLogs(@PathVariable String id) {
        return logs.fuelLogs(id);
    }

    @PostMapping("/assets/{id}/fuel-logs")
    public ResponseEntity<ObjectNode> createFuelLog(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        VehicleRequests.FuelLog in = VehicleRequests.FuelLog.parse(body);
        return ResponseEntity.status(201).body(logs.createFuelLog(id, in, access.userId()));
    }

    @DeleteMapping("/assets/{id}/fuel-logs/{fid}")
    public ResponseEntity<Void> deleteFuelLog(@PathVariable String id, @PathVariable String fid) {
        logs.deleteFuelLog(fid);
        return noContent();
    }

    // Inspeksi berkala

    @GetMapping("/inspection-checkpoints")
    public List<String> inspectionCheckpoints() {
        return VehicleInspectionCatalog.CHECKPOINTS;
    }

    /** Struktur checklist detail per kategori (dipakai modal inspeksi baru). */
    @GetMapping("/inspection-categories")
    public List<Map<String, Object>> inspectionCategories() {
        return VehicleInspectionCatalog.CATEGORIES;
    }

    @GetMapping("/assets/{id}/inspections")
    public List<ObjectNode> inspections(@PathVariable String id) {
        return inspections.list(id);
    }

    @PostMapping("/assets/{id}/inspections")
    public ResponseEntity<ObjectNode> createInspection(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        VehicleRequests.Inspection in = VehicleRequests.Inspection.parse(body);
        return ResponseEntity.status(201).body(inspections.create(id, in, access.userId()));
    }

    @DeleteMapping("/assets/{id}/inspections/{iid}")
    public ResponseEntity<Void> deleteInspection(@PathVariable String id, @PathVariable String iid) {
        inspections.delete(iid);
        return noContent();
    }

    /**
     * Foto bukti inspeksi (1 foto per kategori checklist), field multipart {@code photo}. Seperti filter
     * multer, tipe selain JPEG/PNG/WebP diabaikan (= tidak ada file); &gt; 5MB = error multer → 500 generik.
     */
    @PostMapping("/assets/{id}/inspections/{iid}/photos")
    public ResponseEntity<GaVehicleInspectionPhoto> uploadPhoto(@PathVariable String id, @PathVariable String iid,
                                                                @RequestParam(value = "photo", required = false) MultipartFile photo,
                                                                @RequestParam(value = "category", required = false) String category,
                                                                @RequestParam(value = "caption", required = false) String caption)
            throws IOException {
        boolean accepted = photo != null && ErpVehicleInspectionService.PHOTO_MIME.contains(photo.getContentType());
        if (accepted && photo.getSize() > ErpVehicleInspectionService.MAX_PHOTO_BYTES) {
            throw ErpException.of(500, "Terjadi kesalahan pada server. Silakan coba lagi.");
        }
        if (!accepted) {
            throw ErpException.business("File wajib diupload.");
        }
        String cat = category == null ? "" : category.trim();
        if (cat.isEmpty()) {
            throw ErpException.business("Kategori foto wajib diisi.");
        }
        if (!VehicleInspectionCatalog.CATEGORY_KEYS.contains(cat)) {
            throw ErpException.business("Kategori foto tidak dikenal.");
        }
        return ResponseEntity.status(201).body(
                inspections.addPhoto(id, iid, photo.getBytes(), photo.getContentType(), cat, caption));
    }

    /**
     * Berkas foto inspeksi lewat endpoint ber-RBAC — dipakai {@code <img>} riwayat maupun ekspor PDF
     * (berkas tidak di-serve sebagai statis di ENTARIS).
     */
    @GetMapping("/assets/{id}/inspections/{iid}/photos/{pid}/file")
    public ResponseEntity<Resource> photoFile(@PathVariable String id, @PathVariable String iid, @PathVariable String pid) {
        ErpVehicleInspectionService.PhotoFile file = inspections.photoFile(id, iid, pid);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mime()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(new FileSystemResource(file.path()));
    }

    @DeleteMapping("/assets/{id}/inspections/{iid}/photos/{pid}")
    public ResponseEntity<Void> deletePhoto(@PathVariable String id, @PathVariable String iid, @PathVariable String pid) {
        inspections.deletePhoto(pid);
        return noContent();
    }

    // Insiden & kecelakaan

    @GetMapping("/assets/{id}/incidents")
    public List<GaVehicleIncident> incidents(@PathVariable String id) {
        return logs.incidents(id);
    }

    @PostMapping("/assets/{id}/incidents")
    public ResponseEntity<ObjectNode> createIncident(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        VehicleRequests.Incident in = VehicleRequests.Incident.parse(body, false);
        return ResponseEntity.status(201).body(logs.createIncident(id, in, access.userId()));
    }

    @PatchMapping("/assets/{id}/incidents/{iid}")
    public ObjectNode updateIncident(@PathVariable String id, @PathVariable String iid,
                                     @RequestBody(required = false) JsonNode body) {
        VehicleRequests.Incident in = VehicleRequests.Incident.parse(body, true);
        return logs.updateIncident(iid, in);
    }

    @DeleteMapping("/assets/{id}/incidents/{iid}")
    public ResponseEntity<Void> deleteIncident(@PathVariable String id, @PathVariable String iid) {
        logs.deleteIncident(iid);
        return noContent();
    }

    // Ban (master per posisi roda)

    @GetMapping("/assets/{id}/tires")
    public List<GaVehicleTire> tires(@PathVariable String id) {
        return logs.tires(id);
    }

    @PostMapping("/assets/{id}/tires")
    public ResponseEntity<ObjectNode> createTire(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        VehicleRequests.Tire in = VehicleRequests.Tire.parse(body, false);
        return ResponseEntity.status(201).body(logs.createTire(id, in, access.userId()));
    }

    @PatchMapping("/assets/{id}/tires/{tid}")
    public ObjectNode updateTire(@PathVariable String id, @PathVariable String tid,
                                 @RequestBody(required = false) JsonNode body) {
        VehicleRequests.Tire in = VehicleRequests.Tire.parse(body, true);
        return logs.updateTire(id, tid, in);
    }

    @DeleteMapping("/assets/{id}/tires/{tid}")
    public ResponseEntity<Void> deleteTire(@PathVariable String id, @PathVariable String tid) {
        logs.deleteTire(tid);
        return noContent();
    }

    // Oli

    @GetMapping("/assets/{id}/oil-logs")
    public List<GaVehicleOilLog> oilLogs(@PathVariable String id) {
        return logs.oilLogs(id);
    }

    @PostMapping("/assets/{id}/oil-logs")
    public ResponseEntity<ObjectNode> createOilLog(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        VehicleRequests.OilLog in = VehicleRequests.OilLog.parse(body);
        return ResponseEntity.status(201).body(logs.createOilLog(id, in, access.userId()));
    }

    @DeleteMapping("/assets/{id}/oil-logs/{oid}")
    public ResponseEntity<Void> deleteOilLog(@PathVariable String id, @PathVariable String oid) {
        logs.deleteOilLog(oid);
        return noContent();
    }
}
