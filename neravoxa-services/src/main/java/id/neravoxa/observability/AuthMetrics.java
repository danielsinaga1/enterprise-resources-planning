package id.neravoxa.observability;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Business metrics for authentication.
 *
 * {@code auth.login.attempts} is a counter split by:
 * <ul>
 *   <li>{@code result} — {@code success} | {@code failure}</li>
 *   <li>{@code method} — {@code local} | {@code ldap}</li>
 * </ul>
 * This powers a login success-rate panel and makes credential-stuffing spikes
 * visible (sudden growth in {@code result=failure}).
 */
@Component
public class AuthMetrics {

    public static final String LOGIN_ATTEMPTS = "auth.login.attempts";
    public static final String LOGIN_LOCKED = "auth.login.locked";
    public static final String THROTTLE_DEGRADED = "auth.login.throttle.degraded";

    private final MeterRegistry registry;

    public AuthMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordLogin(String method, boolean success) {
        Counter.builder(LOGIN_ATTEMPTS)
                .description("Login attempts by method and outcome")
                .tag("method", method == null ? "unknown" : method)
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .increment();
    }

    /**
     * Naik tiap kali sebuah NPK/IP menembus ambang kegagalan dan dikunci sementara.
     * Lonjakan di sini adalah tanda serangan tebak-password yang sedang berjalan.
     */
    public void recordLoginLocked() {
        Counter.builder(LOGIN_LOCKED)
                .description("Login lockouts triggered by the brute-force throttle")
                .register(registry)
                .increment();
    }

    /**
     * Naik saat throttle login gagal memakai Redis dan login diteruskan tanpa pembatas.
     * Nilai bukan-nol berarti proteksi brute-force sedang mati — pasang alert di sini,
     * jangan biarkan degradasi ini lewat tanpa suara.
     */
    public void recordThrottleDegraded() {
        Counter.builder(THROTTLE_DEGRADED)
                .description("Login throttle checks that fell open because Redis was unreachable")
                .register(registry)
                .increment();
    }
}
