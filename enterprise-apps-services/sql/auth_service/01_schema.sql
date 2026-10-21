-- ============================================================================
-- db_auth — Auth / Identity Service DDL
-- Owns: users, roles, permissions, user-role assignments, departments (org)
-- ALL downstream services (IRMS, LIMS) store only plain BIGINT refs to user IDs
-- and maintain their own lean read-side snapshots (lims_users, irms_users).
--
-- Naming convention: every table is prefixed with auth_
-- Constraints:  fk_auth_* | ix_auth_* | ux_auth_*
-- Charset:      utf8mb4 / utf8mb4_unicode_ci
-- ============================================================================

CREATE DATABASE IF NOT EXISTS db_auth
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE db_auth;

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- Organisation units (owned by Auth; snapshots pushed to each service)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS auth_departments (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)     NULL,
    name        VARCHAR(191)    NOT NULL,
    created_at  DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  DATETIME        NULL,
    PRIMARY KEY (id),
    KEY ix_auth_departments_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auth_divisions (
    id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code                      VARCHAR(50)     NULL,
    name                      VARCHAR(191)    NOT NULL,
    dept_id                   BIGINT UNSIGNED NULL,
    created_at                DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME        NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at                DATETIME        NULL,
    PRIMARY KEY (id),
    KEY ix_auth_divisions_dept (dept_id),
    CONSTRAINT fk_auth_divisions_dept FOREIGN KEY (dept_id) REFERENCES auth_departments (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auth_job_titles (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(191)    NOT NULL,
    created_at  DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  DATETIME        NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Users  (master record — password hash lives ONLY here)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS auth_users (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    npp                   VARCHAR(50)     NULL,
    npk                   VARCHAR(50)     NULL,
    name                  VARCHAR(191)    NOT NULL,
    email                 VARCHAR(191)    NOT NULL,
    mobile_phone          VARCHAR(50)     NULL,
    rf_id                 VARCHAR(100)    NULL,
    parent_id             BIGINT UNSIGNED NULL,
    job_id                BIGINT UNSIGNED NULL,
    dept_id               BIGINT UNSIGNED NULL,
    division_id           BIGINT UNSIGNED NULL,
    password              VARCHAR(255)    NOT NULL,
    pass_salt             TEXT            NULL,
    status                TINYINT         NOT NULL DEFAULT 1 COMMENT '1=active 0=inactive',
    last_login            DATETIME        NULL,
    last_login_failed     DATETIME        NULL,
    attempt_login_failed  INT             NOT NULL DEFAULT 0,
    created_at            DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(100)    NULL,
    updated_at            DATETIME        NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by            VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_auth_users_email (email),
    KEY ix_auth_users_npk      (npk),
    KEY ix_auth_users_npp      (npp),
    KEY ix_auth_users_parent   (parent_id),
    KEY ix_auth_users_dept     (dept_id),
    KEY ix_auth_users_division (division_id),
    KEY ix_auth_users_job      (job_id),
    CONSTRAINT fk_auth_users_parent   FOREIGN KEY (parent_id)   REFERENCES auth_users      (id) ON DELETE SET NULL,
    CONSTRAINT fk_auth_users_dept     FOREIGN KEY (dept_id)     REFERENCES auth_departments (id) ON DELETE SET NULL,
    CONSTRAINT fk_auth_users_division FOREIGN KEY (division_id) REFERENCES auth_divisions   (id) ON DELETE SET NULL,
    CONSTRAINT fk_auth_users_job      FOREIGN KEY (job_id)      REFERENCES auth_job_titles  (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- RBAC — Roles
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS auth_roles (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code           VARCHAR(50)     NOT NULL,
    name           VARCHAR(100)    NOT NULL,
    title          VARCHAR(200)    NULL,
    description    TEXT            NULL,
    department_id  BIGINT UNSIGNED NULL COMMENT 'optional scope to a department',
    is_system_role TINYINT(1)      NOT NULL DEFAULT 0,
    is_active      TINYINT(1)      NOT NULL DEFAULT 1,
    priority       INT             NOT NULL DEFAULT 0,
    created_at     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100)    NULL,
    updated_at     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by     VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_auth_roles_code (code),
    KEY ix_auth_roles_dept (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- RBAC — Permissions
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS auth_permissions (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(100)    NOT NULL,
    name        VARCHAR(150)    NOT NULL,
    title       VARCHAR(200)    NULL,
    description TEXT            NULL,
    module      VARCHAR(50)     NOT NULL,
    sub_module  VARCHAR(50)     NULL,
    action      VARCHAR(50)     NULL,
    is_active   TINYINT(1)      NOT NULL DEFAULT 1,
    created_at  DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(100)    NULL,
    updated_at  DATETIME        NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by  VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_auth_permissions_code   (code),
    KEY ix_auth_permissions_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- RBAC — Role ↔ Permission pivot (with fine-grained CRUD/approve/export flags)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS auth_role_permissions (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_id       BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    can_create    TINYINT(1)      NOT NULL DEFAULT 0,
    can_read      TINYINT(1)      NOT NULL DEFAULT 0,
    can_update    TINYINT(1)      NOT NULL DEFAULT 0,
    can_delete    TINYINT(1)      NOT NULL DEFAULT 0,
    can_approve   TINYINT(1)      NOT NULL DEFAULT 0,
    can_export    TINYINT(1)      NOT NULL DEFAULT 0,
    created_at    DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_auth_role_permissions (role_id, permission_id),
    KEY ix_auth_rp_permission (permission_id),
    CONSTRAINT fk_auth_rp_role       FOREIGN KEY (role_id)       REFERENCES auth_roles       (id) ON DELETE CASCADE,
    CONSTRAINT fk_auth_rp_permission FOREIGN KEY (permission_id) REFERENCES auth_permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- RBAC — User ↔ Role pivot
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS auth_user_roles (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    role_id     BIGINT UNSIGNED NOT NULL,
    is_primary  TINYINT(1)      NOT NULL DEFAULT 0,
    is_active   TINYINT(1)      NOT NULL DEFAULT 1,
    assigned_at DATETIME        NULL,
    assigned_by VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_auth_user_roles (user_id, role_id),
    KEY ix_auth_ur_role (role_id),
    CONSTRAINT fk_auth_ur_user FOREIGN KEY (user_id) REFERENCES auth_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_auth_ur_role FOREIGN KEY (role_id) REFERENCES auth_roles  (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
