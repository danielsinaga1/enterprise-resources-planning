-- ============================================================================
-- Migration: Fix Unicode Chemical Formulas in LIMS Master Data
-- Date: 2026-03-16
-- Description:
--   Data di tabel lims_test_types, lims_methods, dan lims_units mengandung
--   karakter Unicode yang terkorupsi (double-encoded) saat import awal.
--   Script ini memperbaiki semua data yang mengandung rumus kimia / simbol
--   khusus agar menggunakan Unicode yang benar.
--
--   PENTING: Import dengan:
--     mysql --default-character-set=utf8mb4 db_nirms < 15_fix_unicode_formulas.sql
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- 1. FIX lims_test_types.name — Rumus kimia (subscript/superscript)
-- ============================================================================
UPDATE lims_test_types SET name = 'N₂, Nitrogen'              WHERE id = 1;
UPDATE lims_test_types SET name = 'CO₂, Karbondioksida'       WHERE id = 2;
UPDATE lims_test_types SET name = 'CH₄, Metana'               WHERE id = 3;
UPDATE lims_test_types SET name = 'C₂H₆, Etana'               WHERE id = 4;
UPDATE lims_test_types SET name = 'C₃H₈, Propana'             WHERE id = 5;
UPDATE lims_test_types SET name = 'i-C₄H₁₀, iso Butana'      WHERE id = 6;
UPDATE lims_test_types SET name = 'n-C₄H₁₀, normal Butana'   WHERE id = 7;
UPDATE lims_test_types SET name = 'i-C₅H₁₂, iso Pentana'     WHERE id = 8;
UPDATE lims_test_types SET name = 'n-C₅H₁₂, normal Pentana'  WHERE id = 9;
UPDATE lims_test_types SET name = 'C₆H₁₄⁺, Heksana +'        WHERE id = 10;
UPDATE lims_test_types SET name = 'Purity Ammonia (NH₃)'      WHERE id = 15;
UPDATE lims_test_types SET name = 'Purity Ammonia 2 (NH₃)'    WHERE id = 16;
UPDATE lims_test_types SET name = 'Klorida, Cl⁻'              WHERE id = 20;
UPDATE lims_test_types SET name = 'Ion Ammonium (NH₄)'         WHERE id = 26;
UPDATE lims_test_types SET name = 'Fosfat (PO₄⁻³)'            WHERE id = 29;
UPDATE lims_test_types SET name = 'Total Fosfat (PO₄)'        WHERE id = 30;
UPDATE lims_test_types SET name = 'Sulfat, SO₄'               WHERE id = 31;
UPDATE lims_test_types SET name = 'Silika, SiO₂'              WHERE id = 34;
UPDATE lims_test_types SET name = 'Ion Besi (II), Fe²⁺'       WHERE id = 42;
UPDATE lims_test_types SET name = 'Ion Besi (III), Fe³⁺'      WHERE id = 43;
UPDATE lims_test_types SET name = 'Ammonia total (NH₃-N)'     WHERE id = 52;
UPDATE lims_test_types SET name = 'Fosfat (PO₄⁻³)'            WHERE id = 56;
UPDATE lims_test_types SET name = 'Nitrogen Dioksida, NO₂'    WHERE id = 57;
UPDATE lims_test_types SET name = 'Sulfur Dioksida, SO₂'      WHERE id = 58;
UPDATE lims_test_types SET name = 'Oksigen, O₂'               WHERE id = 61;
UPDATE lims_test_types SET name = 'Nitrogen Oksida, NOₓ'      WHERE id = 63;
UPDATE lims_test_types SET name = 'Hidrogen Sulfida, H₂S'     WHERE id = 65;
UPDATE lims_test_types SET name = 'Amoniak, NH₃'              WHERE id = 66;
UPDATE lims_test_types SET name = 'Sulfat, SO₄²⁻'             WHERE id = 68;

-- ============================================================================
-- 2. FIX lims_methods.name — Rumus kimia dalam nama metode
-- ============================================================================
UPDATE lims_methods SET name = 'AOAC Official Method ²¹st Edition, No. 959.02, 2019'  WHERE id = 2;
UPDATE lims_methods SET name = 'IKL LAB: IKL-08.009 (APHA 4500-Cl⁻  E & 4110 B)'     WHERE id = 7;
UPDATE lims_methods SET name = 'APHA 4500-H⁺ B, 23rd Edition, 2017'                   WHERE id = 9;
UPDATE lims_methods SET name = 'APHA 4500-NH³ F, 23rd Edition, 2017'                   WHERE id = 15;
UPDATE lims_methods SET name = 'APHA 4500-SO₄²⁻ E, 23rd Edition, 2017'                WHERE id = 20;
UPDATE lims_methods SET name = 'APHA 4500-Cl⁻ E, 23rd Edition, 2017'                  WHERE id = 21;

-- ============================================================================
-- 3. FIX lims_units.name — Simbol khusus (µ, °, ‰)
-- ============================================================================
UPDATE lims_units SET name = 'µS/cm2'   WHERE id = 6;
UPDATE lims_units SET name = '°C'       WHERE id = 7;
UPDATE lims_units SET name = 'µg/L'     WHERE id = 9;
UPDATE lims_units SET name = '‰'        WHERE id = 13;
UPDATE lims_units SET name = 'µg/m3'    WHERE id = 14;

-- ============================================================================
-- Verifikasi
-- ============================================================================
SELECT 'lims_test_types' AS tabel, id, name FROM lims_test_types WHERE id IN (1,2,3,4,5,6,7,8,9,10,42,43,68)
UNION ALL
SELECT 'lims_methods', id, name FROM lims_methods WHERE id IN (2,7,9,15,20,21)
UNION ALL
SELECT 'lims_units', id, name FROM lims_units WHERE id IN (6,7,9,13,14);
