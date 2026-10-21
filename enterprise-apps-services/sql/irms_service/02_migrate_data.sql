-- =============================================================================
-- db_irms Data Migration
-- Generated: 2026-06-22
-- Source: db_nirms (monolith) + db_auth (org snapshots) → db_irms
--
-- Run AFTER 01_schema.sql.
-- Requires both db_nirms and db_auth to be accessible from same MySQL instance.
-- =============================================================================

USE db_irms;

SET FOREIGN_KEY_CHECKS = 0;
SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';

-- =============================================================================
-- SECTION 1: ORG SNAPSHOTS (from db_auth)
-- Only identity columns — no passwords, no tokens.
-- =============================================================================

-- NOTE: auth_divisions does NOT have designation_dept_id; omitted.
INSERT INTO `irms_departments` (id, name, code, created_at, updated_at, deleted_at)
SELECT id, name, code, created_at, updated_at, deleted_at
FROM db_auth.`auth_departments`;

INSERT INTO `irms_divisions` (id, name, code, dept_id, created_at, updated_at, deleted_at)
SELECT id, name, code, dept_id, created_at, updated_at, deleted_at
FROM db_auth.`auth_divisions`;

INSERT INTO `irms_job_titles` (id, name, created_at, updated_at, deleted_at)
SELECT id, name, created_at, updated_at, deleted_at
FROM db_auth.`auth_job_titles`;

-- auth_users: select identity columns only (no password, no pass_salt, no last_login, no attempts)
INSERT INTO `irms_users` (id, npk, npp, name, email, mobile_phone, rf_id, parent_id, job_id, dept_id, division_id, status, created_at, updated_at)
SELECT id, npk, npp, name, email, mobile_phone, rf_id, parent_id, job_id, dept_id, division_id, status, created_at, updated_at
FROM db_auth.`auth_users`;

-- =============================================================================
-- SECTION 2: MASTER / LOOKUP TABLES (from db_nirms)
-- =============================================================================

INSERT INTO `irms_category_incidents` (id, name, description, created_at, updated_at, deleted_at)
SELECT id, name, description, created_at, updated_at, deleted_at
FROM db_nirms.`category_incidents`;

INSERT INTO `irms_classification_incidents` (id, name, color, level, created_at, updated_at, deleted_at)
SELECT id, name, color, level, created_at, updated_at, deleted_at
FROM db_nirms.`classification_incidents`;

INSERT INTO `irms_classification_details` (id, description, category_id, classification_id, created_at, updated_at, deleted_at)
SELECT id, description, category_id, classification_id, created_at, updated_at, deleted_at
FROM db_nirms.`classification_details`;

INSERT INTO `irms_area_incidents` (id, name, created_at, updated_at, deleted_at)
SELECT id, name, created_at, updated_at, deleted_at
FROM db_nirms.`area_incidents`;

INSERT INTO `irms_region_incidents` (id, name, created_at, updated_at, deleted_at)
SELECT id, name, created_at, updated_at, deleted_at
FROM db_nirms.`region_incidents`;

INSERT INTO `irms_region_ncrs` (id, name, deleted_at, created_at, updated_at)
SELECT id, name, deleted_at, created_at, updated_at
FROM db_nirms.`region_ncrs`;

INSERT INTO `irms_root_causes` (id, name, created_at, updated_at, deleted_at)
SELECT id, name, created_at, updated_at, deleted_at
FROM db_nirms.`root_causes`;

INSERT INTO `irms_root_cause_so` (id, name, code, created_at, updated_at)
SELECT id, name, code, created_at, updated_at
FROM db_nirms.`root_cause_so`;

INSERT INTO `irms_results` (id, name, created_at, updated_at, deleted_at)
SELECT id, name, created_at, updated_at, deleted_at
FROM db_nirms.`results`;

-- precortives → irms_preventive_actions (typo fix; columns identical)
INSERT INTO `irms_preventive_actions` (id, name, created_at, updated_at, deleted_at)
SELECT id, name, created_at, updated_at, deleted_at
FROM db_nirms.`precortives`;

-- entitys → irms_ncr_entities (typo fix; columns identical)
INSERT INTO `irms_ncr_entities` (id, name, created_at, updated_at, deleted_at)
SELECT id, name, created_at, updated_at, deleted_at
FROM db_nirms.`entitys`;

INSERT INTO `irms_jso_locations` (id, name, created_at, updated_at)
SELECT id, name, created_at, updated_at
FROM db_nirms.`jso_locations`;

INSERT INTO `irms_jso_leader_master` (id, name, npp, department, position, is_active, created_at, updated_at)
SELECT id, name, npp, department, position, is_active, created_at, updated_at
FROM db_nirms.`jso_leader_master`;

INSERT INTO `irms_jso_member_master` (id, name, npp, department, position, is_active, created_at, updated_at)
SELECT id, name, npp, department, position, is_active, created_at, updated_at
FROM db_nirms.`jso_member_master`;

INSERT INTO `irms_nonplant_locations` (id, name, deleted_at, created_at, updated_at)
SELECT id, name, deleted_at, created_at, updated_at
FROM db_nirms.`nonplant_locations`;

-- lookup → irms_lookups
INSERT INTO `irms_lookups` (id, name, type, value)
SELECT id, name, type, value
FROM db_nirms.`lookup`;

INSERT INTO `irms_locations` (id, name, region_ncr_id, deleted_at, created_at, updated_at)
SELECT id, name, region_ncr_id, deleted_at, created_at, updated_at
FROM db_nirms.`locations`;

-- designation_departments: manager_id originally FK to users; now just indexed bigint
INSERT INTO `irms_designation_departments` (id, name, code, manager_id, created_at, updated_at, deleted_at)
SELECT id, name, code, manager_id, created_at, updated_at, deleted_at
FROM db_nirms.`designation_departments`;

-- origin_departments: source department for incident/safety observation reports
INSERT INTO `irms_origin_departments` (id, name, code, created_at, updated_at, deleted_at)
SELECT id, name, code, created_at, updated_at, deleted_at
FROM db_nirms.`origin_departments`;

-- =============================================================================
-- SECTION 3: CORE TRANSACTIONAL TABLES (from db_nirms)
-- =============================================================================

INSERT INTO `irms_chemical_releases` (id, name, created_at, updated_at, deleted_at)
SELECT id, name, created_at, updated_at, deleted_at
FROM db_nirms.`chemical_releases`;

-- incident_reports: dept_origin_id FK → irms_origin_departments, dept_designated_id FK → irms_designation_departments.
-- origin_department_id and designation_department_id are plain bigint columns from Spring (no FK).
INSERT INTO `irms_incident_reports` (
  id, no_laporan, report_number, title, description, reason,
  location, location_detail,
  perbaikan_awal, corrective, preventive,
  revision_notes, manager_notes, follow_up_notes, follow_up_attachments,
  photos, photo_path, document_path,
  status, manager_approval, superintendent_approval,
  risk_level, severity, probability,
  investigation_required, investigation_done,
  incident_time, date_incident, incident_date, date_action,
  assigned_at, reviewed_at, acknowledged_at, done_at, in_progress_at, fully_approved_at,
  direct_cost, copq,
  reporter_id, reviewer_id, acknowledger_id,
  action_by_id, assigned_to_id, designated_manager_id,
  dept_origin_id, origin_department_id, dept_designated_id, designation_department_id,
  root_cause_id, category_id, classify_id, classification_id,
  result_id, area_id, ri_id, region_id,
  cr_id, chemical_release_id,
  created_at, updated_at, deleted_at
)
SELECT
  id, no_laporan, report_number, title, description, reason,
  location, location_detail,
  perbaikan_awal, corrective, preventive,
  revision_notes, manager_notes, follow_up_notes, follow_up_attachments,
  photos, photo_path, document_path,
  status, manager_approval, superintendent_approval,
  risk_level, severity, probability,
  investigation_required, investigation_done,
  incident_time, date_incident, incident_date, date_action,
  assigned_at, reviewed_at, acknowledged_at, done_at, in_progress_at, fully_approved_at,
  direct_cost, copq,
  reporter_id, reviewer_id, acknowledger_id,
  action_by_id, assigned_to_id, designated_manager_id,
  dept_origin_id, origin_department_id, dept_designated_id, designation_department_id,
  root_cause_id, category_id, classify_id, classification_id,
  result_id, area_id, ri_id, region_id,
  cr_id, chemical_release_id,
  created_at, updated_at, deleted_at
FROM db_nirms.`incident_reports`;

INSERT INTO `irms_safety_observations` (
  id, no_laporan, report_number, title, description, description_jso,
  status, corrective, entity_name, wo_number, reason, reason2, delegation_note,
  is_jso, photos, photo_path, document_path,
  admin_approval, safety_admin_approval, manager_approval, location_detail,
  date_event, date_event_jso, observation_date, date_action,
  assigned_at, acknowledged_at, done_at, action_taken_at,
  leader_id, member_1_id, member_2_id, reporter_id,
  action_by_id, assigned_to_id, acknowledged_by_id, reviewed_by_id,
  dept_origin_id, origin_department_id, dept_designated_id, designation_department_id,
  root_cause_id, result_id, area_id,
  location_ncr_id, location_id, location_jso_id, jso_location_id,
  region_id, region_ncr_id, entity_id,
  non_plant_location_id, nonplant_location_id,
  created_at, updated_at, deleted_at
)
SELECT
  id, no_laporan, report_number, title, description, description_jso,
  status, corrective, entity_name, wo_number, reason, reason2, delegation_note,
  is_jso, photos, photo_path, document_path,
  admin_approval, safety_admin_approval, manager_approval, location_detail,
  date_event, date_event_jso, observation_date, date_action,
  assigned_at, acknowledged_at, done_at, action_taken_at,
  leader_id, member_1_id, member_2_id, reporter_id,
  action_by_id, assigned_to_id, acknowledged_by_id, reviewed_by_id,
  dept_origin_id, origin_department_id, dept_designated_id, designation_department_id,
  root_cause_id, result_id, area_id,
  location_ncr_id, location_id, location_jso_id, jso_location_id,
  region_id, region_ncr_id, entity_id,
  non_plant_location_id, nonplant_location_id,
  created_at, updated_at, deleted_at
FROM db_nirms.`safety_observations`;

INSERT INTO `irms_jso_reports` (
  id, findings, potential_hazard, recommendation,
  wr_number, wr_wo_number, request, corrective, preventive, root_cause_jso,
  delegation_note, date_event, description,
  pic_id, action_by_id, division_id,
  category_id, dept_designated_id, so_id, area_id, location_id, non_plant_location_id, region_id,
  created_at, updated_at, deleted_at
)
SELECT
  id, findings, potential_hazard, recommendation,
  wr_number, wr_wo_number, request, corrective, preventive, root_cause_jso,
  delegation_note, date_event, description,
  pic_id, action_by_id, division_id,
  category_id, dept_designated_id, so_id, area_id, location_id, non_plant_location_id, region_id,
  created_at, updated_at, deleted_at
FROM db_nirms.`jso_reports`;

-- investigation_details: precortive_id → still named precortive_id in db_irms column,
-- but its CONSTRAINT now points to irms_preventive_actions (the renamed table).
INSERT INTO `irms_investigation_details` (
  id, recommendation, correction, corrective_action, preventive_action,
  description, finding, status,
  date_deadline, date_actual, due_date,
  pic_id, action_by_id,
  precortive_id, incident_report_id,
  created_at, updated_at, deleted_at
)
SELECT
  id, recommendation, correction, corrective_action, preventive_action,
  description, finding, status,
  date_deadline, date_actual, due_date,
  pic_id, action_by_id,
  precortive_id, incident_report_id,
  created_at, updated_at, deleted_at
FROM db_nirms.`investigation_details`;

INSERT INTO `irms_task_assignments` (
  id, title, description, status, priority,
  start_date, end_date, daily_report_id,
  assignee_id, assigner_id,
  created_at, updated_at, deleted_at
)
SELECT
  id, title, description, status, priority,
  start_date, end_date, daily_report_id,
  assignee_id, assigner_id,
  created_at, updated_at, deleted_at
FROM db_nirms.`task_assignments`;

INSERT INTO `irms_distribution_incidents` (id, incident_report_id, user_id, created_at, updated_at)
SELECT id, incident_report_id, user_id, created_at, updated_at
FROM db_nirms.`distribution_incidents`;

INSERT INTO `irms_comment_safety_observations` (id, so_id, result_id, name_id, description, created_at, updated_at, deleted_at)
SELECT id, so_id, result_id, name_id, description, created_at, updated_at, deleted_at
FROM db_nirms.`comment_safety_observations`;

-- =============================================================================
-- SECTION 4: NOTIFICATION / TOKEN TABLES (from db_nirms)
-- =============================================================================

INSERT INTO `irms_fcm_tokens` (id, user_id, token, device_info, created_at, updated_at)
SELECT id, user_id, token, device_info, created_at, updated_at
FROM db_nirms.`fcm_tokens`;

INSERT INTO `irms_user_alerts` (id, alert_text, alert_link, alert_type, reference_id, reference_type, created_at, updated_at)
SELECT id, alert_text, alert_link, alert_type, reference_id, reference_type, created_at, updated_at
FROM db_nirms.`user_alerts`;

-- user_user_alert → irms_user_user_alerts (plural fix; columns identical)
INSERT INTO `irms_user_user_alerts` (id, user_alert_id, user_id, `read`)
SELECT id, user_alert_id, user_id, `read`
FROM db_nirms.`user_user_alert`;

-- =============================================================================
-- SECTION 5: JSO USER WHITELIST (from db_nirms)
-- =============================================================================

INSERT INTO `irms_jso_users` (id, user_id, created_at, updated_at)
SELECT id, user_id, created_at, updated_at
FROM db_nirms.`jso_users`;

-- =============================================================================
-- SECTION 6: AUDIT LOG (from db_nirms)
-- =============================================================================

INSERT INTO `irms_audit_logs` (id, description, subject_id, subject_type, user_id, properties, host, created_at, updated_at)
SELECT id, description, subject_id, subject_type, user_id, properties, host, created_at, updated_at
FROM db_nirms.`audit_logs`;

-- =============================================================================
-- SECTION 7: NEW TABLES — no data to migrate (irms_incident_tasks,
-- irms_observation_tasks, irms_chart_of_accounts start empty)
-- =============================================================================

-- irms_incident_tasks    — populated by IRMS service going forward
-- irms_observation_tasks — populated by IRMS service going forward
-- irms_chart_of_accounts — seeded manually or via separate seed script

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- POST-MIGRATION SANITY CHECK QUERIES (run manually to verify row counts)
-- =============================================================================
/*
SELECT 'irms_departments'              AS tbl, COUNT(*) AS rows FROM irms_departments
UNION ALL SELECT 'irms_divisions',              COUNT(*) FROM irms_divisions
UNION ALL SELECT 'irms_job_titles',             COUNT(*) FROM irms_job_titles
UNION ALL SELECT 'irms_users',                  COUNT(*) FROM irms_users
UNION ALL SELECT 'irms_category_incidents',     COUNT(*) FROM irms_category_incidents
UNION ALL SELECT 'irms_classification_incidents', COUNT(*) FROM irms_classification_incidents
UNION ALL SELECT 'irms_classification_details', COUNT(*) FROM irms_classification_details
UNION ALL SELECT 'irms_area_incidents',         COUNT(*) FROM irms_area_incidents
UNION ALL SELECT 'irms_region_incidents',       COUNT(*) FROM irms_region_incidents
UNION ALL SELECT 'irms_region_ncrs',            COUNT(*) FROM irms_region_ncrs
UNION ALL SELECT 'irms_root_causes',            COUNT(*) FROM irms_root_causes
UNION ALL SELECT 'irms_root_cause_so',          COUNT(*) FROM irms_root_cause_so
UNION ALL SELECT 'irms_results',                COUNT(*) FROM irms_results
UNION ALL SELECT 'irms_preventive_actions',     COUNT(*) FROM irms_preventive_actions
UNION ALL SELECT 'irms_ncr_entities',           COUNT(*) FROM irms_ncr_entities
UNION ALL SELECT 'irms_jso_locations',          COUNT(*) FROM irms_jso_locations
UNION ALL SELECT 'irms_jso_leader_master',      COUNT(*) FROM irms_jso_leader_master
UNION ALL SELECT 'irms_jso_member_master',      COUNT(*) FROM irms_jso_member_master
UNION ALL SELECT 'irms_nonplant_locations',     COUNT(*) FROM irms_nonplant_locations
UNION ALL SELECT 'irms_lookups',                COUNT(*) FROM irms_lookups
UNION ALL SELECT 'irms_locations',              COUNT(*) FROM irms_locations
UNION ALL SELECT 'irms_designation_departments',COUNT(*) FROM irms_designation_departments
UNION ALL SELECT 'irms_origin_departments',    COUNT(*) FROM irms_origin_departments
UNION ALL SELECT 'irms_chemical_releases',      COUNT(*) FROM irms_chemical_releases
UNION ALL SELECT 'irms_incident_reports',       COUNT(*) FROM irms_incident_reports
UNION ALL SELECT 'irms_safety_observations',    COUNT(*) FROM irms_safety_observations
UNION ALL SELECT 'irms_jso_reports',            COUNT(*) FROM irms_jso_reports
UNION ALL SELECT 'irms_investigation_details',  COUNT(*) FROM irms_investigation_details
UNION ALL SELECT 'irms_task_assignments',       COUNT(*) FROM irms_task_assignments
UNION ALL SELECT 'irms_distribution_incidents', COUNT(*) FROM irms_distribution_incidents
UNION ALL SELECT 'irms_comment_safety_observations', COUNT(*) FROM irms_comment_safety_observations
UNION ALL SELECT 'irms_fcm_tokens',             COUNT(*) FROM irms_fcm_tokens
UNION ALL SELECT 'irms_user_alerts',            COUNT(*) FROM irms_user_alerts
UNION ALL SELECT 'irms_user_user_alerts',       COUNT(*) FROM irms_user_user_alerts
UNION ALL SELECT 'irms_jso_users',              COUNT(*) FROM irms_jso_users
UNION ALL SELECT 'irms_audit_logs',             COUNT(*) FROM irms_audit_logs;
*/

-- End of 02_migrate_data.sql
