-- ============================================================
-- CATATAN PENGIRIMAN PENGINGAT TENGGAT JSO — DDL  (PostgreSQL)
-- ============================================================
-- JsoReminderService berjalan dari cron harian DAN bisa dipicu manual lewat
-- POST /api/v1/observation-tasks/reminders/run. Tanpa catatan ini, memicu manual
-- di hari yang sama dengan cron membuat PIC menerima dua email identik — dan
-- setiap restart/redeploy yang kebetulan melewati jam cron bisa menambah lagi.
--
-- Kunci unik memakai TIGA kolom, bukan dua:
--   (task_id, stage)          -> satu tugas cukup 1x per tahap, TAPI perpanjangan
--                                tenggat jadi tidak pernah diingatkan lagi.
--   (task_id, stage, due_on)  -> dipakai di sini. Tenggat diperpanjang = due_on
--                                berubah = pengingat H-3 berbunyi lagi untuk
--                                tanggal yang baru. Itu perilaku yang benar:
--                                tenggat baru adalah janji baru.
--
-- due_on menyimpan tenggat yang MENJADI DASAR perhitungan saat email dikirim,
-- bukan tanggal kirim. Tanggal kirim ada di sent_at.
--
-- Aman dijalankan ulang.
-- ============================================================

CREATE TABLE IF NOT EXISTS irms_jso_reminder_log (
    id       BIGSERIAL PRIMARY KEY,
    task_id  BIGINT      NOT NULL,
    stage    VARCHAR(10) NOT NULL,
    due_on   DATE        NOT NULL,
    sent_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_irms_jso_reminder_once UNIQUE (task_id, stage, due_on)
);

-- Tanpa FK ke irms_observation_tasks: tugas dihapus secara soft-delete
-- (deleted_at), jadi barisnya tetap ada dan FK tidak menambah jaminan apa pun.
-- Indeks ini yang dipakai saat menelusuri riwayat pengingat satu tugas.
CREATE INDEX IF NOT EXISTS ix_irms_jso_reminder_task
    ON irms_jso_reminder_log (task_id);

-- Verifikasi
-- SELECT task_id, stage, due_on, sent_at FROM irms_jso_reminder_log ORDER BY sent_at DESC LIMIT 20;
