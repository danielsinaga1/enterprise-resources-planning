-- ====================================================================
--  db_lims — Lingkup Pengujian Laboratorium (master_lingkup_pengujian.json)
--  Target   : PostgreSQL (db_lims)
--  Sumber   : sheet "Lingkup Pengujian", sudah dicocokkan ke master data app
--
--  Semua INSERT idempoten (WHERE NOT EXISTS) dan memakai lookup NAMA,
--  bukan id hardcode — aman dijalankan ulang, dan tidak ikut rusak kalau
--  id di DB hidup sudah bergeser dari file seed.
--  Kolom id memakai IDENTITY, jadi id sengaja tidak diisi manual supaya
--  sequence tidak tertinggal.
-- ====================================================================

BEGIN;

-- --------------------------------------------------------------------
-- 1. Perbaikan salah ketik pada lims_methods
--    Superscript dipakai di tempat yang seharusnya angka biasa / subscript.
-- --------------------------------------------------------------------
UPDATE lims_methods SET name = 'AOAC Official Method 21st Edition, No. 959.02, 2019', updated_at = NOW()
 WHERE name = 'AOAC Official Method ²¹st Edition, No. 959.02, 2019';
UPDATE lims_methods SET name = 'APHA 4500-NH₃ F, 23rd Edition, 2017', updated_at = NOW()
 WHERE name = 'APHA 4500-NH³ F, 23rd Edition, 2017';

-- --------------------------------------------------------------------
-- 2. Satuan baru (1)
-- --------------------------------------------------------------------
INSERT INTO lims_units (name, created_at, updated_at)
SELECT 'ppm', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_units WHERE name = 'ppm' AND deleted_at IS NULL);

-- --------------------------------------------------------------------
-- 3. Metode uji baru (27)
-- --------------------------------------------------------------------
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'APHA 2320B, 23rd Edition, 2017', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'APHA 2320B, 23rd Edition, 2017' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'APHA 4500-SiO₂ D, 23rd Edition, 2017', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'APHA 4500-SiO₂ D, 23rd Edition, 2017' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'IKL LAB: IKL-05.021 (Nephelometri)', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'IKL LAB: IKL-05.021 (Nephelometri)' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'IKL LAB: IKL-05.018 (Gravimetri)', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'IKL LAB: IKL-05.018 (Gravimetri)' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'IKL LAB: IKL-05.028 (IR Spektrometri)', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'IKL LAB: IKL-05.028 (IR Spektrometri)' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'IKL LAB: IKL-05.029 (Colorimetri DPD)', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'IKL LAB: IKL-05.029 (Colorimetri DPD)' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'SNI 19-6964.5-2003', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'SNI 19-6964.5-2003' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'IKL LAB: IKL-05.031 (Titrimetri)', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'IKL LAB: IKL-05.031 (Titrimetri)' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'IKL LAB: IKL-05.024 (COD Meter)', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'IKL LAB: IKL-05.024 (COD Meter)' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'IKL LAB: IKL-05.032 (Elektrode Membran) in situ', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'IKL LAB: IKL-05.032 (Elektrode Membran) in situ' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'SNI 19-6964.3-2003', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'SNI 19-6964.3-2003' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'SNI 06-6989.11-2004', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'SNI 06-6989.11-2004' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'JIS K 0806 : 1997', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'JIS K 0806 : 1997' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'APHA 5220C, 23rd Edition, 2017', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'APHA 5220C, 23rd Edition, 2017' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'APHA 5210 D, 23rd Edition, 2017', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'APHA 5210 D, 23rd Edition, 2017' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'APHA 4500-NH₃ D, 23rd Edition, 2017', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'APHA 4500-NH₃ D, 23rd Edition, 2017' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'SNI 06-6989.23-2005', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'SNI 06-6989.23-2005' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'APHA 4500-Cl G, 23rd Edition, 2017', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'APHA 4500-Cl G, 23rd Edition, 2017' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'SNI 7119.2:2017', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'SNI 7119.2:2017' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'SNI 7119.7:2017', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'SNI 7119.7:2017' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'SNI 19-7119.1-2005', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'SNI 19-7119.1-2005' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'IKL LAB : IKL-05.008 (Gas Chromatography)', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'IKL LAB : IKL-05.008 (Gas Chromatography)' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'IKL LAB : IKL-001.023 (Direct Reading)', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'IKL LAB : IKL-001.023 (Direct Reading)' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'SNI 7119.3: 2017', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'SNI 7119.3: 2017' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'IKL LAB : IKL-05.012 (Gas Analyzer)', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'SNI 19-7117.7-2005', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'SNI 19-7117.7-2005' AND deleted_at IS NULL);
INSERT INTO lims_methods (name, created_at, updated_at)
SELECT 'SNI 19-7117.6-2005', NOW(), NOW()
 WHERE NOT EXISTS (SELECT 1 FROM lims_methods WHERE name = 'SNI 19-7117.6-2005' AND deleted_at IS NULL);

-- --------------------------------------------------------------------
-- 4. Parameter uji baru (37 baris)
--    lims_test_types.parameter_id mengikat parameter ke satu material,
--    jadi nama yang sudah ada pada material lain tetap perlu baris sendiri.
-- --------------------------------------------------------------------
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Nitrogen (N₂)', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Anhydrous Ammonia'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Nitrogen (N₂)' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Klorida, Cl⁻', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Proses'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Klorida, Cl⁻' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'pH', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'pH' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Suhu', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Suhu' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Daya Hantar Listrik (DHL)', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Daya Hantar Listrik (DHL)' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Padatan Terlarut Total (TDS)', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Padatan Terlarut Total (TDS)' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Kekeruhan', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Kekeruhan' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Padatan Tersuspensi Total (TSS)', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Padatan Tersuspensi Total (TSS)' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Salinitas', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Salinitas' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Minyak dan Lemak', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Minyak dan Lemak' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Klorin Bebas', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Klorin Bebas' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Sulfat, SO₄²⁻', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Sulfat, SO₄²⁻' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Alkalinitas', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Alkalinitas' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Kebutuhan Oksigen Kimiawi (COD)', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Kebutuhan Oksigen Kimiawi (COD)' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Oksigen Terlarut (DO)', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Oksigen Terlarut (DO)' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Ammonia total (NH₃-N)', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Laut'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Ammonia total (NH₃-N)' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'pH', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'pH' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Padatan Tersuspensi Total (TSS)', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Padatan Tersuspensi Total (TSS)' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Padatan Terlarut Total (TDS)', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Padatan Terlarut Total (TDS)' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Kebutuhan Oksigen Biokimiawi (BOD)', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Kebutuhan Oksigen Biokimiawi (BOD)' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Suhu', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Suhu' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Minyak dan Lemak', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Minyak dan Lemak' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Total Nikel, Ni', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Total Nikel, Ni' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Total Krom, Cr', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Total Krom, Cr' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Total Seng, Zn', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Total Seng, Zn' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Besi Terlarut, Fe', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Besi Terlarut, Fe' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Total Tembaga, Cu', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Total Tembaga, Cu' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Total Kadmium, Cd', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Total Kadmium, Cd' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Total Timbal, Pb', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Air Limbah'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Total Timbal, Pb' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Amoniak, NH₃', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Udara Ambient'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Amoniak, NH₃' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Karbon Monoksida, CO', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Udara Ambient'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Karbon Monoksida, CO' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Suhu', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Udara Ambient'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Suhu' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Suhu', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Udara Dalam Ruangan'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Suhu' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Kelembaban', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Udara Dalam Ruangan'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Kelembaban' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Nitrogen Dioksida, NO₂', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Udara Emisi Sumber Tidak Bergerak'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Nitrogen Dioksida, NO₂' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Sulfur Dioksida, SO₂', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Udara Emisi Sumber Tidak Bergerak'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Sulfur Dioksida, SO₂' AND t.parameter_id = m.id AND t.deleted_at IS NULL);
INSERT INTO lims_test_types (name, parameter_id, created_at, updated_at)
SELECT 'Karbon Dioksida, CO₂', m.id, NOW(), NOW()
  FROM lims_parameters m
 WHERE m.name = 'Udara Emisi Sumber Tidak Bergerak'
   AND NOT EXISTS (SELECT 1 FROM lims_test_types t
                    WHERE t.name = 'Karbon Dioksida, CO₂' AND t.parameter_id = m.id AND t.deleted_at IS NULL);

-- --------------------------------------------------------------------
-- 5. Relasi metode <-> parameter uji (123)
--    MIN(id) dipakai karena master data app masih memuat beberapa baris
--    kembar (mis. "Mangan Terlarut, Mn").
-- --------------------------------------------------------------------
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'N₂, Nitrogen' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'CO₂, Karbondioksida' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'CH₄, Metana' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'C₂H₆, Etana' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'C₃H₈, Propana' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'i-C₄H₁₀, iso Butana' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'n-C₄H₁₀, normal Butana' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'i-C₅H₁₂, iso Pentana' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'n-C₅H₁₂, normal Pentana' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'C₆H₁₄⁺, Heksana +' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'SG, Specific Gravity' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'GHV, Gross Heating Value' AND m.name = 'Natural Gas' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'AOAC Official Method 21st Edition, No. 959.02, 2019' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kadar Air' AND m.name = 'Anhydrous Ammonia' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 06-0045-2006 Amoniak Cair' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kadar Air' AND m.name = 'Anhydrous Ammonia' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'AOAC Official Method 21st Edition, No. 959.02, 2019' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Purity Ammonia (NH₃)' AND m.name = 'Anhydrous Ammonia' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 06-0045-2006 Amoniak Cair' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Purity Ammonia (NH₃)' AND m.name = 'Anhydrous Ammonia' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'AOAC Official Method 21st Edition, No. 959.02, 2019' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Nitrogen (N₂)' AND m.name = 'Anhydrous Ammonia' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 06-0045-2006 Amoniak Cair' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Nitrogen (N₂)' AND m.name = 'Anhydrous Ammonia' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'BS 4431 : 1989' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Minyak' AND m.name = 'Anhydrous Ammonia' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 06-0045-2006 Amoniak Cair' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Minyak' AND m.name = 'Anhydrous Ammonia' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL Lab : IKL-08.010 (APHA 3113B & 3500-Fe B)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Besi, Fe' AND m.name = 'Anhydrous Ammonia' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-08.009 (APHA 4500-Cl⁻  E & 4110 B)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Klorida, Cl⁻' AND m.name = 'Anhydrous Ammonia' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-H⁺ B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'pH' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 2540D, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Padatan Tersuspensi Total (TSS)' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 2540C, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Padatan Terlarut Total (TDS)' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 2540B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Padatan Total (TS)' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 2510B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Daya Hantar Listrik (DHL)' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-O G, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Oksigen Terlarut (DO)' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-NH₃ F, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Ion Ammonium (NH₄)' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 2550B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Suhu' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 5520C, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Minyak dan Lemak' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-P C, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Fosfat (PO₄⁻³)' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-P B & C, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Fosfat (PO₄)' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-SO₄²⁻ E, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Sulfat, SO₄²⁻' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-Cl⁻ E, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Klorida, Cl⁻' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 2130B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kekeruhan' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 2320B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Alkalinitas' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-SiO₂ D, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Silika, SiO₂' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Nikel, Ni' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Nikel, Ni' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Krom, Cr' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Krom, Cr' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Seng, Zn' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Seng, Zn' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Besi, Fe' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Besi, Fe' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3500 Fe B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Besi, Fe' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3500 Fe B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Besi Terlarut, Fe' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3500 Fe B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Ion Besi (II), Fe²⁺' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3500 Fe B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Ion Besi (III), Fe³⁺' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Tembaga, Cu' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Tembaga, Cu' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Natrium, Na' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Natrium, Na' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Kalsium, Ca' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Kalsium, Ca' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Mangan, Mn' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Mangan, Mn' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Aluminium, Al' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Kadmium, Cd' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Timbal, Pb' AND m.name = 'Air Proses' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.017 (Elektrometri) in situ' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'pH' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.025 (Direct Reading) in situ' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Suhu' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.027 (Electrical Conductivity) in situ' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Daya Hantar Listrik (DHL)' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.027 (Electrical Conductivity) in situ' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Padatan Terlarut Total (TDS)' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.023 (Secchi Disc) in situ' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kecerahan' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.021 (Nephelometri)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kekeruhan' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.026 (Titrimetri)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kesadahan Total' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.026 (Titrimetri)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kesadahan-Ca' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.026 (Titrimetri)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kesadahan-Mg' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.018 (Gravimetri)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Padatan Tersuspensi Total (TSS)' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.027 (Electrical Conductivity) in situ' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Salinitas' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.028 (IR Spektrometri)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Minyak dan Lemak' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.029 (Colorimetri DPD)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Klorin Bebas' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 19-6964.5-2003' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Sulfat, SO₄²⁻' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.031 (Titrimetri)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Alkalinitas' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.024 (COD Meter)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kebutuhan Oksigen Kimiawi (COD)' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB: IKL-05.032 (Elektrode Membran) in situ' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Oksigen Terlarut (DO)' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 19-6964.3-2003' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Ammonia total (NH₃-N)' AND m.name = 'Air Laut' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 06-6989.11-2004' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'pH' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 2540D, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Padatan Tersuspensi Total (TSS)' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 2540C, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Padatan Terlarut Total (TDS)' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'JIS K 0806 : 1997' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kebutuhan Oksigen Kimiawi (COD)' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 5220C, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kebutuhan Oksigen Kimiawi (COD)' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 5210 D, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kebutuhan Oksigen Biokimiawi (BOD)' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-NH₃ D, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Ammonia total (NH₃-N)' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-NH₃ F, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Ammonia total (NH₃-N)' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 2550B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Suhu' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 06-6989.23-2005' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Suhu' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 5520C, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Minyak dan Lemak' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Nikel, Ni' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Nikel, Ni' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Krom, Cr' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Krom, Cr' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Seng, Zn' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Seng, Zn' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3500 Fe B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Besi Terlarut, Fe' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Tembaga, Cu' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Tembaga, Cu' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Mangan Terlarut, Mn' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Kadmium, Cd' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Total Timbal, Pb' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-Cl G, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Klorin Bebas' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'APHA 4500-P C, 23rd Edition, 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Fosfat (PO₄⁻³)' AND m.name = 'Air Limbah' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 7119.2:2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Nitrogen Dioksida, NO₂' AND m.name = 'Udara Ambient' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 7119.7:2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Sulfur Dioksida, SO₂' AND m.name = 'Udara Ambient' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 19-7119.1-2005' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Amoniak, NH₃' AND m.name = 'Udara Ambient' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-05.008 (Gas Chromatography)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Karbon Monoksida, CO' AND m.name = 'Udara Ambient' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-001.023 (Direct Reading)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Suhu' AND m.name = 'Udara Ambient' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-001.023 (Direct Reading)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kelembaban' AND m.name = 'Udara Ambient' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 7119.3: 2017' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'TSP 24 jam' AND m.name = 'Udara Ambient' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-001.023 (Direct Reading)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Suhu' AND m.name = 'Udara Dalam Ruangan' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-001.023 (Direct Reading)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Kelembaban' AND m.name = 'Udara Dalam Ruangan' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Nitrogen Dioksida, NO₂' AND m.name = 'Udara Emisi Sumber Tidak Bergerak' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Sulfur Dioksida, SO₂' AND m.name = 'Udara Emisi Sumber Tidak Bergerak' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Karbon Dioksida, CO₂' AND m.name = 'Udara Emisi Sumber Tidak Bergerak' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Oksigen, O₂' AND m.name = 'Udara Emisi Sumber Tidak Bergerak' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-05.008 (Gas Chromatography)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Oksigen, O₂' AND m.name = 'Udara Emisi Sumber Tidak Bergerak' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Nitrogen Oksida, NO' AND m.name = 'Udara Emisi Sumber Tidak Bergerak' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Nitrogen Oksida, NOₓ' AND m.name = 'Udara Emisi Sumber Tidak Bergerak' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Karbon Monoksida, CO' AND m.name = 'Udara Emisi Sumber Tidak Bergerak' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 19-7117.7-2005' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Hidrogen Sulfida, H₂S' AND m.name = 'Udara Emisi Sumber Tidak Bergerak' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);
INSERT INTO lims_method_test_type (method_id, test_type_id)
SELECT me.id, tt.id FROM
  (SELECT MIN(id) AS id FROM lims_methods    WHERE name = 'SNI 19-7117.6-2005' AND deleted_at IS NULL) me,
  (SELECT MIN(t.id) AS id FROM lims_test_types t JOIN lims_parameters m ON m.id = t.parameter_id
    WHERE t.name = 'Amoniak, NH₃' AND m.name = 'Udara Emisi Sumber Tidak Bergerak' AND t.deleted_at IS NULL) tt
 WHERE me.id IS NOT NULL AND tt.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_method_test_type x WHERE x.method_id = me.id AND x.test_type_id = tt.id);

-- --------------------------------------------------------------------
-- 6. Nilai spesifikasi (50 baris lims_standards)
--    69 dari 123 lingkup tidak punya nilai spesifikasi, jadi tidak
--    menghasilkan baris di sini. Relasinya sudah tercatat di bagian 5.
-- --------------------------------------------------------------------
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '0.04', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '0.04' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '0.04' AND me.name = 'GPA Standard No. 2261- 2020' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% mol' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '2.97', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '2.97' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '2.97' AND me.name = 'GPA Standard No. 2261- 2020' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% mol' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '88.14', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '88.14' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '88.14' AND me.name = 'GPA Standard No. 2261- 2020' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% mol' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '6.49', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '6.49' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '6.49' AND me.name = 'GPA Standard No. 2261- 2020' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% mol' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '1.5', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '1.5' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '1.5' AND me.name = 'GPA Standard No. 2261- 2020' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% mol' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '0.24', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '0.24' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '0.24' AND me.name = 'GPA Standard No. 2261- 2020' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% mol' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '0.32', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '0.32' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '0.32' AND me.name = 'GPA Standard No. 2261- 2020' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% mol' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '0.14', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '0.14' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '0.14' AND me.name = 'GPA Standard No. 2261- 2020' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% mol' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '0.08', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '0.08' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '0.08' AND me.name = 'GPA Standard No. 2261- 2020' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% mol' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '1073.9664', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'GPA Standard No. 2261- 2020' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '1073.9664' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '1073.9664' AND me.name = 'GPA Standard No. 2261- 2020' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'BTU/SCF' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT 'Max 0.5', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'AOAC Official Method 21st Edition, No. 959.02, 2019' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = 'Max 0.5' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = 'Max 0.5' AND me.name = 'AOAC Official Method 21st Edition, No. 959.02, 2019' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% Wt' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT 'Max 0.5', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'SNI 06-0045-2006 Amoniak Cair' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = 'Max 0.5' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = 'Max 0.5' AND me.name = 'SNI 06-0045-2006 Amoniak Cair' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% Wt' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT 'Min 99.5', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'AOAC Official Method 21st Edition, No. 959.02, 2019' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = 'Min 99.5' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = 'Min 99.5' AND me.name = 'AOAC Official Method 21st Edition, No. 959.02, 2019' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% Wt' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT 'Min 99.5', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'SNI 06-0045-2006 Amoniak Cair' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = 'Min 99.5' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = 'Min 99.5' AND me.name = 'SNI 06-0045-2006 Amoniak Cair' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = '% Wt' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '8', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'BS 4431 : 1989' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '8' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '8' AND me.name = 'BS 4431 : 1989' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/kg' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '8', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'SNI 06-0045-2006 Amoniak Cair' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '8' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '8' AND me.name = 'SNI 06-0045-2006 Amoniak Cair' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/kg' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '2', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'IKL Lab : IKL-08.010 (APHA 3113B & 3500-Fe B)' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '2' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '2' AND me.name = 'IKL Lab : IKL-08.010 (APHA 3113B & 3500-Fe B)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/kg' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '10', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'IKL LAB: IKL-08.009 (APHA 4500-Cl⁻  E & 4110 B)' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '10' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '10' AND me.name = 'IKL LAB: IKL-08.009 (APHA 4500-Cl⁻  E & 4110 B)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/kg' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '6.5-8.5', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'IKL LAB: IKL-05.017 (Elektrometri) in situ' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '6.5-8.5' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '>3', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'IKL LAB: IKL-05.023 (Secchi Disc) in situ' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '>3' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '>3' AND me.name = 'IKL LAB: IKL-05.023 (Secchi Disc) in situ' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'm' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '80', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'IKL LAB: IKL-05.018 (Gravimetri)' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '80' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '80' AND me.name = 'IKL LAB: IKL-05.018 (Gravimetri)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '5', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'IKL LAB: IKL-05.028 (IR Spektrometri)' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '5' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '5' AND me.name = 'IKL LAB: IKL-05.028 (IR Spektrometri)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '0.3', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'SNI 19-6964.3-2003' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '0.3' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '0.3' AND me.name = 'SNI 19-6964.3-2003' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '6-9', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'SNI 06-6989.11-2004' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '6-9' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '30', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 2540D, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '30' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '30' AND me.name = 'APHA 2540D, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '100', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'JIS K 0806 : 1997' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '100' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '100' AND me.name = 'JIS K 0806 : 1997' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '100', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 5220C, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '100' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '100' AND me.name = 'APHA 5220C, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '30', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 5210 D, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '30' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '30' AND me.name = 'APHA 5210 D, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '10', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 4500-NH₃ D, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '10' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '10' AND me.name = 'APHA 4500-NH₃ D, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '10', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 4500-NH₃ F, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '10' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '10' AND me.name = 'APHA 4500-NH₃ F, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '5', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 5520C, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '5' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '5' AND me.name = 'APHA 5520C, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '0.5', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '0.5' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '0.5' AND me.name = 'APHA 3113B, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '0.5', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '0.5' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '0.5' AND me.name = 'APHA 3120B, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '1', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '1' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '1' AND me.name = 'APHA 3113B, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '1', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '1' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '1' AND me.name = 'APHA 3120B, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '2', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 3113B, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '2' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '2' AND me.name = 'APHA 3113B, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '2', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '2' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '2' AND me.name = 'APHA 3120B, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '0.1', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 3120B, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '0.1' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '0.1' AND me.name = 'APHA 3120B, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '1', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 4500-Cl G, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '1' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '1' AND me.name = 'APHA 4500-Cl G, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '10', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'APHA 4500-P C, 23rd Edition, 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '10' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '10' AND me.name = 'APHA 4500-P C, 23rd Edition, 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/L' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '100', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'SNI 7119.2:2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '100' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '100' AND me.name = 'SNI 7119.2:2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'µg/m3' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '150', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'SNI 7119.7:2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '150' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '150' AND me.name = 'SNI 7119.7:2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'µg/m3' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '2', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'SNI 19-7119.1-2005' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '2' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '2' AND me.name = 'SNI 19-7119.1-2005' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'ppm' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '10000', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'IKL LAB : IKL-05.008 (Gas Chromatography)' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '10000' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '10000' AND me.name = 'IKL LAB : IKL-05.008 (Gas Chromatography)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'µg/m3' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '230', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'SNI 7119.3: 2017' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '230' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '230' AND me.name = 'SNI 7119.3: 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'µg/m3' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '800 (Primary Reformer)
150 (PKB)', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '800 (Primary Reformer)
150 (PKB)' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '800 (Primary Reformer)
150 (PKB)' AND me.name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/m3' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '1000 (Primary Reformer)
650 (PKB)', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '1000 (Primary Reformer)
650 (PKB)' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '1000 (Primary Reformer)
650 (PKB)' AND me.name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/m3' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '1000', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '1000' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '1000' AND me.name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/m3' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '35', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'SNI 19-7117.7-2005' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '35' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '35' AND me.name = 'SNI 19-7117.7-2005' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/m3' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standards (measure, method_id, created_at, updated_at)
SELECT '0.5', me.id, NOW(), NOW()
  FROM (SELECT MIN(id) AS id FROM lims_methods WHERE name = 'SNI 19-7117.6-2005' AND deleted_at IS NULL) me
 WHERE me.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standards s
                    WHERE s.measure = '0.5' AND s.method_id = me.id AND s.deleted_at IS NULL);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '0.5' AND me.name = 'SNI 19-7117.6-2005' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/m3' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);

COMMIT;

-- --------------------------------------------------------------------
-- Verifikasi (jalankan setelah COMMIT)
-- --------------------------------------------------------------------
-- SELECT COUNT(*) FROM lims_test_types WHERE deleted_at IS NULL;  -- 70 -> 107
-- SELECT COUNT(*) FROM lims_methods    WHERE deleted_at IS NULL;  -- 28 -> 55
-- SELECT COUNT(*) FROM lims_units      WHERE deleted_at IS NULL;  -- 16 -> 17
-- SELECT COUNT(*) FROM lims_method_test_type;
-- SELECT COUNT(*) FROM lims_standards  WHERE deleted_at IS NULL;
-- ====================================================================
-- 7. Tautan satuan yang terlewat pada jalankan pertama (2026-09-05)
--
--    Master data di server sudah memakai superscript (µg/m³, mg/m³,
--    µS/cm², mS/cm²) sementara sql/lims_service/02_master_data.sql di
--    repo masih ASCII (µg/m3, ...). Lookup berbasis nama di bagian 6
--    karena itu tidak menemukan satuannya dan 9 tautan tidak terbentuk.
--    Bagian ini memakai ejaan yang dipakai server.
-- ====================================================================

BEGIN;

INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '100' AND me.name = 'SNI 7119.2:2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'µg/m³' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '150' AND me.name = 'SNI 7119.7:2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'µg/m³' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '10000' AND me.name = 'IKL LAB : IKL-05.008 (Gas Chromatography)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'µg/m³' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '230' AND me.name = 'SNI 7119.3: 2017' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'µg/m³' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '800 (Primary Reformer)
150 (PKB)' AND me.name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/m³' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '1000 (Primary Reformer)
650 (PKB)' AND me.name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/m³' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '1000' AND me.name = 'IKL LAB : IKL-05.012 (Gas Analyzer)' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/m³' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '35' AND me.name = 'SNI 19-7117.7-2005' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/m³' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);
INSERT INTO lims_standard_unit (standard_id, unit_id)
SELECT st.id, un.id FROM
  (SELECT MIN(s.id) AS id FROM lims_standards s JOIN lims_methods me ON me.id = s.method_id
    WHERE s.measure = '0.5' AND me.name = 'SNI 19-7117.6-2005' AND s.deleted_at IS NULL) st,
  (SELECT MIN(id) AS id FROM lims_units WHERE name = 'mg/m³' AND deleted_at IS NULL) un
 WHERE st.id IS NOT NULL AND un.id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM lims_standard_unit x WHERE x.standard_id = st.id AND x.unit_id = un.id);

COMMIT;
