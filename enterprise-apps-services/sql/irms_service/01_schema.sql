-- =============================================================================
-- db_irms Schema Migration
-- Generated: 2026-06-22
-- Source: db_nirms (monolith) → db_irms (IRMS microservice)
-- All tables use irms_ prefix. No auth or LIMS tables included.
-- Cross-service FK constraints replaced with plain indexed columns.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS db_irms
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE db_irms;

-- =============================================================================
-- SECTION 1: ORG SNAPSHOTS (synced from db_auth)
-- No passwords. Kept current via sync job / event.
-- =============================================================================

CREATE TABLE IF NOT EXISTS `irms_departments` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code`       varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_irms_departments_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_divisions` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`        varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code`        varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dept_id`     bigint unsigned DEFAULT NULL,
  `created_at`  datetime DEFAULT NULL,
  `updated_at`  datetime DEFAULT NULL,
  `deleted_at`  datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_irms_divisions_dept` (`dept_id`),
  CONSTRAINT `fk_irms_divisions_dept` FOREIGN KEY (`dept_id`) REFERENCES `irms_departments` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_job_titles` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_users` (
  `id`           bigint unsigned NOT NULL AUTO_INCREMENT,
  `npk`          varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `npp`          varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name`         varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email`        varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `mobile_phone` varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rf_id`        varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parent_id`    bigint unsigned DEFAULT NULL,
  `job_id`       bigint unsigned DEFAULT NULL,
  `dept_id`      bigint unsigned DEFAULT NULL,
  `division_id`  bigint unsigned DEFAULT NULL,
  `status`       tinyint NOT NULL DEFAULT '1' COMMENT '1=active 0=inactive',
  `created_at`   datetime DEFAULT NULL,
  `updated_at`   datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_irms_users_email` (`email`),
  KEY `ix_irms_users_npk`      (`npk`),
  KEY `ix_irms_users_npp`      (`npp`),
  KEY `ix_irms_users_parent`   (`parent_id`),
  KEY `ix_irms_users_dept`     (`dept_id`),
  KEY `ix_irms_users_division` (`division_id`),
  KEY `ix_irms_users_job`      (`job_id`)
  -- NOTE: No FK to auth_users. Snapshot is populated by sync job from db_auth.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- SECTION 2: MASTER / LOOKUP TABLES
-- =============================================================================

CREATE TABLE IF NOT EXISTS `irms_category_incidents` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`        varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `created_at`  timestamp NULL DEFAULT NULL,
  `updated_at`  timestamp NULL DEFAULT NULL,
  `deleted_at`  timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_classification_incidents` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `color`      varchar(20)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `level`      int DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_classification_details` (
  `id`                bigint unsigned NOT NULL AUTO_INCREMENT,
  `description`       text COLLATE utf8mb4_unicode_ci,
  `category_id`       bigint unsigned DEFAULT NULL,
  `classification_id` bigint unsigned DEFAULT NULL,
  `created_at`        timestamp NULL DEFAULT NULL,
  `updated_at`        timestamp NULL DEFAULT NULL,
  `deleted_at`        timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_irms_cd_category`       (`category_id`),
  KEY `fk_irms_cd_classification` (`classification_id`),
  CONSTRAINT `fk_irms_cd_category`       FOREIGN KEY (`category_id`)       REFERENCES `irms_category_incidents`      (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_cd_classification` FOREIGN KEY (`classification_id`) REFERENCES `irms_classification_incidents` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_area_incidents` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_region_incidents` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_region_ncrs` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_root_causes` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_root_cause_so` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code`       varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_results` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- precortives → irms_preventive_actions (typo fix)
CREATE TABLE IF NOT EXISTS `irms_preventive_actions` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- entitys → irms_ncr_entities (typo fix)
CREATE TABLE IF NOT EXISTS `irms_ncr_entities` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_jso_locations` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_jso_leader_master` (
  `id`         bigint NOT NULL AUTO_INCREMENT,
  `name`       varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `npp`        varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `department` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `position`   varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_active`  tinyint(1) DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_jso_member_master` (
  `id`         bigint NOT NULL AUTO_INCREMENT,
  `name`       varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `npp`        varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `department` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `position`   varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_active`  tinyint(1) DEFAULT '1',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_nonplant_locations` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- lookup → irms_lookups
CREATE TABLE IF NOT EXISTS `irms_lookups` (
  `id`    bigint NOT NULL AUTO_INCREMENT,
  `name`  varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type`  varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_locations` (
  `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`           varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `region_ncr_id`  bigint unsigned DEFAULT NULL,
  `deleted_at`     timestamp NULL DEFAULT NULL,
  `created_at`     timestamp NULL DEFAULT NULL,
  `updated_at`     timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_irms_locations_region_ncr` (`region_ncr_id`),
  CONSTRAINT `fk_irms_locations_region_ncr` FOREIGN KEY (`region_ncr_id`) REFERENCES `irms_region_ncrs` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_designation_departments` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code`       varchar(20)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `manager_id` bigint unsigned DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_irms_designation_dept_manager` (`manager_id`)
  -- manager_id references irms_users; no FK constraint to keep it loose
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_origin_departments` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code`       varchar(20)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- SECTION 3: CORE TRANSACTIONAL TABLES
-- =============================================================================

CREATE TABLE IF NOT EXISTS `irms_chemical_releases` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`       varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_incident_reports` (
  `id`                        bigint unsigned NOT NULL AUTO_INCREMENT,
  `no_laporan`                varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `report_number`             varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title`                     varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description`               text COLLATE utf8mb4_unicode_ci,
  `reason`                    text COLLATE utf8mb4_unicode_ci,
  `location`                  varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_detail`           varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `perbaikan_awal`            text COLLATE utf8mb4_unicode_ci,
  `corrective`                text COLLATE utf8mb4_unicode_ci,
  `preventive`                text COLLATE utf8mb4_unicode_ci,
  `revision_notes`            text COLLATE utf8mb4_unicode_ci,
  `manager_notes`             text COLLATE utf8mb4_unicode_ci,
  `follow_up_notes`           text COLLATE utf8mb4_unicode_ci,
  `follow_up_attachments`     text COLLATE utf8mb4_unicode_ci,
  `photos`                    text COLLATE utf8mb4_unicode_ci,
  `photo_path`                varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `document_path`             varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status`                    varchar(30)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `manager_approval`          varchar(30)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `superintendent_approval`   varchar(30)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `risk_level`                varchar(30)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `severity`                  int DEFAULT NULL,
  `probability`               int DEFAULT NULL,
  `investigation_required`    bit(1) DEFAULT NULL,
  `investigation_done`        bit(1) DEFAULT NULL,
  `incident_time`             varchar(10)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `date_incident`             datetime DEFAULT NULL,
  `incident_date`             datetime(6)  DEFAULT NULL,
  `date_action`               datetime DEFAULT NULL,
  `assigned_at`               datetime DEFAULT NULL,
  `reviewed_at`               datetime DEFAULT NULL,
  `acknowledged_at`           datetime DEFAULT NULL,
  `done_at`                   datetime DEFAULT NULL,
  `in_progress_at`            datetime DEFAULT NULL,
  `fully_approved_at`         datetime DEFAULT NULL,
  `direct_cost`               decimal(15,2) DEFAULT NULL,
  `copq`                      decimal(15,2) DEFAULT NULL,
  -- User references (no FK to cross-service irms_users; indexed only)
  `reporter_id`               bigint DEFAULT NULL,
  `reviewer_id`               bigint DEFAULT NULL,
  `acknowledger_id`           bigint DEFAULT NULL,
  `action_by_id`              bigint unsigned DEFAULT NULL,
  `assigned_to_id`            bigint unsigned DEFAULT NULL,
  `designated_manager_id`     bigint DEFAULT NULL,
  -- Master data references (FK within db_irms)
  `dept_origin_id`            bigint unsigned DEFAULT NULL,
  `origin_department_id`      bigint DEFAULT NULL,
  `dept_designated_id`        bigint unsigned DEFAULT NULL,
  `designation_department_id` bigint DEFAULT NULL,
  `root_cause_id`             bigint unsigned DEFAULT NULL,
  `category_id`               bigint unsigned DEFAULT NULL,
  `classify_id`               bigint unsigned DEFAULT NULL,
  `classification_id`         bigint DEFAULT NULL,
  `result_id`                 bigint unsigned DEFAULT NULL,
  `area_id`                   bigint unsigned DEFAULT NULL,
  `ri_id`                     bigint unsigned DEFAULT NULL,
  `region_id`                 bigint DEFAULT NULL,
  `cr_id`                     bigint unsigned DEFAULT NULL,
  `chemical_release_id`       bigint DEFAULT NULL,
  `created_at`                timestamp NULL DEFAULT NULL,
  `updated_at`                timestamp NULL DEFAULT NULL,
  `deleted_at`                timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_irms_ir_report_number` (`report_number`),
  KEY `ix_irms_ir_reporter`   (`reporter_id`),
  KEY `ix_irms_ir_action_by`  (`action_by_id`),
  KEY `ix_irms_ir_assigned_to`(`assigned_to_id`),
  KEY `fk_irms_ir_root_cause` (`root_cause_id`),
  KEY `fk_irms_ir_category`   (`category_id`),
  KEY `fk_irms_ir_classify`   (`classify_id`),
  KEY `fk_irms_ir_result`     (`result_id`),
  KEY `fk_irms_ir_area`       (`area_id`),
  KEY `fk_irms_ir_ri`         (`ri_id`),
  KEY `fk_irms_ir_cr`         (`cr_id`),
  KEY `fk_irms_ir_dept_origin`     (`dept_origin_id`),
  KEY `fk_irms_ir_dept_designated` (`dept_designated_id`),
  CONSTRAINT `fk_irms_ir_root_cause`      FOREIGN KEY (`root_cause_id`)   REFERENCES `irms_root_causes`             (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_ir_category`        FOREIGN KEY (`category_id`)     REFERENCES `irms_category_incidents`      (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_ir_classify`        FOREIGN KEY (`classify_id`)     REFERENCES `irms_classification_incidents`(`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_ir_result`          FOREIGN KEY (`result_id`)       REFERENCES `irms_results`                 (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_ir_area`            FOREIGN KEY (`area_id`)         REFERENCES `irms_area_incidents`          (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_ir_ri`              FOREIGN KEY (`ri_id`)           REFERENCES `irms_region_incidents`        (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_ir_cr`              FOREIGN KEY (`cr_id`)           REFERENCES `irms_chemical_releases`       (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_ir_dept_origin`     FOREIGN KEY (`dept_origin_id`)     REFERENCES `irms_origin_departments`      (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_ir_dept_designated` FOREIGN KEY (`dept_designated_id`) REFERENCES `irms_designation_departments` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_safety_observations` (
  `id`                        bigint unsigned NOT NULL AUTO_INCREMENT,
  `no_laporan`                varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `report_number`             varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title`                     varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description`               text COLLATE utf8mb4_unicode_ci,
  `description_jso`           text COLLATE utf8mb4_unicode_ci,
  `status`                    varchar(30)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `corrective`                text COLLATE utf8mb4_unicode_ci,
  `entity_name`               varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `wo_number`                 varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason`                    text COLLATE utf8mb4_unicode_ci,
  `reason2`                   text COLLATE utf8mb4_unicode_ci,
  `delegation_note`           text COLLATE utf8mb4_unicode_ci,
  `is_jso`                    tinyint(1) DEFAULT '0',
  `photos`                    text COLLATE utf8mb4_unicode_ci,
  `photo_path`                varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `document_path`             varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `admin_approval`            varchar(30)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `safety_admin_approval`     varchar(30)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `manager_approval`          varchar(30)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_detail`           varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `date_event`                datetime DEFAULT NULL,
  `date_event_jso`            datetime DEFAULT NULL,
  `observation_date`          datetime(6)  DEFAULT NULL,
  `date_action`               datetime DEFAULT NULL,
  `assigned_at`               datetime DEFAULT NULL,
  `acknowledged_at`           datetime DEFAULT NULL,
  `done_at`                   datetime DEFAULT NULL,
  `action_taken_at`           datetime DEFAULT NULL,
  -- User references (no FK; indexed only)
  `leader_id`                 bigint unsigned DEFAULT NULL,
  `member_1_id`               bigint unsigned DEFAULT NULL,
  `member_2_id`               bigint unsigned DEFAULT NULL,
  `reporter_id`               bigint unsigned DEFAULT NULL,
  `action_by_id`              bigint unsigned DEFAULT NULL,
  `assigned_to_id`            bigint unsigned DEFAULT NULL,
  `acknowledged_by_id`        bigint unsigned DEFAULT NULL,
  `reviewed_by_id`            bigint unsigned DEFAULT NULL,
  -- Master data references (FK within db_irms)
  `dept_origin_id`            bigint unsigned DEFAULT NULL,
  `origin_department_id`      bigint DEFAULT NULL,
  `dept_designated_id`        bigint unsigned DEFAULT NULL,
  `designation_department_id` bigint DEFAULT NULL,
  `root_cause_id`             bigint unsigned DEFAULT NULL,
  `result_id`                 bigint unsigned DEFAULT NULL,
  `area_id`                   bigint unsigned DEFAULT NULL,
  `location_ncr_id`           bigint unsigned DEFAULT NULL,
  `location_id`               bigint DEFAULT NULL,
  `location_jso_id`           bigint unsigned DEFAULT NULL,
  `jso_location_id`           bigint DEFAULT NULL,
  `region_id`                 bigint unsigned DEFAULT NULL,
  `region_ncr_id`             bigint DEFAULT NULL,
  `entity_id`                 bigint unsigned DEFAULT NULL,
  `non_plant_location_id`     bigint unsigned DEFAULT NULL,
  `nonplant_location_id`      bigint DEFAULT NULL,
  `created_at`                timestamp NULL DEFAULT NULL,
  `updated_at`                timestamp NULL DEFAULT NULL,
  `deleted_at`                timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_irms_so_report_number` (`report_number`),
  KEY `ix_irms_so_leader`         (`leader_id`),
  KEY `ix_irms_so_member1`        (`member_1_id`),
  KEY `ix_irms_so_member2`        (`member_2_id`),
  KEY `ix_irms_so_reporter`       (`reporter_id`),
  KEY `fk_irms_so_dept_origin`    (`dept_origin_id`),
  KEY `fk_irms_so_root_cause`     (`root_cause_id`),
  KEY `fk_irms_so_dept_designated`(`dept_designated_id`),
  KEY `ix_irms_so_assigned_to`    (`assigned_to_id`),
  KEY `ix_irms_so_action_by`      (`action_by_id`),
  KEY `ix_irms_so_acknowledged_by`(`acknowledged_by_id`),
  KEY `ix_irms_so_reviewed_by`    (`reviewed_by_id`),
  KEY `fk_irms_so_result`         (`result_id`),
  KEY `fk_irms_so_area`           (`area_id`),
  KEY `fk_irms_so_location_ncr`   (`location_ncr_id`),
  KEY `fk_irms_so_location_jso`   (`location_jso_id`),
  KEY `fk_irms_so_entity`         (`entity_id`),
  KEY `fk_irms_so_nonplant`       (`non_plant_location_id`),
  CONSTRAINT `fk_irms_so_dept_origin`     FOREIGN KEY (`dept_origin_id`)     REFERENCES `irms_origin_departments`      (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_so_dept_designated` FOREIGN KEY (`dept_designated_id`) REFERENCES `irms_designation_departments` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_so_root_cause`      FOREIGN KEY (`root_cause_id`)     REFERENCES `irms_root_cause_so`           (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_so_result`          FOREIGN KEY (`result_id`)         REFERENCES `irms_results`                 (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_so_area`            FOREIGN KEY (`area_id`)           REFERENCES `irms_area_incidents`          (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_so_location_ncr`    FOREIGN KEY (`location_ncr_id`)   REFERENCES `irms_locations`               (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_so_location_jso`    FOREIGN KEY (`location_jso_id`)   REFERENCES `irms_jso_locations`           (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_so_entity`          FOREIGN KEY (`entity_id`)         REFERENCES `irms_ncr_entities`            (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_so_nonplant`        FOREIGN KEY (`non_plant_location_id`) REFERENCES `irms_nonplant_locations`  (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_jso_reports` (
  `id`                  bigint unsigned NOT NULL AUTO_INCREMENT,
  `findings`            text COLLATE utf8mb4_unicode_ci,
  `potential_hazard`    text COLLATE utf8mb4_unicode_ci,
  `recommendation`      text COLLATE utf8mb4_unicode_ci,
  `wr_number`           varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `wr_wo_number`        varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request`             text COLLATE utf8mb4_unicode_ci,
  `corrective`          text COLLATE utf8mb4_unicode_ci,
  `preventive`          text COLLATE utf8mb4_unicode_ci,
  `root_cause_jso`      text COLLATE utf8mb4_unicode_ci,
  `delegation_note`     text COLLATE utf8mb4_unicode_ci,
  `date_event`          datetime DEFAULT NULL,
  `description`         text COLLATE utf8mb4_unicode_ci,
  -- User references (no FK; indexed only)
  `pic_id`              bigint unsigned DEFAULT NULL,
  `action_by_id`        bigint unsigned DEFAULT NULL,
  `division_id`         bigint unsigned DEFAULT NULL,
  -- Master data references (FK within db_irms)
  `category_id`         bigint unsigned DEFAULT NULL,
  `dept_designated_id`  bigint unsigned DEFAULT NULL,
  `so_id`               bigint unsigned DEFAULT NULL,
  `area_id`             bigint unsigned DEFAULT NULL,
  `location_id`         bigint unsigned DEFAULT NULL,
  `non_plant_location_id` bigint unsigned DEFAULT NULL,
  `region_id`           bigint unsigned DEFAULT NULL,
  `created_at`          timestamp NULL DEFAULT NULL,
  `updated_at`          timestamp NULL DEFAULT NULL,
  `deleted_at`          timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_irms_jso_pic`        (`pic_id`),
  KEY `ix_irms_jso_action_by`  (`action_by_id`),
  KEY `fk_irms_jso_category`   (`category_id`),
  KEY `fk_irms_jso_dept`       (`dept_designated_id`),
  KEY `fk_irms_jso_so`         (`so_id`),
  KEY `fk_irms_jso_area`       (`area_id`),
  KEY `fk_irms_jso_location`   (`location_id`),
  KEY `fk_irms_jso_nonplant`   (`non_plant_location_id`),
  CONSTRAINT `fk_irms_jso_category`  FOREIGN KEY (`category_id`)          REFERENCES `irms_root_cause_so`           (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_jso_dept`      FOREIGN KEY (`dept_designated_id`)   REFERENCES `irms_designation_departments` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_jso_so`        FOREIGN KEY (`so_id`)                REFERENCES `irms_safety_observations`     (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_irms_jso_area`      FOREIGN KEY (`area_id`)              REFERENCES `irms_area_incidents`          (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_jso_location`  FOREIGN KEY (`location_id`)          REFERENCES `irms_jso_locations`           (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_jso_nonplant`  FOREIGN KEY (`non_plant_location_id`) REFERENCES `irms_nonplant_locations`     (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_investigation_details` (
  `id`                 bigint unsigned NOT NULL AUTO_INCREMENT,
  `recommendation`     text COLLATE utf8mb4_unicode_ci,
  `correction`         text COLLATE utf8mb4_unicode_ci,
  `corrective_action`  text COLLATE utf8mb4_unicode_ci,
  `preventive_action`  text COLLATE utf8mb4_unicode_ci,
  `description`        text COLLATE utf8mb4_unicode_ci,
  `finding`            text COLLATE utf8mb4_unicode_ci,
  `status`             varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `date_deadline`      date DEFAULT NULL,
  `date_actual`        date DEFAULT NULL,
  `due_date`           datetime(6) DEFAULT NULL,
  -- User references (no FK; indexed only)
  `pic_id`             bigint unsigned DEFAULT NULL,
  `action_by_id`       bigint DEFAULT NULL,
  -- Master data references
  `precortive_id`      bigint unsigned DEFAULT NULL,
  `incident_report_id` bigint unsigned DEFAULT NULL,
  `created_at`         timestamp NULL DEFAULT NULL,
  `updated_at`         timestamp NULL DEFAULT NULL,
  `deleted_at`         timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_irms_inv_pic`        (`pic_id`),
  KEY `fk_irms_inv_preventive` (`precortive_id`),
  KEY `fk_irms_inv_ir`         (`incident_report_id`),
  CONSTRAINT `fk_irms_inv_preventive` FOREIGN KEY (`precortive_id`)      REFERENCES `irms_preventive_actions` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_irms_inv_ir`         FOREIGN KEY (`incident_report_id`) REFERENCES `irms_incident_reports`   (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_task_assignments` (
  `id`              bigint unsigned NOT NULL AUTO_INCREMENT,
  `title`           varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description`     text COLLATE utf8mb4_unicode_ci,
  `status`          varchar(50)  COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Todo',
  `priority`        varchar(20)  COLLATE utf8mb4_unicode_ci DEFAULT 'Medium',
  `start_date`      date NOT NULL,
  `end_date`        date NOT NULL,
  `daily_report_id` bigint unsigned DEFAULT NULL,
  -- User references (no FK; indexed only)
  `assignee_id`     bigint unsigned DEFAULT NULL,
  `assigner_id`     bigint unsigned DEFAULT NULL,
  `created_at`      timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`      timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_irms_task_assignee`   (`assignee_id`),
  KEY `ix_irms_task_assigner`   (`assigner_id`),
  KEY `ix_irms_task_status`     (`status`),
  KEY `ix_irms_task_date_range` (`start_date`,`end_date`),
  CONSTRAINT `chk_irms_task_date_range` CHECK (`start_date` <= `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_distribution_incidents` (
  `id`               bigint unsigned NOT NULL AUTO_INCREMENT,
  `incident_report_id` bigint unsigned DEFAULT NULL,
  `user_id`          bigint unsigned DEFAULT NULL,
  `created_at`       timestamp NULL DEFAULT NULL,
  `updated_at`       timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_irms_dist_ir`   (`incident_report_id`),
  KEY `ix_irms_dist_user` (`user_id`),
  CONSTRAINT `fk_irms_dist_ir` FOREIGN KEY (`incident_report_id`) REFERENCES `irms_incident_reports` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_comment_safety_observations` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `so_id`       bigint unsigned DEFAULT NULL,
  `result_id`   bigint unsigned DEFAULT NULL,
  `name_id`     bigint unsigned DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `created_at`  timestamp NULL DEFAULT NULL,
  `updated_at`  timestamp NULL DEFAULT NULL,
  `deleted_at`  timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_irms_cso_so`     (`so_id`),
  KEY `fk_irms_cso_result` (`result_id`),
  KEY `ix_irms_cso_user`   (`name_id`),
  CONSTRAINT `fk_irms_cso_so`     FOREIGN KEY (`so_id`)     REFERENCES `irms_safety_observations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_irms_cso_result` FOREIGN KEY (`result_id`) REFERENCES `irms_results`             (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- SECTION 4: NOTIFICATION / TOKEN TABLES
-- =============================================================================

CREATE TABLE IF NOT EXISTS `irms_fcm_tokens` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id`     bigint unsigned NOT NULL,
  `token`       varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `device_info` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Browser/device user agent',
  `created_at`  timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_irms_fcm_token` (`token`),
  KEY `ix_irms_fcm_user` (`user_id`)
  -- user_id references irms_users; no FK constraint across snapshot boundary
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `irms_user_alerts` (
  `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
  `alert_text`     text COLLATE utf8mb4_unicode_ci NOT NULL,
  `alert_link`     varchar(191) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `alert_type`     varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_id`   bigint DEFAULT NULL,
  `reference_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at`     timestamp NULL DEFAULT NULL,
  `updated_at`     timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_irms_alert_type` (`alert_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- user_user_alert → irms_user_user_alerts (plural fix)
CREATE TABLE IF NOT EXISTS `irms_user_user_alerts` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_alert_id` bigint unsigned NOT NULL,
  `user_id`       bigint unsigned NOT NULL,
  `read`          tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `fk_irms_uua_alert` (`user_alert_id`),
  KEY `ix_irms_uua_user`  (`user_id`),
  KEY `ix_irms_uua_user_read` (`user_id`,`read`),
  CONSTRAINT `fk_irms_uua_alert` FOREIGN KEY (`user_alert_id`) REFERENCES `irms_user_alerts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- SECTION 5: JSO USER WHITELIST
-- =============================================================================

CREATE TABLE IF NOT EXISTS `irms_jso_users` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id`    bigint unsigned DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_irms_jso_users_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- SECTION 6: AUDIT LOG
-- =============================================================================

CREATE TABLE IF NOT EXISTS `irms_audit_logs` (
  `id`           bigint unsigned NOT NULL AUTO_INCREMENT,
  `description`  varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_id`   bigint unsigned DEFAULT NULL,
  `subject_type` varchar(191) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id`      bigint unsigned DEFAULT NULL,
  `properties`   json DEFAULT NULL,
  `host`         varchar(45)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at`   timestamp NULL DEFAULT NULL,
  `updated_at`   timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_irms_audit_user`    (`user_id`),
  KEY `ix_irms_audit_subject` (`subject_id`,`subject_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- SECTION 7: NEW TABLES (from Spring entities, no data in db_nirms)
-- =============================================================================

-- irms_incident_tasks — tasks assigned per incident report
CREATE TABLE IF NOT EXISTS `irms_incident_tasks` (
  `id`               bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_number`      varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `incident_report_id` bigint unsigned NOT NULL,
  `assigner_id`      bigint unsigned NOT NULL,
  `assignee_id`      bigint unsigned NOT NULL,
  `instruction`      text COLLATE utf8mb4_unicode_ci,
  `deadline`         date DEFAULT NULL,
  `priority`         varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'Medium',
  `status`           varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Pending',
  `kronologi`        text COLLATE utf8mb4_unicode_ci,
  `dampak`           text COLLATE utf8mb4_unicode_ci,
  `solusi_sementara` text COLLATE utf8mb4_unicode_ci,
  `corrective_action` text COLLATE utf8mb4_unicode_ci,
  `preventive_action` text COLLATE utf8mb4_unicode_ci,
  `evidence`         text COLLATE utf8mb4_unicode_ci,
  `catatan`          text COLLATE utf8mb4_unicode_ci,
  `started_at`       datetime DEFAULT NULL,
  `completed_at`     datetime DEFAULT NULL,
  `created_at`       datetime DEFAULT NULL,
  `updated_at`       datetime DEFAULT NULL,
  `deleted_at`       datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_irms_it_task_number` (`task_number`),
  KEY `fk_irms_it_ir`       (`incident_report_id`),
  KEY `ix_irms_it_assigner` (`assigner_id`),
  KEY `ix_irms_it_assignee` (`assignee_id`),
  CONSTRAINT `fk_irms_it_ir` FOREIGN KEY (`incident_report_id`) REFERENCES `irms_incident_reports` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- irms_observation_tasks — tasks assigned per safety observation
CREATE TABLE IF NOT EXISTS `irms_observation_tasks` (
  `id`                   bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_number`          varchar(50)  COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `safety_observation_id` bigint unsigned DEFAULT NULL,
  `title`                varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description`          text COLLATE utf8mb4_unicode_ci,
  `findings`             text COLLATE utf8mb4_unicode_ci,
  `corrective_action`    text COLLATE utf8mb4_unicode_ci,
  `preventive_action`    text COLLATE utf8mb4_unicode_ci,
  `assigned_to_id`       bigint unsigned DEFAULT NULL,
  `assigned_by_id`       bigint unsigned DEFAULT NULL,
  `priority`             varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `due_date`             datetime DEFAULT NULL,
  `status`               varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `completion_notes`     text COLLATE utf8mb4_unicode_ci,
  `completed_at`         datetime DEFAULT NULL,
  `completed_by_id`      bigint unsigned DEFAULT NULL,
  `verified_by_id`       bigint unsigned DEFAULT NULL,
  `verified_at`          datetime DEFAULT NULL,
  `verification_notes`   text COLLATE utf8mb4_unicode_ci,
  `created_at`           datetime DEFAULT NULL,
  `updated_at`           datetime DEFAULT NULL,
  `deleted_at`           datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_irms_ot_task_number` (`task_number`),
  KEY `fk_irms_ot_so`          (`safety_observation_id`),
  KEY `ix_irms_ot_assigned_to` (`assigned_to_id`),
  KEY `ix_irms_ot_assigned_by` (`assigned_by_id`),
  CONSTRAINT `fk_irms_ot_so` FOREIGN KEY (`safety_observation_id`) REFERENCES `irms_safety_observations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- irms_chart_of_accounts — cost tracking for incidents
CREATE TABLE IF NOT EXISTS `irms_chart_of_accounts` (
  `id`           bigint unsigned NOT NULL AUTO_INCREMENT,
  `account_code` varchar(50)  COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_name` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_type` varchar(50)  COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_id`    bigint unsigned DEFAULT NULL,
  `description`  text COLLATE utf8mb4_unicode_ci,
  `is_active`    tinyint(1) NOT NULL DEFAULT '1',
  `created_at`   datetime DEFAULT NULL,
  `updated_at`   datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_irms_coa_account_code` (`account_code`),
  KEY `ix_irms_coa_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- End of 01_schema.sql
