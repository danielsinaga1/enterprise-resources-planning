package id.neravoxa.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.notification.ErpNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kotak notifikasi per pengguna (daftar, jenis, tandai dibaca) + pemeriksaan terjadwal manual & job harian. */
class NotificationFlowTest extends AdminTestSupport {

    @Autowired
    ErpNotificationService notifications;
    @Autowired
    ErpNotificationDailyJob dailyJob;

    @Test
    void inbox_lists_filters_and_marks_read() throws Exception {
        String u = uid();
        String email = "inbox." + u + "@test.local";
        String userId = login(email, "ERP_ACCOUNTANT");
        String otherId = login("inbox2." + u + "@test.local", "ERP_ACCOUNTANT");
        notifications.notifyUsers(List.of(userId), new ErpNotificationService.Notice("adm_test_a", "Alpha " + u, "pesan satu", Map.of("k", 1)));
        notifications.notifyUsers(List.of(userId), new ErpNotificationService.Notice("adm_test_a", "Beta", "pesan khusus-" + u, null));
        notifications.notifyUsers(List.of(userId), new ErpNotificationService.Notice("adm_test_b", "Gamma", "pesan tiga", null));
        notifications.notifyUsers(List.of(otherId), new ErpNotificationService.Notice("adm_test_a", "Milik orang lain", "x", null));
        String otherNotif = jdbc.queryForObject("SELECT id FROM notifications WHERE user_id = ?", String.class, otherId);

        asUser(email, "ERP_ACCOUNTANT");
        JsonNode bell = ok(get("/api/erp/notifications"));
        assertEquals(3, bell.get("data").size());
        assertEquals(3, bell.get("unreadCount").asInt());
        assertFalse(bell.has("total"));
        JsonNode first = bell.get("data").get(0);
        assertEquals(userId, first.get("userId").asText());
        assertFalse(first.get("isRead").asBoolean());
        assertFalse(first.has("user"));

        JsonNode page = ok(get("/api/erp/notifications?page=1&limit=2"));
        assertEquals(2, page.get("data").size());
        assertEquals(3, page.get("total").asInt());
        assertEquals(1, page.get("page").asInt());
        assertEquals(2, page.get("limit").asInt());
        assertEquals(3, page.get("unreadCount").asInt());
        assertEquals(2, ok(get("/api/erp/notifications?type=adm_test_a")).get("total").asInt());
        JsonNode search = ok(get("/api/erp/notifications?search=KHUSUS-" + u));
        assertEquals(1, search.get("total").asInt());
        assertEquals("Beta", search.get("data").get(0).get("title").asText());
        assertEquals(1, ok(get("/api/erp/notifications?search=alpha")).get("total").asInt());
        assertEquals(0, ok(get("/api/erp/notifications?search=%25%25")).get("total").asInt());
        assertEquals("Filter status baca tidak valid.", call(get("/api/erp/notifications?isRead=maybe")).error());

        JsonNode types = ok(get("/api/erp/notifications/types")).get("data");
        assertEquals(2, types.size());
        assertEquals("adm_test_a", types.get(0).get("type").asText());
        assertEquals(2, types.get(0).get("count").asInt());
        assertEquals("adm_test_b", types.get(1).get("type").asText());

        String alphaId = jdbc.queryForObject("SELECT id FROM notifications WHERE user_id = ? AND title = ?", String.class,
                userId, "Alpha " + u);
        assertEquals("Notifikasi ditandai sudah dibaca.", ok(patch("/api/erp/notifications/" + alphaId + "/read", map()))
                .get("message").asText());
        assertEquals(2, ok(get("/api/erp/notifications/unread-count")).get("count").asInt());
        assertEquals(1, ok(get("/api/erp/notifications?isRead=true")).get("total").asInt());
        assertEquals(2, ok(get("/api/erp/notifications?isRead=false")).get("total").asInt());
        Result foreign = call(patch("/api/erp/notifications/" + otherNotif + "/read", map()));
        assertEquals(404, foreign.status());
        assertEquals("Notifikasi tidak ditemukan.", foreign.error());
        assertEquals(404, call(patch("/api/erp/notifications/" + UUID.randomUUID() + "/read", map())).status());

        assertEquals("Semua notifikasi ditandai sudah dibaca.", ok(patch("/api/erp/notifications/read-all", map()))
                .get("message").asText());
        assertEquals(0, ok(get("/api/erp/notifications/unread-count")).get("count").asInt());
        assertFalse(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id = ?", Boolean.class, otherNotif));
    }

    @Test
    void check_runs_domain_checks_for_finance_recipients_once_per_day() throws Exception {
        String adminId = loginAdmin();
        String u = uid();
        String accountantId = login("fin." + u + "@test.local", "ERP_ACCOUNTANT");
        String viewerId = login("view." + u + "@test.local", "ERP_VIEWER");
        String itemId = UUID.randomUUID().toString();
        String code = "LOW-" + u;
        jdbc.update("INSERT INTO inventory_items (id, code, name, unit, current_stock, minimum_stock, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Karung Uji', 'pcs', 2, 50, true, now(), now())", itemId, code);

        asUser("fin." + u + "@test.local", "ERP_ACCOUNTANT");
        Result denied = call(post("/api/erp/notifications/check", map()));
        assertEquals(403, denied.status());
        assertEquals(DENIED, denied.error());

        loginAdmin();
        JsonNode res = ok(post("/api/erp/notifications/check", map()));
        int created = res.get("created").asInt();
        assertTrue(created >= 2, res.toString());
        assertEquals("Pengecekan selesai. " + created + " notifikasi baru dibuat.", res.get("message").asText());
        assertEquals(1, lowStock(adminId, itemId).size());
        assertEquals(1, lowStock(accountantId, itemId).size());
        assertEquals(0, lowStock(viewerId, itemId).size());
        assertEquals("Stok Karung Uji (" + code + ") tersisa 2 pcs, di bawah minimum 50 pcs.", lowStock(adminId, itemId).get(0).get("message"));
        assertTrue(ok(get("/api/erp/notifications?type=low_stock")).get("total").asInt() >= 1);

        // Deduplikasi 24 jam: pemeriksaan ulang (manual maupun job harian) tidak menggandakan.
        ok(post("/api/erp/notifications/check", map()));
        dailyJob.runDaily();
        assertEquals(1, lowStock(adminId, itemId).size());
        assertEquals(1, lowStock(accountantId, itemId).size());
    }

    private List<Map<String, Object>> lowStock(String userId, String itemId) {
        return jdbc.queryForList("SELECT message FROM notifications WHERE user_id = ? AND type = 'low_stock' "
                + "AND metadata ->> 'itemId' = ?", userId, itemId);
    }
}
