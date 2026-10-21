-- ============================================================
-- LIMS — pisahkan hasil Sampler dan Analis  (PostgreSQL, db_lims)
-- ============================================================
-- Sebelumnya satu kolom `result` menampung hasil dari siapa pun yang mengisi,
-- sehingga kontribusi Sampler Lab dan Analis Lab tidak bisa dibedakan saat
-- Tinjauan Teknis. Dua kolom terpisah membuat jejak tiap peran jelas.
--
-- Kolom lama `result` DIPERTAHANKAN (tidak di-drop) supaya data historis tetap
-- terbaca dan rollback aman; nilainya disalin ke result_analysis sebagai basis
-- karena secara historis diisi pada tahap analisis.
--
-- Jalankan:
--     psql -h localhost -U postgres -d db_lims -f 21_lims_result_split.sql

ALTER TABLE lims_laboratory_details
    ADD COLUMN IF NOT EXISTS result_sampler  TEXT,
    ADD COLUMN IF NOT EXISTS result_analysis TEXT;

COMMENT ON COLUMN lims_laboratory_details.result_sampler  IS 'Hasil/pengamatan yang diisi Sampler Lab';
COMMENT ON COLUMN lims_laboratory_details.result_analysis IS 'Hasil pengujian yang diisi Analis Lab';

-- Backfill sekali jalan: hasil lama dianggap hasil analisis.
UPDATE lims_laboratory_details
   SET result_analysis = result
 WHERE result_analysis IS NULL
   AND result IS NOT NULL
   AND result <> '';
