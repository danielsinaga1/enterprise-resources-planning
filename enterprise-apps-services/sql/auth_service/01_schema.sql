-- ============================================================================
-- db_auth — Auth / Identity (PostgreSQL)
-- Owns: pengguna, role, permission, assignment, struktur organisasi, pengaturan umum.
-- Hak akses fitur ERP disimpan terpisah di db_erp (matriks per KODE role di bawah).
--
-- Idempoten. Jalankan pada database db_auth:  psql -d db_auth -f 01_schema.sql
-- Role & permission bawaan dibuat otomatis saat aplikasi start (DataInitializer).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Struktur organisasi
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_departments (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50),
    name        VARCHAR(191) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_auth_departments_code ON auth_departments (code);

CREATE TABLE IF NOT EXISTS auth_divisions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50),
    name        VARCHAR(191) NOT NULL,
    dept_id     BIGINT REFERENCES auth_departments (id) ON DELETE SET NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_auth_divisions_dept ON auth_divisions (dept_id);

CREATE TABLE IF NOT EXISTS auth_job_titles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(191) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP
);

-- ----------------------------------------------------------------------------
-- Pengguna (hash password hanya disimpan di sini; LDAP/AD opsional)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_users (
    id                    BIGSERIAL PRIMARY KEY,
    npp                   VARCHAR(50),
    npk                   VARCHAR(50),
    name                  VARCHAR(191) NOT NULL,
    email                 VARCHAR(191) NOT NULL,
    mobile_phone          VARCHAR(50),
    rf_id                 VARCHAR(100),
    parent_id             BIGINT REFERENCES auth_users (id) ON DELETE SET NULL,
    job_id                BIGINT REFERENCES auth_job_titles (id) ON DELETE SET NULL,
    dept_id               BIGINT REFERENCES auth_departments (id) ON DELETE SET NULL,
    division_id           BIGINT REFERENCES auth_divisions (id) ON DELETE SET NULL,
    password              VARCHAR(255) NOT NULL,
    pass_salt             TEXT,
    status                SMALLINT NOT NULL DEFAULT 1,   -- 1 = aktif, 0 = nonaktif
    last_login            TIMESTAMP,
    last_login_failed     TIMESTAMP,
    attempt_login_failed  INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(100),
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(100),
    CONSTRAINT ux_auth_users_email UNIQUE (email)
);
CREATE INDEX IF NOT EXISTS ix_auth_users_npk      ON auth_users (npk);
CREATE INDEX IF NOT EXISTS ix_auth_users_npp      ON auth_users (npp);
CREATE INDEX IF NOT EXISTS ix_auth_users_parent   ON auth_users (parent_id);
CREATE INDEX IF NOT EXISTS ix_auth_users_dept     ON auth_users (dept_id);
CREATE INDEX IF NOT EXISTS ix_auth_users_division ON auth_users (division_id);
CREATE INDEX IF NOT EXISTS ix_auth_users_job      ON auth_users (job_id);

-- ----------------------------------------------------------------------------
-- RBAC
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_roles (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    title           VARCHAR(200),
    description     TEXT,
    department_id   BIGINT REFERENCES auth_departments (id) ON DELETE SET NULL,
    is_system_role  BOOLEAN DEFAULT FALSE,
    is_active       BOOLEAN DEFAULT TRUE,
    priority        INTEGER DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100),
    CONSTRAINT ux_auth_roles_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS auth_permissions (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(100) NOT NULL,
    name         VARCHAR(150) NOT NULL,
    title        VARCHAR(200),
    description  TEXT,
    module       VARCHAR(50)  NOT NULL,
    sub_module   VARCHAR(50),
    action       VARCHAR(50),
    is_active    BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(100),
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(100),
    CONSTRAINT ux_auth_permissions_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS ix_auth_permissions_module ON auth_permissions (module);

CREATE TABLE IF NOT EXISTS auth_role_permissions (
    id             BIGSERIAL PRIMARY KEY,
    role_id        BIGINT NOT NULL REFERENCES auth_roles (id) ON DELETE CASCADE,
    permission_id  BIGINT NOT NULL REFERENCES auth_permissions (id) ON DELETE CASCADE,
    can_create     BOOLEAN DEFAULT FALSE,
    can_read       BOOLEAN DEFAULT FALSE,
    can_update     BOOLEAN DEFAULT FALSE,
    can_delete     BOOLEAN DEFAULT FALSE,
    can_approve    BOOLEAN DEFAULT FALSE,
    can_export     BOOLEAN DEFAULT FALSE,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    CONSTRAINT ux_auth_role_permissions UNIQUE (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS auth_user_roles (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    role_id      BIGINT NOT NULL REFERENCES auth_roles (id) ON DELETE CASCADE,
    is_primary   BOOLEAN DEFAULT FALSE,
    is_active    BOOLEAN DEFAULT TRUE,
    assigned_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by  VARCHAR(100),
    CONSTRAINT ux_auth_user_roles UNIQUE (user_id, role_id)
);
CREATE INDEX IF NOT EXISTS ix_auth_user_roles_user ON auth_user_roles (user_id);

-- ----------------------------------------------------------------------------
-- Pengaturan umum (branding halaman login & aplikasi)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_settings (
    id             BIGSERIAL PRIMARY KEY,
    setting_key    VARCHAR(100) NOT NULL UNIQUE,
    setting_value  TEXT,
    updated_by     VARCHAR(191),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
