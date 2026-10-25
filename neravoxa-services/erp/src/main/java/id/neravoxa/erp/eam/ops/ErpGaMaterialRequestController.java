package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.security.ErpAccess;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Permintaan barang GA — port {@code routes/ga/materialRequest.ts} (mount
 * {@code requirePerm('ga_material_request')}, dijalankan ErpRouteGuard; approve memakai izin edit modul).
 */
@RestController
@RequestMapping("/api/erp/ga/material-requests")
@RequiredArgsConstructor
@Tag(name = "ERP - GA Permintaan Barang", description = "Material request sparepart EAM")
public class ErpGaMaterialRequestController {

    private final ErpGaMaterialRequestService service;
    private final ErpAccess access;

    @GetMapping
    public Object list(@RequestParam Map<String, String> query) {
        if (query.containsKey("page")) {
            return service.listPaged(query, access.userId());
        }
        return service.list(query, access.userId());
    }

    /** Dideklarasikan sebelum /:id. */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return service.summary(query, access.userId());
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        return service.detail(id);
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode body) {
        ErpGaMaterialRequestService.HeaderInput in = ErpGaMaterialRequestService.parseCreate(body);
        return ResponseEntity.status(201).body(service.create(in, access.userId()));
    }

    @PostMapping("/from-work-order/{workOrderId}")
    public ResponseEntity<Object> fromWorkOrder(@PathVariable String workOrderId,
                                                @RequestBody(required = false) JsonNode body) {
        ErpGaMaterialRequestService.HeaderInput in = ErpGaMaterialRequestService.parseFromWo(body);
        return ResponseEntity.status(201).body(service.createFromWorkOrder(workOrderId, in, access.userId()));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.update(id, ErpGaMaterialRequestService.parseUpdate(body));
    }

    @PatchMapping("/{id}/submit")
    public Map<String, Object> submit(@PathVariable String id) {
        return service.submit(id);
    }

    @PatchMapping("/{id}/approve")
    public Map<String, Object> approve(@PathVariable String id) {
        return service.approve(id, access.userId());
    }

    @PatchMapping("/{id}/reject")
    public Map<String, Object> reject(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        String reason = ErpGaMaterialRequestService.parseReject(body);
        return service.reject(id, reason, access.userId());
    }

    @PatchMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        return service.cancel(id);
    }

    @PatchMapping("/{id}/items/{itemId}/procurement")
    public Map<String, Object> procurement(@PathVariable String id, @PathVariable String itemId,
                                           @RequestBody(required = false) JsonNode body) {
        return service.updateProcurement(id, itemId, ErpGaMaterialRequestService.parseProcurement(body));
    }

    @PatchMapping("/{id}/items/{itemId}/receive")
    public Map<String, Object> receive(@PathVariable String id, @PathVariable String itemId,
                                       @RequestBody(required = false) JsonNode body) {
        return service.receive(id, itemId, ErpGaMaterialRequestService.parseReceive(body), access.userId());
    }
}
