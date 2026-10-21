package id.enterpriseapps.erp.inventory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.Map;

/**
 * Item stok — port {@code routes/inventory/items.ts}. Gerbang {@code requireWrite('inventory')}
 * (GET terbuka untuk form) dijalankan ErpRouteGuard.
 */
@RestController
@RequestMapping("/api/erp/inventory/items")
@RequiredArgsConstructor
public class ErpInventoryItemController {

    private final ErpInventoryItemService service;

    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return service.list(query);
    }

    /** KPI untuk filter yang sama dengan daftar; dideklarasikan sebelum /:id. */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return service.summary(query);
    }

    @GetMapping("/{id}/batches")
    public Map<String, Object> batches(@PathVariable String id,
                                       @RequestParam(required = false) String warehouseId) {
        return service.batches(id, warehouseId != null && !warehouseId.isEmpty() ? warehouseId : null);
    }

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody InventoryItemCreateRequest body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @Valid @RequestBody InventoryItemUpdateRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        return service.delete(id);
    }
}
