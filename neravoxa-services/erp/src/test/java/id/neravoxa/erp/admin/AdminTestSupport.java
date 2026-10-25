package id.neravoxa.erp.admin;

import id.neravoxa.erp.core.approval.ErpApprovalGateService;
import id.neravoxa.erp.core.security.ErpPermissionService;
import id.neravoxa.erp.support.ErpIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fixture bersama uji paket admin (data unik per test; DB tidak di-rollback antar test). */
abstract class AdminTestSupport extends ErpIntegrationTest {

    protected static final String ADMIN_EMAIL = "erp.admin@test.local";
    protected static final String DENIED = "Akses ditolak: Tidak memiliki izin.";

    @Autowired
    protected ErpApprovalGateService gate;
    @Autowired
    protected ErpPermissionService permissions;

    protected static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    protected static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Login + provisi user ERP (akses pertama); kembalikan id baris users. */
    protected String login(String email, String... roles) throws Exception {
        asUser(email, roles);
        ok(get("/api/erp/me"));
        return jdbc.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?)", String.class, email);
    }

    protected String loginAdmin() throws Exception {
        return login(ADMIN_EMAIL, "SUPER_ADMIN");
    }

    /** Antrian approval ber-payload (seperti route POST dokumen untuk role tanpa approval.bypass). */
    protected String queue(String type, Object payload, String amount, String requesterId) {
        return gate.queueApproval(type, payload, new BigDecimal(amount), requesterId, null).getId();
    }

    protected Map<String, Object> requestRow(String id) {
        return jdbc.queryForMap("SELECT * FROM approval_requests WHERE id = ?", id);
    }

    protected List<Map<String, Object>> notificationsOf(String userId, String type, String requestId) {
        return jdbc.queryForList("SELECT id, title, message, metadata::text AS metadata FROM notifications "
                + "WHERE user_id = ? AND type = ? AND metadata ->> 'requestId' = ? ORDER BY created_at", userId, type, requestId);
    }

    /** Role kustom (bukan super-admin): izin modul edit + capability; cache izin dibuang. */
    protected String customRole(String key, List<String> editModules, String... caps) {
        String roleId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO roles (id, key, name, description, is_system, is_super_admin, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'uji', false, false, true, now(), now())", roleId, key, key);
        for (String module : editModules) {
            jdbc.update("INSERT INTO role_permissions (id, role_id, module, can_view, can_edit) VALUES (?, ?, ?, true, true)",
                    UUID.randomUUID().toString(), roleId, module);
        }
        for (String cap : caps) {
            jdbc.update("INSERT INTO role_capabilities (id, role_id, capability) VALUES (?, ?, ?)",
                    UUID.randomUUID().toString(), roleId, cap);
        }
        permissions.invalidate();
        return roleId;
    }

    /** Respons mentah (unduhan berkas). */
    protected MvcResult raw(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(r -> {
            r.setUserPrincipal(SecurityContextHolder.getContext().getAuthentication());
            return r;
        })).andReturn();
    }

    protected String createParty(String name, String type) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO parties (id, name, party_type, is_active, created_at, updated_at) VALUES (?, ?, ?, true, now(), now())",
                id, name, type);
        return id;
    }

    protected String createItem(String code, String name, String unit) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO inventory_items (id, code, name, unit, current_stock, minimum_stock, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 0, 0, true, now(), now())", id, code, name, unit);
        return id;
    }

    protected String openFiscalYearId() {
        return jdbc.queryForObject("SELECT id FROM fiscal_years WHERE is_closed = false AND start_date <= now() "
                + "AND end_date >= now() - interval '1 day' ORDER BY start_date DESC LIMIT 1", String.class);
    }

    protected String createPurchaseInvoice(String partyId, String number, String outstanding, String status, String createdBy) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO purchase_invoices (id, invoice_number, date, party_id, status, grand_total, outstanding, due_date, "
                        + "fiscal_year_id, created_by, submitted_at, created_at, updated_at) "
                        + "VALUES (?, ?, now(), ?, ?, ?, ?, now(), ?, ?, now(), now(), now())",
                id, number, partyId, status, new BigDecimal(outstanding), new BigDecimal(outstanding), openFiscalYearId(), createdBy);
        return id;
    }

    protected String createJournal(String number, String createdBy) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO journal_entries (id, entry_number, date, status, fiscal_year_id, created_by, created_at, updated_at) "
                + "VALUES (?, ?, now(), 'Submitted', ?, ?, now(), now())", id, number, openFiscalYearId(), createdBy);
        return id;
    }

    protected String createPayment(String number, String partyId, String type, String createdBy) {
        String id = UUID.randomUUID().toString();
        String account = jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = '1.1.3'", String.class);
        jdbc.update("INSERT INTO payments (id, payment_number, date, payment_type, party_id, account_id, amount, status, created_by, "
                        + "fiscal_year_id, created_at, updated_at) VALUES (?, ?, now(), ?, ?, ?, 100000, 'Submitted', ?, ?, now(), now())",
                id, number, type, partyId, account, createdBy, openFiscalYearId());
        return id;
    }

    protected static void assertAmount(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(String.valueOf(actual))),
                "nilai " + actual + " ≠ " + expected);
    }
}
