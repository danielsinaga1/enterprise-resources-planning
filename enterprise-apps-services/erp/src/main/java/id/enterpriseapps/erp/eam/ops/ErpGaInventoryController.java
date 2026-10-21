package id.enterpriseapps.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.security.ErpAccess;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import java.util.Map;

/**
 * Inventaris kantor GA — port {@code routes/ga/inventory.ts} (mount {@code requirePerm('ga_inventory')},
 * dijalankan ErpRouteGuard). Gambar item disimpan sebagai berkas publik ({@code /uploads/erp/ga/items/...})
 * tetapi UI mengambilnya lewat GET /items/:id/image.
 */
@RestController
@RequestMapping("/api/erp/ga/inventory")
@RequiredArgsConstructor
@Tag(name = "ERP - GA Inventaris", description = "Stok sparepart/consumable GA per lokasi")
public class ErpGaInventoryController {

    private final ErpGaInventoryService service;
    private final ErpAccess access;

    @GetMapping("/items")
    public Object items(@RequestParam Map<String, String> query) {
        if ("1".equals(query.get("paginated"))) {
            return service.listItemsPaged(query);
        }
        return service.listItems(query);
    }

    /** Dideklarasikan sebelum /items/:id. */
    @GetMapping("/items/summary")
    public Map<String, Object> itemSummary(@RequestParam Map<String, String> query) {
        return service.itemSummary(query);
    }

    @GetMapping("/items/{id}")
    public Object item(@PathVariable String id) {
        return service.itemDetail(id);
    }

    @PostMapping("/items")
    public ResponseEntity<Object> createItem(@RequestBody(required = false) JsonNode body) {
        ErpGaInventoryService.ItemInput in = ErpGaInventoryService.parseItem(body, false);
        return ResponseEntity.status(201).body(service.createItem(in));
    }

    @PutMapping("/items/{id}")
    public Map<String, Object> updateItem(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        ErpGaInventoryService.ItemInput in = ErpGaInventoryService.parseItem(body, true);
        return service.updateItem(id, in);
    }

    @PostMapping("/items/{id}/image")
    public ResponseEntity<Object> uploadImage(@PathVariable String id, HttpServletRequest request) {
        MultipartFile file = GaUploads.single(request, "image", ErpGaInventoryService.IMAGE_MIME,
                ErpGaInventoryService.MAX_IMAGE_BYTES);
        if (file == null) {
            throw ErpException.business(
                    "File gambar wajib diupload (JPG/PNG/WEBP, maks 5 MB).");
        }
        return ResponseEntity.status(201).body(service.uploadImage(id, GaUploads.bytes(file),
                file.getOriginalFilename(), file.getContentType()));
    }

    @GetMapping("/items/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable String id) {
        ErpGaInventoryService.ImageFile f = service.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.mimeType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(new FileSystemResource(f.path()));
    }

    @DeleteMapping("/items/{id}/image")
    public ResponseEntity<Void> deleteImage(@PathVariable String id) {
        service.deleteImage(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/movements")
    public Object movements(@RequestParam Map<String, String> query) {
        if (query.containsKey("page")) {
            return service.listMovementsPaged(query);
        }
        return service.listMovements(query);
    }

    @PostMapping("/movements")
    public ResponseEntity<Object> createMovement(@RequestBody(required = false) JsonNode body) {
        ErpGaInventoryService.MovementInput in = ErpGaInventoryService.parseMovement(body);
        return ResponseEntity.status(201).body(service.createMovement(in, access.userId()));
    }

    @GetMapping("/low-stock")
    public Object lowStock() {
        return service.lowStock();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.dashboard();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String type) {
        ErpGaInventoryService.CsvFile csv = service.export(type == null || type.isEmpty() ? "items" : type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + csv.filename() + "\"")
                .body(csv.bytes());
    }

    @PostMapping("/stock-set")
    public ResponseEntity<Object> stockSet(@RequestBody(required = false) JsonNode body) {
        ErpGaInventoryService.StockSetInput in = ErpGaInventoryService.parseStockSet(body);
        return ResponseEntity.status(201).body(service.stockSet(in, access.userId()));
    }
}
