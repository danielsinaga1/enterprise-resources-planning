-- ============================================================
-- APP SETTINGS — DDL + Seed  (PostgreSQL)
-- ============================================================
-- Pengaturan Umum sebelumnya hanya tersimpan di localStorage browser, sehingga
-- backend tidak bisa memakainya saat merender template email (email dikirim dari
-- server, termasuk oleh job terjadwal — tidak ada browser yang bisa dibaca).
-- Tabel key-value ini menjadikannya satu sumber kebenaran untuk UI dan email.

CREATE TABLE IF NOT EXISTS app_settings (
    id            BIGSERIAL PRIMARY KEY,
    setting_key   VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    updated_by    VARCHAR(191),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Nilai awal. Kontak sengaja kosong: diisi admin lewat Pengaturan Umum,
-- bukan ditebak di sini.
INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('site_name',    'IRMS'),
    ('site_desc',    'Integrated Report Management System'),
    ('site_email',   ''),
    ('site_phone',   ''),
    ('site_address', '')
ON CONFLICT (setting_key) DO NOTHING;
