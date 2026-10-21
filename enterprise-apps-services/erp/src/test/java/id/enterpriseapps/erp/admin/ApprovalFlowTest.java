package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aturan approval, antrian → keputusan lewat registry handler, notifikasi pengaju, penjaga keputusan ganda,
 * penolakan izin, dan pengajuan ulang.
 */
class ApprovalFlowTest extends AdminTestSupport {

    private static final String TYPE = ErpAdminTestApprovalHandler.TYPE;

    @Autowired
    ErpApprovalDecisionService decisions;

    @Test
    void rules_crud_check_and_legacy_request() throws Exception {
        String adminId = loginAdmin();
        String u = uid();
        String type = "AdmRule" + u;

        JsonNode rule = ok(post("/api/erp/approvals/rules", map("documentType", type, "minAmount", 1000000, "requiredRole", "Admin")));
        assertEquals(type, rule.get("documentType").asText());
        assertAmount("1000000", rule.get("minAmount").asText());
        assertTrue(rule.get("isActive").asBoolean());
        String ruleId = rule.get("id").asText();
        Result created = call(post("/api/erp/approvals/rules", map("documentType", type + "x", "minAmount", 5, "requiredRole", "Admin")));
        assertEquals(201, created.status());

        JsonNode rules = ok(get("/api/erp/approvals/rules"));
        assertTrue(contains(rules, ruleId));

        JsonNode check = ok(get("/api/erp/approvals/check?documentType=" + type + "&amount=1500000"));
        assertTrue(check.get("required").asBoolean());
        assertEquals(ruleId, check.get("rule").get("id").asText());
        JsonNode below = ok(get("/api/erp/approvals/check?documentType=" + type + "&amount=999"));
        assertFalse(below.get("required").asBoolean());
        assertTrue(below.get("rule").isNull());
        assertEquals("Parameter documentType dan amount (angka) wajib diisi.",
                call(get("/api/erp/approvals/check?documentType=" + type + "&amount=abc")).error());
        assertEquals(400, call(get("/api/erp/approvals/check?amount=5").param("documentType", " ")).status());

        JsonNode updated = ok(put("/api/erp/approvals/rules/" + ruleId, map("minAmount", "2000000", "isActive", false)));
        assertAmount("2000000", updated.get("minAmount").asText());
        assertFalse(updated.get("isActive").asBoolean());
        assertEquals(type, updated.get("documentType").asText());
        assertFalse(ok(get("/api/erp/approvals/check?documentType=" + type + "&amount=3000000")).get("required").asBoolean());
        Result noAmount = call(put("/api/erp/approvals/rules/" + ruleId, map("isActive", true)));
        assertEquals(500, noAmount.status());
        assertEquals("Gagal memperbarui aturan.", noAmount.error());
        Result noRole = call(post("/api/erp/approvals/rules", map("documentType", type, "minAmount", 1)));
        assertEquals(500, noRole.status());
        assertEquals("Gagal membuat aturan approval.", noRole.error());

        // Pengajuan generik berbasis aturan (tanpa payload).
        JsonNode notNeeded = ok(post("/api/erp/approvals/requests", map("documentType", type, "documentId", "d-" + u,
                "documentNumber", "DOC-" + u, "amount", 5)));
        assertFalse(notNeeded.get("required").asBoolean());
        assertEquals("Tidak perlu approval.", notNeeded.get("message").asText());
        String type2 = "AdmRuleB" + u;
        ok(post("/api/erp/approvals/rules", map("documentType", type2, "minAmount", 0, "requiredRole", "Admin")));
        Result legacy = call(post("/api/erp/approvals/requests", map("documentType", type2, "documentId", "d-" + u,
                "documentNumber", "DOC-" + u, "amount", "250000")));
        assertEquals(201, legacy.status(), String.valueOf(legacy.body()));
        JsonNode legacyReq = legacy.data().get("request");
        assertTrue(legacy.data().get("required").asBoolean());
        assertEquals("Pending", legacyReq.get("status").asText());
        assertEquals(adminId, legacyReq.get("requestedBy").asText());
        assertAmount("250000", legacyReq.get("amount").asText());
        assertFalse(legacyReq.has("requester"));
        assertEquals("Sudah ada request approval yang pending.", call(post("/api/erp/approvals/requests",
                map("documentType", type2, "documentId", "d-" + u, "amount", 1))).error());
        // Tanpa payload & tanpa handler: cukup ditandai Approved.
        JsonNode decided = ok(post("/api/erp/approvals/requests/" + legacyReq.get("id").asText() + "/approve", map("notes", "")));
        assertEquals("Approved", decided.get("status").asText());
        assertFalse(decided.has("docNumber"));
        assertEquals("Pengajuan Anda telah disetujui.", notificationsOf(adminId, "approval_approved",
                legacyReq.get("id").asText()).get(0).get("message"));

        assertEquals(Map.of("success", true), json.convertValue(ok(delete("/api/erp/approvals/rules/" + ruleId)), Map.class));
        Result again = call(delete("/api/erp/approvals/rules/" + ruleId));
        assertEquals(500, again.status());
        assertEquals("Gagal menghapus aturan.", again.error());

        login("acc." + u + "@test.local", "ERP_ACCOUNTANT");
        Result denied = call(get("/api/erp/approvals/rules"));
        assertEquals(403, denied.status());
        assertEquals(DENIED, denied.error());
    }

    @Test
    void approve_executes_handler_notifies_requester_and_blocks_second_decision() throws Exception {
        String adminId = loginAdmin();
        String u = uid();
        String requesterEmail = "req." + u + "@test.local";
        String requesterId = login(requesterEmail, "ERP_ACCOUNTANT");
        String id = queue(TYPE, map("label", "A" + u), "125000", requesterId);

        // Pengaju tanpa approval.view_all hanya melihat miliknya sendiri.
        JsonNode mine = ok(get("/api/erp/approvals/requests?status=Pending"));
        assertTrue(contains(mine, id));
        for (JsonNode r : mine) {
            assertEquals(requesterId, r.get("requestedBy").asText());
        }
        JsonNode detail = ok(get("/api/erp/approvals/requests/" + id));
        assertEquals("ERP_ACCOUNTANT", detail.get("requester").get("role").asText());
        assertNotNull(detail.get("requester").get("fullName").asText());
        assertTrue(detail.get("approver").isNull());
        assertTrue(detail.get("comparison").isNull());
        assertEquals("A" + u, detail.get("payload").get("label").asText());
        Result notDecider = call(post("/api/erp/approvals/requests/" + id + "/approve", map("notes", "")));
        assertEquals(403, notDecider.status());
        assertEquals(DENIED, notDecider.error());

        login("other." + u + "@test.local", "ERP_ACCOUNTANT");
        Result hidden = call(get("/api/erp/approvals/requests/" + id));
        assertEquals(404, hidden.status());
        assertEquals("Pengajuan tidak ditemukan.", hidden.error());
        assertFalse(contains(ok(get("/api/erp/approvals/requests")), id));

        loginAdmin();
        assertTrue(contains(ok(get("/api/erp/approvals/requests?status=Pending")), id));
        JsonNode res = ok(post("/api/erp/approvals/requests/" + id + "/approve", map("notes", "  disetujui  ")));
        assertTrue(res.get("ok").asBoolean());
        assertEquals("Approved", res.get("status").asText());
        assertEquals("ATD-A" + u, res.get("docNumber").asText());
        assertEquals(TYPE, res.get("documentType").asText());

        Map<String, Object> row = requestRow(id);
        assertEquals("Approved", row.get("status"));
        assertEquals(adminId, row.get("approved_by"));
        assertEquals("disetujui", row.get("notes"));
        assertEquals("ATD-A" + u, row.get("document_number"));
        assertNotNull(row.get("document_id"));
        assertNotNull(row.get("decided_at"));
        assertEquals(1, notificationsOf(requesterId, "admin_test_effect", id).size());
        List<Map<String, Object>> notes = notificationsOf(requesterId, "approval_approved", id);
        assertEquals(1, notes.size());
        assertEquals("Pengajuan Anda disetujui", notes.get(0).get("title"));
        assertEquals("Pengajuan disetujui. Dokumen ATD-A" + u + " telah dibuat.", notes.get(0).get("message"));
        JsonNode meta = json.readTree((String) notes.get(0).get("metadata"));
        assertEquals("approval", meta.get("kind").asText());
        assertEquals(row.get("document_id"), meta.get("documentId").asText());

        // Keputusan kedua ditolak; efek dokumen tidak berjalan lagi.
        Result second = call(post("/api/erp/approvals/requests/" + id + "/approve", map("notes", "")));
        assertEquals(400, second.status());
        assertEquals("Pengajuan sudah diproses.", second.error());
        assertEquals("Pengajuan sudah diproses.", call(post("/api/erp/approvals/requests/" + id + "/reject",
                map("notes", "x"))).error());
        assertEquals(1, ErpAdminTestApprovalHandler.APPROVED.get(id).get());
        assertEquals("Pengajuan tidak ditemukan.", call(post("/api/erp/approvals/requests/" + UUID.randomUUID() + "/approve",
                map())).error());

        JsonNode after = ok(get("/api/erp/approvals/requests/" + id));
        assertNotNull(after.get("approver").get("fullName").asText());
        assertEquals(1, after.get("approver").size());
    }

    @Test
    void concurrent_decisions_run_the_handler_once() throws Exception {
        String adminId = loginAdmin();
        String requesterId = login("race." + uid() + "@test.local", "ERP_ACCOUNTANT");
        String id = queue(TYPE, map("label", "R", "sleepMs", 700), "1000", requesterId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                Callable<String> task = () -> {
                    start.await();
                    try {
                        return (String) decisions.decide(id, ErpApprovalDecisionService.APPROVE, adminId, "").get("status");
                    } catch (ErpException e) {
                        return "error:" + e.getMessage();
                    }
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : results) {
                outcomes.add(f.get(60, TimeUnit.SECONDS));
            }
            assertTrue(outcomes.contains("Approved"), outcomes.toString());
            assertTrue(outcomes.contains("error:Pengajuan sudah diproses."), outcomes.toString());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, ErpAdminTestApprovalHandler.APPROVED.get(id).get());
        assertEquals(1, notificationsOf(requesterId, "admin_test_effect", id).size());
        assertEquals(1, notificationsOf(requesterId, "approval_approved", id).size());
    }

    @Test
    void reject_and_request_revision_paths() throws Exception {
        String adminId = loginAdmin();
        String u = uid();
        String requesterId = login("rev." + u + "@test.local", "ERP_ACCOUNTANT");
        String revId = queue(TYPE, map("label", "V"), "5000", requesterId);
        String rejId = queue(TYPE, map("label", "J"), "6000", requesterId);
        String rejNoNote = queue(TYPE, map("label", "K"), "7000", requesterId);

        loginAdmin();
        Result missingNote = call(post("/api/erp/approvals/requests/" + revId + "/request-revision", map("notes", "  ")));
        assertEquals(400, missingNote.status());
        assertEquals("Alasan revisi wajib diisi.", missingNote.error());
        assertEquals("Pending", requestRow(revId).get("status"));

        JsonNode rev = ok(post("/api/erp/approvals/requests/" + revId + "/request-revision", map("notes", "Lengkapi nota")));
        assertEquals("NeedsRevision", rev.get("status").asText());
        Map<String, Object> revRow = requestRow(revId);
        assertEquals("NeedsRevision", revRow.get("status"));
        assertEquals(adminId, revRow.get("rejected_by"));
        assertEquals("Lengkapi nota", revRow.get("notes"));
        assertEquals("Pengajuan perlu revisi: Lengkapi nota",
                notificationsOf(requesterId, "approval_revision", revId).get(0).get("message"));
        assertTrue(ErpAdminTestApprovalHandler.DECLINED.contains(revId + ":revise:Lengkapi nota"));

        JsonNode rej = ok(post("/api/erp/approvals/requests/" + rejId + "/reject", map("notes", "Tidak sesuai")));
        assertEquals("Rejected", rej.get("status").asText());
        assertEquals(TYPE, rej.get("documentType").asText());
        assertEquals("Rejected", requestRow(rejId).get("status"));
        assertEquals("Pengajuan ditolak: Tidak sesuai", notificationsOf(requesterId, "approval_rejected", rejId).get(0).get("message"));
        assertTrue(ErpAdminTestApprovalHandler.DECLINED.contains(rejId + ":reject:Tidak sesuai"));
        ok(post("/api/erp/approvals/requests/" + rejNoNote + "/reject", null));
        assertEquals("Pengajuan ditolak: -", notificationsOf(requesterId, "approval_rejected", rejNoNote).get(0).get("message"));
        assertNull(requestRow(rejNoNote).get("notes"));
        assertNull(ErpAdminTestApprovalHandler.APPROVED.get(rejId));

        // Dokumen sumber dikembalikan ke pengaju (draf produksi): revisi pun dicatat Rejected.
        String srcRev = queue(TYPE, map("label", "S", "returnSource", true), "1000", requesterId);
        String srcRej = queue(TYPE, map("label", "T", "returnSource", true), "1000", requesterId);
        JsonNode returned = ok(post("/api/erp/approvals/requests/" + srcRev + "/request-revision", map("notes", "Ubah tanggal")));
        assertEquals("Rejected", returned.get("status").asText());
        assertEquals("Rejected", requestRow(srcRev).get("status"));
        assertEquals("Pengajuan perlu revisi: Ubah tanggal. Draft dikembalikan. Perbaiki lalu posting ulang.",
                notificationsOf(requesterId, "approval_rejected", srcRev).get(0).get("message"));
        assertEquals(0, notificationsOf(requesterId, "approval_revision", srcRev).size());
        ok(post("/api/erp/approvals/requests/" + srcRej + "/reject", map("notes", "Salah gudang")));
        assertEquals("Pengajuan ditolak: Salah gudang. Draft dikembalikan. Perbaiki lalu posting ulang.",
                notificationsOf(requesterId, "approval_rejected", srcRej).get(0).get("message"));
        // Revisi tanpa catatan ditolak SEBELUM handler dipanggil (draf tidak ikut dikembalikan).
        String srcNoNote = queue(TYPE, map("label", "N", "returnSource", true), "1000", requesterId);
        assertEquals("Alasan revisi wajib diisi.", call(post("/api/erp/approvals/requests/" + srcNoNote + "/request-revision",
                map())).error());
        assertFalse(ErpAdminTestApprovalHandler.DECLINED.stream().anyMatch(d -> d.startsWith(srcNoNote)));
    }

    @Test
    void failing_handler_rolls_back_and_unknown_type_is_rejected() throws Exception {
        String adminId = loginAdmin();
        String requesterId = login("fail." + uid() + "@test.local", "ERP_ACCOUNTANT");
        String id = queue(TYPE, map("label", "F", "fail", true), "1000", requesterId);
        String unknown = queue("AdminUnknownDoc", map("x", 1), "1000", requesterId);

        loginAdmin();
        Result failed = call(post("/api/erp/approvals/requests/" + id + "/approve", map("notes", "")));
        assertEquals(400, failed.status());
        assertEquals("Gagal uji eksekusi.", failed.error());
        Map<String, Object> row = requestRow(id);
        assertEquals("Pending", row.get("status"));
        assertNull(row.get("approved_by"));
        assertEquals(0, notificationsOf(requesterId, "admin_test_effect", id).size());
        assertEquals(0, notificationsOf(requesterId, "approval_approved", id).size());

        Result noHandler = call(post("/api/erp/approvals/requests/" + unknown + "/approve", map()));
        assertEquals(400, noHandler.status());
        assertEquals("Tipe dokumen tidak didukung untuk eksekusi: AdminUnknownDoc", noHandler.error());
        assertEquals("Pending", requestRow(unknown).get("status"));
        // Menolak tetap bisa walau tidak ada handler.
        assertEquals("Rejected", ok(post("/api/erp/approvals/requests/" + unknown + "/reject", map("notes", "salah")))
                .get("status").asText());
        assertEquals(adminId, requestRow(unknown).get("rejected_by"));
    }

    @Test
    void custom_role_with_decide_capability_can_approve() throws Exception {
        loginAdmin();
        String u = uid();
        String requesterId = login("cr." + u + "@test.local", "ERP_ACCOUNTANT");
        String id = queue(TYPE, map("label", "C" + u), "1000", requesterId);
        String key = "ADM_APPROVER_" + u.toUpperCase();
        customRole(key, List.of("approvals"), "approval.decide", "approval.view_all");

        String approverId = login("appr." + u + "@test.local", key);
        assertTrue(contains(ok(get("/api/erp/approvals/requests")), id));
        assertEquals("ATD-C" + u, ok(post("/api/erp/approvals/requests/" + id + "/approve", map("notes", "")))
                .get("docNumber").asText());
        assertEquals(approverId, requestRow(id).get("approved_by"));

        login("viewer." + u + "@test.local", "ERP_VIEWER");
        assertEquals(403, call(get("/api/erp/approvals/requests")).status());
    }

    @Test
    void resubmit_validates_through_handler() throws Exception {
        loginAdmin();
        String u = uid();
        String requesterEmail = "resub." + u + "@test.local";
        String requesterId = login(requesterEmail, "ERP_ACCOUNTANT");
        String id = queue(TYPE, map("label", "R1", "amount", "100"), "100", requesterId);
        String noHandler = queue("AdminUnknownDoc", map("x", 1), "100", requesterId);

        loginAdmin();
        ok(post("/api/erp/approvals/requests/" + id + "/request-revision", map("notes", "Cek kuantitas")));
        ok(post("/api/erp/approvals/requests/" + noHandler + "/request-revision", map("notes", "Cek")));
        assertEquals("NeedsRevision", requestRow(id).get("status"));

        login("someone." + u + "@test.local", "ERP_ACCOUNTANT");
        Result notMine = call(put("/api/erp/approvals/requests/" + id + "/resubmit", map("payload", map("label", "X"))));
        assertEquals(403, notMine.status());
        assertEquals("Bukan pengajuan Anda.", notMine.error());

        asUser(requesterEmail, "ERP_ACCOUNTANT");
        assertEquals("Payload tidak valid.", call(put("/api/erp/approvals/requests/" + id + "/resubmit", map("payload", "x"))).error());
        assertEquals("Payload tidak valid.", call(put("/api/erp/approvals/requests/" + id + "/resubmit", map())).error());
        assertEquals("Data uji tidak valid: label wajib diisi.", call(put("/api/erp/approvals/requests/" + id + "/resubmit",
                map("payload", map("label", " ")))).error());
        assertEquals("Tipe dokumen tidak didukung.", call(put("/api/erp/approvals/requests/" + noHandler + "/resubmit",
                map("payload", map("x", 2)))).error());
        Result missing = call(put("/api/erp/approvals/requests/" + UUID.randomUUID() + "/resubmit", map("payload", map())));
        assertEquals(404, missing.status());
        assertEquals("Request tidak ditemukan.", missing.error());
        assertEquals("NeedsRevision", requestRow(id).get("status"));

        JsonNode res = ok(put("/api/erp/approvals/requests/" + id + "/resubmit", map("payload", map("label", "R2", "amount", "105.555"))));
        assertEquals("Pending", res.get("status").asText());
        assertAmount("105.56", res.get("amount").asText());
        assertTrue(res.get("rejectedBy").isNull());
        assertTrue(res.get("decidedAt").isNull());
        assertEquals("Cek kuantitas", res.get("notes").asText());
        assertEquals("R2", res.get("payload").get("label").asText());
        assertFalse(res.has("requester"));
        assertEquals("Hanya pengajuan berstatus Perlu Revisi yang dapat diajukan ulang.", call(put("/api/erp/approvals/requests/"
                + id + "/resubmit", map("payload", map("label", "R3")))).error());

        // Diajukan ulang → bisa diputuskan lagi dengan payload baru.
        loginAdmin();
        assertEquals("ATD-R2", ok(post("/api/erp/approvals/requests/" + id + "/approve", map())).get("docNumber").asText());
    }

    static boolean contains(JsonNode rows, String id) {
        for (JsonNode r : rows) {
            if (id.equals(r.path("id").asText())) {
                return true;
            }
        }
        return false;
    }
}
