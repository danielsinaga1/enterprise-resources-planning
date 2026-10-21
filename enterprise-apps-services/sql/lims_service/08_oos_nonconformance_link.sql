-- ============================================================================
-- 08 — Tautan hasil di luar spesifikasi (OOS) ke NCR
-- ----------------------------------------------------------------------------
-- Target: db_lims (PostgreSQL). Jalankan setelah 07:
--     psql -U <user> -d db_lims -f 08_oos_nonconformance_link.sql
--
-- NCR-nya sendiri hidup di db_irms (modul safety observation). Di sini hanya
-- disimpan penunjuknya — tidak ada foreign key lintas database, dan memang
-- tidak boleh ada: LIMS harus tetap bisa diangkat jadi service terpisah.
--
-- Seluruh pernyataan idempoten — aman dijalankan ulang.
-- ============================================================================

BEGIN;

ALTER TABLE lims_laboratory_details
    ADD COLUMN IF NOT EXISTS ncr_id        BIGINT,
    ADD COLUMN IF NOT EXISTS ncr_number    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS ncr_opened_at TIMESTAMP;

COMMENT ON COLUMN lims_laboratory_details.ncr_id     IS 'id safety_observations di db_irms — penunjuk, bukan FK (beda database)';
COMMENT ON COLUMN lims_laboratory_details.ncr_number IS 'Nomor NCR untuk dibaca manusia, mis. SO-2610-001';

-- Dipakai untuk mencari hasil OOS yang NCR-nya belum terbentuk (mis. saat
-- integrasi mati sementara, lalu perlu disusulkan).
CREATE INDEX IF NOT EXISTS ix_lims_ld_oos_tanpa_ncr
    ON lims_laboratory_details (spec_status)
    WHERE spec_status = 'FAIL' AND ncr_id IS NULL AND deleted_at IS NULL;

COMMIT;
