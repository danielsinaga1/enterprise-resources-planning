package id.enterpriseapps.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        log.info("Initializing schema for Procurement Audit Log...");
        try {
            jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS procurement_audit_log (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            timestamp TIMESTAMP,
                            username VARCHAR(255),
                            module VARCHAR(255),
                            action VARCHAR(255),
                            reference_number VARCHAR(255),
                            details VARCHAR(1000),
                            ip_address VARCHAR(255)
                        );
                    """);

            jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS procurement_plan (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            category VARCHAR(100) NOT NULL,
                            item_name VARCHAR(200) NOT NULL,
                            requesting_unit VARCHAR(100) NOT NULL,
                            quantity INTEGER NOT NULL,
                            unit VARCHAR(50) NOT NULL,
                            estimated_unit_price DECIMAL(15, 2),
                            total_estimated_price DECIMAL(15, 2),
                            priority VARCHAR(20) NOT NULL,
                            status VARCHAR(20) NOT NULL,
                            fiscal_year INTEGER,
                            created_by VARCHAR(100),
                            created_date TIMESTAMP,
                            updated_by VARCHAR(100),
                            updated_date TIMESTAMP
                        );
                    """);
            log.info("Schema initialization completed.");

            // Create and Seed Budget Table
            jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS budget (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            budget_code VARCHAR(50) NOT NULL UNIQUE,
                            fiscal_year INTEGER NOT NULL,
                            department_id BIGINT,
                            department_name VARCHAR(200),
                            category VARCHAR(100) NOT NULL,
                            sub_category VARCHAR(100),
                            description TEXT,
                            total_budget DECIMAL(15, 2) NOT NULL,
                            allocated_amount DECIMAL(15, 2),
                            used_amount DECIMAL(15, 2),
                            remaining_amount DECIMAL(15, 2),
                            start_date DATE,
                            end_date DATE,
                            status VARCHAR(20) NOT NULL,
                            approved_by VARCHAR(100),
                            approved_date TIMESTAMP,
                            notes TEXT,
                            created_by VARCHAR(100),
                            created_date TIMESTAMP,
                            updated_by VARCHAR(100),
                            updated_date TIMESTAMP
                        );
                    """);

            Integer budgetCount2024 = jdbcTemplate
                    .queryForObject("SELECT COUNT(*) FROM budget WHERE fiscal_year = 2024", Integer.class);
            log.info("Current budget count for 2024: " + budgetCount2024);

            if (budgetCount2024 != null && budgetCount2024 == 0) {
                log.info("Seeding budget data for 2024...");
                jdbcTemplate.execute(
                        """
                                    INSERT INTO budget (
                                        budget_code, fiscal_year, category, total_budget, allocated_amount, used_amount, remaining_amount, status, created_by, created_date
                                    ) VALUES
                                    ('B2024-001', 2024, 'Obat-obatan', 1000000000, 750000000, 750000000, 250000000, 'ACTIVE', 'system', NOW()),
                                    ('B2024-002', 2024, 'Alat Medis', 800000000, 620000000, 620000000, 180000000, 'ACTIVE', 'system', NOW()),
                                    ('B2024-003', 2024, 'Linen & Supplies', 300000000, 220000000, 220000000, 800000000, 'ACTIVE', 'system', NOW()),
                                    ('B2024-004', 2024, 'Jasa Outsourcing', 400000000, 260000000, 260000000, 140000000, 'ACTIVE', 'system', NOW());
                                """);
                log.info("Budget data seeded successfully.");
            } else {
                log.info("Budget data for 2024 already exists. Skipping seed.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize schema: " + e.getMessage());
        }
    }
}
