package id.neravoxa.erp.report;

import id.neravoxa.erp.core.security.ErpAccess;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check integritas data — port {@code routes/healthCheck.ts} di {@code /api/erp/health-check}
 * (gerbang modul {@code audit}: GET view, POST fix edit, oleh ErpRouteGuard).
 */
@RestController
@RequestMapping("/api/erp/health-check")
@RequiredArgsConstructor
public class ErpHealthCheckController {

    private final ErpHealthCheckService health;
    private final ErpAccess access;

    /** { success, data: { status, checks } }. */
    @GetMapping({"", "/"})
    public Map<String, Object> run() {
        return health.runAll();
    }

    /** { success, data: { check, description, warning?, count, details } }. */
    @GetMapping("/preview/{check}")
    public Map<String, Object> preview(@PathVariable String check) {
        return health.preview(check);
    }

    /** { success, data: { fixed, message } }. */
    @PostMapping("/fix/{check}")
    public Map<String, Object> fix(@PathVariable String check, HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        String ip = xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : request.getRemoteAddr();
        return health.fix(check, new ErpHealthCheckService.Caller(access.userId(), ip, request.getHeader("User-Agent")));
    }
}
