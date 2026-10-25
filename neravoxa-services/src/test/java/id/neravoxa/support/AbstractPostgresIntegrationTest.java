package id.neravoxa.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Basis untuk test yang memuat Spring context.
 *
 * Masalah yang dipecahkan: `application.yaml` menetapkan `spring.profiles.active: dev`,
 * jadi test ikut memakai profil dev yang menunjuk PostgreSQL di `localhost:5432` tanpa
 * password. Di mesin tanpa Postgres lokal, context gagal dimuat dengan
 * "SCRAM ... no password was provided" dan SEMUA test @SpringBootTest ikut merah —
 * kegagalan yang terlihat seperti bug aplikasi padahal murni soal lingkungan.
 *
 * Kenapa container kosong sudah cukup untuk db_auth: EntityManagerFactory auth meng-hardcode
 * `hibernate.hbm2ddl.auto = none`, dan test controller mem-mock service-nya lewat @MockitoBean
 * sehingga tidak ada query auth yang benar-benar jalan; Hibernate hanya butuh koneksi JDBC yang
 * hidup untuk mendeteksi dialect. Modul ERP memasang skema & seed db_erp sendiri saat boot
 * (`app.erp.auto-init`), jadi container yang sama juga cukup untuk db_erp.
 */
public abstract class AbstractPostgresIntegrationTest {

    /**
     * Datasource auth dan ERP diarahkan ke satu container yang sama (tidak ada tabel yang bentrok:
     * semua tabel db_auth berawalan auth_ / app_settings).
     *
     * Container sengaja statis dan tidak pernah di-stop — Testcontainers memakai pola
     * singleton ini supaya satu container dipakai ulang oleh seluruh kelas test dalam
     * satu JVM, lalu dibersihkan Ryuk saat JVM berakhir.
     */
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // AuthDataSourceConfig → app.datasource.auth, ErpDataSourceConfig → app.datasource.erp.
        for (String prefix : new String[] { "app.datasource.auth", "app.datasource.erp" }) {
            registry.add(prefix + ".url", POSTGRES::getJdbcUrl);
            registry.add(prefix + ".username", POSTGRES::getUsername);
            registry.add(prefix + ".password", POSTGRES::getPassword);
            registry.add(prefix + ".driver-class-name", () -> "org.postgresql.Driver");
        }
    }
}
