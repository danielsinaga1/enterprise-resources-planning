-- ============================================================================
-- db_auth — Seed data: migrate auth/identity tables from db_nirms
--
-- PREREQUISITE: run 01_schema.sql first.
-- Connect to MySQL as a user that can read db_nirms AND write db_auth.
--
-- Run once:
--   mysql -u root < sql/auth_service/01_schema.sql
--   mysql -u root < sql/auth_service/02_seed_data.sql
--
-- NOTE: origin_departments (db_nirms) is used as the canonical department
-- source for auth_departments. designation_departments is an IRMS-specific
-- grouping and stays in db_nirms.
-- ============================================================================

USE db_auth;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- Departments  (from db_nirms.origin_departments)
-- ----------------------------------------------------------------------------
INSERT INTO auth_departments (id, code, name, created_at, updated_at)
SELECT id, code, name, created_at, updated_at
FROM db_nirms.origin_departments
ON DUPLICATE KEY UPDATE name = VALUES(name), code = VALUES(code), updated_at = VALUES(updated_at);

-- ----------------------------------------------------------------------------
-- Divisions  (from db_nirms.divisions, only dept_id kept — maps to auth_departments)
-- ----------------------------------------------------------------------------
INSERT INTO auth_divisions (id, code, name, dept_id, created_at, updated_at)
SELECT id, code, name, dept_id, created_at, updated_at
FROM db_nirms.divisions
ON DUPLICATE KEY UPDATE name = VALUES(name), dept_id = VALUES(dept_id), updated_at = VALUES(updated_at);

-- ----------------------------------------------------------------------------
-- Job Titles  (from db_nirms.job_titles)
-- ----------------------------------------------------------------------------
INSERT INTO auth_job_titles (id, name, created_at, updated_at)
SELECT id, name, created_at, updated_at
FROM db_nirms.job_titles
ON DUPLICATE KEY UPDATE name = VALUES(name), updated_at = VALUES(updated_at);

-- ----------------------------------------------------------------------------
-- Users  (from db_nirms.users — includes password hashes)
-- auth_users is the ONLY place passwords are stored.
-- ----------------------------------------------------------------------------
INSERT INTO auth_users (
    id, npp, npk, name, email, mobile_phone, rf_id,
    parent_id, job_id, dept_id, division_id,
    password, pass_salt, status,
    last_login, last_login_failed, attempt_login_failed,
    created_at, created_by, updated_at, updated_by
)
SELECT
    id,
    npp,
    npk,
    name,
    email,
    mobile_phone,
    rf_id,
    parent_id,
    job_id,
    dept_id,
    division_id,
    password,
    pass_salt,
    COALESCE(status, 1),
    last_login,
    last_login_failed,
    COALESCE(attempt_login_failed, 0),
    created_at,
    created_by,
    updated_at,
    updated_by
FROM db_nirms.users
ON DUPLICATE KEY UPDATE
    name                 = VALUES(name),
    email                = VALUES(email),
    mobile_phone         = VALUES(mobile_phone),
    parent_id            = VALUES(parent_id),
    job_id               = VALUES(job_id),
    dept_id              = VALUES(dept_id),
    division_id          = VALUES(division_id),
    password             = VALUES(password),
    status               = VALUES(status),
    last_login           = VALUES(last_login),
    attempt_login_failed = VALUES(attempt_login_failed),
    updated_at           = VALUES(updated_at);

-- ----------------------------------------------------------------------------
-- Roles  (from db_nirms.roles — NEW Spring-managed table with code/name/etc.)
-- The old Laravel "roles" table only had title; the Spring migration added
-- code/name/is_system_role/is_active/priority columns. We copy those.
-- ----------------------------------------------------------------------------
INSERT INTO auth_roles (
    id, code, name, title, description,
    department_id, is_system_role, is_active, priority,
    created_at, created_by, updated_at, updated_by
)
SELECT
    id,
    COALESCE(code, UPPER(REPLACE(title, ' ', '_'))),
    COALESCE(name, title),
    title,
    description,
    department_id,
    COALESCE(is_system_role, 0),
    COALESCE(is_active, 1),
    COALESCE(priority, 0),
    created_at,
    created_by,
    updated_at,
    updated_by
FROM db_nirms.roles
WHERE code IS NOT NULL AND code != ''
ON DUPLICATE KEY UPDATE
    name           = VALUES(name),
    title          = VALUES(title),
    description    = VALUES(description),
    is_system_role = VALUES(is_system_role),
    is_active      = VALUES(is_active),
    priority       = VALUES(priority),
    updated_at     = VALUES(updated_at);

-- ----------------------------------------------------------------------------
-- Permissions  (from db_nirms.permissions — Spring-managed table)
-- ----------------------------------------------------------------------------
INSERT INTO auth_permissions (
    id, code, name, title, description,
    module, sub_module, action, is_active,
    created_at, created_by, updated_at, updated_by
)
SELECT
    id, code, name, title, description,
    module, sub_module, action,
    COALESCE(is_active, 1),
    created_at, created_by, updated_at, updated_by
FROM db_nirms.permissions
WHERE code IS NOT NULL AND code != ''
ON DUPLICATE KEY UPDATE
    name       = VALUES(name),
    module     = VALUES(module),
    sub_module = VALUES(sub_module),
    action     = VALUES(action),
    is_active  = VALUES(is_active),
    updated_at = VALUES(updated_at);

-- ----------------------------------------------------------------------------
-- Role–Permission pivot  (from db_nirms.role_permissions)
-- ----------------------------------------------------------------------------
INSERT INTO auth_role_permissions (
    id, role_id, permission_id,
    can_create, can_read, can_update, can_delete, can_approve, can_export,
    created_at, created_by
)
SELECT
    id, role_id, permission_id,
    COALESCE(can_create, 0),
    COALESCE(can_read, 0),
    COALESCE(can_update, 0),
    COALESCE(can_delete, 0),
    COALESCE(can_approve, 0),
    COALESCE(can_export, 0),
    created_at, created_by
FROM db_nirms.role_permissions
ON DUPLICATE KEY UPDATE
    can_create  = VALUES(can_create),
    can_read    = VALUES(can_read),
    can_update  = VALUES(can_update),
    can_delete  = VALUES(can_delete),
    can_approve = VALUES(can_approve),
    can_export  = VALUES(can_export);

-- ----------------------------------------------------------------------------
-- User–Role pivot  (from db_nirms.user_roles)
-- ----------------------------------------------------------------------------
INSERT INTO auth_user_roles (
    id, user_id, role_id,
    is_primary, is_active, assigned_at, assigned_by
)
SELECT
    id, user_id, role_id,
    COALESCE(is_primary, 0),
    COALESCE(is_active, 1),
    assigned_at, assigned_by
FROM db_nirms.user_roles
ON DUPLICATE KEY UPDATE
    is_primary  = VALUES(is_primary),
    is_active   = VALUES(is_active),
    assigned_at = VALUES(assigned_at);

-- ----------------------------------------------------------------------------
-- Realign AUTO_INCREMENT after id-preserving copy
-- (adjust the numbers if your tables have more rows)
-- ----------------------------------------------------------------------------
SELECT @max_dept  := IFNULL(MAX(id), 0) + 1 FROM auth_departments;
SELECT @max_div   := IFNULL(MAX(id), 0) + 1 FROM auth_divisions;
SELECT @max_job   := IFNULL(MAX(id), 0) + 1 FROM auth_job_titles;
SELECT @max_user  := IFNULL(MAX(id), 0) + 1 FROM auth_users;
SELECT @max_role  := IFNULL(MAX(id), 0) + 1 FROM auth_roles;
SELECT @max_perm  := IFNULL(MAX(id), 0) + 1 FROM auth_permissions;
SELECT @max_rp    := IFNULL(MAX(id), 0) + 1 FROM auth_role_permissions;
SELECT @max_ur    := IFNULL(MAX(id), 0) + 1 FROM auth_user_roles;

SET @sql = CONCAT('ALTER TABLE auth_departments      AUTO_INCREMENT = ', @max_dept);  PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = CONCAT('ALTER TABLE auth_divisions        AUTO_INCREMENT = ', @max_div);   PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = CONCAT('ALTER TABLE auth_job_titles       AUTO_INCREMENT = ', @max_job);   PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = CONCAT('ALTER TABLE auth_users            AUTO_INCREMENT = ', @max_user);  PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = CONCAT('ALTER TABLE auth_roles            AUTO_INCREMENT = ', @max_role);  PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = CONCAT('ALTER TABLE auth_permissions      AUTO_INCREMENT = ', @max_perm);  PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = CONCAT('ALTER TABLE auth_role_permissions AUTO_INCREMENT = ', @max_rp);    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = CONCAT('ALTER TABLE auth_user_roles       AUTO_INCREMENT = ', @max_ur);    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET FOREIGN_KEY_CHECKS = 1;

-- After this script completes, update lims_users snapshot in db_lims:
--   INSERT INTO db_lims.lims_users (id, name, npk, email, job_title, parent_id, department_id)
--   SELECT u.id, u.name, u.npk, u.email, j.name, u.parent_id, u.dept_id
--   FROM db_auth.auth_users u
--   LEFT JOIN db_auth.auth_job_titles j ON u.job_id = j.id
--   ON DUPLICATE KEY UPDATE name = VALUES(name), parent_id = VALUES(parent_id), department_id = VALUES(department_id);
