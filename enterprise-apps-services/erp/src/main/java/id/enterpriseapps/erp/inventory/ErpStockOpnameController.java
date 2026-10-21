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
 * Stok opname — port {@code routes/stockOpname.ts}. Gerbang {@code requirePerm('stock_opname')}
 * dijalankan ErpRouteGuard; pembatalan butuh capability {@code doc.cancel}.
 */
@RestController
@RequestMapping("/api/erp/stock-opname")
@RequiredArgsConstructor
public class ErpStockOpnameController {

    private final ErpStockOpnameService service;
    private final ErpAccess access;

    /** Query: page, limit (default 20, ≤100), status, warehouseId, startDate, endDate, search, sort. */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return service.list(query);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        return service.detail(id);
    }

    /** Buat dan submit (finalisasi) dalam satu langkah. */
    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody StockOpnameRequest body) {
        return ResponseEntity.status(201).body(service.create(body, access.userId()));
    }

    @PutMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return service.cancel(id);
    }
}
