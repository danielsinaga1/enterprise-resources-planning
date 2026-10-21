package id.enterpriseapps.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.entity.JournalEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Health check: inkonsistensi yang disuntikkan terdeteksi, fix memperbaikinya dalam satu transaksi + audit. */
class HealthCheckFlowTest extends ReportTestSupport {

    private JsonNode check(String name) throws Exception {
        asAdmin();
        JsonNode body = ok(get("/api/erp/health-check"));
        assertTrue(body.get("success").asBoolean());
        JsonNode checks = body.get("data").get("checks");
        assertEquals(6, checks.size());
        for (JsonNode c : checks) {
            if (name.equals(c.get("name").asText())) {
                return c;
            }
        }
        fail("check " + name + " tidak ada");
        return null;
    }

    private BigDecimal ledgerBalance(String accountNumber) {
        BigDecimal[] l = ledger(accountNumber, "1900-01-01", "2999-01-01");
        return l[0].subtract(l[1]);
    }

    private long auditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_trail WHERE action = ? AND entity_type = 'health-check'",
                Long.class, action);
    }

    @Test
    void balance_drift_is_detected_and_fixed_from_ledger() throws Exception {
        post("2045-01-10", "Health drift", "1.1.3", 120_000, 0, "4.1", 0, 120_000);
        jdbc.update("UPDATE accounts SET balance = balance + 777 WHERE account_number = '1.1.3'");

        JsonNode drift = check("Account Balance Drift");
        assertEquals("warning", drift.get("status").asText());
        assertTrue(drift.get("fixable").asBoolean());
        boolean found = false;
        for (JsonNode d : drift.get("details")) {
            found |= "1.1.3".equals(d.get("account_code").asText());
        }
        assertTrue(found, drift.toString());

        JsonNode preview = ok(get("/api/erp/health-check/preview/account-balance-drift"));
        assertEquals("account-balance-drift", preview.get("data").get("check").asText());
        assertTrue(preview.get("data").get("count").asInt() >= 1);
        assertTrue(preview.get("data").get("description").asText().startsWith("Akan MENGUBAH saldo"));

        long audits = auditCount("FIX_ACCOUNT_BALANCE_DRIFT");
        JsonNode fixed = ok(post("/api/erp/health-check/fix/account-balance-drift", Map.of()));
        assertTrue(fixed.get("success").asBoolean());
        assertTrue(fixed.get("data").get("message").asText().startsWith("Dihitung ulang saldo"));
        assertEquals(0, ledgerBalance("1.1.3").compareTo(balance("1.1.3")));
        assertEquals("ok", check("Account Balance Drift").get("status").asText());
        assertEquals(audits + 1, auditCount("FIX_ACCOUNT_BALANCE_DRIFT"));
        String snapshot = jdbc.queryForObject("SELECT new_values::text FROM audit_trail WHERE action = 'FIX_ACCOUNT_BALANCE_DRIFT' "
                + "ORDER BY created_at DESC LIMIT 1", String.class);
        assertTrue(snapshot.contains("driftedAccountsBefore") && snapshot.contains("1.1.3"), snapshot);
    }

    @Test
    void orphan_ledger_entries_are_cancelled() throws Exception {
        String fy = fiscalYear(2045);
        String ghost = UUID.randomUUID().toString();
        for (Object[] line : new Object[][]{{"1.1.4", 5000, 0}, {"4.1", 0, 5000}}) {
            jdbc.update("INSERT INTO accounting_ledger_entries (id, date, account_id, debit, credit, reference_type, "
                            + "reference_id, description, fiscal_year_id, is_cancelled, created_at) "
                            + "VALUES (?, CAST('2045-02-01 00:00:00' AS timestamp), ?, ?, ?, 'JournalEntry', ?, 'orphan', ?, false, now())",
                    UUID.randomUUID().toString(), accountId((String) line[0]), line[1], line[2], ghost, fy);
        }
        JsonNode orphan = check("Orphan ALE Entries");
        assertEquals("warning", orphan.get("status").asText());
        assertTrue(orphan.get("details").toString().contains(ghost));
        assertEquals("error", check("ALE vs JournalItem Sync").get("status").asText(), "ALE tanpa journal item");

        JsonNode preview = ok(get("/api/erp/health-check/preview/orphan-ale"));
        assertTrue(preview.get("data").get("description").asText().startsWith("Akan MENANDAI"));

        JsonNode fixed = ok(post("/api/erp/health-check/fix/orphan-ale", Map.of()));
        assertEquals(2, fixed.get("data").get("fixed").asInt());
        assertEquals("2 entri ALE orphan ditandai sebagai cancelled", fixed.get("data").get("message").asText());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ? "
                + "AND is_cancelled = true", Integer.class, ghost));
        assertEquals("ok", check("Orphan ALE Entries").get("status").asText());
        assertEquals("ok", check("ALE vs JournalItem Sync").get("status").asText());
        assertTrue(auditCount("FIX_ORPHAN_ALE") >= 1);
    }

    @Test
    void ale_sync_rebuilds_ledger_from_journal_items() throws Exception {
        JournalEntry je = post("2045-03-01", "Health ale sync", "1.1.5", 64_000, 0, "4.2", 0, 64_000);
        jdbc.update("DELETE FROM accounting_ledger_entries WHERE reference_id = ? AND debit > 0", je.getId());
        JsonNode sync = check("ALE vs JournalItem Sync");
        assertEquals("error", sync.get("status").asText());

        JsonNode preview = ok(get("/api/erp/health-check/preview/ale-sync"));
        assertTrue(preview.get("data").has("warning"));
        assertEquals(3, preview.get("data").get("details").size());

        JsonNode fixed = ok(post("/api/erp/health-check/fix/ale-sync", Map.of()));
        assertTrue(fixed.get("data").get("message").asText().startsWith("Dihapus "));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ? "
                + "AND is_cancelled = false", Integer.class, je.getId()));
        assertEquals("ok", check("ALE vs JournalItem Sync").get("status").asText());
        assertEquals("ok", check("Unbalanced Journal Entries").get("status").asText());
    }

    @Test
    void unknown_checks_and_permissions() throws Exception {
        asAdmin();
        Result r = call(post("/api/erp/health-check/fix/stock-mismatch", Map.of()));
        assertEquals(400, r.status());
        assertEquals("Check tidak dapat diperbaiki otomatis", r.error());
        assertFalse(r.data().get("success").asBoolean());
        r = call(get("/api/erp/health-check/preview/unbalanced"));
        assertEquals(400, r.status());
        assertEquals("Check tidak dapat di-preview", r.error());

        asUser("auditor.lihat@test.local", customRole(Map.of("audit", "view")));
        assertEquals(200, call(get("/api/erp/health-check")).status());
        assertEquals(403, call(post("/api/erp/health-check/fix/orphan-ale", Map.of())).status());
        asUser("bukan.auditor@test.local", customRole(Map.of("reports", "view")));
        assertEquals(403, call(get("/api/erp/health-check")).status());
    }
}
