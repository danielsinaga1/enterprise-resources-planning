package id.enterpriseapps.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpJobLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** /api/erp/recurring (CRUD, eksekusi, run-due, runner terjadwal) dan /api/erp/cogs-backfill. */
class RecurringAndCogsTest extends AccountingTestSupport {

    @Autowired
    ErpRecurringService recurring;
    @Autowired
    ErpJobLock jobLock;

    private Map<String, Object> template(String name, String nextRunDate, String debitAccount, String creditAccount) {
        Map<String, Object> first = line(debitAccount, "500", 0);
        first.put("unknownKey", "dibuang");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("narration", "Sewa bulanan " + name);
        data.put("items", List.of(first, line(creditAccount, 0, 500)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("templateType", "journal");
        body.put("frequency", "monthly");
        body.put("dayOfMonth", 31);
        body.put("nextRunDate", nextRunDate);
        body.put("templateData", data);
        return body;
    }

    @Test
    void template_crud_and_manual_execute_posts_journal() throws Exception {
        asAdmin();
        String expense = newAccount("6", "EXPENSE").get("id").asText();
        String cash = accountId("1.1.1");
        String name = "Sewa " + uniq();

        Result created = call(post("/api/erp/recurring", template(name, "2099-01-31", expense, cash)));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        JsonNode t = created.data();
        String id = t.get("id").asText();
        assertEquals(31, t.get("dayOfMonth").asInt());
        assertTrue(t.get("isActive").asBoolean());
        JsonNode item = t.get("templateData").get("items").get(0);
        assertEquals(500, item.get("debit").asInt());
        assertTrue(item.get("debit").isNumber());
        assertFalse(item.has("unknownKey"));

        JsonNode list = ok(get("/api/erp/recurring")).get("data");
        JsonNode mine = null;
        for (JsonNode n : list) {
            if (id.equals(n.get("id").asText())) {
                mine = n;
            }
        }
        assertNotNull(mine);
        assertTrue(mine.get("user").has("fullName"));

        // Update parsial: dayOfMonth null eksplisit menghapus, field absen tidak berubah.
        Map<String, Object> upd = new HashMap<>();
        upd.put("dayOfMonth", null);
        JsonNode updated = ok(put("/api/erp/recurring/" + id, upd));
        assertTrue(updated.get("dayOfMonth").isNull());
        assertEquals("monthly", updated.get("frequency").asText());
        assertEquals(name, ok(put("/api/erp/recurring/" + id, Map.of("isActive", true))).get("name").asText());

        // Eksekusi manual → jurnal JV seimbang, saldo beban +500, jadwal maju.
        JsonNode exec = ok(post("/api/erp/recurring/" + id + "/execute", Map.of()));
        assertTrue(exec.get("success").asBoolean(), String.valueOf(exec));
        JsonNode je = exec.get("result");
        assertTrue(je.get("entryNumber").asText().startsWith("JV-"));
        assertEquals(2, je.get("items").size());
        assertEquals("Sewa bulanan " + name, je.get("narration").asText());
        assertEquals(0, ledgerImbalance(je.get("id").asText()).signum());
        assertEquals(0, new BigDecimal("500").compareTo(balance(expense)));
        Map<String, Object> row = jdbc.queryForMap("SELECT last_run_date, next_run_date FROM recurring_templates WHERE id = ?", id);
        assertNotNull(row.get("last_run_date"));
        assertTrue(((java.sql.Timestamp) row.get("next_run_date")).toInstant().isAfter(Instant.now()));

        assertEquals("Template berulang dinonaktifkan.", ok(delete("/api/erp/recurring/" + id)).get("message").asText());
        assertEquals("Data terkait tidak ditemukan. Periksa referensi yang dipilih.",
                call(delete("/api/erp/recurring/nope-" + uniq())).error());
        Result missing = call(post("/api/erp/recurring/nope-" + uniq() + "/execute", Map.of()));
        assertEquals(404, missing.status());
        assertEquals("Template tidak ditemukan.", missing.error());
    }

    @Test
    void validation_messages_follow_zod() throws Exception {
        asAdmin();
        String cash = accountId("1.1.1");
        Map<String, Object> body = template("V " + uniq(), "2099-01-01", cash, cash);
        body.put("dayOfMonth", 40);
        assertEquals("Too big: expected number to be <=31", call(post("/api/erp/recurring", body)).error());
        body.put("dayOfMonth", 5);
        body.put("templateData", Map.of());
        assertEquals("Invalid input", call(post("/api/erp/recurring", body)).error());
        body.put("frequency", "hourly");
        assertEquals("Invalid option: expected one of \"daily\"|\"weekly\"|\"monthly\"|\"quarterly\"|\"yearly\"",
                call(post("/api/erp/recurring", body)).error());
    }

    @Test
    void run_due_and_scheduled_runner() throws Exception {
        String userId = adminUserId();
        String expense = newAccount("6", "EXPENSE").get("id").asText();
        String cash = accountId("1.1.1");

        // Template faktur (bukan journal) → gagal dengan pesan, tidak dijadwalkan ulang.
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("name", "Faktur " + uniq());
        inv.put("templateType", "sales_invoice");
        inv.put("frequency", "weekly");
        inv.put("nextRunDate", "2020-01-01");
        inv.put("templateData", Map.of("items", List.of(Map.of("itemName", "Jasa", "quantity", 1, "rate", 1000))));
        String invId = ok(post("/api/erp/recurring", inv)).get("id").asText();
        JsonNode invExec = ok(post("/api/erp/recurring/" + invId + "/execute", Map.of()));
        assertFalse(invExec.get("success").asBoolean());
        assertEquals("Eksekusi otomatis untuk tipe sales_invoice belum didukung. Gunakan tipe journal.",
                invExec.get("error").asText());

        String dueId = ok(post("/api/erp/recurring", template("Due " + uniq(), "2020-01-31", expense, cash)))
                .get("id").asText();
        JsonNode run = ok(post("/api/erp/recurring/run-due", Map.of()));
        assertTrue(run.get("message").asText().matches("\\d+/\\d+ template dijalankan\\."));
        boolean ok = false;
        for (JsonNode r : run.get("results")) {
            if (dueId.equals(r.get("templateId").asText())) {
                ok = r.get("success").asBoolean();
                assertFalse(r.has("error"));
            }
            if (invId.equals(r.get("templateId").asText())) {
                assertFalse(r.get("success").asBoolean());
            }
        }
        assertTrue(ok);
        assertEquals(0, new BigDecimal("500").compareTo(balance(expense)));

        // run-due butuh doc.cancel.
        asUser("berulang." + uniq() + "@test.local", customRole(Map.of("recurring", "edit")));
        assertEquals(403, call(post("/api/erp/recurring/run-due", Map.of())).status());

        // Runner terjadwal (di dalam kunci job): atas nama Admin aktif pertama, transaksi per template.
        asAdmin();
        String schedId = ok(post("/api/erp/recurring", template("Sched " + uniq(), "2020-02-29", expense, cash)))
                .get("id").asText();
        ErpRecurringService.RunOutcome[] outcome = new ErpRecurringService.RunOutcome[1];
        jobLock.runExclusive("erp-recurring-templates-test", () -> outcome[0] = recurring.runDueScheduled());
        assertNotNull(outcome[0]);
        assertTrue(outcome[0].executed() >= 1);
        assertTrue(outcome[0].skipped() >= 1);
        assertEquals(0, new BigDecimal("1000").compareTo(balance(expense)));
        String creator = jdbc.queryForObject("SELECT j.created_by FROM journal_entries j JOIN journal_entry_accounts i "
                + "ON i.journal_entry_id = j.id WHERE i.account_id = ? ORDER BY j.created_at DESC LIMIT 1", String.class, expense);
        assertNotNull(creator);
        assertNotNull(jdbc.queryForObject("SELECT last_run_date FROM recurring_templates WHERE id = ?",
                java.sql.Timestamp.class, schedId));
        assertNotNull(userId);
    }

    @Test
    void cogs_backfill_list_and_summary() throws Exception {
        String userId = adminUserId();
        String fy = currentFiscalYearId();
        String customer = newParty("Customer");
        String itemId = UUID.randomUUID().toString();
        String code = "BRG-" + uniq();
        jdbc.update("INSERT INTO inventory_items (id, code, name, unit, current_stock, average_cost) "
                + "VALUES (?, ?, 'Beras Uji', 'kg', 0, 10000)", itemId, code);
        String invoiceId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sales_invoices (id, invoice_number, date, party_id, status, grand_total, outstanding, "
                + "fiscal_year_id, created_by) VALUES (?, ?, now(), ?, 'Submitted', 50000, 50000, ?, ?)",
                invoiceId, "SI-CB-" + uniq(), customer, fy, userId);
        String sii = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sales_invoice_items (id, sales_invoice_id, inventory_item_id, item_name, quantity, unit, "
                + "rate, amount, account_id) VALUES (?, ?, ?, 'Beras', 5, 'kg', 10000, 50000, ?)",
                sii, invoiceId, itemId, accountId("4.1"));
        String queueId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO cogs_backfill_queue (id, sales_invoice_id, sales_invoice_item_id, inventory_item_id, "
                + "qty_pending, qty_original, cost_at_sale, status, fiscal_year_id) "
                + "VALUES (?, ?, ?, ?, 2.5, 5, 0, 'PartiallySettled', ?)", queueId, invoiceId, sii, itemId, fy);
        jdbc.update("INSERT INTO cogs_backfill_settlements (id, queue_id, qty_settled, cost_at_settle, differential, "
                + "trigger_source, trigger_ref_no) VALUES (?, ?, 2.5, 10000, 25000, 'GoodsReceipt', 'STT-X')",
                UUID.randomUUID().toString(), queueId);

        asAdmin();
        JsonNode list = ok(get("/api/erp/cogs-backfill?itemId=" + itemId + "&limit=abc")).get("data");
        assertEquals(1, list.size());
        JsonNode q = list.get(0);
        assertEquals(code, q.get("inventoryItem").get("code").asText());
        assertEquals(6, q.get("inventoryItem").size());
        assertTrue(q.get("salesInvoice").get("customer").has("name"));
        assertEquals(1, q.get("settlements").size());
        assertEquals("STT-X", q.get("settlements").get(0).get("triggerRefNo").asText());
        assertEquals(0, ok(get("/api/erp/cogs-backfill?itemId=" + itemId + "&status=Settled")).get("data").size());

        JsonNode summary = ok(get("/api/erp/cogs-backfill/summary"));
        assertTrue(summary.get("pendingCount").asInt() >= 1);
        JsonNode mine = null;
        for (JsonNode b : summary.get("breakdown")) {
            if (itemId.equals(b.get("itemId").asText())) {
                mine = b;
            }
        }
        assertNotNull(mine);
        assertEquals(2.5, mine.get("qtyPending").asDouble());
        assertEquals(25000, mine.get("estimatedValue").asDouble());
        assertTrue(summary.get("totalPendingValue").asDouble() >= 25000);
    }
}
