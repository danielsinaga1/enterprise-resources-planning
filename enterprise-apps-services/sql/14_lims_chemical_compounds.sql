-- ============================================================================
-- Migration: LIMS Chemical Compounds Master Data
-- Date: 2026-03-16
-- Description:
--   Tabel master senyawa kimia untuk LIMS.
--   Kolom `formula` menggunakan VARCHAR dengan character set utf8mb4
--   agar karakter Unicode (subscript ₂₃₄, superscript ²⁺, simbol ⁻ dll)
--   tersimpan dengan aman.
--
--   Catatan SQL Server:
--     Jika migrasi ke SQL Server, ganti VARCHAR → NVARCHAR agar Unicode
--     tetap terjaga. MySQL utf8mb4 ≡ SQL Server NVARCHAR.
--
--   PENTING: Saat import via mysql CLI, gunakan:
--     mysql --default-character-set=utf8mb4 db_nirms < 14_lims_chemical_compounds.sql
-- ============================================================================

SET NAMES utf8mb4;

-- 1. Tabel master senyawa kimia
CREATE TABLE IF NOT EXISTS `lims_chemical_compounds` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(255)    NOT NULL COMMENT 'Nama senyawa, misal: Methane, Ammonia',
  `formula`       VARCHAR(500)    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Rumus kimia Unicode, misal: CH₄, NH₃, CO₂, H₂SO₄',
  `cas_number`    VARCHAR(50)     NULL     COMMENT 'CAS Registry Number, misal: 74-82-8',
  `category`      VARCHAR(100)    NULL     COMMENT 'Kategori: Gas, Liquid, Solid, Heavy Metal, dll',
  `description`   TEXT            NULL     COMMENT 'Deskripsi/keterangan tambahan',
  `is_active`     TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '1=Active, 0=Inactive',
  `created_at`    TIMESTAMP       NULL,
  `updated_at`    TIMESTAMP       NULL,
  `deleted_at`    TIMESTAMP       NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_compound_name` (`name`),
  INDEX `idx_compound_formula` (`formula`(100)),
  INDEX `idx_compound_category` (`category`),
  INDEX `idx_compound_cas` (`cas_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Tabel relasi senyawa kimia ↔ parameter (many-to-many)
--    Satu senyawa bisa diuji di banyak parameter, satu parameter bisa punya banyak senyawa
CREATE TABLE IF NOT EXISTS `lims_compound_parameter` (
  `compound_id`   BIGINT UNSIGNED NOT NULL,
  `parameter_id`  BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (`compound_id`, `parameter_id`),
  INDEX `idx_cp_parameter` (`parameter_id`),
  CONSTRAINT `fk_cp_compound`  FOREIGN KEY (`compound_id`)  REFERENCES `lims_chemical_compounds` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cp_parameter` FOREIGN KEY (`parameter_id`) REFERENCES `lims_parameters` (`id`)          ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Tabel relasi senyawa kimia ↔ test type (many-to-many)
--    Satu senyawa bisa diuji dengan banyak jenis pengujian
CREATE TABLE IF NOT EXISTS `lims_compound_test_type` (
  `compound_id`   BIGINT UNSIGNED NOT NULL,
  `test_type_id`  BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (`compound_id`, `test_type_id`),
  INDEX `idx_ctt_test_type` (`test_type_id`),
  CONSTRAINT `fk_ctt_compound`  FOREIGN KEY (`compound_id`)  REFERENCES `lims_chemical_compounds` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ctt_test_type` FOREIGN KEY (`test_type_id`) REFERENCES `lims_test_types` (`id`)          ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Seed data: senyawa kimia umum untuk industri petrokimia / laboratorium
-- Rumus menggunakan karakter Unicode subscript: ₀₁₂₃₄₅₆₇₈₉
-- ============================================================================
INSERT INTO `lims_chemical_compounds` (`id`, `name`, `formula`, `cas_number`, `category`, `description`, `is_active`, `created_at`, `updated_at`) VALUES
  (1,  'Methane',               'CH₄',          '74-82-8',    'Gas',         'Komponen utama gas alam',                                      1, NOW(), NOW()),
  (2,  'Ammonia',               'NH₃',          '7664-41-7',  'Gas',         'Anhydrous ammonia, digunakan dalam produksi pupuk',             1, NOW(), NOW()),
  (3,  'Carbon Dioxide',        'CO₂',          '124-38-9',   'Gas',         'Gas rumah kaca, produk pembakaran',                             1, NOW(), NOW()),
  (4,  'Sulfuric Acid',         'H₂SO₄',        '7664-93-9',  'Liquid',      'Asam kuat, digunakan di industri kimia',                       1, NOW(), NOW()),
  (5,  'Nitrogen Dioxide',      'NO₂',          '10102-44-0', 'Gas',         'Gas polutan, parameter udara ambient',                         1, NOW(), NOW()),
  (6,  'Sulfur Dioxide',        'SO₂',          '7446-09-5',  'Gas',         'Gas polutan dari pembakaran bahan bakar belerang',              1, NOW(), NOW()),
  (7,  'Carbon Monoxide',       'CO',           '630-08-0',   'Gas',         'Gas beracun tidak berwarna',                                   1, NOW(), NOW()),
  (8,  'Hydrogen Sulfide',      'H₂S',          '7783-06-4',  'Gas',         'Gas beracun berbau telur busuk',                               1, NOW(), NOW()),
  (9,  'Hydrochloric Acid',     'HCl',          '7647-01-0',  'Liquid',      'Asam klorida',                                                 1, NOW(), NOW()),
  (10, 'Sodium Hydroxide',      'NaOH',         '1310-73-2',  'Solid',       'Soda api / caustic soda',                                      1, NOW(), NOW()),
  (11, 'Phosphoric Acid',       'H₃PO₄',        '7664-38-2',  'Liquid',      'Asam fosfat',                                                  1, NOW(), NOW()),
  (12, 'Nitric Acid',           'HNO₃',         '7697-37-2',  'Liquid',      'Asam nitrat',                                                  1, NOW(), NOW()),
  (13, 'Calcium Carbonate',     'CaCO₃',        '471-34-1',   'Solid',       'Kapur / batu gamping',                                         1, NOW(), NOW()),
  (14, 'Iron(III) Oxide',       'Fe₂O₃',        '1309-37-1',  'Solid',       'Karat besi / hematit',                                         1, NOW(), NOW()),
  (15, 'Ethanol',               'C₂H₅OH',       '64-17-5',    'Liquid',      'Alkohol / etil alkohol',                                       1, NOW(), NOW()),
  (16, 'Urea',                  'CO(NH₂)₂',     '57-13-6',    'Solid',       'Pupuk nitrogen, produk dari ammonia + CO₂',                    1, NOW(), NOW()),
  (17, 'Ozone',                 'O₃',           '10028-15-6', 'Gas',         'Oksidan kuat, parameter udara ambient',                        1, NOW(), NOW()),
  (18, 'Chlorine',              'Cl₂',          '7782-50-5',  'Gas',         'Disinfektan air',                                              1, NOW(), NOW()),
  (19, 'Silica',                'SiO₂',         '7631-86-9',  'Solid',       'Silikon dioksida, parameter air boiler',                       1, NOW(), NOW()),
  (20, 'Phosphate',             'PO₄³⁻',        '14265-44-2', 'Ion',         'Ion fosfat, parameter water treatment',                        1, NOW(), NOW());
