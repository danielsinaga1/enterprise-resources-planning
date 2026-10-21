package id.enterpriseapps.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * ERP datasource — db_erp (DB-per-service, pola yang sama dengan LIMS/SmartDocs).
 *
 * <p>Modul :erp (port fitur ENTARIS) memiliki 114 tabel sendiri; skema dikelola skrip
 * {@code sql/erp_service/} — {@code hbm2ddl} dikunci "none". Repository ERP diberi nama bean
 * berbasis FQCN karena beberapa nama sederhana (UserRepository, RoleRepository, …) sudah
 * dipakai modul lain.
 *
 * <p>Waktu disimpan UTC ({@code hibernate.jdbc.time_zone}), sama dengan kolom
 * {@code timestamp(3)} Prisma di sumber ENTARIS. Dialek di-set eksplisit dan metadata JDBC
 * tidak dibaca saat boot, sehingga aplikasi tetap naik walau db_erp belum tersedia
 * (endpoint ERP yang gagal terhubung akan mengembalikan 503).
 *
 * <p>Semua parameter {@code @Bean} wajib memakai {@code @Qualifier} (lihat catatan di
 * {@link SmartDocsDataSourceConfig}). Properti dari {@code app.datasource.erp.*}.
 */
@Configuration
@EnableJpaRepositories(
    basePackages            = "id.enterpriseapps.erp.repository",
    entityManagerFactoryRef = "erpEntityManagerFactory",
    transactionManagerRef   = "erpTransactionManager",
    nameGenerator           = FullyQualifiedAnnotationBeanNameGenerator.class
)
public class ErpDataSourceConfig {

    @Bean(name = "erpDataSourceProperties")
    @ConfigurationProperties("app.datasource.erp")
    public DataSourceProperties erpDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "erpDataSource")
    @ConfigurationProperties("app.datasource.erp.hikari")
    public HikariDataSource erpDataSource(
            @Qualifier("erpDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        // Jangan gagalkan boot seluruh platform bila db_erp belum ada.
        ds.setInitializationFailTimeout(-1);
        return ds;
    }

    @Bean(name = "erpEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean erpEntityManagerFactory(
            @Qualifier("erpDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceUnitName("erp");
        factory.setPackagesToScan("id.enterpriseapps.erp.entity");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setShowSql(false);
        factory.setJpaVendorAdapter(adapter);

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto",                    "none");
        props.put("hibernate.dialect",                         "id.enterpriseapps.erp.core.ErpPostgreSQLDialect");
        props.put("hibernate.boot.allow_jdbc_metadata_access", false);
        props.put("hibernate.jdbc.time_zone",                  "UTC");
        props.put("hibernate.format_sql",                      true);
        props.put("hibernate.connection.charSet",              "UTF-8");
        props.put("hibernate.connection.characterEncoding",    "UTF-8");
        factory.setJpaPropertyMap(props);

        return factory;
    }

    @Bean(name = "erpTransactionManager")
    public PlatformTransactionManager erpTransactionManager(
            @Qualifier("erpEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    /** JdbcTemplate db_erp — ikut transaksi {@code erpTransactionManager} (koneksi yang sama). */
    @Bean(name = "erpJdbcTemplate")
    public JdbcTemplate erpJdbcTemplate(@Qualifier("erpDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "erpNamedJdbcTemplate")
    public NamedParameterJdbcTemplate erpNamedJdbcTemplate(@Qualifier("erpDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
