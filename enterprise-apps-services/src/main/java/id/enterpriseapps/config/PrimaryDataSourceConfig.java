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
 * Primary datasource — db_nirms.
 * Uses DataSourceProperties to properly map spring.datasource.url → HikariCP jdbcUrl.
 */
@Configuration
@EnableJpaRepositories(
    // LIMS repositories are scanned by LimsDataSourceConfig (db_lims), not here.
    basePackages = "id.enterpriseapps.repository",
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef   = "transactionManager"
)
public class PrimaryDataSourceConfig {

    @Primary
    @Bean(name = "primaryDataSourceProperties")
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "dataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Primary
    @Bean(name = "entityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("dataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceUnitName("primary");
        // LIMS entities are scanned by LimsDataSourceConfig (db_lims), not here.
        factory.setPackagesToScan("id.enterpriseapps.entity");

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

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
