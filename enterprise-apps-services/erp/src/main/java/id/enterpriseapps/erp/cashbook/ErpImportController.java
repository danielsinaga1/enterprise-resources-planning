package id.enterpriseapps.erp.cashbook;

import id.enterpriseapps.erp.core.security.ErpAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Impor data master & saldo awal — port {@code routes/import/*.ts} (mount {@code /api/import} dengan
 * requireWrite('settings'): unggah butuh izin edit pengaturan, unduh template cukup login — dijalankan
 * ErpRouteGuard). Unggahan multipart field {@code file} (CSV/XLSX/XLS, maks. 5 MB); {@code ?preview=true}
 * hanya memvalidasi.
 */
@RestController
@RequestMapping("/api/erp/import")
@RequiredArgsConstructor
public class ErpImportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ErpImportService imports;
    private final ErpImportTemplateService templates;
    private final ErpAccess access;

    private static boolean preview(String value) {
        return "true".equals(value);
    }

    /** POST /coa — {preview: {data, errors, total}} | {success, failed, errors}. */
    @PostMapping("/coa")
    public Map<String, Object> coa(@RequestParam(value = "file", required = false) MultipartFile file,
                                   @RequestParam(value = "preview", required = false) String preview) {
        byte[] content = ImportFiles.read(file);
        return imports.importCoa(content, file.getOriginalFilename(), preview(preview), access.userId());
    }

    /** POST /journals — {preview: {data, errors, total, journalCount}} | {success, failed, errors, totalJournals}. */
    @PostMapping("/journals")
    public Map<String, Object> journals(@RequestParam(value = "file", required = false) MultipartFile file,
                                        @RequestParam(value = "preview", required = false) String preview) {
        byte[] content = ImportFiles.read(file);
        return imports.importJournals(content, file.getOriginalFilename(), preview(preview), access.userId());
    }

    /** POST /parties — {preview: {data, errors, total}} | {success, failed, errors}. */
    @PostMapping("/parties")
    public Map<String, Object> parties(@RequestParam(value = "file", required = false) MultipartFile file,
                                       @RequestParam(value = "preview", required = false) String preview) {
        byte[] content = ImportFiles.read(file);
        return imports.importParties(content, file.getOriginalFilename(), preview(preview), access.userId());
    }

    /** POST /inventory — {preview: {data, errors, total}} | {success, failed, errors}. */
    @PostMapping("/inventory")
    public Map<String, Object> inventory(@RequestParam(value = "file", required = false) MultipartFile file,
                                         @RequestParam(value = "preview", required = false) String preview) {
        byte[] content = ImportFiles.read(file);
        return imports.importInventory(content, file.getOriginalFilename(), preview(preview), access.userId());
    }

    /** GET /template/:type — data saat ini sebagai template Excel (coa | parties | inventory). */
    @GetMapping("/template/{type}")
    public ResponseEntity<byte[]> template(@PathVariable String type) {
        byte[] body = templates.build(type);
        String safeName = type.replaceAll("[^a-zA-Z0-9_-]", "_");
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"template_" + safeName + ".xlsx\"")
                .body(body);
    }
}
