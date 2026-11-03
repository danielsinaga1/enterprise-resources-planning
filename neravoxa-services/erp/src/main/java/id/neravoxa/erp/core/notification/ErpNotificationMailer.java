package id.neravoxa.erp.core.notification;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Salinan email untuk notifikasi in-app. Notifikasi yang dibuat dalam jeda {@code digest-seconds} dikumpulkan
 * per pengguna lalu dikirim sebagai satu email (pemeriksaan harian tidak membanjiri inbox). Hanya ke pengguna
 * aktif yang punya alamat email dan tidak mematikan email di preferensinya. Kegagalan SMTP hanya dicatat —
 * notifikasi in-app tetap tersimpan.
 */
@Slf4j
@Component
public class ErpNotificationMailer {

    static final int MAX_ITEMS = 20;

    private final JdbcTemplate jdbc;
    private final ErpEmailProperties props;
    private final ErpMailTransport transport;
    private final String baseUrl;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "erp-notification-mailer");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, List<ErpNotificationService.Notice>> pending = new LinkedHashMap<>();
    private ScheduledFuture<?> scheduled;

    public ErpNotificationMailer(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc, ErpEmailProperties props,
                                 ErpMailTransport transport, @Value("${app.base-url:}") String baseUrl) {
        this.jdbc = jdbc;
        this.props = props;
        this.transport = transport;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    /** Antrekan notifikasi untuk email (dipanggil setelah notifikasi in-app tersimpan). Tidak pernah melempar. */
    public void enqueue(Collection<String> userIds, ErpNotificationService.Notice n) {
        if (!props.configured() || userIds == null || userIds.isEmpty()) {
            return;
        }
        synchronized (pending) {
            for (String id : userIds) {
                pending.computeIfAbsent(id, k -> new ArrayList<>()).add(n);
            }
            if (scheduled == null || scheduled.isDone()) {
                scheduled = scheduler.schedule(this::flushSafely, Math.max(0, props.getDigestSeconds()), TimeUnit.SECONDS);
            }
        }
    }

    private void flushSafely() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.error("Email notifikasi gagal diproses (diabaikan): {}", e.getMessage());
        }
    }

    /** Kirim semua antrean sekarang; kembalikan jumlah email terkirim. */
    public int flush() {
        Map<String, List<ErpNotificationService.Notice>> batch;
        synchronized (pending) {
            batch = new LinkedHashMap<>(pending);
            pending.clear();
            scheduled = null;
        }
        int sent = 0;
        for (Map.Entry<String, List<ErpNotificationService.Notice>> e : batch.entrySet()) {
            String to = recipient(e.getKey());
            if (to == null) {
                continue;
            }
            try {
                Mail mail = compose(e.getValue(), baseUrl);
                transport.send(to, mail.subject(), mail.text(), mail.html());
                sent++;
            } catch (Exception ex) {
                log.warn("Email notifikasi ke user {} gagal: {}", e.getKey(), ex.getMessage());
            }
        }
        return sent;
    }

    /** Alamat email user aktif yang tidak mematikan email, atau null. */
    private String recipient(String userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT u.email, coalesce(p.email_enabled, TRUE) AS enabled "
                + "FROM users u LEFT JOIN notification_preferences p ON p.user_id = u.id WHERE u.id = ? AND u.is_active = TRUE", userId);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0).get("enabled"))) {
            return null;
        }
        Object email = rows.get(0).get("email");
        return email == null || email.toString().isBlank() || !email.toString().contains("@") ? null : email.toString().trim();
    }

    record Mail(String subject, String text, String html) {
    }

    /** Satu notifikasi → judulnya jadi subjek; beberapa → digest "N notifikasi baru". */
    static Mail compose(List<ErpNotificationService.Notice> notices, String baseUrl) {
        String subject = notices.size() == 1
                ? "[Neravoxa] " + notices.get(0).title()
                : "[Neravoxa] " + notices.size() + " notifikasi baru";
        String link = baseUrl.isEmpty() ? null : baseUrl + "/dashboard/erp/notifications";
        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder("<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#1f2937\">");
        html.append("<p style=\"margin:0 0 12px\">").append(notices.size() == 1 ? "Ada notifikasi baru di Neravoxa:" : "Ada "
                + notices.size() + " notifikasi baru di Neravoxa:").append("</p><ul style=\"padding-left:18px;margin:0 0 16px\">");
        int shown = Math.min(notices.size(), MAX_ITEMS);
        for (int i = 0; i < shown; i++) {
            ErpNotificationService.Notice n = notices.get(i);
            text.append("• ").append(n.title()).append("\n  ").append(n.message()).append("\n\n");
            html.append("<li style=\"margin-bottom:10px\"><strong>").append(HtmlUtils.htmlEscape(n.title())).append("</strong><br>")
                    .append(HtmlUtils.htmlEscape(n.message())).append("</li>");
        }
        if (notices.size() > shown) {
            text.append("… dan ").append(notices.size() - shown).append(" notifikasi lainnya.\n\n");
            html.append("<li>… dan ").append(notices.size() - shown).append(" notifikasi lainnya.</li>");
        }
        html.append("</ul>");
        if (link != null) {
            text.append("Buka di Neravoxa: ").append(link).append("\n\n");
            html.append("<p style=\"margin:0 0 16px\"><a href=\"").append(HtmlUtils.htmlEscape(link))
                    .append("\" style=\"color:#0f766e\">Buka notifikasi di Neravoxa</a></p>");
        }
        String footer = "Email ini dikirim otomatis. Matikan salinan email di halaman Notifikasi Neravoxa.";
        text.append(footer);
        html.append("<p style=\"margin:0;font-size:12px;color:#6b7280\">").append(footer).append("</p></div>");
        return new Mail(subject, text.toString(), html.toString());
    }

    // ───────────────────────────── preferensi ─────────────────────────────

    public Map<String, Object> preferences(String userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT u.email, coalesce(p.email_enabled, TRUE) AS enabled "
                + "FROM users u LEFT JOIN notification_preferences p ON p.user_id = u.id WHERE u.id = ?", userId);
        Object email = rows.isEmpty() ? null : rows.get(0).get("email");
        boolean enabled = rows.isEmpty() || Boolean.TRUE.equals(rows.get(0).get("enabled"));
        return ErpJson.map("emailAvailable", props.configured(), "emailEnabled", enabled, "email", email);
    }

    public Map<String, Object> setEmailEnabled(String userId, boolean enabled) {
        jdbc.update("INSERT INTO notification_preferences (user_id, email_enabled, updated_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (user_id) DO UPDATE SET email_enabled = EXCLUDED.email_enabled, updated_at = EXCLUDED.updated_at",
                userId, enabled, LocalDateTime.now(ZoneOffset.UTC));
        return preferences(userId);
    }

    /** Kirim email uji ke diri sendiri secara langsung (untuk memeriksa setelan SMTP). */
    public Map<String, Object> sendTest(String userId) {
        if (!props.configured()) {
            throw ErpException.business("Email notifikasi belum diaktifkan administrator (ERP_EMAIL_ENABLED, MAIL_HOST, ERP_EMAIL_FROM).");
        }
        Object email = preferences(userId).get("email");
        if (email == null || !email.toString().contains("@")) {
            throw ErpException.business("Akun Anda belum punya alamat email.");
        }
        Mail mail = compose(List.of(new ErpNotificationService.Notice("system", "Email uji Neravoxa",
                "Setelan email notifikasi sudah benar: email ini terkirim dari server Neravoxa.", null)), baseUrl);
        try {
            transport.send(email.toString().trim(), mail.subject(), mail.text(), mail.html());
        } catch (Exception e) {
            log.warn("Email uji gagal: {}", e.getMessage());
            throw ErpException.of(502, "Email uji gagal dikirim: " + e.getMessage());
        }
        return ErpJson.map("message", "Email uji dikirim ke " + email + ".", "email", email);
    }

    @PreDestroy
    void shutdown() {
        flushSafely();
        scheduler.shutdownNow();
    }
}
