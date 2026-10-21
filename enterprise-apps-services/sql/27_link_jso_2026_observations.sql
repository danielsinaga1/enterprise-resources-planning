-- ============================================================
-- Tautkan temuan JSO 2026 ke observasi induknya
-- ------------------------------------------------------------
-- Jalankan SETELAH 26_import_jso_2026.sql.
--
-- Seluruh 1.351 temuan 2019-2025 di tabel ini terikat ke sebuah
-- observasi induk; aplikasi menampilkan temuan LEWAT induknya. Tanpa
-- langkah ini, 111 baris hasil impor ada di tabel tetapi tidak muncul
-- di mana pun pada aplikasi.
--
-- Tanggal 2026-01-05 MEMAKAI ULANG observasi id 192 ("JSO 05 Januari
-- 2026") yang sudah ada, bukan membuat yang baru — sesi JSO-nya sama.
-- Observasi lain bertanggal 2026 sengaja TIDAK dipakai ulang karena
-- isinya data uji ([TEST-E2E], "Test Judul", dst).
--
-- Status observasi baru diisi "closed" mengikuti pola backfill
-- 2019-2025. Status itu keadaan alur kerja, bukan rangkuman status
-- temuan — mengisinya selain closed akan melempar catatan historis ini
-- ke antrean kerja orang.
--
-- Aman dijalankan ulang: pembuatan dijaga NOT EXISTS atas no_laporan,
-- penautan hanya menyentuh baris yang so_id-nya masih kosong.
-- ============================================================

BEGIN;

-- JSO 23 Januari 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2601-002', 'JSO 23 Januari 2026', 'closed', true,
       '2026-01-23'::timestamptz, '2026-01-23'::timestamptz, '2026-01-23'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2601-002');

-- JSO 26 Februari 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2602-006', 'JSO 26 Februari 2026', 'closed', true,
       '2026-02-26'::timestamptz, '2026-02-26'::timestamptz, '2026-02-26'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2602-006');

-- JSO 13 Maret 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2603-003', 'JSO 13 Maret 2026', 'closed', true,
       '2026-03-13'::timestamptz, '2026-03-13'::timestamptz, '2026-03-13'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2603-003');

-- JSO 26 Maret 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2603-004', 'JSO 26 Maret 2026', 'closed', true,
       '2026-03-26'::timestamptz, '2026-03-26'::timestamptz, '2026-03-26'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2603-004');

-- JSO 10 April 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2604-004', 'JSO 10 April 2026', 'closed', true,
       '2026-04-10'::timestamptz, '2026-04-10'::timestamptz, '2026-04-10'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2604-004');

-- JSO 15 April 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2604-005', 'JSO 15 April 2026', 'closed', true,
       '2026-04-15'::timestamptz, '2026-04-15'::timestamptz, '2026-04-15'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2604-005');

-- JSO 07 Mei 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2605-001', 'JSO 07 Mei 2026', 'closed', true,
       '2026-05-07'::timestamptz, '2026-05-07'::timestamptz, '2026-05-07'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2605-001');

-- JSO 12 Mei 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2605-002', 'JSO 12 Mei 2026', 'closed', true,
       '2026-05-12'::timestamptz, '2026-05-12'::timestamptz, '2026-05-12'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2605-002');

-- JSO 22 Mei 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2605-003', 'JSO 22 Mei 2026', 'closed', true,
       '2026-05-22'::timestamptz, '2026-05-22'::timestamptz, '2026-05-22'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2605-003');

-- JSO 20 Juni 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2606-002', 'JSO 20 Juni 2026', 'closed', true,
       '2026-06-20'::timestamptz, '2026-06-20'::timestamptz, '2026-06-20'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2606-002');

-- JSO 23 Juni 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2606-003', 'JSO 23 Juni 2026', 'closed', true,
       '2026-06-23'::timestamptz, '2026-06-23'::timestamptz, '2026-06-23'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2606-003');

-- JSO 15 Juli 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2607-019', 'JSO 15 Juli 2026', 'closed', true,
       '2026-07-15'::timestamptz, '2026-07-15'::timestamptz, '2026-07-15'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2607-019');

-- JSO 28 Juli 2026
INSERT INTO irms_safety_observations
  (no_laporan, title, status, is_jso, date_event, date_event_jso, observation_date, created_at, updated_at)
SELECT 'SO-2607-020', 'JSO 28 Juli 2026', 'closed', true,
       '2026-07-28'::timestamptz, '2026-07-28'::timestamptz, '2026-07-28'::timestamptz, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM irms_safety_observations WHERE no_laporan = 'SO-2607-020');

-- ------------------------------------------------------------
-- Penautan temuan ke induk (hanya yang belum punya induk)
-- ------------------------------------------------------------
-- 2026-01-05
UPDATE irms_jso_reports r
SET so_id = 192, updated_at = now()
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-01-05';

-- 2026-01-23
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-01-23'
  AND s.no_laporan = 'SO-2601-002';

-- 2026-02-26
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-02-26'
  AND s.no_laporan = 'SO-2602-006';

-- 2026-03-13
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-03-13'
  AND s.no_laporan = 'SO-2603-003';

-- 2026-03-26
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-03-26'
  AND s.no_laporan = 'SO-2603-004';

-- 2026-04-10
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-04-10'
  AND s.no_laporan = 'SO-2604-004';

-- 2026-04-15
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-04-15'
  AND s.no_laporan = 'SO-2604-005';

-- 2026-05-07
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-05-07'
  AND s.no_laporan = 'SO-2605-001';

-- 2026-05-12
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-05-12'
  AND s.no_laporan = 'SO-2605-002';

-- 2026-05-22
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-05-22'
  AND s.no_laporan = 'SO-2605-003';

-- 2026-06-20
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-06-20'
  AND s.no_laporan = 'SO-2606-002';

-- 2026-06-23
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-06-23'
  AND s.no_laporan = 'SO-2606-003';

-- 2026-07-15
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-07-15'
  AND s.no_laporan = 'SO-2607-019';

-- 2026-07-28
UPDATE irms_jso_reports r
SET so_id = s.id, updated_at = now()
FROM irms_safety_observations s
WHERE r.so_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND r.date_event::date = '2026-07-28'
  AND s.no_laporan = 'SO-2607-020';

-- ------------------------------------------------------------
-- Lengkapi baris yang sudah ada (id 1617 = No 2 pada Excel)
-- ------------------------------------------------------------
-- Hanya kolom yang MASIH KOSONG yang diisi. Potensi bahaya, akar
-- masalah, dan rekomendasi baris ini sudah identik dengan Excel, dan
-- corrective-nya ("sudah selesai diperbaiki") lebih baru daripada
-- status Open di Excel — menimpanya akan memundurkan data.
UPDATE irms_jso_reports
SET date_event = '2026-01-05'::timestamptz,
    description = $d$Lokasi: PR-3 (E-0212A)
Kategori: Housekeeping | PIC: E/I | Status per Excel: Open (di aplikasi sudah ditindaklanjuti)
Sumber: JSO 2026 - Tim A, Minggu 1, Area 12, No 2$d$,
    dept_designated_id = COALESCE(dept_designated_id, 3),
    updated_at = now()
WHERE id = 1617
  AND date_event IS NULL;

-- Samakan label status pada 111 baris impor agar tidak terbaca sebagai
-- status terkini: yang tersimpan adalah potret Excel, bukan keadaan hidup.
UPDATE irms_jso_reports
SET description = replace(description, ' | Status: ', ' | Status per Excel: '),
    updated_at = now()
WHERE description LIKE '%Sumber: JSO 2026%'
  AND description LIKE '% | Status: %';

COMMIT;

-- Verifikasi
SELECT s.no_laporan, s.title, s.date_event_jso::date AS tgl,
       count(r.id) AS temuan
FROM irms_safety_observations s
LEFT JOIN irms_jso_reports r ON r.so_id = s.id AND r.deleted_at IS NULL
WHERE s.is_jso AND s.date_event_jso >= '2026-01-01' AND s.date_event_jso < '2026-08-01'
GROUP BY 1,2,3 ORDER BY 3;

SELECT count(*) AS temuan_2026_yatim FROM irms_jso_reports
WHERE deleted_at IS NULL AND so_id IS NULL AND date_event >= '2026-01-01';
