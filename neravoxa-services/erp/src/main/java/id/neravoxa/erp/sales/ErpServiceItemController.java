package id.neravoxa.erp.sales;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.security.ErpAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Layanan/jasa — port {@code routes/serviceItems.ts} (mount {@code /api/service-items}).
 * ErpRouteGuard: GET terbuka (data referensi form), tulis butuh edit {@code sales_invoice}.
 */
@RestController
@RequestMapping("/api/erp/service-items")
@RequiredArgsConstructor
@Tag(name = "ERP - Layanan", description = "Master layanan/jasa untuk invoice & penawaran")
public class ErpServiceItemController {

    private final ErpServiceItemService serviceItems;
    private final ErpSalesRequestValidator validator;
    private final ErpAccess access;

    /** Tanpa page: { data } semua baris urut kode. Dengan page: page, limit (≤200), isActive, search, sort. */
    @GetMapping
    @Operation(summary = "Daftar layanan")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return serviceItems.list(query);
    }

    @PostMapping
    @Operation(summary = "Buat layanan")
    public ResponseEntity<ObjectNode> create(@RequestBody ServiceItemRequests.Create body) {
        validator.check(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceItems.create(body));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Ubah layanan")
    public ObjectNode update(@PathVariable String id, @RequestBody ServiceItemRequests.Patch body) {
        validator.check(body);
        return serviceItems.update(id, body);
    }

    /** Soft delete (butuh capability doc.cancel). */
    @DeleteMapping("/{id}")
    @Operation(summary = "Nonaktifkan layanan")
    public Map<String, Object> delete(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return serviceItems.deactivate(id);
    }
}
