package id.enterpriseapps.service.auth;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Service;

import id.enterpriseapps.observability.AuthMetrics;
import id.enterpriseapps.property.LoginThrottleProperties;
import id.enterpriseapps.service.RedisService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Penjaga brute-force untuk endpoint login. Menghitung kegagalan di Redis lalu mengunci
 * NPK sementara begitu ambang terlampaui.
 *
 * <p>Pemeriksaan dilakukan <b>sebelum</b> kredensial diteruskan ke Active Directory. Itu bukan
 * kebetulan: AD punya kebijakan lockout sendiri, jadi tanpa penjaga ini serangan tebak-password
 * akan mengunci akun domain korban — yang ikut memblokir login Windows mereka, bukan hanya IRMS.
 *
 * <p>Bila Redis tidak bisa dihubungi, penjaga ini <b>membiarkan login lewat</b> (fail-open) dan
 * mencatat WARN + metrik. Fail-closed terdengar lebih aman tapi artinya satu gangguan Redis
 * memutus akses semua pengguna ke aplikasi; risiko itu lebih besar daripada jendela brute-force
 * selama gangguan. Metrik {@code auth.login.throttle.degraded} ada supaya kondisi ini berbunyi
 * di Grafana, tidak diam-diam seperti jebakan fail-soft Avantis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginThrottleService {

    private static final String FAIL_NPK = "login:fail:npk:";
    private static final String LOCK_NPK = "login:lock:npk:";
    private static final String FAIL_IP = "login:fail:ip:";
    private static final String LOCK_IP = "login:lock:ip:";

    private final LoginThrottleProperties props;
    private final RedisService redisService;
    private final AuthMetrics authMetrics;

    /**
     * Sisa waktu kunci bila NPK (atau IP, bila lapis itu aktif) sedang terkunci.
     * {@link Optional#empty()} berarti login boleh diteruskan.
     */
    public Optional<Duration> activeLock(String npk, String clientIp) {
        if (!props.isEnabled()) {
            return Optional.empty();
        }
        try {
            Optional<Duration> byNpk = remainingLock(LOCK_NPK + normalizeNpk(npk));
            if (byNpk.isPresent()) {
                return byNpk;
            }
            if (props.isIpEnabled() && clientIp != null) {
                return remainingLock(LOCK_IP + clientIp);
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            degraded("cek kunci", e);
            return Optional.empty();
        }
    }

    /** Catat satu kegagalan login; memasang kunci sementara begitu ambang terlampaui. */
    public void recordFailure(String npk, String clientIp) {
        if (!props.isEnabled()) {
            return;
        }
        try {
            bump(FAIL_NPK + normalizeNpk(npk), LOCK_NPK + normalizeNpk(npk), props.getMaxAttempts());
            if (props.isIpEnabled() && clientIp != null) {
                bump(FAIL_IP + clientIp, LOCK_IP + clientIp, props.getIpMaxAttempts());
            }
        } catch (RuntimeException e) {
            degraded("catat kegagalan", e);
        }
    }

    /** Bersihkan penghitung setelah login berhasil supaya salah ketik sesekali tidak menumpuk. */
    public void recordSuccess(String npk, String clientIp) {
        if (!props.isEnabled()) {
            return;
        }
        try {
            redisService.delete(FAIL_NPK + normalizeNpk(npk));
            if (props.isIpEnabled() && clientIp != null) {
                redisService.delete(FAIL_IP + clientIp);
            }
        } catch (RuntimeException e) {
            degraded("bersihkan penghitung", e);
        }
    }

    /**
     * IP klien nyata dari {@code X-Forwarded-For}, dihitung <b>dari kanan</b> dengan melewati
     * {@code trustedHopCount} proxy tepercaya.
     *
     * <p>Sengaja tidak memakai entri pertama: nginx menambahkan klaim klien lewat
     * {@code $proxy_add_x_forwarded_for}, jadi entri terdepan bisa berisi apa pun yang dikirim
     * penyerang. Entri dari kanan ditambahkan oleh proxy kita sendiri, jadi tidak bisa dipalsukan.
     */
    public String resolveClientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isBlank()) {
            // Backend diakses langsung (dev di :8082, atau probe internal) — tidak ada proxy.
            return request.getRemoteAddr();
        }
        String[] hops = header.split(",");
        int index = hops.length - 1 - props.getTrustedHopCount();
        String resolved = index >= 0 ? hops[index].trim() : hops[0].trim();
        if (resolved.isEmpty()) {
            resolved = request.getRemoteAddr();
        }
        log.debug("IP klien login: {} (X-Forwarded-For='{}', trustedHopCount={})",
                resolved, header, props.getTrustedHopCount());
        return resolved;
    }

    private void bump(String counterKey, String lockKey, int threshold) {
        long attempts = redisService.increment(counterKey);
        if (attempts == 1L) {
            redisService.expireSeconds(counterKey, props.getWindowSeconds());
        }
        if (attempts >= threshold) {
            redisService.save(lockKey, "1", props.getLockoutSeconds() * 1000L);
            redisService.delete(counterKey);
            log.warn("Login dikunci sementara selama {} detik setelah {} kegagalan ({})",
                    props.getLockoutSeconds(), attempts, lockKey);
            authMetrics.recordLoginLocked();
        }
    }

    private Optional<Duration> remainingLock(String lockKey) {
        long ttl = redisService.ttlSeconds(lockKey);
        if (ttl > 0) {
            return Optional.of(Duration.ofSeconds(ttl));
        }
        // ttl == -1: kunci ada tapi tanpa TTL (tidak seharusnya terjadi). Anggap terkunci
        // selama sisa lockout penuh daripada membiarkannya lewat tanpa batas.
        if (ttl == -1L) {
            return Optional.of(Duration.ofSeconds(props.getLockoutSeconds()));
        }
        return Optional.empty();
    }

    private String normalizeNpk(String npk) {
        return npk == null ? "-" : npk.trim().toLowerCase();
    }

    private void degraded(String action, RuntimeException e) {
        log.warn("Throttle login tidak aktif ({} gagal): {} — login diteruskan tanpa pembatas",
                action, e.getMessage());
        authMetrics.recordThrottleDegraded();
    }
}
