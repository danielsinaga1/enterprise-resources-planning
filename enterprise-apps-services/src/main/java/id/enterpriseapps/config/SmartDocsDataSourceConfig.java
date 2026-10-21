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
 * SmartDocs datasource — db_smartdocs (DB-per-service, pola yang sama dengan LIMS).
 *
 * <p>Database ini dipakai bersama service OCR Python: service itu menulis dokumen
 * hasil pindaian, modul ini membaca dan mengelolanya. Karena itu skema TIDAK boleh
 * dibuat Hibernate — {@code hbm2ddl.auto} dikunci "none" dan skema dikelola lewat
 * skrip di {@code sql/smartdocs_service/}.
 *
 * <p>Semua parameter {@code @Bean} di sini wajib memakai {@code @Qualifier}: ada
 * beberapa bean {@link HikariDataSource} di aplikasi ini dan tanpa qualifier Spring
 * diam-diam menyuntikkan bean {@code @Primary} (Postgres db_irms) tanpa error saat
 * startup — kesalahan yang pernah membuat query nyasar ke database yang salah.
 *
 * <p>Properti mengikat dari {@code app.datasource.smartdocs.*} (lihat application-dev.yaml).
 */
@Configuration
@EnableJpaRepositories(
    basePackages            = "id.enterpriseapps.smartdocs.repository",
    entityManagerFactoryRef = "smartDocsEntityManagerFactory",
    transactionManagerRef   = "smartDocsTransactionManager"
)
public class SmartDocsDataSourceConfig {

    @Bean(name = "smartDocsDataSourceProperties")
    @ConfigurationProperties("app.datasource.smartdocs")
    public DataSourceProperties smartDocsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "smartDocsDataSource")
    @ConfigurationProperties("app.datasource.smartdocs.hikari")
    public HikariDataSource smartDocsDataSource(
            @Qualifier("smartDocsDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "smartDocsEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean smartDocsEntityManagerFactory(
            @Qualifier("smartDocsDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceUnitName("smartdocs");
        factory.setPackagesToScan("id.enterpriseapps.smartdocs.entity");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setShowSql(false);
        factory.setJpaVendorAdapter(adapter);

        Map<String, Object> props = new HashMap<>();
        // dialect sengaja tidak di-set: Hibernate 6 auto-detect dari koneksi JDBC.
        props.put("hibernate.hbm2ddl.auto",                 "none");
        props.put("hibernate.format_sql",                   true);
        props.put("hibernate.use_sql_comments",             true);
        props.put("hibernate.connection.charSet",           "UTF-8");
        props.put("hibernate.connection.characterEncoding", "UTF-8");
        factory.setJpaPropertyMap(props);

        return factory;
    }

    @Bean(name = "smartDocsTransactionManager")
    public PlatformTransactionManager smartDocsTransactionManager(
            @Qualifier("smartDocsEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
