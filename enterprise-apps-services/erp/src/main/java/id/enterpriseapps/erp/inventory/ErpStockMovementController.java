package id.enterpriseapps.erp.inventory;

import id.enterpriseapps.erp.core.security.ErpAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Gerakan stok — port {@code routes/inventory/movements.ts}. Gerbang {@code requirePerm('inventory')}
 * dijalankan ErpRouteGuard; pembatalan butuh capability {@code doc.cancel}.
 */
@RestController
@RequestMapping("/api/erp/inventory/movements")
@RequiredArgsConstructor
public class ErpStockMovementController {

    private final ErpStockMovementService service;
    private final ErpAccess access;

    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return service.list(query);
    }

    /** KPI (jumlah, qty & nilai masuk/keluar, batal); dideklarasikan sebelum /:id. */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return service.summary(query);
    }

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody StockMovementCreateRequest body) {
        return ResponseEntity.status(201).body(service.create(body, access.userId()));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @Valid @RequestBody StockMovementUpdateRequest body) {
        return service.update(id, body, access.userId());
    }

    @PutMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return service.cancel(id);
    }
}
