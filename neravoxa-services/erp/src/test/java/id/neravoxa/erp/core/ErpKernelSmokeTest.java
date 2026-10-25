package id.neravoxa.erp.core;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.accounting.ErpAccountBalanceService;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.core.security.ErpActorResolver;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.support.ErpIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Uji asap kernel ERP: skema+seed terpasang, nomor dokumen, posting jurnal seimbang, /me, guard RBAC. */
class ErpKernelSmokeTest extends ErpIntegrationTest {

    @Autowired
    ErpGlPostingService gl;
    @Autowired
    ErpFiscalYearService fiscalYears;
    @Autowired
    ErpSystemAccountService systemAccounts;
    @Autowired
    ErpDocumentNumberService numbers;
    @Autowired
    ErpActorResolver actors;
    @Autowired
    @Qualifier(ErpTx.MANAGER)
    PlatformTransactionManager tm;

    @Test
    void me_returns_superadmin_permissions_and_provisions_user() throws Exception {
        asAdmin();
        JsonNode me = ok(get("/api/erp/me"));
        assertTrue(me.get("user").get("isSuperAdmin").asBoolean());
        assertEquals("erp.admin@test.local", me.get("user").get("email").asText());
    }

    @Test
    void route_guard_denies_without_permission() throws Exception {
        asUser("no.role@test.local", "SOME_OTHER_ROLE");
        Result r = call(get("/api/erp/journals"));
        assertEquals(403, r.status());
        assertEquals("Akses ditolak: Tidak memiliki izin.", r.error());
    }

    @Test
    void posts_balanced_journal_and_updates_balances() {
        asAdmin();
        String userId = actors.current().userId();
        TransactionTemplate tx = new TransactionTemplate(tm);
        JournalEntry je = tx.execute(s -> {
            Instant date = Instant.now();
            FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
            String cash = systemAccounts.getAccountId(SystemAccountRole.CASH);
            String sales = systemAccounts.getAccountId(SystemAccountRole.SALES);
            return gl.journal().date(date).narration("Uji kernel").fiscalYear(fy.getId()).createdBy(userId)
                    .line(cash, new BigDecimal("150000"), BigDecimal.ZERO, "Kas")
                    .line(sales, BigDecimal.ZERO, new BigDecimal("150000"), "Penjualan")
                    .post();
        });
        assertNotNull(je.getId());
        assertTrue(je.getEntryNumber().startsWith("JV-"));
        BigDecimal ledger = jdbc.queryForObject(
                "SELECT SUM(debit) - SUM(credit) FROM accounting_ledger_entries WHERE reference_id = ?",
                BigDecimal.class, je.getId());
        assertEquals(0, ledger.signum());
    }

    @Test
    void rejects_unbalanced_journal() {
        asAdmin();
        String userId = actors.current().userId();
        TransactionTemplate tx = new TransactionTemplate(tm);
        assertThrows(ErpException.class, () -> tx.execute(s -> {
            Instant date = Instant.now();
            FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
            String cash = systemAccounts.getAccountId(SystemAccountRole.CASH);
            return gl.journal().date(date).narration("x").fiscalYear(fy.getId()).createdBy(userId)
                    .line(cash, new BigDecimal("10"), BigDecimal.ZERO, "a").post();
        }));
    }

    @Test
    void document_numbers_are_sequential() {
        TransactionTemplate tx = new TransactionTemplate(tm);
        String a = tx.execute(s -> numbers.generate("SI", Instant.parse("2026-03-05T00:00:00Z"), null));
        assertEquals("SI-202603-0001", a);
    }

    @Test
    void account_impact_direction() {
        assertEquals(new BigDecimal("5"), ErpAccountBalanceService.computeImpact("ASSET", new BigDecimal("10"), new BigDecimal("5")));
        assertEquals(new BigDecimal("-5"), ErpAccountBalanceService.computeImpact("REVENUE", new BigDecimal("10"), new BigDecimal("5")));
    }
}
