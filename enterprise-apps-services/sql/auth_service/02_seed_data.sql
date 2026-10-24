-- ============================================================================
-- db_auth — data awal (PostgreSQL). Idempoten; jalankan setelah 01_schema.sql.
--
-- * Admin awal: id 1, NPK "admin", password awal "Admin@12345" (bcrypt via pgcrypto).
--   GANTI password ini setelah login pertama (Profil). DataInitializer otomatis
--   memberi user id 1 role SUPER_ADMIN, dan membuat role/permission bawaan termasuk
--   role ERP (ERP_ADMIN, ERP_ACCOUNTANT, ERP_PRODUCTION, ERP_VIEWER).
-- * Pengaturan umum (nama & deskripsi aplikasi) untuk halaman login.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO auth_users (id, npk, name, email, password, status, created_by, updated_by)
VALUES (1, 'admin', 'Administrator', 'admin@enterprise-apps.local',
        crypt('Admin@12345', gen_salt('bf', 10)), 1, 'SYSTEM', 'SYSTEM')
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('auth_users', 'id'), GREATEST((SELECT MAX(id) FROM auth_users), 1));

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('site_name',    'KPI ERP'),
    ('site_desc',    'Enterprise Resources Planning'),
    ('site_email',   ''),
    ('site_phone',   ''),
    ('site_address', '')
ON CONFLICT (setting_key) DO NOTHING;
