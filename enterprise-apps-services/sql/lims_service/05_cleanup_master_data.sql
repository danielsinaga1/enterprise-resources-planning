-- ====================================================================
--  db_lims — Pembersihan master data parameter uji
--  Target : PostgreSQL (db_lims)
--  Tanggal: 2026-09-05
--
--  Lima baris lims_test_types adalah sisa kesalahan input, bukan
--  parameter yang benar-benar berbeda. Bukti dari data produksi:
--
--   #14 Kadar Air 2 (Anhydrous Ammonia)
--       Metodenya (SNI 06-0045-2006) SUDAH dimiliki #13 Kadar Air, yang
--       terhubung ke AOAC dan SNI sekaligus. #13 dipakai 3x di hasil uji,
--       #14 tidak pernah. Sistem memang membolehkan satu parameter punya
--       beberapa metode alternatif lewat lims_method_test_type, jadi baris
--       kedua ini tidak menambah apa pun.
--
--   #16 Purity Ammonia 2 (NH₃) (Anhydrous Ammonia)
--       Tanpa metode. Satu-satunya pemakaian ada di laporan uji coba
--       0002-AR-LAB/IX/2025 (status Open) yang mencampur parameter Natural
--       Gas (CH₄) dengan kemurnian amoniak bernilai 200 — tidak masuk akal
--       secara fisik. Dikonfirmasi sebagai data uji coba.
--
--   #18 Minyak 2 (Anhydrous Ammonia)     — tanpa metode, tanpa pemakaian.
--   #54 Mangan Terlarut, Mn (Air Limbah) — kembar #53, dibuat 1 detik
--       sesudahnya, tanpa metode, tanpa pemakaian.
--   #31 Sulfat, SO₄ (Air Proses)         — digantikan #68 Sulfat, SO₄²⁻
--       yang notasi ionnya benar; #31 tanpa metode dan tanpa pemakaian.
--
--  CATATAN: #29 dan #56 Fosfat (PO₄⁻³) TIDAK dibersihkan. Keduanya terikat
--  ke material berbeda (Air Proses dan Air Limbah). Karena
--  lims_test_types.parameter_id mengikat parameter ke satu material, dua
--  baris terpisah memang bentuk yang benar — bukan duplikat.
--
--  Pendekatan: soft delete (deleted_at), mengikuti @SQLRestriction di
--  entity. Baris tetap ada sehingga detail laboratorium lama tidak
--  menggantung, tapi hilang dari daftar pilihan di aplikasi.
--  Setiap statement dikunci id + nama sekaligus, jadi kalau id di suatu
--  lingkungan tidak sama, hasilnya no-op — bukan menghapus baris yang keliru.
-- ====================================================================

BEGIN;

-- --------------------------------------------------------------------
-- 1. Lepas relasi metode milik #14
--    lims_method_test_type murni tabel penghubung tanpa kolom deleted_at,
--    jadi barisnya dihapus. Metode SNI-nya tetap hidup lewat #13.
-- --------------------------------------------------------------------
DELETE FROM lims_method_test_type
 WHERE test_type_id = 14
   AND EXISTS (SELECT 1 FROM lims_test_types t
                WHERE t.id = 14 AND t.name = 'Kadar Air 2');

-- --------------------------------------------------------------------
-- 2. Lepas hasil uji coba yang menempel pada #16
--    Laporan induknya (0002-AR-LAB/IX/2025) sengaja TIDAK disentuh —
--    itu data transaksional, di luar lingkup pembersihan master data.
-- --------------------------------------------------------------------
UPDATE lims_laboratory_details
   SET deleted_at = NOW(), updated_at = NOW()
 WHERE test_type_id = 16
   AND deleted_at IS NULL
   AND EXISTS (SELECT 1 FROM lims_test_types t
                WHERE t.id = 16 AND t.name = 'Purity Ammonia 2 (NH₃)');

-- --------------------------------------------------------------------
-- 3. Soft delete kelima parameter uji
-- --------------------------------------------------------------------
UPDATE lims_test_types SET deleted_at = NOW(), updated_at = NOW()
 WHERE id = 14 AND name = 'Kadar Air 2'             AND deleted_at IS NULL;

UPDATE lims_test_types SET deleted_at = NOW(), updated_at = NOW()
 WHERE id = 16 AND name = 'Purity Ammonia 2 (NH₃)'  AND deleted_at IS NULL;

UPDATE lims_test_types SET deleted_at = NOW(), updated_at = NOW()
 WHERE id = 18 AND name = 'Minyak 2'                AND deleted_at IS NULL;

UPDATE lims_test_types SET deleted_at = NOW(), updated_at = NOW()
 WHERE id = 31 AND name = 'Sulfat, SO₄'             AND deleted_at IS NULL;

UPDATE lims_test_types SET deleted_at = NOW(), updated_at = NOW()
 WHERE id = 54 AND name = 'Mangan Terlarut, Mn'     AND deleted_at IS NULL
   -- jaga-jaga: hanya yang kembar, jangan sentuh #53 yang dipertahankan
   AND EXISTS (SELECT 1 FROM lims_test_types k
                WHERE k.id = 53 AND k.name = 'Mangan Terlarut, Mn'
                  AND k.parameter_id = lims_test_types.parameter_id
                  AND k.deleted_at IS NULL);

COMMIT;

-- --------------------------------------------------------------------
-- Verifikasi
-- --------------------------------------------------------------------
-- Kelima baris harus punya deleted_at terisi:
--   SELECT id, name, deleted_at FROM lims_test_types
--    WHERE id IN (14,16,18,31,54) ORDER BY id;
--
-- Yang dipertahankan harus utuh, metode dan hasilnya tidak berubah:
--   SELECT tt.id, tt.name,
--          (SELECT COUNT(*) FROM lims_method_test_type x WHERE x.test_type_id=tt.id) AS metode,
--          (SELECT COUNT(*) FROM lims_laboratory_details d
--            WHERE d.test_type_id=tt.id AND d.deleted_at IS NULL) AS hasil
--     FROM lims_test_types tt WHERE tt.id IN (13,15,17,53,68) ORDER BY tt.id;
--   -- harapan: 13 -> 2 metode / 3 hasil, 15 -> 2/0, 17 -> 2/0, 53 -> 1/0, 68 -> 1/0
--
-- Jumlah parameter uji aktif: 107 -> 102
--   SELECT COUNT(*) FROM lims_test_types WHERE deleted_at IS NULL;
--
-- Membatalkan (kalau perlu):
--   UPDATE lims_test_types SET deleted_at = NULL WHERE id IN (14,16,18,31,54);
--   UPDATE lims_laboratory_details SET deleted_at = NULL WHERE test_type_id = 16;
--   INSERT INTO lims_method_test_type (method_id, test_type_id) VALUES (3, 14);
