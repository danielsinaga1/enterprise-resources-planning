package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Antrian COGS backfill — port {@code routes/cogsBackfill.ts} (mount {@code /api/cogs-backfill},
 * gerbang requirePerm('journal') dijalankan {@code ErpRouteGuard}).
 */
@RestController
@RequestMapping("/api/erp/cogs-backfill")
@RequiredArgsConstructor
public class ErpCogsBackfillController {

    private final ErpCogsBackfillQueueService queue;

    @GetMapping({"", "/"})
    public JsonNode list(@RequestParam Map<String, String> query) {
        return queue.list(query);
    }

    @GetMapping("/summary")
    public JsonNode summary() {
        return queue.summary();
    }
}
