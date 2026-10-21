-- ============================================================
-- PURCHASE REQUISITION — Permission RBAC  (PostgreSQL, db_auth)
-- ============================================================
-- Menu "PENGADAAN > Monitoring PR" hanya muncul bila user punya permission
-- ber-module 'PROCUREMENT' dengan can_read. SUPER_ADMIN dikecualikan karena
-- daftar modulnya di-hardcode di RolePermissionService.getUserMenu().
--
-- Jalankan pada database auth (db_auth), bukan db_irms.

INSERT INTO auth_permissions
    (code, name, title, module, sub_module, action, description, is_active,
     created_at, created_by, updated_at, updated_by)
VALUES
    ('PR_MONITORING_VIEW',   'Lihat Monitoring PR',   'Monitoring PR',
     'PROCUREMENT', 'PURCHASE_REQUISITION', 'VIEW',
     'Melihat daftar dan detail Purchase Requisition', TRUE,
     CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('PR_MONITORING_MANAGE', 'Kelola Monitoring PR',  'Kelola PR',
     'PROCUREMENT', 'PURCHASE_REQUISITION', 'MANAGE',
     'Tambah, ubah, hapus baris PR dan mencatat riwayat status', TRUE,
     CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (code) DO UPDATE SET
    module     = EXCLUDED.module,
    sub_module = EXCLUDED.sub_module,
    action     = EXCLUDED.action,
    is_active  = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

-- ------------------------------------------------------------
-- Grant ke role admin
-- ------------------------------------------------------------
-- Modul ini dipakai tim IT untuk memantau pengadaannya sendiri, jadi aksesnya
-- dibuka ke role administratif dulu. Buyer/PIC lain diberi akses lewat halaman
-- Permission Matrix, bukan dengan menambah baris di sini.

INSERT INTO auth_role_permissions
    (role_id, permission_id, can_create, can_read, can_update, can_delete,
     can_approve, can_export, created_at, created_by)
SELECT r.id, p.id,
       TRUE, TRUE, TRUE, TRUE, FALSE, TRUE,
       CURRENT_TIMESTAMP, 'SYSTEM'
FROM auth_roles r
CROSS JOIN auth_permissions p
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'ADMIN_IT')
  AND p.code IN ('PR_MONITORING_VIEW', 'PR_MONITORING_MANAGE')
  AND NOT EXISTS (
      SELECT 1 FROM auth_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
