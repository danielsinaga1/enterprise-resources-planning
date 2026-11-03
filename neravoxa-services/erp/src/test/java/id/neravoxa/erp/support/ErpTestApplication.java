package id.neravoxa.erp.support;

import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Konteks Spring minimal untuk uji integrasi modul ERP: hanya bean {@code id.neravoxa.erp}
 * + db_erp di PostgreSQL embedded (proses nyata, tanpa Docker) yang sudah berisi
 * {@code 01_schema.sql} + {@code 02_seed.sql}. Konfigurasi JPA identik dengan
 * {@code ErpDataSourceConfig} aplikasi.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        RedisAutoConfiguration.class})
@ComponentScan(basePackages = "id.neravoxa.erp")
@EnableJpaRepositories(
        basePackages = "id.neravoxa.erp.repository",
        entityManagerFactoryRef = "erpEntityManagerFactory",
        transactionManagerRef = "erpTransactionManager",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class ErpTestApplication {

    private static EmbeddedPostgres postgres;

    /** Satu instance PostgreSQL per JVM test; skema & seed dipasang sekali. */
    static synchronized DataSource sharedDataSource() throws IOException {
        if (postgres == null) {
            postgres = EmbeddedPostgres.builder().start();
            DataSource raw = postgres.getPostgresDatabase();
            JdbcTemplate jdbc = new JdbcTemplate(raw);
            jdbc.execute(read("db/erp/01_schema.sql"));
            jdbc.execute(read("db/erp/02_seed.sql"));
            jdbc.execute(read("db/erp/03_assistant.sql"));
            jdbc.execute(read("db/erp/04_eam_meter.sql"));
        }
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(postgres.getJdbcUrl("postgres", "postgres"));
        ds.setUsername("postgres");
        ds.setMaximumPoolSize(8);
        ds.setConnectionInitSql("SET TIME ZONE 'UTC'");
        return ds;
    }

    private static String read(String path) throws IOException {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Bean(name = "erpDataSource")
    public DataSource erpDataSource() throws IOException {
        return sharedDataSource();
    }

    @Bean(name = "erpEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean erpEntityManagerFactory(@Qualifier("erpDataSource") DataSource ds) {
        LocalContainerEntityManagerFactoryBean f = new LocalContainerEntityManagerFactoryBean();
        f.setDataSource(ds);
        f.setPersistenceUnitName("erp");
        f.setPackagesToScan("id.neravoxa.erp.entity");
        f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String, Object> p = new HashMap<>();
        p.put("hibernate.hbm2ddl.auto", "none");
        p.put("hibernate.dialect", "id.neravoxa.erp.core.ErpPostgreSQLDialect");
        p.put("hibernate.jdbc.time_zone", "UTC");
        f.setJpaPropertyMap(p);
        return f;
    }

    @Bean(name = "erpTransactionManager")
    public PlatformTransactionManager erpTransactionManager(@Qualifier("erpEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean(name = "erpJdbcTemplate")
    public JdbcTemplate erpJdbcTemplate(@Qualifier("erpDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean(name = "erpNamedJdbcTemplate")
    public NamedParameterJdbcTemplate erpNamedJdbcTemplate(@Qualifier("erpDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }
}
