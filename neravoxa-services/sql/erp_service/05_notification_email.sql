-- ─────────────────────────────────────────────────────────────────────
-- Preferensi notifikasi per pengguna ERP: salinan email untuk notifikasi in-app.
--
-- Email hanya dikirim bila administrator mengaktifkannya (ERP_EMAIL_ENABLED + SMTP); tanpa baris di sini
-- pengguna dianggap mau menerima email (bisa dimatikan sendiri di halaman Notifikasi).
--
-- Idempoten: aman dijalankan ulang. Dipasang otomatis saat boot bila app.erp.auto-init=true;
-- untuk produksi jalankan manual setelah 01_schema.sql & 02_seed.sql.
-- ─────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id TEXT NOT NULL,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notification_preferences_pkey PRIMARY KEY (user_id)
);
