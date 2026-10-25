package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Master data GA — port {@code routes/ga/master.ts} (mount {@code requirePerm('ga_master')}: GET = view,
 * tulis = edit; dijalankan ErpRouteGuard).
 */
@RestController
@RequestMapping("/api/erp/ga/master")
@RequiredArgsConstructor
@Tag(name = "ERP - EAM Master", description = "Cabang, cost center, kategori aset, jenis dokumen, kategori inventory, lokasi")
public class ErpGaMasterController {

    private final ErpGaMasterService service;

    private static ResponseEntity<ObjectNode> created(ObjectNode body) {
        return ResponseEntity.status(201).body(body);
    }

    // Cabang
    @GetMapping("/branches")
    public ArrayNode branches() {
        return service.branches();
    }

    @PostMapping("/branches")
    public ResponseEntity<ObjectNode> createBranch(@RequestBody(required = false) JsonNode body) {
        return created(service.saveBranch(null, body));
    }

    @PutMapping("/branches/{id}")
    public ObjectNode updateBranch(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.saveBranch(id, body);
    }

    @DeleteMapping("/branches/{id}")
    public Map<String, Object> deleteBranch(@PathVariable String id) {
        return service.deleteBranch(id);
    }

    // Cost center
    @GetMapping("/cost-centers")
    public ArrayNode costCenters() {
        return service.costCenters();
    }

    @PostMapping("/cost-centers")
    public ResponseEntity<ObjectNode> createCostCenter(@RequestBody(required = false) JsonNode body) {
        return created(service.saveCostCenter(null, body));
    }

    @PutMapping("/cost-centers/{id}")
    public ObjectNode updateCostCenter(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.saveCostCenter(id, body);
    }

    @DeleteMapping("/cost-centers/{id}")
    public Map<String, Object> deleteCostCenter(@PathVariable String id) {
        return service.deleteCostCenter(id);
    }

    // Kategori aset
    @GetMapping("/asset-categories")
    public ArrayNode assetCategories() {
        return service.assetCategories();
    }

    @PostMapping("/asset-categories")
    public ResponseEntity<ObjectNode> createAssetCategory(@RequestBody(required = false) JsonNode body) {
        return created(service.saveAssetCategory(null, body));
    }

    @PutMapping("/asset-categories/{id}")
    public ObjectNode updateAssetCategory(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.saveAssetCategory(id, body);
    }

    @DeleteMapping("/asset-categories/{id}")
    public Map<String, Object> deleteAssetCategory(@PathVariable String id) {
        return service.deleteAssetCategory(id);
    }

    // Jenis dokumen
    @GetMapping("/document-types")
    public ArrayNode documentTypes() {
        return service.documentTypes();
    }

    @PostMapping("/document-types")
    public ResponseEntity<ObjectNode> createDocumentType(@RequestBody(required = false) JsonNode body) {
        return created(service.saveDocumentType(null, body));
    }

    @PutMapping("/document-types/{id}")
    public ObjectNode updateDocumentType(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.saveDocumentType(id, body);
    }

    @DeleteMapping("/document-types/{id}")
    public Map<String, Object> deleteDocumentType(@PathVariable String id) {
        return service.deleteDocumentType(id);
    }

    // Kategori inventory
    @GetMapping("/inventory-categories")
    public ArrayNode inventoryCategories() {
        return service.inventoryCategories();
    }

    @PostMapping("/inventory-categories")
    public ResponseEntity<ObjectNode> createInventoryCategory(@RequestBody(required = false) JsonNode body) {
        return created(service.saveInventoryCategory(null, body));
    }

    @PutMapping("/inventory-categories/{id}")
    public ObjectNode updateInventoryCategory(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.saveInventoryCategory(id, body);
    }

    @DeleteMapping("/inventory-categories/{id}")
    public Map<String, Object> deleteInventoryCategory(@PathVariable String id) {
        return service.deleteInventoryCategory(id);
    }

    // Lokasi
    @GetMapping("/locations")
    public ArrayNode locations() {
        return service.locations();
    }

    @PostMapping("/locations")
    public ResponseEntity<ObjectNode> createLocation(@RequestBody(required = false) JsonNode body) {
        return created(service.saveLocation(null, body));
    }

    @PutMapping("/locations/{id}")
    public ObjectNode updateLocation(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.saveLocation(id, body);
    }

    @DeleteMapping("/locations/{id}")
    public Map<String, Object> deleteLocation(@PathVariable String id) {
        return service.deleteLocation(id);
    }
}
