package id.enterpriseapps.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Pembatas percobaan login — proteksi brute-force / credential stuffing pada
 * {@code POST /api/v1/auth/login}, satu-satunya endpoint autentikasi yang terbuka publik.
 *
 * <p>Penghitung utama adalah <b>per-NPK</b>, bukan per-IP. Alasannya topologi:
 * browser → nginx → gateway → backend. nginx memakai {@code $proxy_add_x_forwarded_for},
 * yang <em>menambahkan</em> klaim {@code X-Forwarded-For} milik klien, sehingga entri
 * terdepan header itu dikendalikan penyerang — throttle per-IP yang membaca entri pertama
 * bisa dilewati hanya dengan memutar nilai palsu. NPK justru objek yang dibrute-force,
 * jadi penghitung per-NPK tidak bisa dihindari penyerang.
 *
 * <p>Throttle per-IP tersedia sebagai lapis tambahan (menangkap penyemprotan password ke
 * banyak NPK sekaligus) tapi <b>mati secara default</b>: kalau {@link #trustedHopCount}
 * salah, seluruh permintaan akan terlihat berasal dari satu IP (gateway) dan penghitungnya
 * akan mengunci semua pengguna sekaligus. Nyalakan hanya setelah IP klien yang terbaca
 * diverifikasi di server (log DEBUG {@code LoginThrottleService}).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.security.login-throttle")
public class LoginThrottleProperties {

    /** Saklar utama. Bila false, login tidak dibatasi sama sekali. */
    private boolean enabled = true;

    /** Jumlah kegagalan per NPK dalam {@link #windowSeconds} sebelum akun dikunci sementara. */
    private int maxAttempts = 5;

    /** Rentang hitung kegagalan (detik). Penghitung kedaluwarsa sendiri setelah ini. */
    private long windowSeconds = 300;

    /**
     * Lama kunci sementara (detik) setelah ambang terlampaui. Kunci ini sengaja
     * kedaluwarsa sendiri, bukan permanen: kunci permanen membuat penyerang bisa
     * mematikan akun orang lain hanya dengan menebak salah berkali-kali.
     */
    private long lockoutSeconds = 900;

    /** Lapis tambahan per-IP. Baca catatan kelas sebelum menyalakan. */
    private boolean ipEnabled = false;

    /** Ambang kegagalan per IP; jauh lebih longgar karena satu NAT kantor dipakai banyak orang. */
    private int ipMaxAttempts = 30;

    /**
     * Banyak proxy tepercaya yang menambahkan dirinya ke ujung {@code X-Forwarded-For}
     * sebelum permintaan sampai ke backend. Pada rantai nginx → gateway → backend nilainya 1
     * (gateway menambahkan IP nginx), sehingga IP klien nyata adalah entri ke-2 dari kanan.
     */
    private int trustedHopCount = 1;
}
