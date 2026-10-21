package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpJson;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Lampiran transaksi — port {@code routes/attachments.ts}. Prefix OPEN di ErpRouteGuard: akses mengikuti
 * dokumen induk dan dicek di service ({@link ErpAttachmentAccessService}). Unduhan memakai header
 * Authorization biasa (UI mengambil berkas dengan token Bearer), tidak ada token di query/cookie.
 */
@RestController
@RequestMapping("/api/erp/attachments")
@RequiredArgsConstructor
public class ErpAttachmentController {

    private final ErpAttachmentService attachments;

    @PostMapping("/upload")
    public ResponseEntity<Object> upload(@RequestParam(value = "referenceType", required = false) String referenceType,
                                         @RequestParam(value = "referenceId", required = false) String referenceId,
                                         @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.status(201).body(attachments.upload(referenceType, referenceId, files));
    }

    /** Sajikan berkas (gambar inline, selain itu attachment dengan nama berkas aman). */
    @GetMapping("/file/{id}")
    public ResponseEntity<Resource> file(@PathVariable String id) {
        ErpAttachmentService.FileDownload f = attachments.file(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, f.mimeType())
                .header(HttpHeaders.CONTENT_DISPOSITION, f.contentDisposition())
                .body(new FileSystemResource(f.path()));
    }

    @GetMapping("/invoice/{type}/{id}/related")
    public Object related(@PathVariable String type, @PathVariable String id) {
        return attachments.related(type, id);
    }

    @GetMapping("/{referenceType}/{referenceId}")
    public Object list(@PathVariable String referenceType, @PathVariable String referenceId) {
        return attachments.list(referenceType, referenceId);
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable String id) {
        attachments.delete(id);
        return ErpJson.map("message", "Lampiran dihapus.");
    }

    @PostMapping("/counts")
    public Object counts(@RequestBody(required = false) JsonNode body) {
        return attachments.counts(body);
    }
}
