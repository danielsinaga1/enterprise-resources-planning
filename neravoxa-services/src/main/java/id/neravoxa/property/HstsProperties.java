package id.neravoxa.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Header {@code Strict-Transport-Security}.
 *
 * <p>Kenapa perlu bisa diatur: HSTS adalah instruksi SEKALI-KIRIM yang menempel di browser
 * tiap pengguna selama {@link #maxAgeSeconds}, dan <b>tidak bisa dibatalkan dari sisi server</b>.
 * Bila diaktifkan untuk sebuah NAMA HOST yang memakai sertifikat self-signed, Chrome mengubah
 * peringatan sertifikat menjadi blokir yang tidak bisa dilewati — tombol "Proceed anyway"
 * hilang — dan satu-satunya pemulihan adalah membersihkan HSTS di setiap browser satu per satu.
 * Karena server dev KPI ada di LAN tanpa DNS publik (Let's Encrypt HTTP-01 tidak bisa dipakai),
 * skenario itu sangat mungkin terjadi. Jadi saklarnya harus ada SEBELUM TLS dinyalakan,
 * bukan sesudah.
 *
 * <p>Pasang TLS di reverse proxy (nginx) lebih dulu sebelum menyalakan HSTS.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.security.hsts")
public class HstsProperties {

    /** Kirim header HSTS. Matikan sementara bila memasang TLS dengan sertifikat self-signed. */
    private boolean enabled = true;

    /** Masa berlaku instruksi HSTS di browser. Default 1 tahun. */
    private long maxAgeSeconds = 31536000;

    private boolean includeSubDomains = true;

    /**
     * Menandai domain untuk daftar HSTS preload browser. Default {@code false}: preload hanya
     * berlaku untuk domain publik yang terdaftar, jadi pada host internal seperti
     * domain internal (mis. {@code *.kpi.local}) penanda ini tidak pernah bisa dipenuhi — ia hanya menambah
     * kekakuan tanpa manfaat. Nyalakan hanya bila aplikasi benar-benar tayang di domain publik.
     */
    private boolean preload = false;

    /**
     * Kirim HSTS juga pada koneksi non-HTTPS.
     *
     * <p>Default {@code false}, yaitu perilaku baku Spring Security (hanya request aman).
     * Sebelumnya kode memaksa {@code AnyRequestMatcher.INSTANCE} sehingga header dikirim pada
     * SEMUA request termasuk HTTP biasa. Itu tidak menambah keamanan apa pun: RFC 6797 §7.2
     * mewajibkan browser MENGABAIKAN header HSTS yang datang lewat transport tidak aman, jadi
     * selama aplikasi masih di port 80 header itu tidak pernah berefek. Yang tersisa hanyalah
     * jebakan: ia langsung aktif begitu TLS dinyalakan, bahkan sebelum sertifikatnya tepercaya.
     */
    private boolean allRequests = false;
}
