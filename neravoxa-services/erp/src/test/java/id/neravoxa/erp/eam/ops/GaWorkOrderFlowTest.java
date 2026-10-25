package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.accounting.ErpVehicleServiceJournalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pemeliharaan GA: jadwal preventif → WO preventif → assign → start → part (stok GA berkurang) →
 * complete (jadwal bergulir) → close (log servis WORK_ORDER, tanpa efek GL); WO dari permintaan servis
 * kendaraan (odometer naik); cancel, foto, dan guard status/peran.
 */
class GaWorkOrderFlowTest extends GaOpsTestSupport {

    @Autowired
    private ErpVehicleServiceJournalService vehicleJournals;

    private static final byte[] JPG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00, 0x01};

    private String wo(String path) {
        return MT + "/work-orders/" + path;
    }

    private Instant ts(String sql, Object... args) {
        Timestamp t = jdbc.queryForObject(sql, Timestamp.class, args);
        return t == null ? null : t.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    @Test
    void preventive_lifecycle_consumes_stock_rolls_schedule_and_logs_service_on_close() throws Exception {
        String admin = adminId();
        String techRole = customRole(Map.of("ga_maintenance", "edit"));
        String techEmail = email("teknisi");
        String tech = provision(techEmail, techRole);
        asAdmin();
        String t = tag();
        String asset = asset(t, "MESIN_PRODUKSI");

        // Jadwal preventif.
        expectError(400, "Isi salah satu interval (hari, jam, atau km).",
                post(MT + "/schedules", body("assetId", asset, "nextDueAt", "2026-01-01")));
        expectError(400, "assetId wajib diisi.",
                post(MT + "/schedules", body("assetId", "", "intervalDays", 30, "nextDueAt", "2026-01-01")));
        expectError(400, "Invalid input: expected int, received number",
                post(MT + "/schedules", body("assetId", asset, "intervalDays", 1.5, "nextDueAt", "2026-01-01")));
        expectError(400, "nextDueAt wajib diisi.",
                post(MT + "/schedules", body("assetId", asset, "intervalDays", 30, "nextDueAt", "")));
        expectError(400, "Aset tidak ditemukan.",
                post(MT + "/schedules", body("assetId", "tidak-ada", "intervalDays", 30, "nextDueAt", "2026-01-01")));
        Result sch = call(post(MT + "/schedules", body("assetId", asset, "intervalDays", 30, "nextDueAt", "2020-01-01",
                "notes", "PM bulanan")));
        assertEquals(201, sch.status(), String.valueOf(sch.body()));
        String scheduleId = sch.data().get("id").asText();
        JsonNode schedules = ok(get(MT + "/schedules?assetId=" + asset + "&active=true"));
        assertEquals(1, schedules.size());
        assertEquals("AST-" + t, schedules.get(0).get("asset").get("code").asText());
        assertEquals("2020-01-01T00:00:00.000Z", schedules.get(0).get("nextDueAt").asText());
        assertEquals(scheduleId, ok(put(MT + "/schedules/" + scheduleId, body("notes", "PM 30 hari"))).get("id").asText());
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.",
                put(MT + "/schedules/tidak-ada", body("notes", "x")));

        // WO preventif dari jadwal (target sudah lewat → terlambat saat selesai).
        expectError(400, "Judul wajib diisi.", post(MT + "/work-orders", body("assetId", asset, "title", "")));
        expectError(400, "Invalid option: expected one of \"LOW\"|\"MEDIUM\"|\"HIGH\"|\"URGENT\"",
                post(MT + "/work-orders", body("assetId", asset, "title", "X", "priority", "KRITIS")));
        expectError(400, "Jadwal maintenance tidak ditemukan.",
                post(MT + "/work-orders", body("assetId", asset, "title", "X", "scheduleId", "tidak-ada")));
        Result created = call(post(MT + "/work-orders", body("assetId", asset, "type", "PREVENTIVE", "title", "PM " + t,
                "component", "Gearbox", "priority", "HIGH", "scheduleId", scheduleId, "dueAt", "2020-01-02")));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        String woId = created.data().get("id").asText();
        String code = created.data().get("code").asText();
        assertTrue(code.matches("WO-" + Instant.now().atZone(ZoneOffset.UTC).getYear() + "-\\d{4}"), code);

        // Assign → start (hanya teknisi).
        expectError(400, "Teknisi wajib dipilih.", patch(wo(woId + "/assign"), body("assignedToId", "")));
        assertEquals("ASSIGNED", ok(patch(wo(woId + "/assign"), body("assignedToId", tech))).get("status").asText());
        expectError(403, "Hanya teknisi yang ditugaskan yang bisa mulai.", patch(wo(woId + "/start"), Map.of()));
        expectError(403, "Hanya teknisi yang ditugaskan yang bisa tandai selesai.", patch(wo(woId + "/complete"), Map.of()));

        // Stok GA untuk part.
        String cat = invCategory(t);
        String loc = location(t, "WS");
        String itemId = ok(post(INV + "/items", body("sku", "WO-SKU-" + t, "name", "Oli gear " + t, "categoryId", cat,
                "uom", "liter"))).get("id").asText();
        ok(post(INV + "/movements", body("itemId", itemId, "type", "IN", "quantity", 5, "toLocationId", loc)));

        asUser(techEmail, techRole);
        assertEquals("IN_PROGRESS", ok(patch(wo(woId + "/start"), Map.of())).get("status").asText());
        expectError(400, "Tidak bisa start dari status IN_PROGRESS.", patch(wo(woId + "/start"), Map.of()));
        expectError(400, "Item wajib dipilih.", post(wo(woId + "/parts"), body("inventoryItemId", "", "quantity", 1, "unitCost", 1)));
        expectError(400, "Item inventory tidak ditemukan.",
                post(wo(woId + "/parts"), body("inventoryItemId", "tidak-ada", "quantity", 1, "unitCost", 1)));
        Result p1 = call(post(wo(woId + "/parts"), body("inventoryItemId", itemId, "fromLocationId", loc, "quantity", 2,
                "unitCost", 40000)));
        assertEquals(201, p1.status(), String.valueOf(p1.body()));
        assertTrue(p1.data().get("warning").isNull());
        assertDec("3", stockAt(itemId, loc));
        // Melebihi stok: tetap diizinkan (stok negatif) dengan peringatan.
        JsonNode p2 = ok(post(wo(woId + "/parts"), body("inventoryItemId", itemId, "fromLocationId", loc, "quantity", 5,
                "unitCost", 40000)));
        assertEquals("Stok tidak cukup untuk item " + itemId + " (sisa -2). Tetap diizinkan (stok negatif).",
                p2.get("warning").asText());
        assertDec("-2", stockAt(itemId, loc));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ga_inventory_movements WHERE item_id = ? AND type = 'OUT' "
                + "AND reference_no = ?", Integer.class, itemId, "MAINTENANCE_" + code));
        // Part tanpa lokasi: tidak ada movement.
        ok(post(wo(woId + "/parts"), body("inventoryItemId", itemId, "quantity", 1, "unitCost", 10000)));
        assertDec("-2", stockAt(itemId, loc));

        // Complete: checklist wajib, biaya, jadwal bergulir.
        expectError(400, "Checklist wajib belum lengkap: Ganti oli",
                patch(wo(woId + "/complete"), body("checklist", List.of(body("id", "1", "label", "Ganti oli", "required", true)))));
        expectError(400, "Too small: expected number to be >=0", patch(wo(woId + "/complete"), body("laborCost", -1)));
        JsonNode done = ok(patch(wo(woId + "/complete"), body("resolution", "Selesai", "checklist",
                List.of(body("id", "1", "label", "Ganti oli", "required", true, "done", true)),
                "laborHours", 2, "laborCost", 150000, "externalVendor", "  Bengkel Jaya  ", "externalCost", 50000)));
        assertEquals("DONE", done.get("status").asText());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT was_late FROM ga_work_orders WHERE id = ?", Boolean.class, woId));
        Instant completedAt = ts("SELECT completed_at FROM ga_work_orders WHERE id = ?", woId);
        assertEquals(completedAt, ts("SELECT last_done_at FROM ga_maintenance_schedules WHERE id = ?", scheduleId));
        assertEquals(completedAt.plus(Duration.ofDays(30)), ts("SELECT next_due_at FROM ga_maintenance_schedules WHERE id = ?", scheduleId));
        String checklist = jdbc.queryForObject("SELECT checklist::text FROM ga_work_orders WHERE id = ?", String.class, woId);
        assertTrue(checklist.contains("\"done\": true") || checklist.contains("\"done\":true"), checklist);

        // Detail & daftar (biaya = tenaga kerja + vendor + part).
        asAdmin();
        JsonNode detail = ok(get(wo(woId)));
        assertDec("490000", detail.get("totalCost").asText()); // 150000 + 50000 + 2×40000 + 5×40000 + 10000
        assertEquals(3, detail.get("parts").size());
        assertEquals("WO-SKU-" + t, detail.get("parts").get(0).get("inventoryItem").get("sku").asText());
        assertEquals(scheduleId, detail.get("schedule").get("id").asText());
        assertEquals("AST-" + t, detail.get("asset").get("code").asText());
        JsonNode legacy = ok(get(MT + "/work-orders?assetId=" + asset));
        assertEquals(1, legacy.size());
        assertEquals(3, legacy.get(0).get("_count").get("parts").asInt());
        assertDec("490000", legacy.get(0).get("totalCost").asText());
        JsonNode paged = ok(get(MT + "/work-orders?page=1&search=pm " + t.toLowerCase()));
        assertEquals(1, paged.get("total").asInt());
        assertEquals(tech, paged.get("data").get(0).get("assignee").get("id").asText());
        assertEquals(0, ok(get(MT + "/work-orders?page=1&status=ACTIVE&assetId=" + asset)).get("total").asInt());
        JsonNode byPriority = ok(get(MT + "/work-orders?page=1&limit=200&sort=-priority"));
        String prevP = null;
        for (JsonNode r : byPriority.get("data")) {
            String p = r.get("priority").asText();
            if (prevP != null) {
                assertTrue(GaWorkOrderList.PRIORITIES.indexOf(p) <= GaWorkOrderList.PRIORITIES.indexOf(prevP), prevP + " -> " + p);
            }
            prevP = p;
        }
        ok(get(MT + "/work-orders?page=1&sort=dueAt"));
        ok(get(MT + "/work-orders?page=1&sort=status&startDate=2020-01-01&endDate=2099-12-31"));
        expectError(400, "Status work order tidak dikenal.", get(MT + "/work-orders?page=1&status=FOO"));
        expectError(400, "Filter jatuh tempo tidak dikenal.", get(MT + "/work-orders?page=1&due=later"));
        expectError(500, "Gagal mengambil work order.", get(MT + "/work-orders?status=FOO"));
        JsonNode sum = ok(get(MT + "/work-orders/summary?assetId=" + asset));
        assertEquals(1, sum.get("count").asInt());
        assertEquals(0, sum.get("active").asInt());
        assertEquals(1, sum.get("doneThisMonth").asInt());
        assertDec("490000", sum.get("totalCost").asText());

        // Close (DONE → CLOSED): log servis WORK_ORDER, biaya tenaga kerja + vendor, tanpa jurnal.
        expectError(400, "Tidak bisa cancel dari status DONE.", patch(wo(woId + "/cancel"), body("reason", "x")));
        assertEquals("CLOSED", ok(patch(wo(woId + "/close"), Map.of())).get("status").asText());
        expectError(400, "Tidak bisa close dari status CLOSED.", patch(wo(woId + "/close"), Map.of()));
        expectError(400, "Tidak bisa menambah part pada WO berstatus CLOSED.",
                post(wo(woId + "/parts"), body("inventoryItemId", itemId, "quantity", 1, "unitCost", 1)));
        Map<String, Object> log = jdbc.queryForMap("SELECT id, source, biaya, bengkel, jenis_service, deskripsi, odometer, "
                + "journal_entry_id, created_by_id FROM ga_vehicle_service_logs WHERE work_order_id = ?", woId);
        assertEquals("WORK_ORDER", log.get("source"));
        assertDec("200000", log.get("biaya"));
        assertEquals("Bengkel Jaya", log.get("bengkel"));
        assertEquals("LAINNYA", log.get("jenis_service"));
        assertEquals("PM " + t, log.get("deskripsi"));
        assertNull(log.get("odometer"));
        assertNull(log.get("journal_entry_id"));
        assertEquals(admin, log.get("created_by_id"));
        // Log WORK_ORDER tidak bisa dijurnal lewat kernel (nilai sudah tercatat di GL lewat jalur lain).
        ErpException refused = assertThrows(ErpException.class,
                () -> vehicleJournals.post((String) log.get("id"), null, null, admin));
        assertEquals("Service log bersumber \"WORK_ORDER\" (data historis) tidak dapat diposting ke jurnal karena nilainya "
                + "sudah tercatat di GL lewat input manual.", refused.getMessage());
    }

    @Test
    void vehicle_service_request_work_order_close_links_log_and_bumps_odometer() throws Exception {
        String admin = adminId();
        String t = tag();
        String vehicle = asset(t, "KENDARAAN");
        JsonNode created = ok(post(MT + "/work-orders", body("assetId", vehicle, "title", "Servis berkala " + t)));
        String woId = created.get("id").asText();
        assertEquals("CORRECTIVE", jdbc.queryForObject("SELECT type FROM ga_work_orders WHERE id = ?", String.class, woId));
        assertEquals("MEDIUM", jdbc.queryForObject("SELECT priority FROM ga_work_orders WHERE id = ?", String.class, woId));
        String req = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_vehicle_service_requests (id, asset_id, requested_by_id, jenis_service, odometer, deskripsi, "
                + "urgensi, status, work_order_id, created_at, updated_at) VALUES (?, ?, ?, 'SERVIS_BERKALA', 12345, 'Servis 10rb km', "
                + "'MEDIUM', 'APPROVED', ?, now(), now())", req, vehicle, admin, woId);
        jdbc.update("INSERT INTO ga_vehicle_data (id, asset_id, odometer_terakhir, created_at, updated_at) "
                + "VALUES (?, ?, 10000, now(), now())", UUID.randomUUID().toString(), vehicle);

        ok(patch(wo(woId + "/assign"), body("assignedToId", admin, "priority", "URGENT")));
        assertEquals("URGENT", jdbc.queryForObject("SELECT priority FROM ga_work_orders WHERE id = ?", String.class, woId));
        // Complete langsung dari ASSIGNED; tanpa checklist → kolom checklist tidak diubah.
        ok(patch(wo(woId + "/complete"), body("externalVendor", "AHASS " + t, "externalCost", 300000)));
        ok(patch(wo(woId + "/close"), Map.of()));

        Map<String, Object> log = jdbc.queryForMap("SELECT id, source, biaya, bengkel, jenis_service, odometer, deskripsi "
                + "FROM ga_vehicle_service_logs WHERE work_order_id = ?", woId);
        assertEquals("WORK_ORDER", log.get("source"));
        assertEquals("SERVIS_BERKALA", log.get("jenis_service"));
        assertEquals(12345, ((Number) log.get("odometer")).intValue());
        assertDec("300000", log.get("biaya"));
        assertEquals("AHASS " + t, log.get("bengkel"));
        assertEquals(log.get("id"), jdbc.queryForObject("SELECT service_log_id FROM ga_vehicle_service_requests WHERE id = ?",
                String.class, req));
        assertEquals(12345, jdbc.queryForObject("SELECT odometer_terakhir FROM ga_vehicle_data WHERE asset_id = ?",
                Integer.class, vehicle));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ga_vehicle_service_logs WHERE work_order_id = ?",
                Integer.class, woId));
        assertThrows(ErpException.class, () -> vehicleJournals.post((String) log.get("id"), null, null, admin));
    }

    @Test
    void cancel_photos_and_guards() throws Exception {
        adminId();
        String otherRole = customRole(Map.of("ga_maintenance", "edit"));
        String otherEmail = email("lain");
        provision(otherEmail, otherRole);
        asAdmin();
        String t = tag();
        String asset = asset(t, "EQUIPMENT");
        String woId = ok(post(MT + "/work-orders", body("assetId", asset, "title", "Kompresor bocor " + t,
                "dueAt", Instant.now().plus(Duration.ofDays(3)).toString()))).get("id").asText();

        // Foto BEFORE/AFTER (multipart).
        expectError(400, "File wajib diupload.", MockMvcRequestBuilders.multipart(wo(woId + "/photos")).param("type", "BEFORE"));
        expectError(400, "type harus BEFORE atau AFTER.", MockMvcRequestBuilders.multipart(wo(woId + "/photos"))
                .file(new MockMultipartFile("photo", "a.jpg", "image/jpeg", JPG)).param("type", "DURING"));
        expectError(404, "Work order tidak ditemukan.", MockMvcRequestBuilders.multipart(wo("tidak-ada/photos"))
                .file(new MockMultipartFile("photo", "a.jpg", "image/jpeg", JPG)).param("type", "BEFORE"));
        Result photo = call(MockMvcRequestBuilders.multipart(wo(woId + "/photos"))
                .file(new MockMultipartFile("photo", "a.jpg", "image/jpeg", JPG)).param("type", "before").param("caption", "Bocor"));
        assertEquals(201, photo.status(), String.valueOf(photo.body()));
        assertEquals("BEFORE", photo.data().get("type").asText());
        assertEquals("Bocor", photo.data().get("caption").asText());
        assertTrue(photo.data().get("filePath").asText().startsWith("erp/ga/workorders/"));
        asUser(otherEmail, otherRole);
        expectError(403, "Hanya pelapor atau teknisi yang bisa upload foto.", MockMvcRequestBuilders.multipart(wo(woId + "/photos"))
                .file(new MockMultipartFile("photo", "a.jpg", "image/jpeg", JPG)).param("type", "AFTER"));
        asAdmin();
        JsonNode detail = ok(get(wo(woId)));
        assertEquals(1, detail.get("photos").size());
        assertEquals(0, detail.get("parts").size());

        // Jatuh tempo dalam 7 hari → filter dueSoon.
        assertEquals(1, ok(get(MT + "/work-orders?page=1&due=dueSoon&assetId=" + asset)).get("total").asInt());
        assertEquals(0, ok(get(MT + "/work-orders?page=1&due=overdue&assetId=" + asset)).get("total").asInt());

        // Cancel.
        expectError(400, "Alasan wajib diisi.", patch(wo(woId + "/cancel"), body("reason", "")));
        JsonNode c = ok(patch(wo(woId + "/cancel"), body("reason", "Duplikat")));
        assertEquals("CANCELLED", c.get("status").asText());
        assertEquals("Duplikat", jdbc.queryForObject("SELECT cancel_reason FROM ga_work_orders WHERE id = ?", String.class, woId));
        expectError(400, "Tidak bisa cancel dari status CANCELLED.", patch(wo(woId + "/cancel"), body("reason", "x")));
        expectError(400, "Tidak bisa assign dari status CANCELLED.", patch(wo(woId + "/assign"), body("assignedToId", "u")));
        expectError(400, "Tidak bisa menambah part pada WO berstatus CANCELLED.",
                post(wo(woId + "/parts"), body("inventoryItemId", "x", "quantity", 1, "unitCost", 0)));
        expectError(400, "Work order tidak ditemukan.", patch(wo("tidak-ada/assign"), body("assignedToId", "u")));
        expectError(404, "Work order tidak ditemukan.", get(wo("tidak-ada")));
        assertEquals(BigDecimal.ZERO.intValue(), jdbc.queryForObject(
                "SELECT COUNT(*) FROM ga_vehicle_service_logs WHERE work_order_id = ?", Integer.class, woId));
    }
}
