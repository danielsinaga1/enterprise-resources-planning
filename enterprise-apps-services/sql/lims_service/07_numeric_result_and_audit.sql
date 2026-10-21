-- ============================================================================
-- 07 — Hasil numerik + batas spesifikasi (OOS) dan audit trail
-- ----------------------------------------------------------------------------
-- Target: db_lims. Jalankan sekali:
--     psql -U <user> -d db_lims -f 07_numeric_result_and_audit.sql
--
-- Dialek: berkas 01-06 masih ditulis untuk MySQL (warisan sebelum migrasi
-- PostgreSQL 2026-07-11). Database yang benar-benar dipakai adalah PostgreSQL
-- (lihat app.datasource.lims.url di application-dev.yaml), jadi berkas ini
-- memakai dialek PostgreSQL. Seluruh pernyataan idempoten — aman diulang.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Kolom pelaku (siapa) pada semua tabel turunan LimsBaseEntity
--    Sebelumnya hanya ada created_at/updated_at/deleted_at — "kapan" tanpa
--    "siapa", sehingga hasil uji bisa diubah atau dihapus tanpa jejak pemilik.
-- ----------------------------------------------------------------------------
DO $migrasi$
DECLARE
    t text;
    tables text[] := ARRAY[
        'lims_departments', 'lims_parameters', 'lims_test_types', 'lims_methods',
        'lims_standards', 'lims_units', 'lims_results', 'lims_chemical_compounds',
        'lims_inventory_items', 'lims_laboratory_reports', 'lims_laboratory_details',
        'lims_daily_reports'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        IF to_regclass(t) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS created_by BIGINT', t);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS updated_by BIGINT', t);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS deleted_by BIGINT', t);
        END IF;
    END LOOP;
END
$migrasi$;

-- ----------------------------------------------------------------------------
-- 2. Batas spesifikasi pada master standar
--    limit_type menentukan cara membandingkan:
--      QUALITATIVE — tidak dievaluasi numerik (mis. "Jernih", "Tidak berbau")
--      RANGE       — lower_limit <= nilai <= upper_limit
--      MAX         — nilai <= upper_limit
--      MIN         — nilai >= lower_limit
-- ----------------------------------------------------------------------------
ALTER TABLE lims_standards
    ADD COLUMN IF NOT EXISTS limit_type     VARCHAR(20) NOT NULL DEFAULT 'QUALITATIVE',
    ADD COLUMN IF NOT EXISTS lower_limit    NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS upper_limit    NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS target_value   NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS decimal_places SMALLINT;

COMMENT ON COLUMN lims_standards.limit_type     IS 'QUALITATIVE | RANGE | MAX | MIN';
COMMENT ON COLUMN lims_standards.decimal_places IS 'Jumlah desimal untuk penyajian hasil; NULL = apa adanya';

ALTER TABLE lims_standards DROP CONSTRAINT IF EXISTS ck_lims_standards_limit_type;
ALTER TABLE lims_standards ADD CONSTRAINT ck_lims_standards_limit_type
    CHECK (limit_type IN ('QUALITATIVE', 'RANGE', 'MAX', 'MIN'));

-- Batas bawah tidak boleh melampaui batas atas. Dibiarkan longgar bila salah
-- satu masih NULL supaya master data bisa diisi bertahap.
ALTER TABLE lims_standards DROP CONSTRAINT IF EXISTS ck_lims_standards_limit_order;
ALTER TABLE lims_standards ADD CONSTRAINT ck_lims_standards_limit_order
    CHECK (lower_limit IS NULL OR upper_limit IS NULL OR lower_limit <= upper_limit);

-- ----------------------------------------------------------------------------
-- 3. Hasil numerik + status spesifikasi pada detail analisis
--    value_operator menampung hasil di bawah/di atas batas deteksi ("<", ">"),
--    mis. "<0,01". Nilainya tidak diketahui persis, jadi kesimpulan lulus/tidak
--    hanya diambil bila operator tetap menentukan — lihat LimsSpecEvaluator.
-- ----------------------------------------------------------------------------
ALTER TABLE lims_laboratory_details
    ADD COLUMN IF NOT EXISTS value_numeric     NUMERIC(20,6),
    ADD COLUMN IF NOT EXISTS value_operator    VARCHAR(2),
    ADD COLUMN IF NOT EXISTS spec_status       VARCHAR(20) NOT NULL DEFAULT 'NOT_EVALUATED',
    ADD COLUMN IF NOT EXISTS spec_evaluated_at TIMESTAMP;

COMMENT ON COLUMN lims_laboratory_details.spec_status    IS 'PASS | FAIL | NOT_EVALUATED — dihitung backend, bukan diketik pengguna';
COMMENT ON COLUMN lims_laboratory_details.value_operator IS 'NULL | < | > — penanda batas deteksi';

ALTER TABLE lims_laboratory_details DROP CONSTRAINT IF EXISTS ck_lims_ld_spec_status;
ALTER TABLE lims_laboratory_details ADD CONSTRAINT ck_lims_ld_spec_status
    CHECK (spec_status IN ('PASS', 'FAIL', 'NOT_EVALUATED'));

ALTER TABLE lims_laboratory_details DROP CONSTRAINT IF EXISTS ck_lims_ld_value_operator;
ALTER TABLE lims_laboratory_details ADD CONSTRAINT ck_lims_ld_value_operator
    CHECK (value_operator IS NULL OR value_operator IN ('<', '>'));

CREATE INDEX IF NOT EXISTS ix_lims_ld_spec_status ON lims_laboratory_details (spec_status);

-- ----------------------------------------------------------------------------
-- 4. Alasan penolakan laporan
--    reject() sebelumnya hanya mengganti status tanpa menyimpan alasan, jadi
--    laporan yang ditolak tidak bisa dipertanggungjawabkan.
-- ----------------------------------------------------------------------------
ALTER TABLE lims_laboratory_reports
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
    ADD COLUMN IF NOT EXISTS rejected_by_id   BIGINT,
    ADD COLUMN IF NOT EXISTS rejected_at      TIMESTAMP;

-- ----------------------------------------------------------------------------
-- 5. Audit trail — append-only
--    Satu baris = satu perubahan satu field (atau satu transisi status).
--    Tidak ada FK ke tabel sumber: jejak audit harus tetap hidup walau baris
--    sumbernya dihapus.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lims_audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    parent_type VARCHAR(50),
    parent_id   BIGINT,
    action      VARCHAR(30)  NOT NULL,
    field_name  VARCHAR(100),
    old_value   TEXT,
    new_value   TEXT,
    reason      TEXT,
    actor_id    BIGINT,
    actor_name  VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  lims_audit_logs             IS 'Append-only. Jejak audit LIMS (ALCOA+): siapa mengubah apa, dari nilai apa ke apa, kapan, kenapa.';
COMMENT ON COLUMN lims_audit_logs.parent_type IS 'Induk untuk pengelompokan, mis. detail analisis dikelompokkan ke LABORATORY_REPORT';
COMMENT ON COLUMN lims_audit_logs.action      IS 'CREATE | UPDATE | DELETE | STATUS_CHANGE';

CREATE INDEX IF NOT EXISTS ix_lims_audit_entity     ON lims_audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS ix_lims_audit_parent     ON lims_audit_logs (parent_type, parent_id);
CREATE INDEX IF NOT EXISTS ix_lims_audit_created_at ON lims_audit_logs (created_at DESC);

-- Jejak audit yang bisa disunting bukan jejak audit. Trigger ini menolak UPDATE
-- dan DELETE dari aplikasi; pemeliharaan yang sah (mis. arsip tahunan) harus
-- menonaktifkan trigger secara sadar lebih dulu.
CREATE OR REPLACE FUNCTION lims_audit_logs_immutable() RETURNS TRIGGER AS
$imut$
BEGIN
    RAISE EXCEPTION 'lims_audit_logs bersifat append-only (operasi % ditolak)', TG_OP;
END
$imut$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_lims_audit_logs_immutable ON lims_audit_logs;
CREATE TRIGGER trg_lims_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON lims_audit_logs
    FOR EACH ROW EXECUTE FUNCTION lims_audit_logs_immutable();

COMMIT;
