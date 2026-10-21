-- ============================================================
-- SMARTDOCS — Permission RBAC  (PostgreSQL, db_auth)
-- ============================================================
-- Seksi menu "SMARTDOCS" hanya muncul bila user punya permission ber-module
-- 'SMARTDOCS' dengan can_read. SUPER_ADMIN dikecualikan karena daftar modulnya
-- di-hardcode di RolePermissionService.getUserMenu().
--
-- Jalankan pada database auth (db_auth), bukan db_irms maupun db_smartdocs.

INSERT INTO auth_permissions
    (code, name, title, module, sub_module, action, description, is_active,
     created_at, created_by, updated_at, updated_by)
VALUES
    ('SMARTDOCS_VIEW',   'Lihat Dokumen SmartDocs',  'Dokumen SmartDocs',
     'SMARTDOCS', 'DOCUMENT', 'VIEW',
     'Melihat daftar, detail, laporan, dan log aktivitas dokumen hasil OCR', TRUE,
     CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('SMARTDOCS_MANAGE', 'Kelola Dokumen SmartDocs', 'Kelola SmartDocs',
     'SMARTDOCS', 'DOCUMENT', 'MANAGE',
     'Menyimpan, mengoreksi hasil OCR, dan menghapus dokumen', TRUE,
     CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (code) DO UPDATE SET
    module     = EXCLUDED.module,
    sub_module = EXCLUDED.sub_module,
    action     = EXCLUDED.action,
    is_active  = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

-- ------------------------------------------------------------
-- Grant ke role administratif
-- ------------------------------------------------------------
-- Endpoint tulis SmartDocs dibatasi SUPER_ADMIN/ADMIN_IT/ADMIN di controller,
-- jadi daftar di bawah dibuat sejalan dengan itu. Pengguna lain yang hanya
-- perlu membaca diberi akses lewat halaman Permission Matrix — jangan
-- menambah role di sini tanpa menyesuaikan @PreAuthorize di controller,
-- karena menunya akan muncul tetapi aksinya ditolak 403.

INSERT INTO auth_role_permissions
    (role_id, permission_id, can_create, can_read, can_update, can_delete,
     can_approve, can_export, created_at, created_by)
SELECT r.id, p.id,
       TRUE, TRUE, TRUE, TRUE, FALSE, TRUE,
       CURRENT_TIMESTAMP, 'SYSTEM'
FROM auth_roles r
CROSS JOIN auth_permissions p
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'ADMIN_IT')
  AND p.code IN ('SMARTDOCS_VIEW', 'SMARTDOCS_MANAGE')
  AND NOT EXISTS (
      SELECT 1 FROM auth_role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
