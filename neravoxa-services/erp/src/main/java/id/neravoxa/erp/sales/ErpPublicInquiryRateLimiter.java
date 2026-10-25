package id.neravoxa.erp.sales;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pembatas laju form inquiry publik — port {@code express-rate-limit} di routes/public.ts
 * (5 permintaan per IP per jam, jendela tetap per klien seperti MemoryStore; semua permintaan
 * dihitung, termasuk yang gagal validasi). Penyimpanan in-memory per instance.
 *
 * <p>IP klien dibaca dari {@code X-Forwarded-For} dihitung dari kanan dengan melewati proxy
 * tepercaya (padanan {@code app.set('trust proxy', 1)} di ENTARIS dan {@code resolveClientIp}
 * platform): entri terdepan header bisa dipalsukan klien, entri dari kanan ditambahkan proxy kita.
 */
@Component
public class ErpPublicInquiryRateLimiter {

    static final int MAX = 5;
    static final Duration WINDOW = Duration.ofHours(1);

    /** Hasil satu hit: jumlah dalam jendela + detik sampai jendela reset. */
    public record Hit(int count, long resetSeconds) {
        public boolean blocked() {
            return count > MAX;
        }

        public int remaining() {
            return Math.max(0, MAX - count);
        }
    }

    private record Window(int count, long resetAtMillis) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int trustedHopCount;

    public ErpPublicInquiryRateLimiter(@Value("${app.security.login-throttle.trusted-hop-count:1}") int trustedHopCount) {
        this.trustedHopCount = trustedHopCount;
    }

    /** Catat satu permintaan dari {@code key} (IP). */
    public Hit hit(String key) {
        long now = System.currentTimeMillis();
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().resetAtMillis() <= now);
        }
        Window w = windows.compute(key, (k, cur) -> cur == null || cur.resetAtMillis() <= now
                ? new Window(1, now + WINDOW.toMillis())
                : new Window(cur.count() + 1, cur.resetAtMillis()));
        long reset = Math.max(0, (w.resetAtMillis() - now + 999) / 1000);
        return new Hit(w.count(), reset);
    }

    /** IP klien (padanan {@code req.ip} dengan trust proxy). */
    public String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] hops = header.split(",");
        int index = hops.length - 1 - trustedHopCount;
        String resolved = index >= 0 ? hops[index].trim() : hops[0].trim();
        return resolved.isEmpty() ? request.getRemoteAddr() : resolved;
    }
}
