-- ====================================================================
--  db_lims — Pembersihan laporan uji coba 0002-AR-LAB/IX/2025
--  Target : PostgreSQL (db_lims)
--  Tanggal: 2026-09-05
--
--  Laporan id 30 dibuat 2025-09-24, status Open, tidak pernah direview
--  atau ditutup. Isinya mencampur parameter Natural Gas (CH₄, Metana)
--  dengan kemurnian amoniak bernilai 200 — kombinasi yang tidak masuk
--  akal secara fisik untuk satu sampel. Baris Purity Ammonia 2-nya sudah
--  dibersihkan di 05_cleanup_master_data.sql; ini membereskan sisanya.
--
--  Dikonfirmasi sebagai data uji coba.
--
--  Soft delete, mengikuti @SQLRestriction di entity — bisa dibatalkan.
--  Dikunci id + nomor laporan sekaligus, jadi no-op kalau id bergeser.
-- ====================================================================

BEGIN;

-- Detail yang masih menempel (CH₄, Metana)
UPDATE lims_laboratory_details
   SET deleted_at = NOW(), updated_at = NOW()
 WHERE laboratory_report_id = 30
   AND deleted_at IS NULL
   AND EXISTS (SELECT 1 FROM lims_laboratory_reports r
                WHERE r.id = 30 AND r.no_report = '0002-AR-LAB/IX/2025');

-- Laporannya sendiri
UPDATE lims_laboratory_reports
   SET deleted_at = NOW(), updated_at = NOW()
 WHERE id = 30
   AND no_report = '0002-AR-LAB/IX/2025'
   AND status = 'Open'
   AND deleted_at IS NULL;

COMMIT;

-- --------------------------------------------------------------------
-- Verifikasi
-- --------------------------------------------------------------------
--   SELECT id, no_report, status, deleted_at IS NOT NULL AS terhapus
--     FROM lims_laboratory_reports WHERE id = 30;
--   SELECT COUNT(*) FROM lims_laboratory_details
--    WHERE laboratory_report_id = 30 AND deleted_at IS NULL;   -- harus 0
--
-- Membatalkan:
--   UPDATE lims_laboratory_reports  SET deleted_at = NULL WHERE id = 30;
--   UPDATE lims_laboratory_details  SET deleted_at = NULL WHERE laboratory_report_id = 30;
