package id.enterpriseapps.erp.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Jejak audit — port {@code routes/audit.ts}. Gerbang {@code requirePerm('audit')} dijalankan ErpRouteGuard.
 * Kontrak lama: {@code ?page&limit(default 50, maks 200)&userId&action&entityType&startDate&endDate}
 * → {@code { data, total, page, limit }}; tambahan opsional {@code search} (entity id) & {@code sort}.
 */
@RestController
@RequestMapping("/api/erp/audit-logs")
@RequiredArgsConstructor
public class ErpAuditLogController {

    private final ErpAuditLogService audit;

    @GetMapping
    public Object list(@RequestParam Map<String, String> query) {
        return audit.list(query);
    }

    @GetMapping("/summary")
    public Object summary(@RequestParam Map<String, String> query) {
        return audit.summary(query);
    }

    @GetMapping("/facets")
    public Object facets() {
        return audit.facets();
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        return audit.detail(id);
    }
}
