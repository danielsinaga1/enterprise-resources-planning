package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Laporan biaya pemeliharaan: total, per aset, per bulan, keterlambatan, dan batas periode. */
class MaintenanceCostReportTest extends GaOpsTestSupport {

    private String wo(String tag, int n, String assetId, String type, String status, String completedAt, Integer labor,
                      Integer external, boolean late) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_work_orders (id, code, asset_id, type, status, priority, title, reported_at, completed_at, "
                        + "labor_cost, external_cost, was_late, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'MEDIUM', ?, "
                        + "CAST(? AS timestamp) - interval '2 days', CAST(? AS timestamp), ?, ?, ?, now(), now())",
                id, "WO-T" + tag + "-" + n, assetId, type, status, "WO uji " + n, completedAt, completedAt, labor, external, late);
        return id;
    }

    @Test
    void aggregates_completed_work_orders_in_period() throws Exception {
        asAdmin();
        String t = tag();
        String pump = asset(t + "P", "MESIN_PRODUKSI");
        String fan = asset(t + "F", "MESIN_PRODUKSI");
        String item = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_inventory_items (id, sku, name, category_id, uom, created_at) VALUES (?, ?, ?, ?, 'pcs', now())",
                item, "SKU-" + t, "Seal " + t, invCategory(t));

        String w1 = wo(t, 1, pump, "PREVENTIVE", "CLOSED", "2025-03-10 08:00:00", 100_000, null, false);
        jdbc.update("INSERT INTO ga_work_order_parts (id, work_order_id, inventory_item_id, quantity, unit_cost) VALUES (?, ?, ?, 2, 50000)",
                UUID.randomUUID().toString(), w1, item);
        wo(t, 2, pump, "CORRECTIVE", "DONE", "2025-04-05 10:00:00", 50_000, 300_000, true);
        wo(t, 3, fan, "CORRECTIVE", "CLOSED", "2025-04-20 09:00:00", 20_000, null, false);
        wo(t, 4, fan, "CORRECTIVE", "CLOSED", "2024-12-01 09:00:00", 999_000, null, false);   // di luar periode
        wo(t, 5, fan, "CORRECTIVE", "OPEN", "2025-04-21 09:00:00", 777_000, null, false);     // belum selesai

        JsonNode r = ok(get(MT + "/cost-report?from=2025-03-01&to=2025-04-30"));
        JsonNode totals = r.get("totals");
        assertEquals(2, r.get("period").get("months").asInt());
        assertDec("570000", totals.get("total").asText());
        assertDec("170000", totals.get("labor").asText());
        assertDec("300000", totals.get("external").asText());
        assertDec("100000", totals.get("parts").asText());
        assertEquals(3, totals.get("workOrders").asInt());
        assertEquals(1, totals.get("preventive").asInt());
        assertEquals(1, totals.get("late").asInt());
        assertDec("0.3333", totals.get("lateRate").asText());
        assertDec("285000", totals.get("costPerMonth").asText());
        assertDec("48.0", totals.get("avgResolutionHours").asText());
        assertEquals(2, totals.get("assetCount").asInt());
        assertTrue(totals.get("openOverdue").asLong() >= 0);

        JsonNode top = r.get("assets").get(0);
        assertEquals(pump, top.get("assetId").asText());
        assertEquals("AST-" + t + "P", top.get("code").asText());
        assertDec("550000", top.get("total").asText());
        assertEquals(2, top.get("workOrders").asInt());
        assertEquals(1, top.get("late").asInt());

        JsonNode trend = r.get("monthlyTrend");
        assertEquals(2, trend.size());
        assertEquals("2025-03", trend.get(0).get("month").asText());
        assertDec("200000", trend.get(0).get("total").asText());
        assertDec("370000", trend.get(1).get("total").asText());
        assertEquals(2, trend.get(1).get("workOrders").asInt());

        // Periode tanpa WO selesai: semua nol, bulan tetap muncul.
        JsonNode empty = ok(get(MT + "/cost-report?from=2019-01-01&to=2019-02-28"));
        assertDec("0", empty.get("totals").get("total").asText());
        assertEquals(0, empty.get("assets").size());
        assertEquals(2, empty.get("monthlyTrend").size());
    }
}
