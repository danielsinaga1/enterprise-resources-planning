-- ============================================================
-- Perbaikan pemetaan PIC JSO 2026: isi division_id
-- ------------------------------------------------------------
-- Kolom PIC pada Excel ternyata memakai kode yang sama dengan
-- irms_divisions.code untuk sebagian nilainya. Pemetaan dilakukan lewat
-- kode itu, bukan tebakan nama:
--     GA -> GA, MECH -> MECH, E/I -> EI, LOG -> LOG
--
-- QSHE, OPR, dan TSP TIDAK diisi di sini: ketiganya kode DEPARTEMEN, dan
-- tiap departemen itu memuat dua divisi (QPS/SHE, Ammonia/Utility,
-- Inspection/Reliability). Menebak salah satunya akan melempar temuan ke
-- antrean Superintendent yang keliru, karena routing JSO memakai division_id.
-- Ketiganya tetap punya dept_designated_id yang benar.
--
-- Aman dijalankan ulang: hanya menyentuh baris impor JSO 2026 yang
-- division_id-nya masih kosong.
-- ============================================================

BEGIN;

UPDATE irms_jso_reports r
SET division_id = d.id,
    updated_at = now()
FROM irms_divisions d
WHERE r.division_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND d.deleted_at IS NULL
  AND d.code = CASE
        WHEN r.description LIKE '%| PIC: GA |%'   THEN 'GA'
        WHEN r.description LIKE '%| PIC: MECH |%' THEN 'MECH'
        WHEN r.description LIKE '%| PIC: E/I |%'  THEN 'EI'
        WHEN r.description LIKE '%| PIC: LOG |%'  THEN 'LOG'
      END;

COMMIT;

-- Verifikasi: sebaran divisi pada temuan JSO 2026
SELECT COALESCE(d.code, '(belum diisi)') AS divisi,
       COALESCE(d.name, '-')             AS nama,
       count(*)                          AS temuan
FROM irms_jso_reports r
LEFT JOIN irms_divisions d ON d.id = r.division_id
WHERE r.deleted_at IS NULL
  AND r.date_event >= '2026-01-01' AND r.date_event < '2027-01-01'
GROUP BY 1, 2
ORDER BY 3 DESC;

-- ------------------------------------------------------------
-- Tiga kode PIC yang setingkat departemen
-- ------------------------------------------------------------
-- QSHE, OPR, dan TSP adalah kode DEPARTEMEN; masing-masing memuat dua divisi,
-- sehingga pemetaannya tidak bisa disimpulkan dari data. Pilihan berikut
-- ditentukan pemilik proses (Daniel, 2026-09-17):
--     QSHE -> SHE  (bukan QPS)  : temuannya kebersihan, rambu, APAR, drainase
--     OPR  -> AMMO (bukan UTIL) : lokasinya area proses amonia
--     TSP  -> INSP (bukan RE)   : temuan kondisi peralatan, ranah inspeksi
-- Routing JSO memakai division_id, jadi angka ini menentukan Superintendent
-- mana yang menerima temuan.

BEGIN;

UPDATE irms_jso_reports r
SET division_id = d.id,
    updated_at = now()
FROM irms_divisions d
WHERE r.division_id IS NULL
  AND r.description LIKE '%Sumber: JSO 2026%'
  AND d.deleted_at IS NULL
  AND d.code = CASE
        WHEN r.description LIKE '%| PIC: QSHE |%' THEN 'SHE'
        WHEN r.description LIKE '%| PIC: OPR |%'  THEN 'AMMO'
        WHEN r.description LIKE '%| PIC: TSP |%'  THEN 'INSP'
      END;

COMMIT;

SELECT COALESCE(d.code, '(belum diisi)') AS divisi,
       COALESCE(d.name, '-')             AS nama,
       count(*)                          AS temuan
FROM irms_jso_reports r
LEFT JOIN irms_divisions d ON d.id = r.division_id
WHERE r.deleted_at IS NULL
  AND r.date_event >= '2026-01-01' AND r.date_event < '2027-01-01'
GROUP BY 1, 2
ORDER BY 3 DESC;
