package id.enterpriseapps.erp.inventory;

import id.enterpriseapps.erp.core.security.ErpAccess;
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
 * Gudang — port {@code routes/warehouses.ts}. Gerbang {@code requireWrite('warehouse')} dijalankan
 * ErpRouteGuard; hapus gudang butuh capability {@code doc.cancel}.
 */
@RestController
@RequestMapping("/api/erp/warehouses")
@RequiredArgsConstructor
public class ErpWarehouseController {

    private final ErpWarehouseService service;
    private final ErpAccess access;

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String active) {
        return service.list(active);
    }

    /** Stok per gudang per akhir tanggal; HARUS sebelum /{id}. */
    @GetMapping("/stock-snapshot")
    public Map<String, Object> snapshot(@RequestParam(required = false) String date,
                                        @RequestParam(required = false) String warehouseIds) {
        return service.snapshot(date, warehouseIds);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        return service.detail(id);
    }

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody WarehouseRequest body) {
        body.requireCreateFields();
        return ResponseEntity.status(201).body(service.create(body));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @Valid @RequestBody WarehouseRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return service.delete(id);
    }
}
