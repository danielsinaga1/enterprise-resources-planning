-- ====================================================================
-- LIMS Module Database Schema
-- Integrated into db_nirms (MySQL)
-- All tables prefixed with 'lims_' to avoid conflicts
-- ====================================================================

-- ====================================================================
-- Master Data Tables
-- ====================================================================

CREATE TABLE IF NOT EXISTS lims_departments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_parameters (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_test_types (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parameter_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_lims_test_types_parameter FOREIGN KEY (parameter_id) REFERENCES lims_parameters(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_methods (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    test_type_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_lims_methods_test_type FOREIGN KEY (test_type_id) REFERENCES lims_test_types(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_standards (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    measure VARCHAR(255) NOT NULL,
    method_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_lims_standards_method FOREIGN KEY (method_id) REFERENCES lims_methods(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_units (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    standard_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_lims_units_standard FOREIGN KEY (standard_id) REFERENCES lims_standards(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_results (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================================================================
-- Pivot Tables (Many-to-Many)
-- ====================================================================

CREATE TABLE IF NOT EXISTS lims_method_test_type (
    method_id BIGINT UNSIGNED NOT NULL,
    test_type_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (method_id, test_type_id),
    CONSTRAINT fk_lims_mtt_method FOREIGN KEY (method_id) REFERENCES lims_methods(id) ON DELETE CASCADE,
    CONSTRAINT fk_lims_mtt_test_type FOREIGN KEY (test_type_id) REFERENCES lims_test_types(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_standard_unit (
    standard_id BIGINT UNSIGNED NOT NULL,
    unit_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (standard_id, unit_id),
    CONSTRAINT fk_lims_su_standard FOREIGN KEY (standard_id) REFERENCES lims_standards(id) ON DELETE CASCADE,
    CONSTRAINT fk_lims_su_unit FOREIGN KEY (unit_id) REFERENCES lims_units(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================================================================
-- Laboratory Reports
-- ====================================================================

CREATE TABLE IF NOT EXISTS lims_laboratory_reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    no_report VARCHAR(100) NULL,
    date_sampling DATE NULL,
    location VARCHAR(255) NULL,
    status VARCHAR(50) DEFAULT 'Open',
    requested_by VARCHAR(255) NULL,
    date_analysis DATE NULL,
    remark TEXT NULL,
    notes TEXT NULL,
    reviewed_at TIMESTAMP NULL DEFAULT NULL,
    acknowledge_at TIMESTAMP NULL DEFAULT NULL,
    sampler_id BIGINT UNSIGNED NULL,
    analyst_id BIGINT UNSIGNED NULL,
    reviewed_by_id BIGINT UNSIGNED NULL,
    acknowledge_by_id BIGINT UNSIGNED NULL,
    department_id BIGINT UNSIGNED NULL,
    reporter_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_lims_lr_department FOREIGN KEY (department_id) REFERENCES lims_departments(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================================================================
-- Laboratory Details (Analysis Details)
-- ====================================================================

CREATE TABLE IF NOT EXISTS lims_laboratory_details (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    description TEXT NULL,
    status VARCHAR(50) NULL,
    result VARCHAR(255) NULL,
    date_sampling DATE NULL,
    date_analysis DATE NULL,
    laboratory_report_id BIGINT UNSIGNED NULL,
    parameter_id BIGINT UNSIGNED NULL,
    standard_id BIGINT UNSIGNED NULL,
    test_type_id BIGINT UNSIGNED NULL,
    unit_id BIGINT UNSIGNED NULL,
    method_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_lims_ld_report FOREIGN KEY (laboratory_report_id) REFERENCES lims_laboratory_reports(id) ON DELETE CASCADE,
    CONSTRAINT fk_lims_ld_parameter FOREIGN KEY (parameter_id) REFERENCES lims_parameters(id) ON DELETE SET NULL,
    CONSTRAINT fk_lims_ld_standard FOREIGN KEY (standard_id) REFERENCES lims_standards(id) ON DELETE SET NULL,
    CONSTRAINT fk_lims_ld_test_type FOREIGN KEY (test_type_id) REFERENCES lims_test_types(id) ON DELETE SET NULL,
    CONSTRAINT fk_lims_ld_unit FOREIGN KEY (unit_id) REFERENCES lims_units(id) ON DELETE SET NULL,
    CONSTRAINT fk_lims_ld_method FOREIGN KEY (method_id) REFERENCES lims_methods(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================================================================
-- Daily Reports
-- ====================================================================

CREATE TABLE IF NOT EXISTS lims_daily_reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    due_date DATE NULL,
    location VARCHAR(255) NULL,
    assigned_to_id BIGINT UNSIGNED NULL,
    unit_id BIGINT UNSIGNED NULL,
    parameter_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NULL DEFAULT NULL,
    updated_at TIMESTAMP NULL DEFAULT NULL,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_lims_dr_unit FOREIGN KEY (unit_id) REFERENCES lims_units(id) ON DELETE SET NULL,
    CONSTRAINT fk_lims_dr_parameter FOREIGN KEY (parameter_id) REFERENCES lims_parameters(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================================================================
-- Seed Data (Sample)
-- ====================================================================

INSERT INTO lims_departments (name, created_at, updated_at) VALUES
('Laboratorium', NOW(), NOW()),
('Quality Control', NOW(), NOW()),
('Research & Development', NOW(), NOW());

INSERT INTO lims_results (name, created_at, updated_at) VALUES
('Memenuhi Syarat', NOW(), NOW()),
('Tidak Memenuhi Syarat', NOW(), NOW()),
('Pending', NOW(), NOW());
