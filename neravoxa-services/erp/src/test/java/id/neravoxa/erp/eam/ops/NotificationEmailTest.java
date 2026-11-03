package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.notification.ErpEmailProperties;
import id.neravoxa.erp.core.notification.ErpMailTransport;
import id.neravoxa.erp.core.notification.ErpNotificationMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Salinan email notifikasi: satu notifikasi → subjek = judul, beberapa → digest, preferensi per pengguna,
 * dan email uji. SMTP diganti transport tiruan; antrean di-flush manual (digest 1 jam agar tidak berjalan sendiri).
 */
@TestPropertySource(properties = {
        "app.erp.email.enabled=true",
        "app.erp.email.from=Neravoxa <erp@test.local>",
        "app.erp.email.digest-seconds=3600",
        "app.erp.email.smtp.host=localhost",
        "app.base-url=https://erp.test.local"})
@Import(NotificationEmailTest.CaptureConfig.class)
class NotificationEmailTest extends GaOpsTestSupport {

    record Sent(String to, String subject, String text, String html) {
    }

    static class CapturingTransport extends ErpMailTransport {
        final List<Sent> sent = new CopyOnWriteArrayList<>();

        CapturingTransport(ErpEmailProperties props) {
            super(props);
        }

        @Override
        public void send(String to, String subject, String text, String html) {
            sent.add(new Sent(to, subject, text, html));
        }
    }

    @TestConfiguration
    static class CaptureConfig {
        @Bean
        @Primary
        CapturingTransport capturingTransport(ErpEmailProperties props) {
            return new CapturingTransport(props);
        }
    }

    @Autowired
    CapturingTransport transport;

    @Autowired
    ErpNotificationMailer mailer;

    @BeforeEach
    void clear() {
        mailer.flush();
        transport.sent.clear();
    }

    private List<Sent> sentTo(String email) {
        return transport.sent.stream().filter(s -> s.to().equalsIgnoreCase(email)).toList();
    }

    private String workOrder(String assetId, String title) throws Exception {
        return ok(post(MT + "/work-orders", body("assetId", assetId, "type", "CORRECTIVE", "title", title))).get("id").asText();
    }

    @Test
    void notifications_are_emailed_digested_and_respect_preferences() throws Exception {
        adminId();
        String techRole = customRole(Map.of("ga_maintenance", "edit"));
        String techEmail = email("teknisi");
        String tech = provision(techEmail, techRole);
        asAdmin();
        String t = tag();
        String asset = asset(t + "E", "MESIN_PRODUKSI");

        // Satu notifikasi → subjek = judul, isi memuat pesan & tautan.
        ok(patch(MT + "/work-orders/" + workOrder(asset, "Ganti seal " + t) + "/assign", body("assignedToId", tech)));
        assertEquals(1, mailer.flush());
        List<Sent> one = sentTo(techEmail);
        assertEquals(1, one.size());
        assertEquals("[Neravoxa] Work Order Ditugaskan", one.get(0).subject());
        assertTrue(one.get(0).text().contains("Ganti seal " + t), one.get(0).text());
        assertTrue(one.get(0).html().contains("https://erp.test.local/dashboard/erp/notifications"));

        // Dua notifikasi dalam jeda yang sama → satu email digest.
        transport.sent.clear();
        ok(patch(MT + "/work-orders/" + workOrder(asset, "Cek bearing " + t) + "/assign", body("assignedToId", tech)));
        ok(patch(MT + "/work-orders/" + workOrder(asset, "Cek belt <b>" + t + "</b>") + "/assign", body("assignedToId", tech)));
        mailer.flush();
        List<Sent> digest = sentTo(techEmail);
        assertEquals(1, digest.size());
        assertEquals("[Neravoxa] 2 notifikasi baru", digest.get(0).subject());
        assertTrue(digest.get(0).html().contains("&lt;b&gt;"), "judul/pesan di-escape di HTML");

        // Preferensi: teknisi mematikan email → tidak ada email lagi, notifikasi in-app tetap ada.
        asUser(techEmail, techRole);
        JsonNode prefs = ok(get("/api/erp/notifications/preferences"));
        assertTrue(prefs.get("emailAvailable").asBoolean());
        assertTrue(prefs.get("emailEnabled").asBoolean());
        assertEquals(techEmail, prefs.get("email").asText());
        assertFalse(ok(put("/api/erp/notifications/preferences", body("emailEnabled", false))).get("emailEnabled").asBoolean());
        expectError(400, "emailEnabled wajib berupa true/false.", put("/api/erp/notifications/preferences", body("emailEnabled", "ya")));

        asAdmin();
        transport.sent.clear();
        String quiet = workOrder(asset, "Tanpa email " + t);
        ok(patch(MT + "/work-orders/" + quiet + "/assign", body("assignedToId", tech)));
        mailer.flush();
        assertEquals(0, sentTo(techEmail).size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'wo_assigned' "
                + "AND metadata->>'woId' = ?", Integer.class, tech, quiet));

        // Email uji langsung ke diri sendiri.
        transport.sent.clear();
        JsonNode test = ok(post("/api/erp/notifications/preferences/test-email", Map.of()));
        assertEquals("erp.admin@test.local", test.get("email").asText());
        assertEquals("[Neravoxa] Email uji Neravoxa", sentTo("erp.admin@test.local").get(0).subject());
    }
}
