package id.enterpriseapps.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.production.ErpProductionBodyParser.Coerce;
import id.enterpriseapps.erp.production.ProductionRequests.MachineCreateRequest;
import id.enterpriseapps.erp.production.ProductionRequests.MachineUpdateRequest;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Master mesin (OEE) — port {@code routes/inventory/machines.ts}. Gerbang
 * {@code requireWrite('production')} dijalankan ErpRouteGuard (GET terbuka untuk form).
 */
@RestController
@RequestMapping("/api/erp/inventory/machines")
@RequiredArgsConstructor
public class ErpMachineController {

    static final List<Coerce> NUMBERS = List.of(new Coerce("idealRatePerHour", true));
    static final Map<String, String> UPDATE_NON_NULL = nonNull();

    private final ErpMachineService service;
    private final ErpProductionBodyParser parser;

    private static Map<String, String> nonNull() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("code", "Invalid input: expected string, received null");
        m.put("name", "Invalid input: expected string, received null");
        m.put("unit", "Invalid input: expected string, received null");
        m.put("isActive", "Invalid input: expected boolean, received null");
        return m;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String active) {
        return service.list(active);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode raw) {
        MachineCreateRequest body = parser.parse(raw, MachineCreateRequest.class, NUMBERS);
        return ResponseEntity.status(201).body(service.create(body, raw));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody(required = false) JsonNode raw) {
        MachineUpdateRequest body = parser.parse(raw, MachineUpdateRequest.class, NUMBERS, UPDATE_NON_NULL);
        return service.update(id, body, raw == null ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode() : raw);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        return service.delete(id);
    }
}
