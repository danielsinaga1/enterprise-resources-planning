package id.enterpriseapps.support;

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
 * Kenapa container kosong sudah cukup: ketiga EntityManagerFactory
 * (auth / primary / lims) meng-hardcode `hibernate.hbm2ddl.auto = none`, dan test
 * controller mem-mock service-nya lewat @MockitoBean sehingga tidak ada query yang
 * benar-benar jalan. Yang dibutuhkan Hibernate hanyalah koneksi JDBC yang hidup untuk
 * mendeteksi dialect. Jadi tidak perlu DDL maupun seed — dan itu penting, karena
 * berkas SQL di repo ini (`sql/*_service/01_schema.sql`, `docker/seed/*.sql`) masih
 * dump MySQL dari era sebelum migrasi ke PostgreSQL-only, sehingga tidak bisa dimuat
 * ke Postgres apa adanya.
 *
 * Begitu ada test yang benar-benar menyentuh tabel, container ini perlu diberi skema
 * PostgreSQL lebih dulu — bukan sekadar dinyalakan.
 */
public abstract class AbstractPostgresIntegrationTest {

    /**
     * Ketiga datasource aplikasi diarahkan ke satu container yang sama. Aman selama
     * belum ada skema: tidak ada tabel yang bisa bentrok antar db_auth / db_irms / db_lims.
     *
     * Container sengaja statis dan tidak pernah di-stop — Testcontainers memakai pola
     * singleton ini supaya satu container dipakai ulang oleh seluruh kelas test dalam
     * satu JVM, lalu dibersihkan Ryuk saat JVM berakhir. Menyalakan container per kelas
     * membuat suite jauh lebih lambat.
     *
     * Versi 16 dipilih karena sudah ter-cache lokal dan sama dengan pin `rag-postgres`
     * di docker-compose. Server produksi memakai PostgreSQL 18; naikkan versi di sini
     * bila nanti ada test yang bergantung pada perilaku skema.
     */
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // Prefix berbeda-beda karena tiap DataSource dibaca dari konfigurasinya sendiri:
        // PrimaryDataSourceConfig → spring.datasource, AuthDataSourceConfig →
        // app.datasource.auth, LimsDataSourceConfig → app.datasource.lims.
        for (String prefix : new String[] {
                "spring.datasource", "app.datasource.auth", "app.datasource.lims" }) {
            registry.add(prefix + ".url", POSTGRES::getJdbcUrl);
            registry.add(prefix + ".username", POSTGRES::getUsername);
            registry.add(prefix + ".password", POSTGRES::getPassword);
            registry.add(prefix + ".driver-class-name", () -> "org.postgresql.Driver");
        }

        // Avantis (SQL Server) tidak ikut dijalankan saat test. Nilainya cuma perlu ADA:
        // `application.yaml` memberi default kosong lewat ${WR_DB_URL:}, dan Hikari menolak
        // jdbcUrl kosong saat driverClassName ter-set ("jdbcUrl is required with
        // driverClassName") sehingga context gagal dimuat. Koneksi sungguhan tidak pernah
        // dibuka karena WrDataSourceConfig memakai initializationFailTimeout(-1), dan
        // WorkRequestService sendiri fail-soft.
        registry.add("app.datasource-wr.url",
                () -> "jdbc:sqlserver://localhost:1433;databaseName=avantis-dummy;encrypt=false");
        registry.add("app.datasource-wr.username", () -> "test");
        registry.add("app.datasource-wr.password", () -> "test");
    }
}
