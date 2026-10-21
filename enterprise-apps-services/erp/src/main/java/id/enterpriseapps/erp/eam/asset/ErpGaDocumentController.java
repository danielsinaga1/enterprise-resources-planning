package id.enterpriseapps.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.security.ErpAccess;
import id.enterpriseapps.erp.core.storage.ErpFileStorage;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Dokumen & sertifikat GA — port {@code routes/ga/documents.ts} (mount {@code requirePerm('ga_documents')}).
 */
@RestController
@RequestMapping("/api/erp/ga/documents")
@RequiredArgsConstructor
@Tag(name = "ERP - EAM Dokumen", description = "Dokumen legal/sertifikat aset dengan versi PDF dan masa berlaku")
public class ErpGaDocumentController {

    /** Batas multer unggahan versi (20 MB, hanya PDF). */
    static final long MAX_PDF_BYTES = 20L * 1024 * 1024;

    private final ErpGaDocumentService service;
    private final ErpAccess access;

    @GetMapping
    public Object list(@RequestParam Map<String, String> query) {
        return service.list(query);
    }

    /** Ringkasan KPI (harus sebelum /{id}). */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return service.summary(query);
    }

    @GetMapping("/{id}")
    public ObjectNode detail(@PathVariable String id) {
        return service.detail(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody(required = false) JsonNode body) {
        ErpGaDocumentService.DocumentInput in = ErpGaDocumentService.parse(body, false);
        return ResponseEntity.status(201).body(service.create(in, access.userId()));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.update(id, ErpGaDocumentService.parse(body, true));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        return service.delete(id);
    }

    /** Unggah versi baru (field multipart {@code file}, PDF ≤ 20 MB; tipe lain diabaikan seperti fileFilter multer). */
    @PostMapping("/{id}/versions")
    public ResponseEntity<ObjectNode> uploadVersion(@PathVariable String id,
                                                    @RequestParam(value = "file", required = false) MultipartFile file,
                                                    @RequestParam(value = "notes", required = false) String notes)
            throws IOException {
        if (file == null || file.isEmpty() || !"application/pdf".equals(file.getContentType())) {
            throw ErpException.business("File PDF wajib diupload.");
        }
        if (file.getSize() > MAX_PDF_BYTES) {
            throw ErpException.of(500, "Terjadi kesalahan pada server. Silakan coba lagi.");
        }
        return ResponseEntity.status(201)
                .body(service.addVersion(id, file.getBytes(), file.getOriginalFilename(), notes, access.userId()));
    }

    /** Unduh berkas versi (lampiran PDF, nama {@code <kode>-v<n>.pdf}). */
    @GetMapping("/{id}/versions/{vid}/file")
    public ResponseEntity<Resource> download(@PathVariable String id, @PathVariable String vid) {
        ErpGaDocumentService.Download d = service.versionFile(id, vid);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ErpFileStorage.contentDisposition(d.fileName(), false))
                .body(new FileSystemResource(d.path()));
    }
}
