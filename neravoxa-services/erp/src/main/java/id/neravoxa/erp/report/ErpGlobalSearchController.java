package id.neravoxa.erp.report;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Pencarian global — port {@code routes/search.ts} di {@code /api/erp/search}. Rute terbuka untuk
 * pengguna login (ErpRouteGuard OPEN); izin per jenis record dicek di service ({@link SearchScope}).
 */
@RestController
@RequestMapping("/api/erp/search")
@RequiredArgsConstructor
public class ErpGlobalSearchController {

    private final ErpGlobalSearchService search;

    /** Query: q (min. 2 karakter), limit (default 10, maks. 20). */
    @GetMapping({"", "/"})
    public List<Map<String, Object>> search(@RequestParam Map<String, String> query) {
        return search.search(query);
    }
}
