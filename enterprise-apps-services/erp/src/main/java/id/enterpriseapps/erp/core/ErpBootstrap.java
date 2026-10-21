package id.enterpriseapps.erp.core;

import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.security.ErpModules;
import id.enterpriseapps.erp.core.security.ErpPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persiapan db_erp saat aplikasi siap — padanan entrypoint Docker ENTARIS (migrate + seed) dan
 * {@code ensureRbacSeed()} / {@code systemAccounts.validateStartup()} di index.ts.
 *
 * <ol>
 *   <li>Bila {@code app.erp.auto-init=true} (default) dan tabel {@code accounts} belum ada, jalankan
 *       {@code db/erp/01_schema.sql} lalu {@code 02_seed.sql} (salinan sql/erp_service/). Untuk
 *       produksi, skrip yang sama dijalankan manual oleh ops dan auto-init boleh dimatikan.</li>
 *   <li>Role ERP bawaan + matriks izin default dibuat bila belum ada (tidak menimpa perubahan admin).</li>
 *   <li>Peringatan di log bila pemetaan akun sistem wajib belum lengkap.</li>
 * </ol>
 * Kegagalan di sini hanya dicatat — modul lain platform tetap berjalan meski db_erp belum siap.
 */
@Slf4j
@Component
public class ErpBootstrap {

    private final JdbcTemplate jdbc;
    private final ErpPermissionService permissions;
    private final ErpSystemAccountService systemAccounts;
    private final boolean autoInit;

    public ErpBootstrap(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc,
                        ErpPermissionService permissions,
                        ErpSystemAccountService systemAccounts,
                        @Value("${app.erp.auto-init:true}") boolean autoInit) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.systemAccounts = systemAccounts;
        this.autoInit = autoInit;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            if (autoInit && !tableExists("accounts")) {
                log.info("ERP: db_erp kosong — memasang skema & data awal...");
                runScript("db/erp/01_schema.sql");
                runScript("db/erp/02_seed.sql");
                log.info("ERP: skema & data awal terpasang.");
            }
            if (!tableExists("roles")) {
                log.warn("ERP: tabel db_erp belum ada. Jalankan sql/erp_service/01_schema.sql & 02_seed.sql.");
                return;
            }
            ensureRbacSeed();
            systemAccounts.validateStartup();
        } catch (RuntimeException e) {
            log.error("ERP: bootstrap db_erp gagal (modul ERP mungkin belum bisa dipakai): {}", e.getMessage());
        }
    }

    private boolean tableExists(String table) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?)",
                Boolean.class, table);
        return Boolean.TRUE.equals(exists);
    }

    private void runScript(String path) {
        try {
            String sql = new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // pgjdbc memecah multi-statement sendiri (paham $$ dollar-quoting).
            jdbc.execute(sql);
        } catch (IOException e) {
            throw new IllegalStateException("Skrip " + path + " tidak ditemukan di classpath", e);
        }
    }

    /** Role ERP bawaan + matriks default; hanya mengisi yang belum ada. Port {@code ensureRbacSeed}. */
    void ensureRbacSeed() {
        for (ErpModules.BuiltinRole def : ErpModules.BUILTIN_ROLES) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, is_system, is_super_admin FROM roles WHERE key = ?", def.key());
            if (rows.isEmpty()) {
                String id = UUID.randomUUID().toString();
                jdbc.update("INSERT INTO roles (id, key, name, description, is_system, is_super_admin, is_active, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, TRUE, ?, TRUE, now(), now())", id, def.key(), def.name(), def.description(), def.superAdmin());
                Map<String, String> matrix = ErpModules.DEFAULT_MATRIX.get(def.key());
                if (matrix != null) {
                    matrix.forEach((module, action) -> jdbc.update(
                            "INSERT INTO role_permissions (id, role_id, module, can_view, can_edit) VALUES (?, ?, ?, TRUE, ?) "
                                    + "ON CONFLICT (role_id, module) DO NOTHING",
                            UUID.randomUUID().toString(), id, module, "edit".equals(action)));
                }
                log.info("ERP RBAC: role bawaan {} dibuat + matriks default", def.key());
            } else {
                Map<String, Object> r = rows.get(0);
                if (!Boolean.TRUE.equals(r.get("is_system")) || !Boolean.valueOf(def.superAdmin()).equals(r.get("is_super_admin"))) {
                    jdbc.update("UPDATE roles SET is_system = TRUE, is_super_admin = ?, updated_at = now() WHERE id = ?",
                            def.superAdmin(), r.get("id"));
                }
            }
        }
        permissions.invalidate();
    }
}
