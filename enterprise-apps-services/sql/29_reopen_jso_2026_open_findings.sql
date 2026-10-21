-- ============================================================
-- Buka kembali sesi JSO 2026 yang masih punya temuan belum selesai
-- ------------------------------------------------------------
-- Observasi hasil backfill dibuat berstatus "closed" mengikuti pola impor
-- 2019-2025. Untuk 2026 itu tidak tepat: 29 temuan masih berstatus Open dan
-- memang harus dikerjakan lewat aplikasi. Observasi yang closed tidak
-- memunculkan aksi apa pun, sehingga temuan itu tidak akan pernah sampai ke
-- siapa-siapa.
--
-- Status tujuan "reviewed" dipilih karena itulah tahap tempat Superintendent
-- divisi menugaskan subordinate sebagai PIC per temuan — persis keadaan data
-- ini: divisi sudah diketahui, PIC belum.
--
-- Kriteria "belum selesai" memakai DUA syarat sekaligus:
--   status Excel = Open  DAN  belum ada tindak lanjut tercatat.
-- Satu syarat saja salah: baris 1617 tercatat Open di Excel tetapi sudah
-- ditindaklanjuti di aplikasi, sedangkan No 3 tercatat Close tanpa keterangan
-- sama sekali. Keduanya tidak boleh dianggap pekerjaan terbuka.
--
-- Sesi yang seluruh temuannya sudah selesai sengaja DIBIARKAN closed.
-- Aman dijalankan ulang; untuk mengembalikan, set status kembali ke 'closed'
-- pada nomor laporan yang tercantum di hasil verifikasi.
-- ============================================================

BEGIN;

UPDATE irms_safety_observations s
SET status = 'reviewed',
    updated_at = now()
WHERE s.status = 'closed'
  AND EXISTS (
        SELECT 1
        FROM irms_jso_reports r
        WHERE r.so_id = s.id
          AND r.deleted_at IS NULL
          AND r.description LIKE '%Sumber: JSO 2026%'
          AND r.description LIKE '%Status per Excel: Open%'
          AND (r.corrective IS NULL OR btrim(r.corrective) = '')
      );

COMMIT;

-- Verifikasi
SELECT s.no_laporan, s.title, s.status,
       count(r.id) AS temuan,
       count(*) FILTER (
         WHERE r.description LIKE '%Status per Excel: Open%'
           AND (r.corrective IS NULL OR btrim(r.corrective) = '')
       ) AS perlu_tindak_lanjut
FROM irms_safety_observations s
JOIN irms_jso_reports r ON r.so_id = s.id AND r.deleted_at IS NULL
WHERE s.title LIKE 'JSO%2026' AND s.date_event_jso < '2026-08-01'
GROUP BY 1,2,3, s.date_event_jso
ORDER BY s.date_event_jso;
