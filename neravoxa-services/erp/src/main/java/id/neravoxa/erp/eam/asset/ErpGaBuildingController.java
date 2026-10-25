package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.security.ErpAccess;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Gedung & sewa — port {@code routes/ga/building.ts} (mount {@code requirePerm('ga_building')}).
 */
@RestController
@RequestMapping("/api/erp/ga/building")
@RequiredArgsConstructor
@Tag(name = "ERP - EAM Gedung", description = "Data bangunan per aset dan kontrak sewa")
public class ErpGaBuildingController {

    private final ErpGaBuildingService service;
    private final ErpAccess access;

    /** Data bangunan aset, atau {@code null} bila belum ada. */
    @GetMapping("/assets/{id}")
    public JsonNode get(@PathVariable String id) {
        return service.buildingData(id);
    }

    @PutMapping("/assets/{id}")
    public ObjectNode save(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.saveBuildingData(id, body);
    }

    @GetMapping("/assets/{id}/rentals")
    public ArrayNode rentals(@PathVariable String id) {
        return service.rentals(id);
    }

    @PostMapping("/assets/{id}/rentals")
    public ResponseEntity<ObjectNode> createRental(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return ResponseEntity.status(201).body(service.createRental(id, body, access.userId()));
    }

    @PatchMapping("/assets/{id}/rentals/{rid}")
    public ObjectNode updateRental(@PathVariable String id, @PathVariable String rid,
                                   @RequestBody(required = false) JsonNode body) {
        return service.updateRental(rid, body);
    }

    @PatchMapping("/assets/{id}/rentals/{rid}/end")
    public ObjectNode endRental(@PathVariable String id, @PathVariable String rid) {
        return service.endRental(rid);
    }

    @DeleteMapping("/assets/{id}/rentals/{rid}")
    public ResponseEntity<Void> deleteRental(@PathVariable String id, @PathVariable String rid) {
        service.deleteRental(rid);
        return ResponseEntity.noContent().build();
    }
}
