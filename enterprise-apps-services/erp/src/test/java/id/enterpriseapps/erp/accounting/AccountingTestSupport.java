package id.enterpriseapps.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.security.ErpPermissionService;
import id.enterpriseapps.erp.support.ErpIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Helper bersama uji slice akuntansi (data unik per test; DB dipakai bersama satu JVM). */
abstract class AccountingTestSupport extends ErpIntegrationTest {

    @Autowired
    protected ErpPermissionService permissions;

    protected static String uniq() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected static int rnd() {
        return ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }

    protected String accountId(String number) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", String.class, number);
    }

    protected BigDecimal balance(String accountId) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    protected String adminUserId() throws Exception {
        asAdmin();
        ok(get("/api/erp/fiscal-years"));
        return jdbc.queryForObject("SELECT id FROM users WHERE email = 'erp.admin@test.local'", String.class);
    }

    protected String currentFiscalYearId() {
        return jdbc.queryForObject("SELECT id FROM fiscal_years WHERE start_date <= now() AND end_date >= now() "
                + "AND is_closed = false LIMIT 1", String.class);
    }

    /** Akun detail baru di bawah grup {@code parentNumber} (nomor unik). */
    protected JsonNode newAccount(String parentNumber, String rootType) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountNumber", parentNumber + "." + rnd());
        body.put("name", "Akun Uji " + uniq());
        body.put("accountType", rootType);
        body.put("rootType", rootType);
        body.put("parentId", accountId(parentNumber));
        asAdmin();
        Result r = call(post("/api/erp/coa", body));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.data();
    }

    protected String newParty(String type) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO parties (id, name, party_type, created_at, updated_at) VALUES (?, ?, ?, now(), now())",
                id, "Mitra Uji " + uniq(), type);
        return id;
    }

    protected static Map<String, Object> line(String accountId, Object debit, Object credit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountId", accountId);
        m.put("debit", debit);
        m.put("credit", credit);
        return m;
    }

    protected static Map<String, Object> journal(String date, String narration, List<Map<String, Object>> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("narration", narration);
        m.put("items", items);
        return m;
    }

    /** Selisih Σdebit − Σkredit ledger aktif untuk satu jurnal (harus 0). */
    protected BigDecimal ledgerImbalance(String journalId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(debit),0) - COALESCE(SUM(credit),0) "
                + "FROM accounting_ledger_entries WHERE reference_id = ?", BigDecimal.class, journalId);
    }

    /** Role uji non-super-admin dengan izin modul tertentu (+ capability opsional). */
    protected String customRole(Map<String, String> modules, String... caps) {
        String key = "TEST_ROLE_" + uniq().toUpperCase();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO roles (id, key, name, is_system, is_super_admin, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, false, false, true, now(), now())", id, key, key);
        modules.forEach((module, action) -> jdbc.update(
                "INSERT INTO role_permissions (id, role_id, module, can_view, can_edit) VALUES (?, ?, ?, true, ?)",
                UUID.randomUUID().toString(), id, module, "edit".equals(action)));
        for (String cap : caps) {
            jdbc.update("INSERT INTO role_capabilities (id, role_id, capability) VALUES (?, ?, ?)",
                    UUID.randomUUID().toString(), id, cap);
        }
        permissions.invalidate();
        return key;
    }
}
