package id.neravoxa.erp.eam.asset;

import id.neravoxa.erp.core.ErpException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Scan QR aset TANPA login — port {@code routes/ga/publicQr.ts} (mount {@code /api/ga/public}). Path ini
 * dikecualikan dari ErpRouteGuard/audit dan di-whitelist di security & gateway platform.
 */
@Slf4j
@RestController
@RequestMapping("/api/erp/ga/public")
@RequiredArgsConstructor
@Tag(name = "ERP - EAM QR Publik", description = "Info aset non-sensitif dari kode QR (tanpa login)")
public class ErpGaPublicQrController {

    private final ErpGaPublicQrService service;

    @GetMapping("/{qrCode}")
    public Map<String, Object> info(@PathVariable String qrCode) {
        return service.info(qrCode);
    }

    /** Proxy foto: hanya foto milik aset QR ini. Galat → status tanpa body seperti {@code res.status(x).end()}. */
    @GetMapping("/{qrCode}/photos/{photoId}.{ext}")
    public ResponseEntity<Resource> photo(@PathVariable String qrCode, @PathVariable String photoId,
                                          @PathVariable String ext) {
        try {
            Path file = service.photo(qrCode, photoId);
            if (file == null || !Files.isRegularFile(file)) {
                return ResponseEntity.notFound().build();
            }
            MediaType type = MediaTypeFactory.getMediaType(file.getFileName().toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .contentType(type)
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                    .body(new FileSystemResource(file));
        } catch (ErpException e) {
            // Path traversal guard: berkas harus tetap di dalam folder uploads.
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("GET /ga/public/:qrCode/photos error", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
