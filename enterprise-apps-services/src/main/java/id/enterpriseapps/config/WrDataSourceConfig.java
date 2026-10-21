package id.enterpriseapps.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Read-only connection to Avantis (external maintenance/EAM system on SQL Server, not part of
 * this application) — used only to search the kpi_wr_irms view for the Work Request autocomplete
 * on incident/JSO forms. Deliberately plain JDBC, not JPA: we don't own this schema and have no
 * business modelling it as a domain entity for a single lookup query.
 *
 * initializationFailTimeout(-1) means the pool does NOT try to connect at startup — if Avantis is
 * unreachable or WR_DB_* is unset, the app still boots normally and WorkRequestService simply
 * returns no results (see its try/catch) instead of taking down an unrelated deployment.
 */
@Configuration
public class WrDataSourceConfig {

    @Bean(name = "wrDataSource")
    public HikariDataSource wrDataSource(
            @Value("${app.datasource-wr.url}") String url,
            @Value("${app.datasource-wr.username}") String username,
            @Value("${app.datasource-wr.password}") String password) {
        // A blank optional URL cannot pass HikariConfig.validate(), even with fail-fast off.
        // Keep the pool lazy so WorkRequestService can return its existing empty fallback.
        if (url == null || url.isBlank()) {
            HikariDataSource unconfigured = new HikariDataSource();
            unconfigured.setPoolName("WrPool");
            return unconfigured;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        config.setPoolName("WrPool");
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.setInitializationFailTimeout(-1);
        config.setReadOnly(true);
        config.setValidationTimeout(3000);
        // connectionTimeout hanya membatasi pengambilan koneksi DARI POOL; ia tidak menolong
        // sama sekali kalau Avantis menerima koneksi lalu diam. Dua properti driver di bawah
        // memberi batas waktu nyata di level socket, sehingga query yang menggantung dibatalkan
        // alih-alih menahan thread HTTP selamanya. Ini bukan teoretis: endpoint autocomplete WR
        // ditembak setiap ketikan, jadi satu Avantis lambat bisa menghabiskan thread Tomcat dan
        // menjatuhkan seluruh backend — padahal fiturnya sendiri hanya pelengkap form.
        // socketTimeout (ms) dipasang lebih longgar daripada queryTimeout di wrJdbcTemplate
        // supaya pembatalan query yang rapi terjadi lebih dulu, dan socket jadi jaring terakhir.
        config.addDataSourceProperty("socketTimeout", "15000");
        config.addDataSourceProperty("loginTimeout", "5");
        return new HikariDataSource(config);
    }

    // @Qualifier here is NOT optional. There are four HikariDataSource beans in this context
    // (primary, auth, lims, wr) and the build doesn't compile with -parameters, so Spring can't
    // read this method's parameter name to disambiguate by name — without an explicit qualifier
    // it silently falls back to whichever DataSource is marked @Primary (the app's own Postgres
    // datasource, in PrimaryDataSourceConfig). That's exactly what happened here: this bean built
    // fine and searches went through JdbcTemplate.query() without error, but every query landed on
    // Postgres instead of Avantis — surfaced only because "SELECT TOP" is invalid Postgres syntax
    // (its error read "syntax error at or near '50'", Postgres's phrasing; SQL Server would have
    // said "Incorrect syntax near ..."). Confirmed via the concrete exception class:
    // org.postgresql.util.PSQLException, not com.microsoft.sqlserver.jdbc.SQLServerException.
    @Bean(name = "wrJdbcTemplate")
    public JdbcTemplate wrJdbcTemplate(@Qualifier("wrDataSource") HikariDataSource wrDataSource) {
        JdbcTemplate template = new JdbcTemplate(wrDataSource);
        // Batas 10 detik per query. Tanpa ini sebuah lock di sisi Avantis akan menahan thread
        // pemanggil tanpa batas; WorkRequestService memang fail-soft, tapi try/catch-nya hanya
        // menangkap exception — bukan query yang tidak pernah kembali.
        template.setQueryTimeout(10);
        return template;
    }
}
