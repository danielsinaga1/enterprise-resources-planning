-- ============================================================================
-- IRMS (Incident Report Management System) - DDL
-- Database: db_irms (MySQL 8.0+)
-- Reverse-engineered from legacy data dump
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- RBAC & AUTH TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS `roles` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `permissions` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `permission_role` (
    `role_id` BIGINT UNSIGNED NOT NULL,
    `permission_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`role_id`, `permission_id`),
    KEY `fk_permission_role_role` (`role_id`),
    KEY `fk_permission_role_permission` (`permission_id`),
    CONSTRAINT `fk_permission_role_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_permission_role_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `password_resets` (
    `email` VARCHAR(191) NOT NULL,
    `token` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    KEY `password_resets_email_index` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `personal_access_tokens` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `tokenable_type` VARCHAR(191) NOT NULL,
    `tokenable_id` BIGINT UNSIGNED NOT NULL,
    `name` VARCHAR(191) NOT NULL,
    `token` VARCHAR(64) NOT NULL,
    `abilities` TEXT NULL,
    `last_used_at` TIMESTAMP NULL DEFAULT NULL,
    `expires_at` TIMESTAMP NULL DEFAULT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `personal_access_tokens_token_unique` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- OAuth tables (Laravel Passport)
CREATE TABLE IF NOT EXISTS `oauth_access_tokens` (
    `id` VARCHAR(100) NOT NULL,
    `user_id` BIGINT UNSIGNED NULL,
    `client_id` BIGINT UNSIGNED NOT NULL,
    `name` VARCHAR(191) NULL,
    `scopes` TEXT NULL,
    `revoked` TINYINT(1) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `expires_at` DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `oauth_access_tokens_user_id_index` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `oauth_auth_codes` (
    `id` VARCHAR(100) NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `client_id` BIGINT UNSIGNED NOT NULL,
    `scopes` TEXT NULL,
    `revoked` TINYINT(1) NOT NULL,
    `expires_at` DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `oauth_auth_codes_user_id_index` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `oauth_clients` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NULL,
    `name` VARCHAR(191) NOT NULL,
    `secret` VARCHAR(100) NULL,
    `provider` VARCHAR(191) NULL,
    `redirect` TEXT NOT NULL,
    `personal_access_client` TINYINT(1) NOT NULL,
    `password_client` TINYINT(1) NOT NULL,
    `revoked` TINYINT(1) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `oauth_clients_user_id_index` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `oauth_personal_access_clients` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `client_id` BIGINT UNSIGNED NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `oauth_refresh_tokens` (
    `id` VARCHAR(100) NOT NULL,
    `access_token_id` VARCHAR(100) NOT NULL,
    `revoked` TINYINT(1) NOT NULL,
    `expires_at` DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `oauth_refresh_tokens_access_token_id_index` (`access_token_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sessions` (
    `id` VARCHAR(191) NOT NULL,
    `user_id` BIGINT UNSIGNED NULL,
    `ip_address` VARCHAR(45) NULL,
    `user_agent` TEXT NULL,
    `payload` LONGTEXT NOT NULL,
    `last_activity` INT NOT NULL,
    PRIMARY KEY (`id`),
    KEY `sessions_user_id_index` (`user_id`),
    KEY `sessions_last_activity_index` (`last_activity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- ORGANIZATION / LOOKUP TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS `designation_departments` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    `code` VARCHAR(50) NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `origin_departments` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    `code` VARCHAR(50) NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `divisions` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `code` VARCHAR(50) NULL,
    `dept_id` BIGINT UNSIGNED NULL,
    `designation_department_id` BIGINT UNSIGNED NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_divisions_dept` (`dept_id`),
    KEY `fk_divisions_designation` (`designation_department_id`),
    CONSTRAINT `fk_divisions_dept` FOREIGN KEY (`dept_id`) REFERENCES `origin_departments` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_divisions_designation` FOREIGN KEY (`designation_department_id`) REFERENCES `designation_departments` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `job_titles` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `email` VARCHAR(191) NOT NULL,
    `email_verified_at` TIMESTAMP NULL DEFAULT NULL,
    `password` VARCHAR(191) NOT NULL,
    `remember_token` VARCHAR(100) NULL,
    `npk` VARCHAR(50) NULL,
    `parent_id` BIGINT UNSIGNED NULL,
    `job_id` BIGINT UNSIGNED NULL,
    `dept_id` BIGINT UNSIGNED NULL,
    `division_id` BIGINT UNSIGNED NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `users_email_unique` (`email`),
    KEY `fk_users_parent` (`parent_id`),
    KEY `fk_users_job` (`job_id`),
    KEY `fk_users_dept` (`dept_id`),
    KEY `fk_users_division` (`division_id`),
    CONSTRAINT `fk_users_parent` FOREIGN KEY (`parent_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_users_job` FOREIGN KEY (`job_id`) REFERENCES `job_titles` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_users_dept` FOREIGN KEY (`dept_id`) REFERENCES `origin_departments` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_users_division` FOREIGN KEY (`division_id`) REFERENCES `divisions` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `role_user` (
    `user_id` BIGINT UNSIGNED NOT NULL,
    `role_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    KEY `fk_role_user_user` (`user_id`),
    KEY `fk_role_user_role` (`role_id`),
    CONSTRAINT `fk_role_user_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_role_user_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_alerts` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `alert_text` TEXT NOT NULL,
    `alert_link` VARCHAR(191) NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_user_alert` (
    `user_alert_id` BIGINT UNSIGNED NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `read` TINYINT(1) NOT NULL DEFAULT 0,
    KEY `fk_user_user_alert_alert` (`user_alert_id`),
    KEY `fk_user_user_alert_user` (`user_id`),
    CONSTRAINT `fk_user_user_alert_alert` FOREIGN KEY (`user_alert_id`) REFERENCES `user_alerts` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_user_alert_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- INCIDENT REPORT LOOKUP TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS `area_incidents` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `region_incidents` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `category_incidents` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `classification_incidents` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `classification_details` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `description` TEXT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    `category_id` BIGINT UNSIGNED NULL,
    `classification_id` BIGINT UNSIGNED NULL,
    PRIMARY KEY (`id`),
    KEY `fk_classification_details_category` (`category_id`),
    KEY `fk_classification_details_classification` (`classification_id`),
    CONSTRAINT `fk_classification_details_category` FOREIGN KEY (`category_id`) REFERENCES `category_incidents` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_classification_details_classification` FOREIGN KEY (`classification_id`) REFERENCES `classification_incidents` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `chemical_releases` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `root_causes` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `results` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `precortives` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `distribution_incidents` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `incident_report_id` BIGINT UNSIGNED NULL,
    `user_id` BIGINT UNSIGNED NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_distribution_incidents_user` (`user_id`),
    CONSTRAINT `fk_distribution_incidents_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- INCIDENT REPORT MAIN TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS `incident_reports` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `no_laporan` VARCHAR(50) NULL,
    `title` VARCHAR(255) NOT NULL,
    `perbaikan_awal` TEXT NULL,
    `location` VARCHAR(255) NULL,
    `perbaikan` TEXT NULL,
    `pencegahan` TEXT NULL,
    `description` TEXT NULL,
    `reason` TEXT NULL,
    `status` VARCHAR(50) NULL DEFAULT 'Open',
    `date_incident` DATETIME NULL,
    `date_action` DATETIME NULL,
    `assigned_at` DATETIME NULL,
    `reviewed_at` DATETIME NULL,
    `acknowledged_at` DATETIME NULL,
    `done_at` DATETIME NULL,
    `direct_cost` DECIMAL(15,2) NULL,
    `copq` DECIMAL(15,2) NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    `reporter_id` BIGINT UNSIGNED NULL,
    `reviewed_by_id` BIGINT UNSIGNED NULL,
    `dept_origin_id` BIGINT UNSIGNED NULL,
    `root_cause_id` BIGINT UNSIGNED NULL,
    `category_id` BIGINT UNSIGNED NULL,
    `classify_id` BIGINT UNSIGNED NULL,
    `dept_designated_id` BIGINT UNSIGNED NULL,
    `result_id` BIGINT UNSIGNED NULL,
    `acknowledged_by_id` BIGINT UNSIGNED NULL,
    `action_by_id` BIGINT UNSIGNED NULL,
    `assigned_to_id` BIGINT UNSIGNED NULL,
    `cr_id` BIGINT UNSIGNED NULL,
    `area_id` BIGINT UNSIGNED NULL,
    `ri_id` BIGINT UNSIGNED NULL,
    `designated_manager_id` BIGINT UNSIGNED NULL,
    `revision_notes` TEXT NULL,
    `manager_notes` TEXT NULL,
    `follow_up_notes` TEXT NULL,
    `fully_approved_at` DATETIME NULL,
    `in_progress_at` DATETIME NULL,
    PRIMARY KEY (`id`),
    KEY `fk_ir_reporter` (`reporter_id`),
    KEY `fk_ir_reviewer` (`reviewed_by_id`),
    KEY `fk_ir_dept_origin` (`dept_origin_id`),
    KEY `fk_ir_root_cause` (`root_cause_id`),
    KEY `fk_ir_category` (`category_id`),
    KEY `fk_ir_classify` (`classify_id`),
    KEY `fk_ir_dept_designated` (`dept_designated_id`),
    KEY `fk_ir_result` (`result_id`),
    KEY `fk_ir_acknowledged_by` (`acknowledged_by_id`),
    KEY `fk_ir_action_by` (`action_by_id`),
    KEY `fk_ir_assigned_to` (`assigned_to_id`),
    KEY `fk_ir_cr` (`cr_id`),
    KEY `fk_ir_area` (`area_id`),
    KEY `fk_ir_ri` (`ri_id`),
    KEY `fk_ir_designated_manager` (`designated_manager_id`),
    CONSTRAINT `fk_ir_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_reviewer` FOREIGN KEY (`reviewed_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_dept_origin` FOREIGN KEY (`dept_origin_id`) REFERENCES `origin_departments` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_root_cause` FOREIGN KEY (`root_cause_id`) REFERENCES `root_causes` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_category` FOREIGN KEY (`category_id`) REFERENCES `category_incidents` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_classify` FOREIGN KEY (`classify_id`) REFERENCES `classification_incidents` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_dept_designated` FOREIGN KEY (`dept_designated_id`) REFERENCES `designation_departments` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_result` FOREIGN KEY (`result_id`) REFERENCES `results` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_acknowledged_by` FOREIGN KEY (`acknowledged_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_action_by` FOREIGN KEY (`action_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_assigned_to` FOREIGN KEY (`assigned_to_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_cr` FOREIGN KEY (`cr_id`) REFERENCES `chemical_releases` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_area` FOREIGN KEY (`area_id`) REFERENCES `area_incidents` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_ri` FOREIGN KEY (`ri_id`) REFERENCES `region_incidents` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ir_designated_manager` FOREIGN KEY (`designated_manager_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `investigation_details` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `recommendation` TEXT NULL,
    `date_deadline` DATE NULL,
    `date_actual` DATE NULL,
    `status` VARCHAR(50) NULL DEFAULT 'Open',
    `pic_id` BIGINT UNSIGNED NULL,
    `precortive_id` BIGINT UNSIGNED NULL,
    `incident_report_id` BIGINT UNSIGNED NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    `correction` TEXT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_investigation_pic` (`pic_id`),
    KEY `fk_investigation_precortive` (`precortive_id`),
    KEY `fk_investigation_ir` (`incident_report_id`),
    CONSTRAINT `fk_investigation_pic` FOREIGN KEY (`pic_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_investigation_precortive` FOREIGN KEY (`precortive_id`) REFERENCES `precortives` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_investigation_ir` FOREIGN KEY (`incident_report_id`) REFERENCES `incident_reports` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- SAFETY OBSERVATION LOOKUP TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS `root_cause_so` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `code` VARCHAR(50) NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `entitys` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `region_ncrs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `locations` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `region_ncr_id` BIGINT UNSIGNED NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_locations_region_ncr` (`region_ncr_id`),
    CONSTRAINT `fk_locations_region_ncr` FOREIGN KEY (`region_ncr_id`) REFERENCES `region_ncrs` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `jso_locations` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `nonplant_locations` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- SAFETY OBSERVATION MAIN TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS `safety_observations` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `no_laporan` VARCHAR(50) NULL,
    `date_event` DATETIME NULL,
    `date_event_jso` DATETIME NULL,
    `description` TEXT NULL,
    `description_jso` TEXT NULL,
    `status` VARCHAR(50) NULL DEFAULT 'Open',
    `corrective` TEXT NULL,
    `entity_name` VARCHAR(255) NULL,
    `wo_number` VARCHAR(100) NULL,
    `date_action` DATETIME NULL,
    `assigned_at` DATETIME NULL,
    `acknowledged_at` DATETIME NULL,
    `done_at` DATETIME NULL,
    `action_taken_at` DATETIME NULL,
    `reason` TEXT NULL,
    `reason2` TEXT NULL,
    `is_jso` TINYINT(1) NULL DEFAULT 0,
    `leader_id` BIGINT UNSIGNED NULL,
    `member_1_id` BIGINT UNSIGNED NULL,
    `member_2_id` BIGINT UNSIGNED NULL,
    `reporter_id` BIGINT UNSIGNED NULL,
    `dept_origin_id` BIGINT UNSIGNED NULL,
    `root_cause_id` BIGINT UNSIGNED NULL,
    `dept_designated_id` BIGINT UNSIGNED NULL,
    `assigned_to_id` BIGINT UNSIGNED NULL,
    `action_by_id` BIGINT UNSIGNED NULL,
    `acknowledged_by_id` BIGINT UNSIGNED NULL,
    `reviewed_by_id` BIGINT UNSIGNED NULL,
    `result_id` BIGINT UNSIGNED NULL,
    `area_id` BIGINT UNSIGNED NULL,
    `location_ncr_id` BIGINT UNSIGNED NULL,
    `location_jso_id` BIGINT UNSIGNED NULL,
    `region_id` BIGINT UNSIGNED NULL,
    `entity_id` BIGINT UNSIGNED NULL,
    `non_plant_location_id` BIGINT UNSIGNED NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_so_leader` (`leader_id`),
    KEY `fk_so_member1` (`member_1_id`),
    KEY `fk_so_member2` (`member_2_id`),
    KEY `fk_so_reporter` (`reporter_id`),
    KEY `fk_so_dept_origin` (`dept_origin_id`),
    KEY `fk_so_root_cause` (`root_cause_id`),
    KEY `fk_so_dept_designated` (`dept_designated_id`),
    KEY `fk_so_assigned_to` (`assigned_to_id`),
    KEY `fk_so_action_by` (`action_by_id`),
    KEY `fk_so_acknowledged_by` (`acknowledged_by_id`),
    KEY `fk_so_reviewed_by` (`reviewed_by_id`),
    KEY `fk_so_result` (`result_id`),
    KEY `fk_so_area` (`area_id`),
    KEY `fk_so_location_ncr` (`location_ncr_id`),
    KEY `fk_so_location_jso` (`location_jso_id`),
    KEY `fk_so_entity` (`entity_id`),
    KEY `fk_so_nonplant` (`non_plant_location_id`),
    CONSTRAINT `fk_so_leader` FOREIGN KEY (`leader_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_member1` FOREIGN KEY (`member_1_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_member2` FOREIGN KEY (`member_2_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_dept_origin` FOREIGN KEY (`dept_origin_id`) REFERENCES `origin_departments` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_root_cause` FOREIGN KEY (`root_cause_id`) REFERENCES `root_cause_so` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_dept_designated` FOREIGN KEY (`dept_designated_id`) REFERENCES `designation_departments` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_assigned_to` FOREIGN KEY (`assigned_to_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_action_by` FOREIGN KEY (`action_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_acknowledged_by` FOREIGN KEY (`acknowledged_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_reviewed_by` FOREIGN KEY (`reviewed_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_result` FOREIGN KEY (`result_id`) REFERENCES `results` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_area` FOREIGN KEY (`area_id`) REFERENCES `area_incidents` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_location_ncr` FOREIGN KEY (`location_ncr_id`) REFERENCES `locations` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_location_jso` FOREIGN KEY (`location_jso_id`) REFERENCES `jso_locations` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_entity` FOREIGN KEY (`entity_id`) REFERENCES `entitys` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_so_nonplant` FOREIGN KEY (`non_plant_location_id`) REFERENCES `nonplant_locations` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `comment_safety_observations` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `so_id` BIGINT UNSIGNED NULL,
    `result_id` BIGINT UNSIGNED NULL,
    `name_id` BIGINT UNSIGNED NULL,
    `description` TEXT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_comment_so_so` (`so_id`),
    KEY `fk_comment_so_result` (`result_id`),
    KEY `fk_comment_so_user` (`name_id`),
    CONSTRAINT `fk_comment_so_so` FOREIGN KEY (`so_id`) REFERENCES `safety_observations` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_comment_so_result` FOREIGN KEY (`result_id`) REFERENCES `results` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_comment_so_user` FOREIGN KEY (`name_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `jso_reports` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `findings` TEXT NULL,
    `potential_hazard` TEXT NULL,
    `recommendation` TEXT NULL,
    `wr_number` VARCHAR(100) NULL,
    `wr_wo_number` VARCHAR(100) NULL,
    `request` TEXT NULL,
    `corrective` TEXT NULL,
    `preventive` TEXT NULL,
    `root_cause_jso` TEXT NULL,
    `category_id` BIGINT UNSIGNED NULL,
    `pic_id` BIGINT UNSIGNED NULL,
    `action_by_id` BIGINT UNSIGNED NULL,
    `delegation_note` TEXT NULL,
    `dept_designated_id` BIGINT UNSIGNED NULL,
    `division_id` BIGINT UNSIGNED NULL,
    `so_id` BIGINT UNSIGNED NULL,
    `area_id` BIGINT UNSIGNED NULL,
    `location_id` BIGINT UNSIGNED NULL,
    `non_plant_location_id` BIGINT UNSIGNED NULL,
    `date_event` DATETIME NULL,
    `description` TEXT NULL,
    `region_id` BIGINT UNSIGNED NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_jso_category` (`category_id`),
    KEY `fk_jso_pic` (`pic_id`),
    KEY `fk_jso_action_by` (`action_by_id`),
    KEY `fk_jso_dept_designated` (`dept_designated_id`),
    KEY `fk_jso_division` (`division_id`),
    KEY `fk_jso_so` (`so_id`),
    KEY `fk_jso_area` (`area_id`),
    KEY `fk_jso_location` (`location_id`),
    KEY `fk_jso_nonplant` (`non_plant_location_id`),
    CONSTRAINT `fk_jso_category` FOREIGN KEY (`category_id`) REFERENCES `root_cause_so` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_jso_pic` FOREIGN KEY (`pic_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_jso_action_by` FOREIGN KEY (`action_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_jso_dept_designated` FOREIGN KEY (`dept_designated_id`) REFERENCES `designation_departments` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_jso_division` FOREIGN KEY (`division_id`) REFERENCES `divisions` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_jso_so` FOREIGN KEY (`so_id`) REFERENCES `safety_observations` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_jso_area` FOREIGN KEY (`area_id`) REFERENCES `area_incidents` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_jso_location` FOREIGN KEY (`location_id`) REFERENCES `jso_locations` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_jso_nonplant` FOREIGN KEY (`non_plant_location_id`) REFERENCES `nonplant_locations` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `jso_users` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_jso_users_user` (`user_id`),
    CONSTRAINT `fk_jso_users_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- ASSET TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS `asset_categories` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `asset_statuses` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `asset_locations` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `assets` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `serial_number` VARCHAR(191) NULL,
    `category_id` BIGINT UNSIGNED NULL,
    `status_id` BIGINT UNSIGNED NULL,
    `location_id` BIGINT UNSIGNED NULL,
    `assigned_to_id` BIGINT UNSIGNED NULL,
    `notes` TEXT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_assets_category` (`category_id`),
    KEY `fk_assets_status` (`status_id`),
    KEY `fk_assets_location` (`location_id`),
    KEY `fk_assets_assigned_to` (`assigned_to_id`),
    CONSTRAINT `fk_assets_category` FOREIGN KEY (`category_id`) REFERENCES `asset_categories` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_assets_status` FOREIGN KEY (`status_id`) REFERENCES `asset_statuses` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_assets_location` FOREIGN KEY (`location_id`) REFERENCES `asset_locations` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_assets_assigned_to` FOREIGN KEY (`assigned_to_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `assets_histories` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `asset_id` BIGINT UNSIGNED NULL,
    `status_id` BIGINT UNSIGNED NULL,
    `location_id` BIGINT UNSIGNED NULL,
    `assigned_to_id` BIGINT UNSIGNED NULL,
    `notes` TEXT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_assets_histories_asset` (`asset_id`),
    CONSTRAINT `fk_assets_histories_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `area_user` (
    `area_incident_id` BIGINT UNSIGNED NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`area_incident_id`, `user_id`),
    CONSTRAINT `fk_area_user_area` FOREIGN KEY (`area_incident_id`) REFERENCES `area_incidents` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_area_user_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- MEDIA TABLE (Spatie Media Library)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `media` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `model_type` VARCHAR(191) NOT NULL,
    `model_id` BIGINT UNSIGNED NOT NULL,
    `uuid` CHAR(36) NULL,
    `collection_name` VARCHAR(191) NOT NULL,
    `name` VARCHAR(191) NOT NULL,
    `file_name` VARCHAR(191) NOT NULL,
    `mime_type` VARCHAR(191) NULL,
    `disk` VARCHAR(191) NOT NULL,
    `conversions_disk` VARCHAR(191) NULL,
    `size` BIGINT UNSIGNED NOT NULL,
    `manipulations` JSON NOT NULL,
    `custom_properties` JSON NOT NULL,
    `generated_conversions` JSON NOT NULL,
    `responsive_images` JSON NOT NULL,
    `order_column` INT UNSIGNED NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `media_uuid_unique` (`uuid`),
    KEY `media_model_type_model_id_index` (`model_type`, `model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- AUDIT LOG TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS `audit_logs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `description` VARCHAR(191) NOT NULL,
    `subject_id` BIGINT UNSIGNED NULL,
    `subject_type` VARCHAR(191) NULL,
    `user_id` BIGINT UNSIGNED NULL,
    `properties` JSON NULL,
    `host` VARCHAR(45) NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_audit_logs_user` (`user_id`),
    KEY `audit_logs_subject_index` (`subject_id`, `subject_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- TIMESHEET TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS `time_projects` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `time_work_types` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(191) NOT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `time_entries` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `work_type_id` BIGINT UNSIGNED NULL,
    `project_id` BIGINT UNSIGNED NULL,
    `start_time` DATETIME NULL,
    `end_time` DATETIME NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    `deleted_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_time_entries_work_type` (`work_type_id`),
    KEY `fk_time_entries_project` (`project_id`),
    CONSTRAINT `fk_time_entries_work_type` FOREIGN KEY (`work_type_id`) REFERENCES `time_work_types` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_time_entries_project` FOREIGN KEY (`project_id`) REFERENCES `time_projects` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- QA TOPICS TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS `qa_topics` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(191) NOT NULL,
    `content` TEXT NULL,
    `created_at` TIMESTAMP NULL DEFAULT NULL,
    `updated_at` TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- MIGRATIONS TABLE (Laravel)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `migrations` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `migration` VARCHAR(191) NOT NULL,
    `batch` INT NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
