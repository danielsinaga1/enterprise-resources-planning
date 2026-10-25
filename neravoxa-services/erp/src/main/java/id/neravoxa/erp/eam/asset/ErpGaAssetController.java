package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Aset GA — port {@code routes/ga/assets.ts} (mount {@code requirePerm('ga_assets')}, dijalankan ErpRouteGuard).
 */
@RestController
@RequestMapping("/api/erp/ga/assets")
@RequiredArgsConstructor
@Tag(name = "ERP - EAM Aset", description = "Registri aset fisik, kode aset, QR, dan foto")
public class ErpGaAssetController {

    private final ErpGaAssetService service;

    @GetMapping
    public Object list(@RequestParam Map<String, String> query) {
        return service.list(query);
    }

    /** Saran kode berikutnya (harus sebelum /{id}). */
    @GetMapping("/_next-code")
    public Map<String, Object> nextCode(@RequestParam(required = false) String categoryId,
                                        @RequestParam(required = false) String branchId) {
        if (categoryId == null || categoryId.isEmpty() || branchId == null || branchId.isEmpty()) {
            throw ErpException.business("categoryId & branchId wajib diisi.");
        }
        return service.nextCode(categoryId, branchId);
    }

    @GetMapping("/{id}")
    public ObjectNode detail(@PathVariable String id) {
        return service.detail(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody(required = false) JsonNode body) {
        return ResponseEntity.status(201).body(service.create(GaAssetUpdate.parse(body, false)));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.update(id, GaAssetUpdate.parse(body, true));
    }

    @PatchMapping("/bulk/update")
    public Map<String, Object> bulkUpdate(@RequestBody(required = false) JsonNode body) {
        return service.bulkUpdate(body);
    }

    @PostMapping("/bulk/delete")
    public Map<String, Object> bulkDelete(@RequestBody(required = false) JsonNode body) {
        return service.bulkDelete(body);
    }

    @PatchMapping("/{id}/condition")
    public Map<String, Object> condition(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.updateCondition(id, body);
    }

    /**
     * Unggah foto (field multipart {@code photo}, maks. 5 MB, hanya JPEG/PNG/WebP). Tipe lain diabaikan
     * seperti {@code fileFilter} multer sehingga menjadi "File wajib diupload.".
     */
    @PostMapping("/{id}/photos")
    public ResponseEntity<ObjectNode> uploadPhoto(@PathVariable String id,
                                                  @RequestParam(value = "photo", required = false) MultipartFile photo,
                                                  @RequestParam(value = "caption", required = false) String caption)
            throws IOException {
        if (photo == null || photo.isEmpty() || !ErpGaAssetService.PHOTO_MIME.contains(photo.getContentType())) {
            throw ErpException.business("File wajib diupload.");
        }
        if (photo.getSize() > ErpGaAssetService.PHOTO_MAX_BYTES) {
            // multer LIMIT_FILE_SIZE jatuh ke handler global Express → 500 generik.
            throw ErpException.of(500, "Terjadi kesalahan pada server. Silakan coba lagi.");
        }
        ErpGaAssetService.PhotoUpload file = new ErpGaAssetService.PhotoUpload(
                photo.getBytes(), photo.getOriginalFilename(), photo.getContentType());
        return ResponseEntity.status(201).body(service.addPhoto(id, file, caption));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable String id, @PathVariable String photoId) {
        service.deletePhoto(photoId);
        return ResponseEntity.noContent().build();
    }
}
