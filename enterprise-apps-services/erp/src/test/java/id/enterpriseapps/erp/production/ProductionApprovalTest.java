package id.enterpriseapps.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.approval.ErpApprovalGateService;
import id.enterpriseapps.erp.core.approval.ErpApprovalHandler;
import id.enterpriseapps.erp.core.approval.ErpApprovalHandlerRegistry;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import id.enterpriseapps.erp.repository.ApprovalRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gerbang approval Data Produksi: role non-bypass mengantrekan posting draft (202) dan edit run (202,
 * snapshot {@code _before}); handler ProductionRun / ProductionRunEdit mengeksekusi atas nama pengaju
 * saat disetujui, dan penolakan mengembalikan draft ke Draft.
 */
class ProductionApprovalTest extends ProductionTestSupport {

    @Autowired
    ErpApprovalHandlerRegistry handlers;
    @Autowired
    ApprovalRequestRepository requests;
    @Autowired
    @Qualifier(ErpTx.MANAGER)
    PlatformTransactionManager tm;

    private ErpApprovalHandler.ApprovedDocument approve(String requestId, String approverId) {
        return new TransactionTemplate(tm).execute(st -> {
            ApprovalRequest r = requests.findById(requestId).orElseThrow();
            ErpApprovalHandler h = handlers.find(r.getDocumentType()).orElseThrow();
            JsonNode[] split = ErpApprovalGateService.splitStoredPayload(r.getPayload());
            return h.approve(r, split[0], split[1], approverId, "");
        });
    }

    private void decline(String requestId, String action, String approverId, String note) {
        new TransactionTemplate(tm).executeWithoutResult(st -> {
            ApprovalRequest r = requests.findById(requestId).orElseThrow();
            handlers.find(r.getDocumentType()).orElseThrow().onDecline(r, action, approverId, note);
        });
    }

    private Map<String, Object> draftRow(String draftId) {
        return jdbc.queryForMap("SELECT status, last_error, approval_request_id, posted_run_id, posted_by_id "
                + "FROM production_run_drafts WHERE id = ?", draftId);
    }

    @Test
    void draft_post_by_non_bypass_role_is_queued_then_executed_or_returned() throws Exception {
        asAdmin();
        Scenario s = scenario();
        String adminId = userId(ADMIN_EMAIL);
        String staffEmail = "prod.staff." + uid().toLowerCase() + "@test.local";

        asUser(staffEmail, "ERP_PRODUCTION");
        String staffId = null;
        JsonNode draft = createDraft(standardBody(s)).get("draft");
        String draftId = draft.get("id").asText();
        Result queued = call(post(DRAFTS + "/" + draftId + "/post", map()));
        assertEquals(202, queued.status(), String.valueOf(queued.body()));
        assertTrue(queued.data().get("pendingApproval").asBoolean());
        assertEquals("Data produksi menunggu persetujuan manajer sebelum diproses.", queued.data().get("message").asText());
        JsonNode req = queued.data().get("request");
        assertEquals("ProductionRun", req.get("documentType").asText());
        assertEquals("Data Produksi " + draft.get("draftNumber").asText(), req.get("documentNumber").asText());
        assertEquals("Pending", req.get("status").asText());
        assertNum("1000", req.get("amount"));
        staffId = userId(staffEmail);
        assertEquals(staffId, req.get("requestedBy").asText());
        Map<String, Object> pending = draftRow(draftId);
        assertEquals("PendingApproval", pending.get("status"));
        assertEquals(req.get("id").asText(), pending.get("approval_request_id"));
        // Belum ada efek stok/GL.
        assertNum("1000", stock(s.gkp()));
        assertNum("0", stock(s.beras()));

        ErpApprovalHandler.ApprovedDocument doc = approve(req.get("id").asText(), adminId);
        assertTrue(doc.number().startsWith("PR-"));
        assertEquals(staffId, jdbc.queryForObject("SELECT created_by_id FROM production_runs WHERE id = ?", String.class, doc.id()));
        assertNum("0", stock(s.gkp()));
        assertNum("600", stock(s.beras()));
        BigDecimal[] t = jeTotals("JV-" + doc.number());
        assertNum("5130000.02", t[0]);
        assertNum("5130000.02", t[1]);
        assertEquals(staffId, jdbc.queryForObject("SELECT created_by FROM journal_entries WHERE entry_number = ?",
                String.class, "JV-" + doc.number()));
        Map<String, Object> posted = draftRow(draftId);
        assertEquals("Posted", posted.get("status"));
        assertEquals(doc.id(), posted.get("posted_run_id"));
        assertEquals(adminId, posted.get("posted_by_id"));

        // Ditolak → draft kembali ke Draft dengan catatan, antrian dilepas.
        asUser(staffEmail, "ERP_PRODUCTION");
        String d2 = createDraft(standardBody(s)).get("draft").get("id").asText();
        String r2 = call(post(DRAFTS + "/" + d2 + "/post", map())).data().get("request").get("id").asText();
        decline(r2, "reject", adminId, "Data salah");
        Map<String, Object> rejected = draftRow(d2);
        assertEquals("Draft", rejected.get("status"));
        assertEquals("Ditolak: Data salah", rejected.get("last_error"));
        assertNull(rejected.get("approval_request_id"));

        // Minta revisi wajib alasan; dengan alasan → draft kembali dengan label "Perlu revisi".
        String r3 = call(post(DRAFTS + "/" + d2 + "/post", map())).data().get("request").get("id").asText();
        ErpException noNote = assertThrows(ErpException.class, () -> decline(r3, "revise", adminId, " "));
        assertEquals("Alasan revisi wajib diisi.", noNote.getMessage());
        assertEquals("PendingApproval", draftRow(d2).get("status"));
        decline(r3, "revise", adminId, "Perbaiki qty");
        assertEquals("Perlu revisi: Perbaiki qty", draftRow(d2).get("last_error"));
        assertEquals("Draft", draftRow(d2).get("status"));

        // Approve gagal (stok bahan sudah habis) → transaksi batal, draft tetap PendingApproval.
        String r4 = call(post(DRAFTS + "/" + d2 + "/post", map())).data().get("request").get("id").asText();
        ErpException noStock = assertThrows(ErpException.class, () -> approve(r4, adminId));
        assertTrue(noStock.getMessage().startsWith("Stok '" + itemName(s.gkp()) + "' tidak cukup."), noStock.getMessage());
        assertEquals("PendingApproval", draftRow(d2).get("status"));
    }

    @Test
    void edit_by_non_bypass_role_is_queued_with_before_snapshot_and_applied_on_approve() throws Exception {
        asAdmin();
        Scenario s = scenario();
        String adminId = userId(ADMIN_EMAIL);
        JsonNode run = submitRun(standardBody(s));
        String runId = run.get("id").asText();
        String runNumber = run.get("runNumber").asText();
        String jv = "JV-" + runNumber;

        String staffEmail = "prod.editor." + uid().toLowerCase() + "@test.local";
        asUser(staffEmail, "ERP_PRODUCTION");
        Result queued = call(put(RUNS + "/" + runId, editedBody(s)));
        assertEquals(202, queued.status(), String.valueOf(queued.body()));
        assertTrue(queued.data().get("pendingApproval").asBoolean());
        assertEquals("Perubahan data produksi menunggu persetujuan manajer sebelum diproses.", queued.data().get("message").asText());
        JsonNode req = queued.data().get("request");
        assertEquals("ProductionRunEdit", req.get("documentType").asText());
        assertEquals(runId, req.get("documentId").asText());
        assertEquals(runNumber, req.get("documentNumber").asText());
        assertNum("1000", req.get("amount"));
        JsonNode before = req.get("payload").get("_before");
        assertEquals("ProductionRunEdit", before.get("kind").asText());
        assertEquals(runNumber, before.get("runNumber").asText());
        assertEquals(today(), before.get("date").asText());
        assertNum("1000", before.get("inputs").get(0).get("quantity"));
        assertEquals(2, before.get("outputs").size());
        assertNum("8216.6667", before.get("computedHpp"));
        assertEquals(itemName(s.beras()), before.get("itemNames").get(s.beras()).asText());
        assertNum("650", req.get("payload").get("outputs").get(0).get("quantity"));
        // Belum diterapkan.
        assertEquals("Submitted", jeStatus(jv));
        assertNum("600", stock(s.beras()));

        Result dup = call(put(RUNS + "/" + runId, editedBody(s)));
        assertEquals(409, dup.status());
        assertEquals(ErpApprovalGateService.PENDING_EXISTS_MESSAGE, dup.error());
        Result missing = call(put(RUNS + "/00000000-0000-4000-8000-000000000000", editedBody(s)));
        assertEquals(404, missing.status());
        assertEquals("Data proses produksi tidak ditemukan.", missing.error());

        ErpApprovalHandler.ApprovedDocument doc = approve(req.get("id").asText(), adminId);
        assertEquals(runId, doc.id());
        assertEquals(runNumber, doc.number());
        assertEquals("Cancelled", jeStatus(jv));
        assertEquals("Submitted", jeStatus(jv + "-R1"));
        assertEquals(userId(staffEmail), jdbc.queryForObject("SELECT created_by FROM journal_entries WHERE entry_number = ?",
                String.class, jv + "-R1"));
        assertNum("650", stock(s.beras()));
        assertNum("150", stock(s.sekam()));
        assertNum("7461.54", avg(s.beras()));
        assertBalanceMatchesLedger("1.4.0");
        assertBalanceMatchesLedger("5.1");

        // Edit pada run yang dibatalkan ditolak saat diantrekan (400).
        asAdmin();
        ok(put(RUNS + "/" + runId + "/cancel", map()));
        asUser(staffEmail, "ERP_PRODUCTION");
        Result cancelled = call(put(RUNS + "/" + runId, editedBody(s)));
        assertEquals(400, cancelled.status());
        assertEquals("Proses produksi yang sudah dibatalkan tidak bisa diedit.", cancelled.error());
    }
}
