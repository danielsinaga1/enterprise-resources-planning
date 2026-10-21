package id.enterpriseapps.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Auth datasource — db_auth (pengguna, role, menu, struktur organisasi, pengaturan umum).
 * Datasource utama aplikasi inti, karena itu {@code @Primary}: {@code @Transactional} tanpa nama
 * transaction manager memakai db_auth. Modul ERP memakai datasource sendiri ({@code @ErpTx}).
 * Uses DataSourceProperties to properly map app.datasource.auth.url → HikariCP jdbcUrl.
 */
@Configuration
@EnableJpaRepositories(
    basePackages            = "id.enterpriseapps.auth.repository",
    entityManagerFactoryRef = "authEntityManagerFactory",
    transactionManagerRef   = "authTransactionManager"
)
public class AuthDataSourceConfig {

    @Bean(name = "authDataSourceProperties")
    @Primary
    @ConfigurationProperties("app.datasource.auth")
    public DataSourceProperties authDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "authDataSource")
    @Primary
    @ConfigurationProperties("app.datasource.auth.hikari")
    public HikariDataSource authDataSource(
            @Qualifier("authDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "authEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean authEntityManagerFactory(
            @Qualifier("authDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceUnitName("auth");
        factory.setPackagesToScan("id.enterpriseapps.auth.entity");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setShowSql(false);
        factory.setJpaVendorAdapter(adapter);

        Map<String, Object> props = new HashMap<>();
        // dialect sengaja tidak di-set: Hibernate 6 auto-detect dari koneksi JDBC
        // sehingga konfigurasi ini bekerja untuk PostgreSQL maupun MySQL.
        props.put("hibernate.hbm2ddl.auto",                "none");
        props.put("hibernate.format_sql",                  true);
        props.put("hibernate.use_sql_comments",            true);
        props.put("hibernate.connection.charSet",          "UTF-8");
        props.put("hibernate.connection.characterEncoding","UTF-8");
        factory.setJpaPropertyMap(props);

        return factory;
    }

    @Bean(name = "authTransactionManager")
    @Primary
    public PlatformTransactionManager authTransactionManager(
            @Qualifier("authEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
