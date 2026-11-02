package id.neravoxa.erp.core.notification;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.security.ErpPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Notifikasi in-app ERP — port {@code services/notificationService.ts} (tanpa Telegram).
 *
 * <p>Notifikasi tidak boleh menggagalkan operasi bisnis: semua method menelan error dan
 * mengembalikan jumlah yang dibuat. Bila dipanggil di dalam transaksi aktif, penulisan ditunda
 * sampai transaksi COMMIT (setara "panggil setelah transaksi selesai" di sumber) — rollback
 * berarti tidak ada notifikasi.
 */
@Slf4j
@Service
public class ErpNotificationService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ErpPermissionService permissions;
    private final String baseUrl;

    public ErpNotificationService(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc,
                                  @Qualifier(ErpTx.MANAGER) PlatformTransactionManager tm,
                                  ErpPermissionService permissions,
                                  @Value("${app.base-url:}") String baseUrl) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.permissions = permissions;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    /** Isi notifikasi. {@code metadata} boleh Map/JsonNode/null. */
    public record Notice(String type, String title, String message, Object metadata) {
    }

    /** Tautan aplikasi absolut (untuk teks notifikasi/email), null bila app.base-url kosong. */
    public String appLink(String erpPath) {
        if (baseUrl.isEmpty()) {
            return null;
        }
        return baseUrl + "/dashboard/erp" + (erpPath.startsWith("/") ? erpPath : "/" + erpPath);
    }

    /** Buat notifikasi untuk daftar user. Tidak pernah melempar. */
    public int notifyUsers(Collection<String> userIds, Notice n) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        List<String> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    insert(ids, n);
                }
            });
            return ids.size();
        }
        return insert(ids, n);
    }

    private int insert(List<String> ids, Notice n) {
        try {
            JsonNode meta = n.metadata() == null ? ErpJson.object() : ErpJson.tree(n.metadata());
            String metaJson = ErpJson.stringify(meta);
            Integer created = tx.execute(st -> {
                // Kolom timestamp tanpa zona berisi UTC; java.sql.Timestamp akan digeser driver ke zona JVM.
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                int c = 0;
                for (String uid : ids) {
                    c += jdbc.update("INSERT INTO notifications (id, user_id, type, title, message, is_read, metadata, created_at) "
                                    + "VALUES (?, ?, ?, ?, ?, false, CAST(? AS jsonb), ?)",
                            UUID.randomUUID().toString(), uid, n.type(), n.title(), n.message(), metaJson, now);
                }
                return c;
            });
            return created == null ? 0 : created;
        } catch (RuntimeException e) {
            log.error("ERP notifyUsers gagal (diabaikan) type={}: {}", n.type(), e.getMessage());
            return 0;
        }
    }

    /** Notifikasi ke semua user aktif yang memiliki salah satu role (kode role platform). */
    public int notifyRole(Collection<String> roleKeys, Notice n, String excludeUserId) {
        try {
            List<String> ids = activeUserIdsWithRoles(roleKeys);
            ids.removeIf(id -> id.equals(excludeUserId));
            return notifyUsers(ids, n);
        } catch (RuntimeException e) {
            log.error("ERP notifyRole gagal (diabaikan) type={}: {}", n.type(), e.getMessage());
            return 0;
        }
    }

    /** Notifikasi ke super-admin ERP (padanan notifyAdmins / role "Admin"). */
    public int notifyAdmins(Notice n, String excludeUserId) {
        return notifyRole(permissions.superAdminRoleKeys(), n, excludeUserId);
    }

    /** Notifikasi ke semua pemegang capability (termasuk super-admin), mis. "approval.decide". */
    public int notifyCapability(String capability, Notice n, String excludeUserId) {
        return notifyRole(permissions.roleKeysWithCap(capability), n, excludeUserId);
    }

    /** Notifikasi ke semua yang punya izin modul (view/edit). */
    public int notifyModule(String module, String action, Notice n, String excludeUserId) {
        return notifyRole(permissions.roleKeysWithPerm(module, action), n, excludeUserId);
    }

    /** id user ERP aktif yang memiliki salah satu kode role (snapshot kolom users.roles). */
    public List<String> activeUserIdsWithRoles(Collection<String> roleKeys) {
        Set<String> wanted = new HashSet<>(roleKeys == null ? List.of() : roleKeys);
        List<String> out = new ArrayList<>();
        if (wanted.isEmpty()) {
            return out;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, roles, role FROM users WHERE is_active = true");
        for (Map<String, Object> r : rows) {
            Set<String> have = new LinkedHashSet<>();
            Object csv = r.get("roles");
            if (csv != null) {
                have.addAll(Arrays.stream(csv.toString().split(",")).map(String::trim).toList());
            }
            Object primary = r.get("role");
            if (primary != null) {
                have.add(primary.toString());
            }
            if (have.stream().anyMatch(wanted::contains)) {
                out.add(r.get("id").toString());
            }
        }
        return out;
    }

    /**
     * Kunci deduplikasi notifikasi bertipe {@code type} yang pernah dibuat: nilai metadata {@code fields}
     * digabung dengan "|". Untuk pengingat "sekali per keadaan" (mis. id + state + tanggal jatuh tempo).
     */
    public Set<String> sentKeys(String type, String... fields) {
        StringBuilder key = new StringBuilder();
        for (String f : fields) {
            if (!f.matches("[A-Za-z]+")) {
                throw new IllegalArgumentException("Nama field metadata tidak valid: " + f);
            }
            key.append(key.isEmpty() ? "" : " || '|' || ").append("coalesce(metadata->>'").append(f).append("', '')");
        }
        return new HashSet<>(jdbc.queryForList("SELECT DISTINCT " + key + " FROM notifications WHERE type = ?",
                String.class, type));
    }

    /** Gabungan nilai untuk dicocokkan dengan {@link #sentKeys(String, String...)}. */
    public static String key(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            sb.append(i == 0 ? "" : "|").append(values[i] == null ? "" : values[i]);
        }
        return sb.toString();
    }
}
