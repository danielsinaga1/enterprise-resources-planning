package id.enterpriseapps.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Alur uang jaminan proyek: setor, settlement parsial/penuh, batal settlement, batal setoran, daftar & KPI. */
class ProjectGuaranteeFlowTest extends CashBankTestSupport {

    @Autowired
    ErpProjectGuaranteeAccountSetup accountSetup;

    private Map<String, Object> guaranteeBody(String partyId, String project, Object amount, String expected) {
        return body("date", today(), "partyId", partyId, "guaranteeType", "Pelaksanaan", "projectName", project,
                "referenceNo", "SPK-01", "amount", amount, "accountId", accountId("1.1.3"),
                "expectedReturnDate", expected, "notes", null);
    }

    @Test
    void deposit_settle_cancel_cycle_keeps_gl_balanced() throws Exception {
        String owner = createParty(uniq("Pemberi Proyek"), "Customer");
        String project = uniq("Proyek Jalan");
        BigDecimal pg0 = balance("1.8");
        BigDecimal bank0 = balance("1.1.3");
        BigDecimal other0 = balance("8.7");
        asAdmin();

        var created = call(post("/api/erp/project-guarantees", guaranteeBody(owner, project, 1000000, daysFromToday(60))));
        assertEquals(201, created.status());
        JsonNode g = created.data();
        String id = g.get("id").asText();
        String number = g.get("guaranteeNumber").asText();
        assertTrue(number.startsWith("UJ-"));
        assertEquals("Disetor", g.get("status").asText());
        assertEquals(2, g.get("party").size());
        assertEquals(owner, g.get("party").get("id").asText());
        assertBalancedJournal("JV-" + number, "Submitted");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry_accounts WHERE journal_entry_id = ? "
                + "AND party_id IS NOT NULL", Integer.class, g.get("journalEntryId").asText()));
        assertAmount("1000000", balance("1.8").subtract(pg0));
        assertAmount("-1000000", balance("1.1.3").subtract(bank0));

        // Settlement parsial: 600rb kembali ke bank + 100rb hangus ke Beban Lain-lain (default).
        var settled = call(post("/api/erp/project-guarantees/" + id + "/settle", body("date", today(),
                "returnAmount", 600000, "forfeitAmount", "100000", "returnAccountId", accountId("1.1.3"))));
        assertEquals(201, settled.status());
        assertAmount("300000", settled.data().get("outstanding"));
        JsonNode s1 = settled.data().get("settlement");
        assertEquals(accountId("8.7"), s1.get("forfeitAccountId").asText());
        String s1Journal = jdbc.queryForObject("SELECT entry_number FROM journal_entries WHERE id = ?", String.class,
                s1.get("journalEntryId").asText());
        assertBalancedJournal(s1Journal, "Submitted");
        assertAmount("300000", balance("1.8").subtract(pg0));
        assertAmount("-400000", balance("1.1.3").subtract(bank0));
        assertAmount("100000", balance("8.7").subtract(other0));

        var tooMuch = call(post("/api/erp/project-guarantees/" + id + "/settle", body("date", today(),
                "returnAmount", 400000, "returnAccountId", accountId("1.1.3"))));
        assertEquals("Jumlah settlement (400000.00) melebihi sisa jaminan (300000.00).", tooMuch.error());
        assertEquals("Sudah ada settlement aktif. Batalkan settlement terlebih dahulu.",
                call(patch("/api/erp/project-guarantees/" + id + "/cancel", Map.of())).error());

        JsonNode s2 = ok(post("/api/erp/project-guarantees/" + id + "/settle", body("date", today(),
                "returnAmount", 300000, "returnAccountId", accountId("1.1.3")))).get("settlement");
        JsonNode detail = ok(get("/api/erp/project-guarantees/" + id));
        assertEquals("Selesai", detail.get("status").asText());
        assertAmount("0", detail.get("outstanding"));
        assertEquals(2, detail.get("settlements").size());
        assertEquals("Uang jaminan berstatus Selesai, tidak bisa di-settle.",
                call(post("/api/erp/project-guarantees/" + id + "/settle", body("date", today(), "forfeitAmount", 1))).error());

        // Batalkan settlement terakhir → jaminan dibuka lagi, jurnal batal, saldo kembali.
        JsonNode msg = ok(post("/api/erp/project-guarantees/settlements/" + s2.get("id").asText() + "/cancel", Map.of()));
        assertEquals("Settlement dibatalkan.", msg.get("message").asText());
        detail = ok(get("/api/erp/project-guarantees/" + id));
        assertEquals("Disetor", detail.get("status").asText());
        assertAmount("300000", detail.get("outstanding"));
        assertAmount("300000", balance("1.8").subtract(pg0));
        assertEquals("Settlement sudah dibatalkan.",
                call(post("/api/erp/project-guarantees/settlements/" + s2.get("id").asText() + "/cancel", Map.of())).error());

        ok(post("/api/erp/project-guarantees/settlements/" + s1.get("id").asText() + "/cancel", Map.of()));
        assertBalancedJournal(s1Journal, "Cancelled");
        JsonNode cancelled = ok(patch("/api/erp/project-guarantees/" + id + "/cancel", Map.of()));
        assertEquals("Uang jaminan dibatalkan.", cancelled.get("message").asText());
        assertBalancedJournal("JV-" + number, "Cancelled");
        assertAmount("0", balance("1.8").subtract(pg0));
        assertAmount("0", balance("1.1.3").subtract(bank0));
        assertAmount("0", balance("8.7").subtract(other0));
        assertEquals("Uang jaminan sudah dibatalkan.",
                call(patch("/api/erp/project-guarantees/" + id + "/cancel", Map.of())).error());
    }

    @Test
    void validation_messages_follow_schema() throws Exception {
        String owner = createParty(uniq("Pemberi"), "Customer");
        asAdmin();
        var r = call(post("/api/erp/project-guarantees", body("date", today(), "partyId", "bukan-uuid",
                "guaranteeType", "Pelaksanaan", "projectName", "X", "amount", 1, "accountId", accountId("1.1.3"))));
        assertEquals("Pihak pemberi proyek wajib dipilih.", r.error());
        r = call(post("/api/erp/project-guarantees", body("date", today(), "partyId", owner,
                "guaranteeType", "Tender", "projectName", "X", "amount", 1, "accountId", accountId("1.1.3"))));
        assertEquals("Invalid option: expected one of \"Penawaran\"|\"Pelaksanaan\"|\"Pemeliharaan\"|\"Lainnya\"", r.error());
        r = call(post("/api/erp/project-guarantees", body("date", today(), "partyId", owner,
                "guaranteeType", "Lainnya", "projectName", "X", "amount", 1, "accountId", accountId("2.1.1"))));
        assertEquals("Akun yang dipilih bukan akun kas/bank.", r.error());

        JsonNode g = ok(post("/api/erp/project-guarantees", guaranteeBody(owner, uniq("Proyek"), 500000, null)));
        String id = g.get("id").asText();
        assertTrue(g.get("expectedReturnDate").isNull());
        r = call(post("/api/erp/project-guarantees/" + id + "/settle", body("date", today())));
        assertEquals("Masukkan jumlah dikembalikan atau hangus minimal salah satu.", r.error());
        r = call(post("/api/erp/project-guarantees/" + id + "/settle", body("date", today(), "returnAmount", 10)));
        assertEquals("Pilih akun kas/bank tujuan pengembalian.", r.error());
        r = call(post("/api/erp/project-guarantees/" + id + "/settle", body("date", today(), "forfeitAmount", 10,
                "expenseAccountId", accountId("1.1.1"))));
        assertEquals("Akun hangus harus akun beban (EXPENSE).", r.error());
        r = call(post("/api/erp/project-guarantees/" + id + "/settle", body("date", today(), "forfeitAmount", -1)));
        assertEquals("Too small: expected number to be >=0", r.error());
        r = call(get("/api/erp/project-guarantees/00000000-0000-0000-0000-000000000000"));
        assertEquals(404, r.status());
        assertEquals("Uang jaminan tidak ditemukan.", r.error());
    }

    @Test
    void paged_list_due_filter_and_summary() throws Exception {
        String owner = createParty(uniq("Pemberi KPI"), "Customer");
        String tag = uniq("ProyekKPI");
        asAdmin();
        JsonNode overdue = ok(post("/api/erp/project-guarantees", guaranteeBody(owner, tag + "-A", 200000, daysFromToday(-3))));
        ok(post("/api/erp/project-guarantees", guaranteeBody(owner, tag + "-B", 300000, daysFromToday(10))));
        JsonNode later = ok(post("/api/erp/project-guarantees", guaranteeBody(owner, tag + "-C", 400000, daysFromToday(90))));
        ok(patch("/api/erp/project-guarantees/" + later.get("id").asText() + "/cancel", Map.of()));

        JsonNode page = ok(get("/api/erp/project-guarantees?page=1&limit=2&search=" + tag + "&sort=-outstanding"));
        assertEquals(3, page.get("total").asInt());
        assertEquals(2, page.get("data").size());
        JsonNode first = page.get("data").get(0);
        assertAmount("300000", first.get("outstanding"));
        assertEquals("dueSoon", first.get("dueState").asText());
        assertEquals("1.1.3", first.get("account").get("accountNumber").asText());
        assertTrue(first.get("settlements").isArray());
        assertEquals(2, first.get("party").size());

        JsonNode due = ok(get("/api/erp/project-guarantees?page=1&due=overdue&partyId=" + owner));
        assertEquals(1, due.get("total").asInt());
        assertEquals(overdue.get("id").asText(), due.get("data").get(0).get("id").asText());

        JsonNode summary = ok(get("/api/erp/project-guarantees/summary?partyId=" + owner));
        assertEquals(3, summary.get("count").asInt());
        assertEquals(1, summary.get("cancelledCount").asInt());
        assertEquals(2, summary.get("activeCount").asInt());
        assertAmount("500000", summary.get("totalDeposited"));
        assertAmount("500000", summary.get("totalOutstanding"));
        assertEquals(1, summary.get("overdueCount").asInt());
        assertAmount("200000", summary.get("overdueOutstanding"));
        assertEquals(1, summary.get("dueSoonCount").asInt());

        JsonNode legacy = ok(get("/api/erp/project-guarantees?partyId=" + owner));
        assertEquals(3, legacy.get("data").size());
        assertFalse(legacy.get("data").get(0).has("dueState"));
        assertAmount("500000", legacy.get("summary").get("totalDeposited"));
        assertEquals(2, legacy.get("summary").get("activeCount").asInt());

        assertEquals("Filter jatuh tempo tidak dikenal.", call(get("/api/erp/project-guarantees?page=1&due=late")).error());
        assertEquals("Jenis jaminan tidak dikenal.", call(get("/api/erp/project-guarantees/summary?guaranteeType=X")).error());
        assertEquals("Status uang jaminan tidak dikenal.", call(get("/api/erp/project-guarantees?page=1&status=Open")).error());
    }

    @Test
    void startup_setup_recreates_missing_mapping() throws Exception {
        jdbc.update("DELETE FROM system_account_mappings WHERE role = 'PROJECT_GUARANTEE'");
        assertTrue(accountSetup.ensureProjectGuaranteeAccount());
        assertEquals(accountId("1.8"), jdbc.queryForObject(
                "SELECT account_id FROM system_account_mappings WHERE role = 'PROJECT_GUARANTEE'", String.class));
        assertFalse(accountSetup.ensureProjectGuaranteeAccount());
        String owner = createParty(uniq("Pemberi"), "Customer");
        asAdmin();
        ok(post("/api/erp/project-guarantees", guaranteeBody(owner, uniq("Proyek"), 1000, null)));
    }
}
