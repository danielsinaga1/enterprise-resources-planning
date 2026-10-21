package id.enterpriseapps.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;

import id.enterpriseapps.observability.AuthMetrics;
import id.enterpriseapps.property.LoginThrottleProperties;
import id.enterpriseapps.service.RedisService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LoginThrottleService Unit Tests")
class LoginThrottleServiceTest {

    @Mock
    private RedisService redisService;

    @Mock
    private AuthMetrics authMetrics;

    private LoginThrottleProperties props;
    private LoginThrottleService service;

    @BeforeEach
    void setUp() {
        props = new LoginThrottleProperties();
        props.setMaxAttempts(5);
        props.setWindowSeconds(300);
        props.setLockoutSeconds(900);
        service = new LoginThrottleService(props, redisService, authMetrics);
    }

    @Nested
    @DisplayName("Penghitung kegagalan")
    class FailureCounter {

        @Test
        @DisplayName("Kegagalan pertama memasang TTL jendela dan belum mengunci")
        void firstFailure_SetsWindowTtl_NoLock() {
            when(redisService.increment("login:fail:npk:12345")).thenReturn(1L);

            service.recordFailure("12345", "10.0.0.5");

            verify(redisService).expireSeconds("login:fail:npk:12345", 300L);
            verify(redisService, never()).save(eq("login:lock:npk:12345"), anyString(), anyLong());
        }

        @Test
        @DisplayName("Kegagalan di bawah ambang tidak memasang ulang TTL dan tidak mengunci")
        void midFailure_NoTtlReset_NoLock() {
            when(redisService.increment("login:fail:npk:12345")).thenReturn(3L);

            service.recordFailure("12345", "10.0.0.5");

            verify(redisService, never()).expireSeconds(anyString(), anyLong());
            verify(redisService, never()).save(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("Kegagalan ke-5 mengunci 900 detik dan membuang penghitung")
        void thresholdFailure_LocksAndClearsCounter() {
            when(redisService.increment("login:fail:npk:12345")).thenReturn(5L);

            service.recordFailure("12345", "10.0.0.5");

            verify(redisService).save("login:lock:npk:12345", "1", 900_000L);
            verify(redisService).delete("login:fail:npk:12345");
            verify(authMetrics).recordLoginLocked();
        }

        @Test
        @DisplayName("NPK dinormalkan sehingga spasi dan huruf besar tidak jadi celah")
        void npkIsNormalized() {
            when(redisService.increment("login:fail:npk:ab12")).thenReturn(1L);

            service.recordFailure("  AB12 ", null);

            verify(redisService).increment("login:fail:npk:ab12");
        }

        @Test
        @DisplayName("Lapis per-IP diam selama ipEnabled masih false")
        void ipLayerOffByDefault() {
            when(redisService.increment(anyString())).thenReturn(1L);

            service.recordFailure("12345", "10.0.0.5");

            verify(redisService, never()).increment("login:fail:ip:10.0.0.5");
        }
    }

    @Nested
    @DisplayName("Status kunci")
    class LockState {

        @Test
        @DisplayName("Sisa TTL dikembalikan sebagai durasi tunggu")
        void activeLock_ReturnsRemaining() {
            when(redisService.ttlSeconds("login:lock:npk:12345")).thenReturn(420L);

            assertThat(service.activeLock("12345", "10.0.0.5"))
                    .contains(Duration.ofSeconds(420));
        }

        @Test
        @DisplayName("Tanpa kunci, login diteruskan")
        void activeLock_AbsentWhenNoKey() {
            when(redisService.ttlSeconds(anyString())).thenReturn(-2L);

            assertThat(service.activeLock("12345", "10.0.0.5")).isEmpty();
        }

        @Test
        @DisplayName("Login berhasil membersihkan penghitung")
        void success_ClearsCounter() {
            service.recordSuccess("12345", "10.0.0.5");

            verify(redisService).delete("login:fail:npk:12345");
        }

        @Test
        @DisplayName("Saklar mati melewatkan seluruh pemeriksaan")
        void disabled_SkipsEverything() {
            props.setEnabled(false);

            assertThat(service.activeLock("12345", "10.0.0.5")).isEmpty();
            service.recordFailure("12345", "10.0.0.5");

            verify(redisService, never()).increment(anyString());
            verify(redisService, never()).ttlSeconds(anyString());
        }
    }

    @Nested
    @DisplayName("Degradasi saat Redis mati")
    class RedisDown {

        @Test
        @DisplayName("Redis error membuat login lewat (fail-open) tapi metrik degraded naik")
        void redisFailure_FailsOpenAndReportsMetric() {
            when(redisService.ttlSeconds(anyString()))
                    .thenThrow(new org.springframework.dao.QueryTimeoutException("redis mati"));

            assertThat(service.activeLock("12345", "10.0.0.5")).isEmpty();

            verify(authMetrics).recordThrottleDegraded();
        }
    }

    @Nested
    @DisplayName("Resolusi IP klien")
    class ClientIp {

        /**
         * Rantai nyata: browser → nginx → gateway → backend. Bila klien mengirim klaim palsu,
         * nginx menambahkannya di depan, jadi entri pertama tidak boleh dipercaya.
         */
        @Test
        @DisplayName("Entri palsu di depan diabaikan; IP klien diambil dari kanan")
        void spoofedLeadingEntryIgnored() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "1.2.3.4, 192.168.5.77, 192.168.5.105");

            assertThat(service.resolveClientIp(request)).isEqualTo("192.168.5.77");
        }

        @Test
        @DisplayName("Tanpa X-Forwarded-For dipakai remote address langsung")
        void noHeader_FallsBackToRemoteAddr() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.1.2.3");

            assertThat(service.resolveClientIp(request)).isEqualTo("10.1.2.3");
        }

        @Test
        @DisplayName("Rantai lebih pendek dari trustedHopCount tidak melempar")
        void shorterChainThanHops_DoesNotThrow() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "203.0.113.9");

            assertThat(service.resolveClientIp(request)).isEqualTo("203.0.113.9");
        }
    }
}
