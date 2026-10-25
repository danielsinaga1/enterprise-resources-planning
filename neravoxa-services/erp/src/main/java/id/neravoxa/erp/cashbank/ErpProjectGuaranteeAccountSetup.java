package id.neravoxa.erp.cashbank;

import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Bootstrap idempoten modul Uang Jaminan Proyek — port {@code services/projectGuaranteeSeed.ts}: pastikan akun
 * aset 1.8 dan pemetaan akun sistem PROJECT_GUARANTEE ada di db_erp yang sudah berjalan (seed hanya dipasang di
 * database baru). Pemetaan HANYA dibuat bila role itu belum punya pemetaan sama sekali — pemetaan ulang Admin
 * lewat Pengaturan &gt; Akun Sistem tidak ditimpa. Kegagalan hanya dicatat (tidak menghentikan boot).
 */
@Slf4j
@Component
public class ErpProjectGuaranteeAccountSetup {

    static final String GUARANTEE_ACCOUNT_NUMBER = "1.8";
    static final String GUARANTEE_ACCOUNT_NAME = "Uang Jaminan Proyek";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ErpSystemAccountService systemAccounts;

    public ErpProjectGuaranteeAccountSetup(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc,
                                           @Qualifier(ErpTx.MANAGER) PlatformTransactionManager tm,
                                           ErpSystemAccountService systemAccounts) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
        this.systemAccounts = systemAccounts;
    }

    /** Dijalankan setelah ErpBootstrap (skema/seed) agar tabel sudah ada pada database kosong. */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onReady() {
        ensureProjectGuaranteeAccount();
    }

    /** @return true bila ada yang dibuat (akun atau pemetaan). */
    public boolean ensureProjectGuaranteeAccount() {
        try {
            if (!tableExists("accounts") || !tableExists("system_account_mappings")) {
                return false;
            }
            Boolean changed = tx.execute(status -> {
                boolean created = false;
                List<String> found = jdbc.queryForList("SELECT id FROM accounts WHERE account_number = ?", String.class,
                        GUARANTEE_ACCOUNT_NUMBER);
                String accountId;
                if (found.isEmpty()) {
                    accountId = UUID.randomUUID().toString();
                    List<String> parent = jdbc.queryForList("SELECT id FROM accounts WHERE account_number = '1'", String.class);
                    jdbc.update("INSERT INTO accounts (id, name, account_number, account_type, root_type, is_group, is_active, "
                                    + "balance, currency, parent_id, created_at, updated_at) "
                                    + "VALUES (?, ?, ?, 'ASSET', 'ASSET', FALSE, TRUE, 0, 'IDR', ?, now(), now())",
                            accountId, GUARANTEE_ACCOUNT_NAME, GUARANTEE_ACCOUNT_NUMBER, parent.isEmpty() ? null : parent.get(0));
                    log.info("Akun Uang Jaminan Proyek dibuat accountNumber={}", GUARANTEE_ACCOUNT_NUMBER);
                    created = true;
                } else {
                    accountId = found.get(0);
                }
                Integer mappings = jdbc.queryForObject("SELECT COUNT(*) FROM system_account_mappings WHERE role = ?",
                        Integer.class, SystemAccountRole.PROJECT_GUARANTEE.name());
                if (mappings == null || mappings == 0) {
                    jdbc.update("INSERT INTO system_account_mappings (id, role, account_id, sort_order, created_at, updated_at) "
                                    + "VALUES (?, ?, ?, 0, now(), now())",
                            UUID.randomUUID().toString(), SystemAccountRole.PROJECT_GUARANTEE.name(), accountId);
                    log.info("System account mapping PROJECT_GUARANTEE → {} dibuat", GUARANTEE_ACCOUNT_NUMBER);
                    created = true;
                }
                return created;
            });
            if (Boolean.TRUE.equals(changed)) {
                systemAccounts.invalidateCache();
            }
            return Boolean.TRUE.equals(changed);
        } catch (RuntimeException e) {
            log.error("ensureProjectGuaranteeAccount gagal (diabaikan saat boot): {}", e.getMessage());
            return false;
        }
    }

    private boolean tableExists(String table) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?)",
                Boolean.class, table);
        return Boolean.TRUE.equals(exists);
    }
}
