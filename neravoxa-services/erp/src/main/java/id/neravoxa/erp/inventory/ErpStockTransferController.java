package id.neravoxa.erp.inventory;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.security.ErpAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Transfer stok antar gudang — port {@code routes/stockTransfers.ts}. Gerbang
 * {@code requirePerm('stock_transfer')} dijalankan ErpRouteGuard.
 */
@RestController
@RequestMapping("/api/erp/stock-transfers")
@RequiredArgsConstructor
public class ErpStockTransferController {

    private final ErpStockTransferService service;
    private final ErpAccess access;

    /** Query: page, limit (default 50, ≤200), status, warehouseId, startDate, endDate, search, sort. */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return service.list(query);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        return service.detail(id);
    }

    /** Buat + submit (satu langkah). */
    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody StockTransferRequest body) {
        if (body.fromWarehouseId().equals(body.toWarehouseId())) {
            throw ErpException.business("Gudang asal dan tujuan harus berbeda.");
        }
        return ResponseEntity.status(201).body(service.submit(body, access.userId()));
    }

    /** Edit field header saja (catatan, mobil, supir, penerima). */
    @PatchMapping("/{id}")
    public Map<String, Object> updateHeader(@PathVariable String id, @Valid @RequestBody StockTransferHeaderRequest body) {
        return service.updateHeader(id, body);
    }

    @PostMapping("/{id}/mark-printed")
    public Map<String, Object> markPrinted(@PathVariable String id) {
        return service.markPrinted(id);
    }

    @PatchMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        return service.cancel(id);
    }
}
