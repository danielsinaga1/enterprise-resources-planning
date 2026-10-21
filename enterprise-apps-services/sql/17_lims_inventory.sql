-- ============================================================================
-- LIMS Inventory Module — lab equipment / instrument inventory
-- ----------------------------------------------------------------------------
-- One row = one type of equipment, with unit count, reorder threshold, asset
-- value, condition, and calibration / service / borrow tracking.
--
-- Apply with:
--     mysql --default-character-set=utf8mb4 db_nirms < 17_lims_inventory.sql
-- ============================================================================

SET NAMES utf8mb4;

-- 1. Tabel inventaris alat laboratorium
CREATE TABLE IF NOT EXISTS `lims_inventory_items` (
  `id`                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `item_code`              VARCHAR(50)     NOT NULL COMMENT 'Kode alat unik, misal: INV/2026/0001',
  `name`                   VARCHAR(255)    NOT NULL COMMENT 'Nama alat, misal: Spektrofotometer UV-Vis',
  `category`               VARCHAR(100)    NULL     COMMENT 'Kategori alat, misal: Instrumen, Gelas, Furnitur',
  `specification`          TEXT            NULL     COMMENT 'Spesifikasi teknis alat',
  `location`               VARCHAR(255)    NULL     COMMENT 'Lokasi penyimpanan / lab',
  `condition`              VARCHAR(50)     NULL     DEFAULT 'Baik' COMMENT 'Baik / Rusak Ringan / Rusak Berat',
  `quantity`               INT             NOT NULL DEFAULT 0 COMMENT 'Jumlah unit (stok)',
  `min_stock`              INT             NOT NULL DEFAULT 0 COMMENT 'Stok minimum untuk reorder',
  `unit_price`            DECIMAL(18,2)   NULL     DEFAULT 0 COMMENT 'Nilai aset per unit (Rupiah)',
  `status`                 VARCHAR(30)     NOT NULL DEFAULT 'Available' COMMENT 'Available / Borrowed / Service',
  `last_calibration_date`  DATE            NULL     COMMENT 'Tanggal kalibrasi terakhir',
  `next_calibration_date`  DATE            NULL     COMMENT 'Tanggal kalibrasi berikutnya (jatuh tempo)',
  `last_service_date`      DATE            NULL     COMMENT 'Tanggal servis terakhir',
  `next_service_date`      DATE            NULL     COMMENT 'Tanggal servis berikutnya',
  `borrowed_by`            VARCHAR(255)    NULL     COMMENT 'Nama peminjam (jika status Borrowed)',
  `borrowed_at`            DATE            NULL     COMMENT 'Tanggal peminjaman',
  `notes`                  TEXT            NULL     COMMENT 'Catatan tambahan',
  `is_active`              TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '1=Active, 0=Inactive',
  `created_at`             TIMESTAMP       NULL,
  `updated_at`             TIMESTAMP       NULL,
  `deleted_at`             TIMESTAMP       NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_inventory_item_code` (`item_code`),
  INDEX `idx_inventory_name` (`name`),
  INDEX `idx_inventory_category` (`category`),
  INDEX `idx_inventory_status` (`status`),
  INDEX `idx_inventory_location` (`location`),
  INDEX `idx_inventory_next_calibration` (`next_calibration_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Contoh data awal (opsional — hapus blok ini jika tidak diperlukan)
INSERT INTO `lims_inventory_items`
  (`item_code`, `name`, `category`, `specification`, `location`, `condition`, `quantity`, `min_stock`, `unit_price`, `status`, `last_calibration_date`, `next_calibration_date`, `last_service_date`, `next_service_date`, `created_at`, `updated_at`)
VALUES
  ('INV/2026/0001', 'Spektrofotometer UV-Vis', 'Instrumen', 'Range 190-1100 nm, double beam', 'Lab Kimia A', 'Baik', 2, 1, 85000000.00, 'Available', '2026-01-15', '2026-07-15', '2025-12-01', '2026-06-01', NOW(), NOW()),
  ('INV/2026/0002', 'Neraca Analitik', 'Instrumen', 'Kapasitas 220g, akurasi 0.1mg', 'Lab Kimia A', 'Baik', 3, 2, 25000000.00, 'Available', '2026-02-10', '2026-06-20', NULL, NULL, NOW(), NOW()),
  ('INV/2026/0003', 'pH Meter', 'Instrumen', 'Digital, range 0-14 pH', 'Lab Lingkungan', 'Baik', 1, 2, 8000000.00, 'Borrowed', '2026-03-01', '2026-09-01', NULL, NULL, NOW(), NOW()),
  ('INV/2026/0004', 'Oven Laboratorium', 'Instrumen', 'Suhu maks 250°C, kapasitas 50L', 'Lab Mikrobiologi', 'Rusak Ringan', 1, 1, 18000000.00, 'Service', NULL, NULL, '2025-11-20', '2026-05-20', NOW(), NOW()),
  ('INV/2026/0005', 'Gelas Beaker 1000ml', 'Gelas', 'Borosilikat, tahan panas', 'Gudang Lab', 'Baik', 12, 20, 75000.00, 'Available', NULL, NULL, NULL, NULL, NOW(), NOW());

UPDATE `lims_inventory_items` SET `borrowed_by` = 'Budi Santoso', `borrowed_at` = '2026-05-28' WHERE `item_code` = 'INV/2026/0003';
