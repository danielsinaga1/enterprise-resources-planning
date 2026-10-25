package id.neravoxa.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.production.ErpProductionBodyParser.Coerce;
import id.neravoxa.erp.production.ProductionRequests.ProcessCreateRequest;
import id.neravoxa.erp.production.ProductionRequests.ProcessUpdateRequest;
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

import java.util.List;
import java.util.Map;

/**
 * Master proses produksi — port {@code routes/inventory/productionProcesses.ts}. Gerbang
 * {@code requireWrite('production')} dijalankan ErpRouteGuard; hapus butuh capability {@code doc.cancel}.
 */
@RestController
@RequestMapping("/api/erp/inventory/production-processes")
@RequiredArgsConstructor
public class ErpProductionProcessController {

    static final List<Coerce> NUMBERS = List.of(new Coerce("sortOrder", false));
    static final Map<String, String> UPDATE_NON_NULL = Map.of(
            "name", "Invalid input: expected string, received null",
            "isActive", "Invalid input: expected boolean, received null");

    private final ErpProductionProcessService service;
    private final ErpProductionBodyParser parser;
    private final ErpAccess access;

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String active) {
        return service.list(active);
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode raw) {
        ProcessCreateRequest body = parser.parse(raw, ProcessCreateRequest.class, NUMBERS);
        return ResponseEntity.status(201).body(service.create(body, raw));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody(required = false) JsonNode raw) {
        ProcessUpdateRequest body = parser.parse(raw, ProcessUpdateRequest.class, NUMBERS, UPDATE_NON_NULL);
        return service.update(id, body, raw == null ? JsonNodeFactory.instance.objectNode() : raw);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return service.delete(id);
    }
}
