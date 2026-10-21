package id.enterpriseapps.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.security.ErpAccess;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Aset tetap — port {@code routes/fixedAssets.ts} (mount {@code requirePerm('fixed_assets')}, dijalankan ErpRouteGuard).
 */
@RestController
@RequestMapping("/api/erp/fixed-assets")
@RequiredArgsConstructor
@Tag(name = "ERP - Aset Tetap", description = "Register aset tetap, penyusutan bulanan, pelepasan, impor")
public class ErpFixedAssetController {

    private final ErpFixedAssetService service;
    private final ErpAccess access;

    /** Tautan EAM paralel yang lolos pra-cek tetapi ditolak indeks unik → 409 seperti sumber. */
    private static <T> T linkGuard(Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            if (FixedAssetGaLink.isConflict(e)) {
                throw ErpException.conflict(FixedAssetGaLink.CONFLICT_MESSAGE);
            }
            throw e;
        }
    }

    @GetMapping
    public Object list(@RequestParam Map<String, String> query) {
        return service.list(query);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return service.categories();
    }

    /** Ringkasan per kategori (aset Active). */
    @GetMapping("/summary/by-category")
    public List<Map<String, Object>> summaryByCategory() {
        return service.summaryByCategory();
    }

    @GetMapping("/{id}")
    public ObjectNode detail(@PathVariable String id) {
        return service.detail(id);
    }

    @PostMapping
    public ResponseEntity<ObjectNode> create(@RequestBody(required = false) JsonNode body) {
        GaZod z = GaZod.of(body, false);
        ErpFixedAssetService.AssetRow row = ErpFixedAssetService.parseRow(z, true);
        z.throwIfInvalid();
        String userId = access.userId();
        return ResponseEntity.status(201).body(linkGuard(() -> service.create(row, userId)));
    }

    @PostMapping("/{id}/depreciate")
    public Map<String, Object> depreciate(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        GaZod z = GaZod.of(body, false);
        String entryId = z.uuid("entryId", "entryId harus UUID valid.");
        z.throwIfInvalid();
        return service.depreciate(entryId, access.userId());
    }

    @PostMapping("/depreciate-bulk")
    public Map<String, Object> depreciateBulk(@RequestBody(required = false) JsonNode body) {
        return service.depreciateBulk(body, access.userId());
    }

    @PutMapping("/{id}")
    public ObjectNode update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return linkGuard(() -> service.update(id, body));
    }

    /** Impor CSV: {@code { assets: [...] }} (1–500 baris), pesan validasi default Zod per baris. */
    @PostMapping("/import")
    public Map<String, Object> importAssets(@RequestBody(required = false) JsonNode body) {
        GaZod root = GaZod.of(body, false);
        JsonNode assets = root.raw("assets");
        List<ErpFixedAssetService.AssetRow> rows = new ArrayList<>();
        if (!assets.isArray()) {
            root.issue(GaZod.invalidType("array", assets));
        } else {
            for (JsonNode el : assets) {
                GaZod z = GaZod.of(el, false);
                rows.add(ErpFixedAssetService.parseRow(z, false));
                if (z.error() != null) {
                    root.issue(z.error());
                }
            }
            if (assets.isEmpty()) {
                root.issue("Too small: expected array to have >=1 items");
            } else if (assets.size() > 500) {
                root.issue("Too big: expected array to have <=500 items");
            }
        }
        root.throwIfInvalid();
        return service.importAssets(rows, access.userId());
    }

    @PostMapping("/{id}/dispose")
    public Map<String, Object> dispose(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return service.dispose(id, body);
    }
}
