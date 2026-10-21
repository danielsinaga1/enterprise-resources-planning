-- ====================================================================
--  db_lims — Transactional data migration (historical lab records)
--
--  These tables are bulky and unicode-heavy, so they are migrated by
--  copying straight from the legacy dump rather than hand-transcribed.
--
--  PREREQUISITE — load the legacy dump into a staging schema first:
--      mysql> CREATE DATABASE lims_legacy CHARACTER SET utf8mb4;
--      $ mysql lims_legacy < sql/db_lims.sql
--
--  Only columns that exist in the clean schema are carried over; the
--  legacy report-level method/unit/standard/parameter columns are
--  dropped (those belong at the analysis-detail level).
-- ====================================================================

USE db_lims;
SET FOREIGN_KEY_CHECKS = 0;

-- Laboratory reports -------------------------------------------------
INSERT INTO lims_laboratory_reports
    (id, no_report, date_sampling, location, status, requested_by, date_analysis,
     remark, notes, reviewed_at, acknowledge_at, sampler_id, analyst_id,
     reviewed_by_id, acknowledge_by_id, department_id, reporter_id,
     created_at, updated_at, deleted_at)
SELECT
    id, no_report, DATE(date_sampling), location, status, requested_by, DATE(date_analysis),
    remark, notes, reviewed_at, acknowledge_at, sampler_id, analyst_id,
    reviewed_by_id, acknowledge_by_id, department_id, reporter_id,
    created_at, updated_at, deleted_at
FROM lims_legacy.laboratory_reports;

-- Laboratory (analysis) details --------------------------------------
INSERT INTO lims_laboratory_details
    (id, description, status, result, date_sampling, date_analysis,
     laboratory_report_id, parameter_id, standard_id, test_type_id, unit_id, method_id,
     created_at, updated_at, deleted_at)
SELECT
    id, description, status, result, DATE(date_sampling), DATE(date_analysis),
    laboratory_report_id, parameter_id, standard_id, test_type_id, unit_id, method_id,
    created_at, updated_at, deleted_at
FROM lims_legacy.laboratory_details;

-- Daily reports ------------------------------------------------------
INSERT INTO lims_daily_reports
    (id, name, description, due_date, location, assigned_to_id, unit_id, parameter_id,
     created_at, updated_at, deleted_at)
SELECT
    id, name, description, due_date, location, assigned_to_id, unit_id, parameter_id,
    created_at, updated_at, deleted_at
FROM lims_legacy.daily_reports;

SET FOREIGN_KEY_CHECKS = 1;

-- Realign AUTO_INCREMENT after id-preserving copy.
ALTER TABLE lims_laboratory_reports AUTO_INCREMENT = 33;
ALTER TABLE lims_laboratory_details AUTO_INCREMENT = 70;
ALTER TABLE lims_daily_reports      AUTO_INCREMENT = 10;
