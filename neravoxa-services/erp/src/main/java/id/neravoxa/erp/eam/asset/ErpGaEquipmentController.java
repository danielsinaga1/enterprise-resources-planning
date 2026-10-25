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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Peralatan & kalibrasi — port {@code routes/ga/equipment.ts} (mount {@code requirePerm('ga_equipment')}).
 */
@RestController
@RequestMapping("/api/erp/ga/equipment")
@RequiredArgsConstructor
@Tag(name = "ERP - EAM Peralatan", description = "Data alat timbang per aset dan log kalibrasi")
public class ErpGaEquipmentController {

    private final ErpGaEquipmentService service;
    private final ErpAccess access;

    /** Data alat, atau {@code null} bila belum ada. */
    @GetMapping("/assets/{id}")
    public JsonNode get(@PathVariable String id) {
        return service.equipmentData(id);
    }

    @PutMapping("/assets/{id}")
    public ObjectNode save(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.saveEquipmentData(id, body);
    }

    @GetMapping("/assets/{id}/calibration-logs")
    public ArrayNode logs(@PathVariable String id) {
        return service.calibrationLogs(id);
    }

    @PostMapping("/assets/{id}/calibration-logs")
    public ResponseEntity<ObjectNode> createLog(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return ResponseEntity.status(201).body(service.createCalibrationLog(id, body, access.userId()));
    }

    @DeleteMapping("/assets/{id}/calibration-logs/{lid}")
    public ResponseEntity<Void> deleteLog(@PathVariable String id, @PathVariable String lid) {
        service.deleteCalibrationLog(lid);
        return ResponseEntity.noContent().build();
    }
}
