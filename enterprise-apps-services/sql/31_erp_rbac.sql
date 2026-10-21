-- ============================================================
-- ERP (port fitur ENTARIS) — role platform  (PostgreSQL, db_auth)
-- ============================================================
-- Hak akses ERP per modul (Lihat/Edit) dan hak khusus (batal dokumen, putuskan approval,
-- lewati approval, stok minus, kelola hak akses) disimpan di db_erp (tabel roles,
-- role_permissions, role_capabilities) dan dikelola dari ERP › Sistem ERP › Pengguna & Hak
-- Akses. Kuncinya adalah KODE role platform di bawah ini; pengguna dengan beberapa role
-- mendapat gabungan izinnya. SUPER_ADMIN otomatis super-admin ERP.
--
-- Skrip ini hanya membuat role platform agar bisa di-assign ke pengguna lewat
-- Pengaturan › Manajemen User. Matriks izin bawaan dibuat otomatis oleh ERP saat boot
-- (ErpBootstrap) bila belum ada.
--
-- Jalankan pada database auth (db_auth).

INSERT INTO auth_roles
    (code, name, title, description, is_system_role, is_active, priority,
     created_at, created_by, updated_at, updated_by)
VALUES
    ('ERP_ADMIN',      'Admin ERP',          'Admin ERP',
     'Akses penuh modul ERP (keuangan, penjualan, pembelian, persediaan, aset).',
     TRUE, TRUE, 0, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ERP_ACCOUNTANT', 'Akuntan / Keuangan', 'Akuntan / Keuangan',
     'Operasional keuangan, penjualan, pembelian, dan stok di modul ERP.',
     TRUE, TRUE, 0, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ERP_PRODUCTION', 'Staff Produksi',     'Staff Produksi',
     'Operasional pembelian, stok, produksi, dan surat jalan di modul ERP.',
     TRUE, TRUE, 0, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ERP_VIEWER',     'Viewer ERP',         'Viewer ERP',
     'Hanya melihat data ERP (read-only).',
     TRUE, TRUE, 0, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    title       = EXCLUDED.title,
    description = EXCLUDED.description,
    is_active   = TRUE,
    updated_at  = CURRENT_TIMESTAMP;
