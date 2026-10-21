-- ============================================================================
-- LIMS Permission Matrix — RBAC Schema Enhancement & Seed Data
-- Database: db_nirms (MySQL 8.0+)
-- 
-- Skema ini menambahkan:
--   1. Role khusus LIMS (jika belum ada)
--   2. Permission granular untuk setiap modul/fitur LIMS
--   3. Mapping role_permissions (matriks akses) untuk role LIMS
--
-- Prasyarat: Tabel roles, permissions, role_permissions sudah ada
--            dari 01_irms_ddl.sql dan skema Spring Boot entity.
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 1. LIMS ROLES
-- ============================================================================
-- Pastikan role LIMS tersedia. Gunakan INSERT IGNORE agar idempotent.

INSERT INTO `roles` (`code`, `name`, `title`, `description`, `is_system_role`, `is_active`, `priority`, `created_at`, `created_by`, `updated_at`, `updated_by`)
VALUES
  ('KEPALA_LAB',       'Kepala Laboratorium',      'Kepala Laboratorium',      'Kepala unit laboratorium, full access LIMS',                    1, 1, 20, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('MANAGER_LAB',      'Manajer Laboratorium',     'Manajer Laboratorium',     'Manajer lab, approval dan reporting',                           1, 1, 25, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('ANALIS_LAB',       'Analis Laboratorium',      'Analis Laboratorium',      'Analis yang menjalankan pengujian dan input hasil',              1, 1, 40, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('STAFF_LAB',        'Staf Laboratorium',        'Staf Laboratorium',        'Staf admin lab, input data dasar',                              1, 1, 45, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('QUALITY_MANAGER',  'Manajer Mutu',             'Manajer Mutu',             'Penanggung jawab mutu, review & approve laporan',                1, 1, 22, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('QUALITY_STAFF',    'Staf Mutu',                'Staf Mutu',                'Staf quality control, view & create laporan mutu',               1, 1, 42, NOW(), 'SYSTEM', NOW(), 'SYSTEM')
ON DUPLICATE KEY UPDATE `updated_at` = NOW();

-- ============================================================================
-- 2. LIMS PERMISSIONS  (module = 'LIMS')
-- ============================================================================
-- Setiap permission merepresentasikan satu fitur/sub-modul.
-- Field `action` menunjukkan tipe aksi utama, tetapi granularitas CRUD
-- diatur di tabel role_permissions (can_create, can_read, can_update, etc.)

INSERT INTO `permissions` (`code`, `name`, `title`, `module`, `sub_module`, `action`, `description`, `is_active`, `created_at`, `created_by`, `updated_at`, `updated_by`)
VALUES
  -- Dashboard LIMS
  ('LIMS_DASHBOARD_VIEW',         'Lihat Dashboard LIMS',          'Dashboard LIMS',          'LIMS', 'DASHBOARD',  'VIEW',    'Akses halaman dashboard LIMS',            1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Laporan Laboratorium
  ('LAB_REPORT_VIEW',             'Lihat Laporan Laboratorium',    'Lihat Lab Report',        'LIMS', 'REPORT',     'VIEW',    'Melihat daftar laporan laboratorium',     1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_REPORT_CREATE',           'Buat Laporan Laboratorium',     'Buat Lab Report',         'LIMS', 'REPORT',     'CREATE',  'Membuat laporan laboratorium baru',       1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_REPORT_MANAGE',           'Kelola Laporan Laboratorium',   'Kelola Lab Report',       'LIMS', 'REPORT',     'MANAGE',  'Edit & hapus laporan laboratorium',       1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Analisis Detail
  ('LAB_ANALYSIS_VIEW',           'Lihat Detail Analisis',         'Lihat Analisis',          'LIMS', 'ANALYSIS',   'VIEW',    'Melihat detail analisis lab',             1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_ANALYSIS_MANAGE',         'Kelola Detail Analisis',        'Kelola Analisis',         'LIMS', 'ANALYSIS',   'MANAGE',  'Edit & kelola detail analisis',           1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Approval
  ('LAB_REPORT_REVIEW',           'Review Laporan Laboratorium',   'Review Lab Report',       'LIMS', 'APPROVAL',   'APPROVE', 'Review laporan laboratorium',             1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_REPORT_APPROVE',          'Approve Laporan Laboratorium',  'Approve Lab Report',      'LIMS', 'APPROVAL',   'APPROVE', 'Final approve laporan lab',               1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Laporan Harian
  ('DAILY_REPORT_VIEW',           'Lihat Laporan Harian',          'Lihat Daily Report',      'LIMS', 'DAILY',      'VIEW',    'Melihat laporan harian lab',              1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('DAILY_REPORT_CREATE',         'Buat Laporan Harian',           'Buat Daily Report',       'LIMS', 'DAILY',      'CREATE',  'Membuat laporan harian baru',             1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('DAILY_REPORT_MANAGE',         'Kelola Laporan Harian',         'Kelola Daily Report',     'LIMS', 'DAILY',      'MANAGE',  'Edit & hapus laporan harian',             1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Kalender
  ('DAILY_CALENDAR_VIEW',         'Lihat Kalender Harian',         'Kalender Harian',         'LIMS', 'CALENDAR',   'VIEW',    'Akses kalender jadwal harian',            1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Master Data: Parameter Uji
  ('LAB_PARAMETER_VIEW',          'Lihat Parameter Uji',           'Lihat Parameter',         'LIMS', 'PARAMETER',  'VIEW',    'Melihat master parameter uji',            1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_PARAMETER_MANAGE',        'Kelola Parameter Uji',          'Kelola Parameter',        'LIMS', 'PARAMETER',  'MANAGE',  'CRUD master parameter uji',               1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Master Data: Tipe Pengujian
  ('LAB_TEST_TYPE_VIEW',          'Lihat Tipe Pengujian',          'Lihat Tipe Uji',          'LIMS', 'TEST_TYPE',  'VIEW',    'Melihat daftar tipe pengujian',           1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_TEST_TYPE_MANAGE',        'Kelola Tipe Pengujian',         'Kelola Tipe Uji',         'LIMS', 'TEST_TYPE',  'MANAGE',  'CRUD tipe pengujian',                     1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Master Data: Metode
  ('LAB_METHOD_VIEW',             'Lihat Metode',                  'Lihat Metode',            'LIMS', 'METHOD',     'VIEW',    'Melihat daftar metode pengujian',         1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_METHOD_MANAGE',           'Kelola Metode',                 'Kelola Metode',           'LIMS', 'METHOD',     'MANAGE',  'CRUD metode pengujian',                   1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Master Data: Standar
  ('LAB_STANDARD_VIEW',           'Lihat Standar',                 'Lihat Standar',           'LIMS', 'STANDARD',   'VIEW',    'Melihat daftar standar',                  1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_STANDARD_MANAGE',         'Kelola Standar',                'Kelola Standar',          'LIMS', 'STANDARD',   'MANAGE',  'CRUD standar',                            1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Master Data: Satuan
  ('LAB_UNIT_VIEW',               'Lihat Satuan',                  'Lihat Satuan',            'LIMS', 'UNIT',       'VIEW',    'Melihat master satuan',                   1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_UNIT_MANAGE',             'Kelola Satuan',                 'Kelola Satuan',           'LIMS', 'UNIT',       'MANAGE',  'CRUD satuan',                             1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Hasil Pengujian
  ('LAB_RESULT_VIEW',             'Lihat Hasil Pengujian',         'Lihat Hasil',             'LIMS', 'RESULT',     'VIEW',    'Melihat hasil pengujian lab',             1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_RESULT_MANAGE',           'Kelola Hasil Pengujian',        'Kelola Hasil',            'LIMS', 'RESULT',     'MANAGE',  'Edit & kelola hasil pengujian',           1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Departemen Lab
  ('LAB_DEPT_VIEW',               'Lihat Departemen Lab',          'Lihat Dept Lab',          'LIMS', 'LAB_DEPT',   'VIEW',    'Melihat departemen laboratorium',         1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_DEPT_MANAGE',             'Kelola Departemen Lab',         'Kelola Dept Lab',         'LIMS', 'LAB_DEPT',   'MANAGE',  'CRUD departemen laboratorium',            1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),

  -- Senyawa Kimia
  ('LAB_COMPOUND_VIEW',           'Lihat Senyawa Kimia',           'Lihat Senyawa',           'LIMS', 'COMPOUND',   'VIEW',    'Melihat master senyawa kimia',            1, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),
  ('LAB_COMPOUND_MANAGE',         'Kelola Senyawa Kimia',          'Kelola Senyawa',          'LIMS', 'COMPOUND',   'MANAGE',  'CRUD senyawa kimia',                      1, NOW(), 'SYSTEM', NOW(), 'SYSTEM')

ON DUPLICATE KEY UPDATE `updated_at` = NOW();

-- ============================================================================
-- 3. ROLE_PERMISSIONS MATRIX (LIMS)
-- ============================================================================
-- Matriks akses default: siapa boleh apa.
-- can_create, can_read, can_update, can_delete, can_approve, can_export

-- Helper: Ambil ID permission by code
-- Kita gunakan sub-query agar tidak hardcode ID.

-- ---------- KEPALA_LAB — Full Access semua modul LIMS ----------
INSERT INTO `role_permissions` (`role_id`, `permission_id`, `can_create`, `can_read`, `can_update`, `can_delete`, `can_approve`, `can_export`, `created_at`, `created_by`)
SELECT r.id, p.id, 1, 1, 1, 1, 1, 1, NOW(), 'SYSTEM'
FROM `roles` r, `permissions` p
WHERE r.code = 'KEPALA_LAB' AND p.module = 'LIMS'
ON DUPLICATE KEY UPDATE can_create=1, can_read=1, can_update=1, can_delete=1, can_approve=1, can_export=1;

-- ---------- MANAGER_LAB — Full Access kecuali delete master data ----------
INSERT INTO `role_permissions` (`role_id`, `permission_id`, `can_create`, `can_read`, `can_update`, `can_delete`, `can_approve`, `can_export`, `created_at`, `created_by`)
SELECT r.id, p.id, 1, 1, 1, 0, 1, 1, NOW(), 'SYSTEM'
FROM `roles` r, `permissions` p
WHERE r.code = 'MANAGER_LAB' AND p.module = 'LIMS'
ON DUPLICATE KEY UPDATE can_create=1, can_read=1, can_update=1, can_delete=0, can_approve=1, can_export=1;

-- ---------- QUALITY_MANAGER — Read + Approve + Export, no create/delete ----------
INSERT INTO `role_permissions` (`role_id`, `permission_id`, `can_create`, `can_read`, `can_update`, `can_delete`, `can_approve`, `can_export`, `created_at`, `created_by`)
SELECT r.id, p.id, 0, 1, 1, 0, 1, 1, NOW(), 'SYSTEM'
FROM `roles` r, `permissions` p
WHERE r.code = 'QUALITY_MANAGER' AND p.module = 'LIMS'
ON DUPLICATE KEY UPDATE can_create=0, can_read=1, can_update=1, can_delete=0, can_approve=1, can_export=1;

-- ---------- ANALIS_LAB — Create + Read + Update laporan & analisis, read master data ----------
INSERT INTO `role_permissions` (`role_id`, `permission_id`, `can_create`, `can_read`, `can_update`, `can_delete`, `can_approve`, `can_export`, `created_at`, `created_by`)
SELECT r.id, p.id, 1, 1, 1, 0, 0, 1, NOW(), 'SYSTEM'
FROM `roles` r, `permissions` p
WHERE r.code = 'ANALIS_LAB' AND p.module = 'LIMS' AND p.sub_module IN ('REPORT', 'ANALYSIS', 'DAILY', 'RESULT')
ON DUPLICATE KEY UPDATE can_create=1, can_read=1, can_update=1, can_delete=0, can_approve=0, can_export=1;

INSERT INTO `role_permissions` (`role_id`, `permission_id`, `can_create`, `can_read`, `can_update`, `can_delete`, `can_approve`, `can_export`, `created_at`, `created_by`)
SELECT r.id, p.id, 0, 1, 0, 0, 0, 0, NOW(), 'SYSTEM'
FROM `roles` r, `permissions` p
WHERE r.code = 'ANALIS_LAB' AND p.module = 'LIMS' AND p.sub_module NOT IN ('REPORT', 'ANALYSIS', 'DAILY', 'RESULT')
ON DUPLICATE KEY UPDATE can_create=0, can_read=1, can_update=0, can_delete=0, can_approve=0, can_export=0;

-- ---------- STAFF_LAB — Read-only + limited create ----------
INSERT INTO `role_permissions` (`role_id`, `permission_id`, `can_create`, `can_read`, `can_update`, `can_delete`, `can_approve`, `can_export`, `created_at`, `created_by`)
SELECT r.id, p.id, 0, 1, 0, 0, 0, 0, NOW(), 'SYSTEM'
FROM `roles` r, `permissions` p
WHERE r.code = 'STAFF_LAB' AND p.module = 'LIMS'
ON DUPLICATE KEY UPDATE can_create=0, can_read=1, can_update=0, can_delete=0, can_approve=0, can_export=0;

-- Staff Lab boleh create daily report
INSERT INTO `role_permissions` (`role_id`, `permission_id`, `can_create`, `can_read`, `can_update`, `can_delete`, `can_approve`, `can_export`, `created_at`, `created_by`)
SELECT r.id, p.id, 1, 1, 1, 0, 0, 1, NOW(), 'SYSTEM'
FROM `roles` r, `permissions` p
WHERE r.code = 'STAFF_LAB' AND p.code IN ('DAILY_REPORT_VIEW', 'DAILY_REPORT_CREATE', 'DAILY_REPORT_MANAGE')
ON DUPLICATE KEY UPDATE can_create=1, can_read=1, can_update=1, can_delete=0, can_approve=0, can_export=1;

-- ---------- QUALITY_STAFF — Read + Report + Export ----------
INSERT INTO `role_permissions` (`role_id`, `permission_id`, `can_create`, `can_read`, `can_update`, `can_delete`, `can_approve`, `can_export`, `created_at`, `created_by`)
SELECT r.id, p.id, 0, 1, 0, 0, 0, 1, NOW(), 'SYSTEM'
FROM `roles` r, `permissions` p
WHERE r.code = 'QUALITY_STAFF' AND p.module = 'LIMS'
ON DUPLICATE KEY UPDATE can_create=0, can_read=1, can_update=0, can_delete=0, can_approve=0, can_export=1;

-- ---------- SUPER_ADMIN — Full Access semua modul LIMS ----------
INSERT INTO `role_permissions` (`role_id`, `permission_id`, `can_create`, `can_read`, `can_update`, `can_delete`, `can_approve`, `can_export`, `created_at`, `created_by`)
SELECT r.id, p.id, 1, 1, 1, 1, 1, 1, NOW(), 'SYSTEM'
FROM `roles` r, `permissions` p
WHERE r.code = 'SUPER_ADMIN' AND p.module = 'LIMS'
ON DUPLICATE KEY UPDATE can_create=1, can_read=1, can_update=1, can_delete=1, can_approve=1, can_export=1;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- Verifikasi: Cek matriks yang dibuat
-- ============================================================================
-- SELECT r.code AS role_code, r.name AS role_name,
--        p.code AS permission_code, p.name AS permission_name,
--        p.sub_module,
--        rp.can_create, rp.can_read, rp.can_update,
--        rp.can_delete, rp.can_approve, rp.can_export
-- FROM role_permissions rp
-- JOIN roles r ON r.id = rp.role_id
-- JOIN permissions p ON p.id = rp.permission_id
-- WHERE p.module = 'LIMS'
-- ORDER BY r.priority, p.sub_module, p.code;
