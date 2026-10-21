-- ============================================================
-- PENOMORAN OBSERVASI: SO-MMYY-NNN  ->  SO-YYMM-NNN  (PostgreSQL)
-- ============================================================
-- Pada format lama tahun berada di belakang bulan, sehingga urutan teks
-- nomor tidak sama dengan urutan waktu: "SO-0225" (Feb 2025) jatuh SEBELUM
-- "SO-1124" (Nov 2024) ketika kolom no_laporan diurutkan. Dengan tahun di
-- depan, urutan teks otomatis kronologis.
--
-- Perubahannya murni TRANSPOSISI, bukan penomoran ulang:
--   SO-0826-001  ->  SO-2608-001
--   SO-0524-003  ->  SO-2405-003
-- Bagian NNN tidak disentuh, jadi tidak ada nomor yang bertukar pemilik dan
-- dokumen/ekspor lama masih bisa dicocokkan. Pemetaan MMYY->YYMM bersifat
-- bijektif, sehingga nomor yang tadinya unik tetap unik.
--
-- Baris soft-deleted ikut diubah supaya tidak bentrok dengan nomor baru.
-- Idempoten: baris yang sudah berformat baru tidak ikut terkena karena
-- pola WHERE hanya cocok pada nomor yang belum dikonversi... lihat catatan.

BEGIN;

-- Catatan idempotensi: SO-MMYY dan SO-YYMM punya bentuk yang sama (4 digit),
-- jadi pola regex saja tidak bisa membedakan. Penanda konversi disimpan di
-- app_settings; skrip berhenti bila sudah pernah dijalankan.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'so_number_format') THEN
        RAISE NOTICE 'Konversi nomor SO sudah pernah dijalankan — dilewati.';
        RETURN;
    END IF;

    UPDATE irms_safety_observations
    -- 'SO-MMYY-NNN' : posisi 4-5 = MM, 6-7 = YY, 8.. = '-NNN'
    SET no_laporan = 'SO-'
                  || substring(no_laporan from 6 for 2)   -- YY
                  || substring(no_laporan from 4 for 2)   -- MM
                  || substring(no_laporan from 8)         -- '-NNN'
    WHERE no_laporan ~ '^SO-[0-9]{4}-[0-9]{3}$';

    INSERT INTO app_settings (setting_key, setting_value)
    VALUES ('so_number_format', 'YYMM');
END $$;

COMMIT;

-- Verifikasi (jalankan manual):
--   SELECT no_laporan FROM irms_safety_observations ORDER BY no_laporan LIMIT 10;
--   SELECT COUNT(*) total, COUNT(DISTINCT no_laporan) unik FROM irms_safety_observations;
