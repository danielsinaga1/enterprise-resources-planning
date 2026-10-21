package id.enterpriseapps.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * LIMS datasource — db_lims (DB-per-service).
 *
 * The LIMS module is package-isolated (id.enterpriseapps.lims.*) and owns its
 * own database with no cross-DB foreign keys. User data is a lean local
 * snapshot (lims_users) seeded from the Auth service — never the password.
 *
 * LIMS entities/repositories are scanned ONLY here, not in
 * {@link PrimaryDataSourceConfig}. LIMS services qualify their @Transactional
 * with transactionManager = "limsTransactionManager" so writes are atomic
 * against this datasource.
 *
 * Properties bind from app.datasource.lims.* (see application-dev.yaml).
 */
@Configuration
@EnableJpaRepositories(
    basePackages            = "id.enterpriseapps.lims.repository",
    entityManagerFactoryRef = "limsEntityManagerFactory",
    transactionManagerRef   = "limsTransactionManager"
)
public class LimsDataSourceConfig {

    @Bean(name = "limsDataSourceProperties")
    @ConfigurationProperties("app.datasource.lims")
    public DataSourceProperties limsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "limsDataSource")
    @ConfigurationProperties("app.datasource.lims.hikari")
    public HikariDataSource limsDataSource(
            @Qualifier("limsDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "limsEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean limsEntityManagerFactory(
            @Qualifier("limsDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceUnitName("lims");
        factory.setPackagesToScan("id.enterpriseapps.lims.entity");

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

    @Bean(name = "limsTransactionManager")
    public PlatformTransactionManager limsTransactionManager(
            @Qualifier("limsEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
