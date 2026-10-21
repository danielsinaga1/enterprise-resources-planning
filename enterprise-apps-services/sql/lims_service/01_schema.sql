-- ====================================================================
--  db_lims — Standalone schema for the LIMS microservice
--  (DB-per-service; no cross-database FK; user data is a local snapshot)
--
--  Engine : MySQL 8.x   Charset: utf8mb4 / utf8mb4_unicode_ci
--  Naming : see docs/DATABASE_NAMING_CONVENTION.md
--
--  Run order:  01_schema.sql  ->  02_master_data.sql  ->  03_transactional_data.sql
--  Then also load the already-prefixed modules:
--      sql/14_lims_chemical_compounds.sql
--      sql/17_lims_inventory.sql
-- ====================================================================

CREATE DATABASE IF NOT EXISTS db_lims
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE db_lims;

SET FOREIGN_KEY_CHECKS = 0;

-- --------------------------------------------------------------------
-- Reference snapshots (data owned by other services, copied in lean)
-- --------------------------------------------------------------------

-- Organizational departments referenced by lab reports.
CREATE TABLE IF NOT EXISTS lims_departments (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NULL DEFAULT NULL,
    updated_at  TIMESTAMP NULL DEFAULT NULL,
    deleted_at  TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Lean snapshot of users (master lives in the Auth service). Only the
-- fields LIMS actually needs: display name + manager chain (parent_id)
-- for the berjenjang review/acknowledge approval, plus org context.
CREATE TABLE IF NOT EXISTS lims_users (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name           VARCHAR(255) NULL,
    npk            VARCHAR(255) NULL,
    email          VARCHAR(255) NULL,
    job_title      VARCHAR(255) NULL,
    parent_id      BIGINT UNSIGNED NULL,
    department_id  BIGINT UNSIGNED NULL,
    created_at     TIMESTAMP NULL DEFAULT NULL,
    updated_at     TIMESTAMP NULL DEFAULT NULL,
    deleted_at     TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_lims_users_email (email),
    KEY ix_lims_users_parent_id (parent_id),
    KEY ix_lims_users_department_id (department_id),
    CONSTRAINT fk_lims_users_parent
        FOREIGN KEY (parent_id) REFERENCES lims_users (id) ON DELETE SET NULL,
    CONSTRAINT fk_lims_users_department
        FOREIGN KEY (department_id) REFERENCES lims_departments (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------
-- Master data — the analysis dependency chain
--   parameter -> test_type -> method -> standard -> unit
-- --------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS lims_parameters (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NULL DEFAULT NULL,
    updated_at  TIMESTAMP NULL DEFAULT NULL,
    deleted_at  TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_test_types (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name          VARCHAR(255) NOT NULL,
    parameter_id  BIGINT UNSIGNED NULL,
    created_at    TIMESTAMP NULL DEFAULT NULL,
    updated_at    TIMESTAMP NULL DEFAULT NULL,
    deleted_at    TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY ix_lims_test_types_parameter_id (parameter_id),
    CONSTRAINT fk_lims_test_types_parameter
        FOREIGN KEY (parameter_id) REFERENCES lims_parameters (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_methods (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name          VARCHAR(255) NOT NULL,
    test_type_id  BIGINT UNSIGNED NULL,
    created_at    TIMESTAMP NULL DEFAULT NULL,
    updated_at    TIMESTAMP NULL DEFAULT NULL,
    deleted_at    TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY ix_lims_methods_test_type_id (test_type_id),
    CONSTRAINT fk_lims_methods_test_type
        FOREIGN KEY (test_type_id) REFERENCES lims_test_types (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_standards (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    measure     VARCHAR(255) NULL,
    method_id   BIGINT UNSIGNED NULL,
    created_at  TIMESTAMP NULL DEFAULT NULL,
    updated_at  TIMESTAMP NULL DEFAULT NULL,
    deleted_at  TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY ix_lims_standards_method_id (method_id),
    CONSTRAINT fk_lims_standards_method
        FOREIGN KEY (method_id) REFERENCES lims_methods (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_units (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name         VARCHAR(255) NOT NULL,
    standard_id  BIGINT UNSIGNED NULL,
    created_at   TIMESTAMP NULL DEFAULT NULL,
    updated_at   TIMESTAMP NULL DEFAULT NULL,
    deleted_at   TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY ix_lims_units_standard_id (standard_id),
    CONSTRAINT fk_lims_units_standard
        FOREIGN KEY (standard_id) REFERENCES lims_standards (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_results (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NULL DEFAULT NULL,
    updated_at  TIMESTAMP NULL DEFAULT NULL,
    deleted_at  TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------
-- Many-to-many pivots
-- --------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS lims_method_test_type (
    method_id     BIGINT UNSIGNED NOT NULL,
    test_type_id  BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (method_id, test_type_id),
    KEY ix_lims_mtt_test_type (test_type_id),
    CONSTRAINT fk_lims_mtt_method
        FOREIGN KEY (method_id) REFERENCES lims_methods (id) ON DELETE CASCADE,
    CONSTRAINT fk_lims_mtt_test_type
        FOREIGN KEY (test_type_id) REFERENCES lims_test_types (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_standard_unit (
    standard_id  BIGINT UNSIGNED NOT NULL,
    unit_id      BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (standard_id, unit_id),
    KEY ix_lims_su_unit (unit_id),
    CONSTRAINT fk_lims_su_standard
        FOREIGN KEY (standard_id) REFERENCES lims_standards (id) ON DELETE CASCADE,
    CONSTRAINT fk_lims_su_unit
        FOREIGN KEY (unit_id) REFERENCES lims_units (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------------------
-- Transactional — laboratory reports + analysis details + daily reports
-- (user references are plain Long IDs into lims_users; no hard FK so a
--  report never breaks if a user snapshot row is missing.)
-- --------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS lims_laboratory_reports (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    no_report          VARCHAR(100) NULL,
    date_sampling      DATE NULL,
    location           VARCHAR(255) NULL,
    status             VARCHAR(50) DEFAULT 'Open',
    requested_by       VARCHAR(255) NULL,
    date_analysis      DATE NULL,
    remark             TEXT NULL,
    notes              TEXT NULL,
    reviewed_at        TIMESTAMP NULL DEFAULT NULL,
    acknowledge_at     TIMESTAMP NULL DEFAULT NULL,
    sampler_id         BIGINT UNSIGNED NULL,
    analyst_id         BIGINT UNSIGNED NULL,
    reviewed_by_id     BIGINT UNSIGNED NULL,
    acknowledge_by_id  BIGINT UNSIGNED NULL,
    department_id      BIGINT UNSIGNED NULL,
    reporter_id        BIGINT UNSIGNED NULL,
    created_at         TIMESTAMP NULL DEFAULT NULL,
    updated_at         TIMESTAMP NULL DEFAULT NULL,
    deleted_at         TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY ix_lims_lr_department_id (department_id),
    KEY ix_lims_lr_status (status),
    CONSTRAINT fk_lims_lr_department
        FOREIGN KEY (department_id) REFERENCES lims_departments (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_laboratory_details (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    description           TEXT NULL,
    status                VARCHAR(50) NULL,
    result                VARCHAR(255) NULL,
    date_sampling         DATE NULL,
    date_analysis         DATE NULL,
    laboratory_report_id  BIGINT UNSIGNED NULL,
    parameter_id          BIGINT UNSIGNED NULL,
    standard_id           BIGINT UNSIGNED NULL,
    test_type_id          BIGINT UNSIGNED NULL,
    unit_id               BIGINT UNSIGNED NULL,
    method_id             BIGINT UNSIGNED NULL,
    created_at            TIMESTAMP NULL DEFAULT NULL,
    updated_at            TIMESTAMP NULL DEFAULT NULL,
    deleted_at            TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY ix_lims_ld_report_id (laboratory_report_id),
    CONSTRAINT fk_lims_ld_report
        FOREIGN KEY (laboratory_report_id) REFERENCES lims_laboratory_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_lims_ld_parameter
        FOREIGN KEY (parameter_id) REFERENCES lims_parameters (id) ON DELETE SET NULL,
    CONSTRAINT fk_lims_ld_standard
        FOREIGN KEY (standard_id) REFERENCES lims_standards (id) ON DELETE SET NULL,
    CONSTRAINT fk_lims_ld_test_type
        FOREIGN KEY (test_type_id) REFERENCES lims_test_types (id) ON DELETE SET NULL,
    CONSTRAINT fk_lims_ld_unit
        FOREIGN KEY (unit_id) REFERENCES lims_units (id) ON DELETE SET NULL,
    CONSTRAINT fk_lims_ld_method
        FOREIGN KEY (method_id) REFERENCES lims_methods (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lims_daily_reports (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name                VARCHAR(255) NOT NULL,
    description         TEXT NULL,
    due_date            DATE NULL,
    location            VARCHAR(255) NULL,
    status              VARCHAR(50) NOT NULL DEFAULT 'Open',
    reviewed_by_id      BIGINT UNSIGNED NULL,
    reviewed_at         TIMESTAMP NULL DEFAULT NULL,
    acknowledged_by_id  BIGINT UNSIGNED NULL,
    acknowledged_at     TIMESTAMP NULL DEFAULT NULL,
    assigned_to_id      BIGINT UNSIGNED NULL,
    unit_id             BIGINT UNSIGNED NULL,
    parameter_id        BIGINT UNSIGNED NULL,
    created_at          TIMESTAMP NULL DEFAULT NULL,
    updated_at          TIMESTAMP NULL DEFAULT NULL,
    deleted_at          TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY ix_lims_dr_unit_id (unit_id),
    KEY ix_lims_dr_parameter_id (parameter_id),
    CONSTRAINT fk_lims_dr_unit
        FOREIGN KEY (unit_id) REFERENCES lims_units (id) ON DELETE SET NULL,
    CONSTRAINT fk_lims_dr_parameter
        FOREIGN KEY (parameter_id) REFERENCES lims_parameters (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
