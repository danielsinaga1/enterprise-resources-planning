package id.enterpriseapps.erp.report;

import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.security.ErpPermissionService;
import id.enterpriseapps.erp.entity.JournalEntry;
import id.enterpriseapps.erp.support.ErpIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Helper uji slice laporan. DB dipakai bersama satu JVM: tiap test memakai tahun fiskal
 * tersendiri (2041, 2042, …) atau nama unik agar angka laporan bisa dipastikan.
 */
abstract class ReportTestSupport extends ErpIntegrationTest {

    @Autowired
    protected ErpGlPostingService gl;
    @Autowired
    protected ErpPermissionService permissions;
    @Autowired
    @Qualifier(ErpTx.MANAGER)
    protected PlatformTransactionManager tm;

    protected static String uniq() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected String accountId(String number) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", String.class, number);
    }

    protected BigDecimal balance(String number) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE account_number = ?", BigDecimal.class, number);
    }

    /** id pengguna ERP super-admin (diprovisi lewat satu request). */
    protected String adminUserId() throws Exception {
        asAdmin();
        ok(get("/api/erp/fiscal-years"));
        return jdbc.queryForObject("SELECT id FROM users WHERE email = 'erp.admin@test.local'", String.class);
    }

    /** Tahun fiskal terbuka untuk tahun tertentu (dibuat bila belum ada). */
    protected String fiscalYear(int year) {
        jdbc.update("INSERT INTO fiscal_years (id, name, start_date, end_date, is_closed, created_at) "
                        + "VALUES (?, ?, CAST(? AS timestamp), CAST(? AS timestamp), false, now()) ON CONFLICT (name) DO NOTHING",
                UUID.randomUUID().toString(), String.valueOf(year), year + "-01-01 00:00:00", year + "-12-31 00:00:00");
        return jdbc.queryForObject("SELECT id FROM fiscal_years WHERE name = ?", String.class, String.valueOf(year));
    }

    protected String currentFiscalYearId() {
        return jdbc.queryForObject("SELECT id FROM fiscal_years WHERE start_date <= now() AND end_date >= now() "
                + "AND is_closed = false LIMIT 1", String.class);
    }

    /**
     * Posting jurnal lewat kernel. {@code lines} = triplet (nomorAkun, debit, kredit).
     * Tanggal {@code YYYY-MM-DD} = tengah malam UTC (seperti dokumen ENTARIS).
     */
    protected JournalEntry post(String isoDate, String narration, Object... lines) throws Exception {
        String userId = adminUserId();
        int year = Integer.parseInt(isoDate.substring(0, 4));
        String fy = fiscalYear(year);
        Instant date = Instant.parse(isoDate + "T00:00:00Z");
        TransactionTemplate tx = new TransactionTemplate(tm);
        return tx.execute(s -> {
            ErpGlPostingService.Builder b = gl.journal().date(date).narration(narration).fiscalYear(fy).createdBy(userId);
            for (int i = 0; i < lines.length; i += 3) {
                b.line(accountId((String) lines[i]), new BigDecimal(lines[i + 1].toString()),
                        new BigDecimal(lines[i + 2].toString()), narration);
            }
            return b.post();
        });
    }

    protected String newParty(String name, String type) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO parties (id, name, party_type, created_at, updated_at) VALUES (?, ?, ?, now(), now())",
                id, name, type);
        return id;
    }

    /** Faktur langsung di DB (tanpa GL) untuk laporan umur/jadwal; due = sekarang + offset hari. */
    protected String newInvoice(boolean purchase, String partyId, String number, String status, BigDecimal total,
                                BigDecimal outstanding, Integer dueOffsetDays) throws Exception {
        String id = UUID.randomUUID().toString();
        String table = purchase ? "purchase_invoices" : "sales_invoices";
        jdbc.update("INSERT INTO " + table + " (id, invoice_number, date, party_id, status, grand_total, outstanding, "
                        + "due_date, fiscal_year_id, created_by, created_at, updated_at) VALUES (?, ?, "
                        + "(now() AT TIME ZONE 'UTC') - interval '60 days', ?, ?, ?, ?, "
                        + (dueOffsetDays == null ? "NULL" : "(now() AT TIME ZONE 'UTC') + (? * interval '1 day')")
                        + ", ?, ?, now(), now())",
                dueOffsetDays == null
                        ? new Object[]{id, number, partyId, status, total, outstanding, currentFiscalYearId(), adminUserId()}
                        : new Object[]{id, number, partyId, status, total, outstanding, dueOffsetDays, currentFiscalYearId(),
                        adminUserId()});
        return id;
    }

    /** Role uji non-super-admin dengan izin modul tertentu. */
    protected String customRole(Map<String, String> modules) {
        String key = "TEST_RPT_" + uniq().toUpperCase();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO roles (id, key, name, is_system, is_super_admin, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, false, false, true, now(), now())", id, key, key);
        modules.forEach((module, action) -> jdbc.update(
                "INSERT INTO role_permissions (id, role_id, module, can_view, can_edit) VALUES (?, ?, ?, true, ?)",
                UUID.randomUUID().toString(), id, module, "edit".equals(action)));
        permissions.invalidate();
        return key;
    }

    /** Respons mentah (untuk unduhan berkas yang tidak dibungkus Response). */
    protected MockHttpServletResponse raw(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(request -> {
            request.setUserPrincipal(SecurityContextHolder.getContext().getAuthentication());
            return request;
        })).andReturn().getResponse();
    }

    /** Σdebit/Σkredit ledger aktif satu akun dalam rentang UTC [from, to). */
    protected BigDecimal[] ledger(String accountNumber, String fromTs, String toTs) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(e.debit),0), COALESCE(SUM(e.credit),0) FROM accounting_ledger_entries e "
                        + "JOIN accounts a ON a.id = e.account_id WHERE a.account_number = ? AND e.is_cancelled = false "
                        + "AND e.date >= CAST(? AS timestamp) AND e.date < CAST(? AS timestamp)",
                (rs, i) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2)}, accountNumber, fromTs, toTs);
    }
}
