package id.neravoxa.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.entity.Party;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
 * Pelanggan & Vendor — port {@code routes/parties.ts} (mount {@code /api/parties}).
 * ErpRouteGuard (requireWrite 'parties'): GET terbuka untuk form/picker, tulis butuh edit
 * {@code parties}; hapus juga butuh capability {@code doc.cancel}.
 */
@RestController
@RequestMapping("/api/erp/parties")
@RequiredArgsConstructor
@Tag(name = "ERP - Mitra", description = "Pelanggan & vendor")
public class ErpPartyController {

    private final ErpPartyService parties;
    private final ErpPartyDashboardService dashboard;
    private final ErpAccess access;

    @GetMapping
    @Operation(summary = "Daftar mitra (mode daftar: search/sort/isActive/hasOutstanding; mode lama: type)")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return parties.list(query);
    }

    @GetMapping("/summary")
    @Operation(summary = "Ringkasan KPI mitra (filter sama dengan daftar)")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return parties.summary(query);
    }

    @GetMapping("/similar")
    @Operation(summary = "Mitra dengan nama serupa (deteksi duplikat)")
    public Map<String, Object> similar(@RequestParam Map<String, String> query) {
        return parties.similar(query.get("name"), query.get("type"));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Analisis piutang/utang mitra")
    public Map<String, Object> dashboard() {
        return dashboard.dashboard();
    }

    @PostMapping
    @Operation(summary = "Buat mitra")
    public ResponseEntity<Party> create(@RequestBody(required = false) JsonNode body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parties.create(body));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail mitra")
    public Party get(@PathVariable String id) {
        return parties.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Ubah mitra")
    public Party update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return parties.update(id, body);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Hapus mitra (dinonaktifkan bila punya transaksi)")
    public Map<String, Object> delete(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return parties.delete(id);
    }
}
