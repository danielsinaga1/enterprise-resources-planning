-- ============================================================
-- FOTO PER TEMUAN JSO  (PostgreSQL)
-- ============================================================
-- Sebelumnya foto observasi hanya tersimpan satu set di header
-- (safety_observations.photos), padahal satu observasi JSO bisa berisi
-- belasan temuan di lokasi berbeda — fotonya jadi tercampur dan tidak
-- bisa dipakai sebagai bukti temuan tertentu.
--
-- Kolom ini menyimpan JSON array nama file per temuan, mis. ["uuid1.jpg"].
-- Format dan endpoint unggahnya sama dengan safety_observations.photos.
--
-- Foto header lama TIDAK dipindahkan: tidak ada informasi temuan mana yang
-- dimaksud tiap file, jadi menebak pemetaannya justru merusak bukti.
-- Data lama tetap tampil di detail sebagai "Foto Observasi (lama)".

ALTER TABLE irms_jso_reports
    ADD COLUMN IF NOT EXISTS photos TEXT NULL;
