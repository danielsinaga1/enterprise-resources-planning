package id.neravoxa.erp.eam.ops;

import id.neravoxa.erp.core.ErpDates;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Notifikasi alur kerja EAM: WO ditugaskan/selesai, MR diajukan/disetujui/ditolak, serta pengingat harian WO
 * terlambat, kalibrasi, dokumen kedaluwarsa dan sewa berakhir (sekali per keadaan per tanggal jatuh tempo).
 */
class GaWorkflowNotificationTest extends GaOpsTestSupport {

    private static final String CHECK = "/api/erp/notifications/check";

    private List<Map<String, Object>> notices(String userId, String type, String key, String id) {
        return jdbc.queryForList("SELECT title, message, metadata->>'state' AS state FROM notifications "
                + "WHERE user_id = ? AND type = ? AND metadata->>'" + key + "' = ?", userId, type, id);
    }

    private static Map<String, Object> item(String name) {
        return body("itemName", name, "uom", "pcs", "quantity", 1);
    }

    /** Tanggal saja disimpan sebagai tengah malam UTC. */
    private static java.time.LocalDateTime day(int offset) {
        return ErpDates.businessToday().plusDays(offset).atStartOfDay();
    }

    @Test
    void work_order_assigned_completed_and_overdue() throws Exception {
        String admin = adminId();
        String techRole = customRole(Map.of("ga_maintenance", "edit"));
        String techEmail = email("teknisi");
        String tech = provision(techEmail, techRole);
        String viewer = provision(email("viewer"), customRole(Map.of("ga_maintenance", "view")));
        asAdmin();
        String t = tag();
        String asset = asset(t + "W", "MESIN_PRODUKSI");

        String woId = ok(post(MT + "/work-orders", body("assetId", asset, "type", "CORRECTIVE", "title", "Ganti bearing " + t,
                "priority", "HIGH", "dueAt", "2020-01-01"))).get("id").asText();
        String code = jdbc.queryForObject("SELECT code FROM ga_work_orders WHERE id = ?", String.class, woId);
        ok(patch(MT + "/work-orders/" + woId + "/assign", body("assignedToId", tech)));
        List<Map<String, Object>> assigned = notices(tech, "wo_assigned", "woId", woId);
        assertEquals(1, assigned.size());
        assertEquals("Work Order Ditugaskan", assigned.get(0).get("title"));
        String msg = String.valueOf(assigned.get(0).get("message"));
        assertTrue(msg.startsWith(code + " — Ganti bearing " + t + " (Aset " + t + "W, AST-" + t + "W) ditugaskan kepada Anda oleh "), msg);
        assertTrue(msg.endsWith("Prioritas tinggi, tenggat 1 Januari 2020."), msg);
        assertEquals(0, notices(admin, "wo_assigned", "woId", woId).size());

        // Pengingat harian: teknisi + pemegang izin edit, sekali per tenggat.
        ok(post(CHECK, Map.of()));
        List<Map<String, Object>> late = notices(tech, "wo_overdue", "woId", woId);
        assertEquals(1, late.size());
        assertTrue(String.valueOf(late.get(0).get("message")).contains("melewati tenggat 1 Januari 2020, dikerjakan "), String.valueOf(late.get(0).get("message")));
        assertEquals(1, notices(admin, "wo_overdue", "woId", woId).size());
        assertEquals(0, notices(viewer, "wo_overdue", "woId", woId).size());
        ok(post(CHECK, Map.of()));
        assertEquals(1, notices(tech, "wo_overdue", "woId", woId).size());

        asUser(techEmail, techRole);
        ok(patch(MT + "/work-orders/" + woId + "/start", Map.of()));
        ok(patch(MT + "/work-orders/" + woId + "/complete", body("resolution", "Bearing diganti")));
        List<Map<String, Object>> done = notices(admin, "wo_completed", "woId", woId);
        assertEquals(1, done.size());
        assertTrue(String.valueOf(done.get(0).get("message")).contains(" (melewati tenggat). Periksa hasilnya lalu tutup WO."),
                String.valueOf(done.get(0).get("message")));
        assertEquals(0, notices(tech, "wo_completed", "woId", woId).size());

        // WO selesai tidak lagi diingatkan.
        asAdmin();
        jdbc.update("DELETE FROM notifications WHERE type = 'wo_overdue' AND metadata->>'woId' = ?", woId);
        ok(post(CHECK, Map.of()));
        assertEquals(0, notices(admin, "wo_overdue", "woId", woId).size());

        // Menugaskan diri sendiri tidak memberi notifikasi.
        String own = ok(post(MT + "/work-orders", body("assetId", asset, "type", "CORRECTIVE", "title", "Cek " + t))).get("id").asText();
        ok(patch(MT + "/work-orders/" + own + "/assign", body("assignedToId", admin)));
        assertEquals(0, notices(admin, "wo_assigned", "woId", own).size());
    }

    @Test
    void material_request_submit_approve_reject() throws Exception {
        String admin = adminId();
        String reqRole = customRole(Map.of("ga_material_request", "edit"));
        String reqEmail = email("peminta");
        String requester = provision(reqEmail, reqRole);
        String t = tag();
        String asset = asset(t + "M", "MESIN_PRODUKSI");

        asUser(reqEmail, reqRole);
        String mr1 = ok(post(MR, body("assetId", asset, "items", List.of(item("Bearing " + t))))).get("id").asText();
        String mr2 = ok(post(MR, body("machineTarget", "Kompresor " + t, "items", List.of(item("Seal " + t))))).get("id").asText();
        ok(patch(MR + "/" + mr1 + "/submit", Map.of()));
        ok(patch(MR + "/" + mr2 + "/submit", Map.of()));
        List<Map<String, Object>> pending = notices(admin, "mr_pending", "materialRequestId", mr1);
        assertEquals(1, pending.size());
        assertTrue(String.valueOf(pending.get(0).get("message")).contains(" untuk Aset " + t + "M (AST-" + t + "M) dari "),
                String.valueOf(pending.get(0).get("message")));
        assertEquals(0, notices(requester, "mr_pending", "materialRequestId", mr1).size());
        assertTrue(String.valueOf(notices(admin, "mr_pending", "materialRequestId", mr2).get(0).get("message"))
                .contains(" untuk Kompresor " + t + " dari "));

        asAdmin();
        ok(patch(MR + "/" + mr1 + "/approve", Map.of()));
        ok(patch(MR + "/" + mr2 + "/reject", body("reason", "Stok masih ada")));
        assertEquals("Permintaan Material Disetujui", notices(requester, "mr_approved", "materialRequestId", mr1).get(0).get("title"));
        assertTrue(String.valueOf(notices(requester, "mr_rejected", "materialRequestId", mr2).get(0).get("message"))
                .endsWith(": Stok masih ada"));
        assertEquals(0, notices(admin, "mr_approved", "materialRequestId", mr1).size());
    }

    @Test
    void email_copies_are_off_by_default() throws Exception {
        adminId();
        asAdmin();
        com.fasterxml.jackson.databind.JsonNode prefs = ok(get("/api/erp/notifications/preferences"));
        assertTrue(!prefs.get("emailAvailable").asBoolean());
        assertTrue(prefs.get("emailEnabled").asBoolean());
        expectError(400, "Email notifikasi belum diaktifkan administrator (ERP_EMAIL_ENABLED, MAIL_HOST, ERP_EMAIL_FROM).",
                post("/api/erp/notifications/preferences/test-email", Map.of()));
    }

    @Test
    void calibration_document_and_rental_reminders_once_per_state() throws Exception {
        String admin = adminId();
        String docRole = customRole(Map.of("ga_documents", "edit"));
        String docUser = provision(email("dokumen"), docRole);
        String t = tag();

        String gauge = asset(t + "C", "MESIN_PRODUKSI");
        jdbc.update("INSERT INTO ga_equipment_data (asset_id, next_calibration_due) VALUES (?, ?)", gauge, day(-1));
        String scale = asset(t + "S", "MESIN_PRODUKSI");
        jdbc.update("INSERT INTO ga_equipment_data (asset_id, next_calibration_due) VALUES (?, ?)", scale, day(45));

        String type = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_document_types (id, code, name) VALUES (?, ?, ?)", type, "DT-" + t, "Izin " + t);
        String doc = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_documents (id, code, title, type_id, expiry_date) VALUES (?, ?, ?, ?, ?)",
                doc, "DOC-" + t, "SIUP " + t, type, day(10));
        String revoked = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_documents (id, code, title, type_id, expiry_date, status) VALUES (?, ?, ?, ?, ?, 'REVOKED')",
                revoked, "DOC-" + t + "R", "Lama " + t, type, day(-5));

        String building = asset(t + "B", "MESIN_PRODUKSI");
        String rental = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_building_rentals (id, asset_id, arah, pihak, nilai_sewa, periode, tanggal_mulai, tanggal_akhir) "
                + "VALUES (?, ?, 'SEWA_KELUAR', ?, 1000000, 'TAHUNAN', ?, ?)", rental, building, "PT Penyewa " + t, day(-300), day(40));

        asAdmin();
        ok(post(CHECK, Map.of()));
        List<Map<String, Object>> cal = notices(admin, "calibration_due", "assetId", gauge);
        assertEquals(1, cal.size());
        assertEquals("Kalibrasi Terlambat", cal.get(0).get("title"));
        assertEquals("overdue", cal.get(0).get("state"));
        assertEquals(0, notices(admin, "calibration_due", "assetId", scale).size());

        List<Map<String, Object>> docs = notices(docUser, "document_expiring", "documentId", doc);
        assertEquals(1, docs.size());
        assertEquals("Dokumen Segera Kedaluwarsa", docs.get(0).get("title"));
        assertTrue(String.valueOf(docs.get(0).get("message")).endsWith(" (10 hari lagi)."), String.valueOf(docs.get(0).get("message")));
        assertEquals(0, notices(admin, "document_expiring", "documentId", revoked).size());
        assertEquals(0, notices(docUser, "calibration_due", "assetId", gauge).size());

        List<Map<String, Object>> rent = notices(admin, "rental_expiring", "rentalId", rental);
        assertEquals(1, rent.size());
        assertTrue(String.valueOf(rent.get(0).get("message")).contains("dengan PT Penyewa " + t + " berakhir "));

        // Pemeriksaan berikutnya tidak mengulang; tanggal baru / keadaan baru memicu pengingat baru.
        ok(post(CHECK, Map.of()));
        assertEquals(1, notices(docUser, "document_expiring", "documentId", doc).size());
        assertEquals(1, notices(admin, "calibration_due", "assetId", gauge).size());
        jdbc.update("UPDATE ga_documents SET expiry_date = ? WHERE id = ?", day(-2), doc);
        ok(post(CHECK, Map.of()));
        List<Map<String, Object>> expired = notices(docUser, "document_expiring", "documentId", doc);
        assertEquals(2, expired.size());
        assertTrue(expired.stream().anyMatch(n -> "Dokumen Kedaluwarsa".equals(n.get("title"))));
    }
}
