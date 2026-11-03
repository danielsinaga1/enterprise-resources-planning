package id.neravoxa.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.notification.ErpNotificationMailer;
import id.neravoxa.erp.core.security.ErpAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Notifikasi in-app — port {@code routes/notifications.ts}. Per pengguna (cukup login; prefix OPEN di
 * ErpRouteGuard); {@code POST /check} butuh izin modul {@code audit} (requirePerm → edit untuk POST).
 */
@RestController
@RequestMapping("/api/erp/notifications")
@RequiredArgsConstructor
public class ErpNotificationController {

    private final ErpAccess access;
    private final ErpNotificationInboxService inbox;
    private final ErpNotificationCheckRunner checks;
    private final ErpNotificationMailer mailer;

    @GetMapping
    public Object list(@RequestParam Map<String, String> query) {
        return inbox.list(access.userId(), query);
    }

    @GetMapping("/types")
    public Object types() {
        return inbox.types(access.userId());
    }

    @GetMapping("/unread-count")
    public Object unreadCount() {
        return ErpJson.map("count", inbox.unread(access.userId()));
    }

    @PatchMapping("/{id}/read")
    public Object markRead(@PathVariable String id) {
        inbox.markRead(id, access.userId());
        return ErpJson.map("message", "Notifikasi ditandai sudah dibaca.");
    }

    @PatchMapping("/read-all")
    public Object markAllRead() {
        inbox.markAllRead(access.userId());
        return ErpJson.map("message", "Semua notifikasi ditandai sudah dibaca.");
    }

    /** Preferensi notifikasi pengguna: salinan email (tersedia bila diaktifkan administrator). */
    @GetMapping("/preferences")
    public Object preferences() {
        return mailer.preferences(access.userId());
    }

    @PutMapping("/preferences")
    public Object updatePreferences(@RequestBody(required = false) JsonNode body) {
        JsonNode v = body == null ? null : body.get("emailEnabled");
        if (v == null || !v.isBoolean()) {
            throw ErpException.badRequest("emailEnabled wajib berupa true/false.");
        }
        return mailer.setEmailEnabled(access.userId(), v.booleanValue());
    }

    /** Kirim email uji ke alamat pengguna sendiri (memeriksa setelan SMTP). */
    @PostMapping("/preferences/test-email")
    public Object testEmail() {
        return mailer.sendTest(access.userId());
    }

    /** Jalankan pemeriksaan notifikasi manual (logika sama dengan job harian). */
    @PostMapping("/check")
    public Object check() {
        access.requireCan("audit", "edit");
        int created = checks.run();
        return ErpJson.map("message", "Pengecekan selesai. " + created + " notifikasi baru dibuat.", "created", created);
    }
}
