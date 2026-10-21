package id.enterpriseapps.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.security.ErpAccess;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pemeliharaan GA — port {@code routes/ga/maintenance.ts} (mount {@code requirePerm('ga_maintenance')},
 * dijalankan ErpRouteGuard): jadwal preventif dan work order.
 */
@RestController
@RequestMapping("/api/erp/ga/maintenance")
@RequiredArgsConstructor
@Tag(name = "ERP - GA Pemeliharaan", description = "Jadwal preventif & work order")
public class ErpGaMaintenanceController {

    private final ErpGaMaintenanceScheduleService schedules;
    private final ErpGaWorkOrderService workOrders;
    private final ErpAccess access;

    // ─────────────── jadwal ───────────────

    @GetMapping("/schedules")
    public Object listSchedules(@RequestParam Map<String, String> query) {
        return schedules.list(query);
    }

    @PostMapping("/schedules")
    public ResponseEntity<Object> createSchedule(@RequestBody(required = false) JsonNode body) {
        ErpGaMaintenanceScheduleService.CreateInput in = ErpGaMaintenanceScheduleService.parseCreate(body);
        return ResponseEntity.status(201).body(schedules.create(in, access.userId()));
    }

    @PutMapping("/schedules/{id}")
    public Map<String, Object> updateSchedule(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return schedules.update(id, ErpGaMaintenanceScheduleService.parseUpdate(body));
    }

    // ─────────────── work order ───────────────

    @GetMapping("/work-orders")
    public Object listWorkOrders(@RequestParam Map<String, String> query) {
        String userId = access.userId();
        if (query.containsKey("page")) {
            return workOrders.listPaged(query, userId);
        }
        return workOrders.list(query, userId);
    }

    /** Dideklarasikan sebelum /work-orders/:id. */
    @GetMapping("/work-orders/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return workOrders.summary(query, access.userId());
    }

    @GetMapping("/work-orders/{id}")
    public Object detail(@PathVariable String id) {
        return workOrders.detail(id);
    }

    @PostMapping("/work-orders")
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode body) {
        ErpGaWorkOrderService.CreateInput in = ErpGaWorkOrderService.parseCreate(body);
        return ResponseEntity.status(201).body(workOrders.create(in, access.userId()));
    }

    @PatchMapping("/work-orders/{id}/assign")
    public Map<String, Object> assign(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        ErpGaWorkOrderService.AssignInput in = ErpGaWorkOrderService.parseAssign(body);
        return workOrders.assign(id, in, access.userId());
    }

    @PatchMapping("/work-orders/{id}/start")
    public Map<String, Object> start(@PathVariable String id) {
        return workOrders.start(id, access.userId());
    }

    @PatchMapping("/work-orders/{id}/complete")
    public Map<String, Object> complete(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        ErpGaWorkOrderService.CompleteInput in = ErpGaWorkOrderService.parseComplete(body);
        return workOrders.complete(id, in, access.userId());
    }

    @PatchMapping("/work-orders/{id}/close")
    public Map<String, Object> close(@PathVariable String id) {
        return workOrders.close(id, access.userId());
    }

    @PatchMapping("/work-orders/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        String reason = ErpGaWorkOrderService.parseCancelReason(body);
        return workOrders.cancel(id, reason, access.userId());
    }

    /** Multipart: field berkas {@code photo}, field teks {@code type} (BEFORE/AFTER) & {@code caption}. */
    @PostMapping("/work-orders/{id}/photos")
    public ResponseEntity<Object> photo(@PathVariable String id, HttpServletRequest request) {
        MultipartFile file = GaUploads.single(request, "photo", ErpGaWorkOrderService.PHOTO_MIME,
                ErpGaWorkOrderService.MAX_PHOTO_BYTES);
        if (file == null) {
            throw ErpException.business("File wajib diupload.");
        }
        String rawType = request.getParameter("type");
        String type = (rawType == null ? "" : rawType).toUpperCase(Locale.ROOT);
        if (!List.of("BEFORE", "AFTER").contains(type)) {
            throw ErpException.business("type harus BEFORE atau AFTER.");
        }
        return ResponseEntity.status(201).body(workOrders.addPhoto(id, type, request.getParameter("caption"),
                GaUploads.bytes(file), file.getOriginalFilename(), file.getContentType(), access.userId()));
    }

    @PostMapping("/work-orders/{id}/parts")
    public ResponseEntity<Object> part(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        ErpGaWorkOrderService.PartInput in = ErpGaWorkOrderService.parsePart(body);
        return ResponseEntity.status(201).body(workOrders.addPart(id, in, access.userId()));
    }
}
