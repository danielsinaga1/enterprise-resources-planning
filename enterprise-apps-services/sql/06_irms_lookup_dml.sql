-- ============================================================
-- IRMS Database Seed Data
-- Generated from db_irms legacy database
-- MySQL 8.0 Compatible
-- ============================================================
SET FOREIGN_KEY_CHECKS = 0;
-- Lookup/Reference Data: Incident & Safety Observation Lookups

-- Dumping data for table db_irms.area_incidents: ~2 rows (approximately)
DELETE FROM `area_incidents`;
INSERT INTO `area_incidents` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`) VALUES
	(1, 'Plant', '2020-02-16 00:01:03', '2020-02-16 00:01:22', NULL),
	(2, 'Non-Plant', '2020-02-16 00:01:15', '2020-02-16 00:01:15', NULL);


-- Dumping data for table db_irms.region_incidents: ~2 rows (approximately)
DELETE FROM `region_incidents`;
INSERT INTO `region_incidents` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`) VALUES
	(1, 'Process', '2020-06-23 03:56:17', '2020-06-23 03:56:17', NULL),
	(2, 'Utility', '2020-06-23 03:57:36', '2020-06-23 03:57:36', NULL);


-- Dumping data for table db_irms.category_incidents: ~6 rows (approximately)
DELETE FROM `category_incidents`;
INSERT INTO `category_incidents` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`) VALUES
	(1, 'Manusia', '2020-01-25 16:49:53', '2020-01-25 16:49:53', NULL),
	(2, 'Asset/Produksi', '2020-01-25 16:50:00', '2020-01-25 16:50:00', NULL),
	(3, 'Lingkungan', '2020-01-25 16:50:09', '2020-01-25 16:50:09', NULL),
	(4, 'Reputasi', '2020-01-25 16:50:18', '2020-01-25 16:50:18', NULL),
	(5, 'Security Fisik', '2020-01-25 16:50:24', '2020-01-25 16:50:24', NULL),
	(6, 'Security Siber', '2020-01-25 16:50:30', '2020-01-25 16:50:30', NULL);


-- Dumping data for table db_irms.classification_incidents: ~5 rows (approximately)
DELETE FROM `classification_incidents`;
INSERT INTO `classification_incidents` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`) VALUES
	(1, 'Insignificant', '2020-01-25 16:50:52', '2020-01-25 16:50:52', NULL),
	(2, 'Minor', '2020-01-25 16:50:59', '2020-01-25 16:50:59', NULL),
	(3, 'Moderate', '2020-01-25 16:51:05', '2020-01-25 16:51:05', NULL),
	(4, 'Major', '2020-01-25 16:51:11', '2020-01-25 16:51:11', NULL),
	(5, 'Catastrophic', '2020-01-25 16:51:20', '2020-01-25 16:51:20', NULL);


-- Dumping data for table db_irms.classification_details: ~30 rows (approximately)
DELETE FROM `classification_details`;
INSERT INTO `classification_details` (`id`, `description`, `created_at`, `updated_at`, `deleted_at`, `category_id`, `classification_id`) VALUES
	(1, '<p>First aid case (tanpa tindakan medis)</p>', '2020-01-29 03:15:11', '2020-01-29 03:16:21', NULL, 1, 1),
	(2, '<p>First aid case (ada tindakan medis)</p>', '2020-01-29 03:15:24', '2020-01-29 03:15:24', NULL, 1, 2),
	(3, '<p>Medical treatment case</p>', '2020-01-29 03:15:36', '2020-01-29 03:15:36', NULL, 1, 3),
	(4, '<p>Lost workday case atau Cacat Permanen</p>', '2020-01-29 03:15:52', '2020-01-29 03:15:52', NULL, 1, 4),
	(5, '<p>Kematian (1 atau lebih orang)</p>', '2020-01-29 03:16:06', '2020-01-29 03:16:06', NULL, 1, 5),
	(6, '<p>Pabrik beroperasi normal dengan kondisi gangguan tidak berarti</p>', '2020-01-29 03:16:34', '2020-01-29 03:16:34', NULL, 2, 1),
	(7, '<p>Pabrik beroperasi normal dengan gangguan yang menyebabkan perbaikan di tempat</p>', '2020-01-29 03:16:44', '2020-01-29 03:16:44', NULL, 2, 2),
	(8, '<ul><li>&nbsp;Pabrik beroperasi tidak normal dan perlu perbaikan dengan&nbsp; menurunkan rate produksi&nbsp;</li><li>&nbsp;Kerugian &lt; USD 50.000</li></ul>', '2020-01-29 03:17:14', '2020-01-29 03:17:14', NULL, 2, 3),
	(9, '<ul><li>Pabrik shutdown dengan kerusakan yang harus di perbaiki sampai dengan 5 hari</li><li>&nbsp;Kerugian USD 50.000 â€“ 100.000</li></ul>', '2020-01-29 03:17:47', '2020-01-29 03:17:47', NULL, 2, 4),
	(10, '<ul><li>Pabrik shutdown dengan memerlukan perbaikan lebih dari 5 hari</li><li>Kerugian &gt; USD 100.000</li></ul>', '2020-01-29 03:18:02', '2020-01-29 03:18:02', NULL, 2, 5),
	(11, '<p>Pencemaran di lingkungan kerja</p>', '2020-01-29 03:18:19', '2020-01-29 03:18:19', NULL, 3, 1),
	(12, '<p>Pencemaran di lingkungan perusahaan</p>', '2020-01-29 03:18:35', '2020-01-29 03:18:35', NULL, 3, 2),
	(13, '<p>Pencemaran ke masyarakat</p>', '2020-01-29 03:18:55', '2020-01-29 03:18:55', NULL, 3, 3),
	(14, '<p>Pencemaran ke masyarakat dan menimbulkan protes masyarakat</p>', '2020-01-29 03:19:08', '2020-01-29 03:19:08', NULL, 3, 4),
	(15, '<p>Pencemaran lingkungan yang menimbulkan tuntutan hukum</p>', '2020-01-29 03:19:24', '2020-01-29 03:19:24', NULL, 3, 5),
	(16, '<p>Reaksi di internal kawasan industri KIE</p>', '2020-01-29 03:19:38', '2020-01-29 03:19:38', NULL, 4, 1),
	(17, '<p>Liputan oleh media lokal (Bontang Post)</p>', '2020-01-29 03:19:53', '2020-01-29 03:19:53', NULL, 4, 2),
	(18, '<ul><li>Liputan oleh media regional (Kaltim Post)</li><li>Aduan kerusakan oleh masyarakat</li></ul>', '2020-01-29 03:20:38', '2020-01-29 03:20:38', NULL, 4, 3),
	(19, '<ul><li>Liputan oleh media Nasional</li><li>Tuntutan hukum oleh masyarakat/LSM</li><li>PROPER Nasional MERAH</li></ul>', '2020-01-29 03:20:51', '2020-01-29 03:20:51', NULL, 4, 4),
	(20, '<ul><li>Liputan oleh media Internasional&nbsp;</li><li>Investigasi dari Pemerintah/Kepolisian</li></ul>', '2020-01-29 03:21:14', '2020-01-29 03:21:14', NULL, 4, 5),
	(21, '<p>Masuk tanpa izin, tidak ada kehilangan</p>', '2020-01-29 03:21:30', '2020-01-29 03:21:30', NULL, 5, 1),
	(22, '<p>Masuk tanpa izin, tidak ada kehilangan, dan mengganggu kenyamanan</p>', '2020-01-29 03:21:44', '2020-01-29 03:21:44', NULL, 5, 2),
	(23, '<p>Masuk tanpa izin, ada kehilangan 1 barang</p>', '2020-01-29 03:22:03', '2020-01-29 03:22:03', NULL, 5, 3),
	(24, '<p>Masuk tanpa izin, ada kehilangan lebih dari 1 barang</p>', '2020-01-29 03:22:29', '2020-01-29 03:22:29', NULL, 5, 4),
	(25, '<p>ancaman bom, pembajakan pabrik, sabotase</p>', '2020-01-29 03:22:44', '2020-01-29 03:22:44', NULL, 5, 5),
	(26, '<p>Penggunaan hardware yang tidak terdaftar tanpa kehilangan informasi perusahaan</p>', '2020-01-29 03:22:58', '2020-01-29 03:22:58', NULL, 6, 1),
	(27, '<p>System down kurang dari 2 jam</p>', '2020-01-29 03:23:11', '2020-01-29 03:23:11', NULL, 6, 2),
	(28, '<p>System down 2 - 8 jam, Kehilangan data perusahaan secara permanen</p>', '2020-01-29 03:23:31', '2020-01-29 03:23:31', NULL, 6, 3),
	(29, '<p>System diretas, tanpa diketahui, dan sewaktu-waktu dapat dikendalikan dari luar</p>', '2020-01-29 03:23:49', '2020-01-29 03:23:49', NULL, 6, 4),
	(30, '<p>Kelumpuhan infrastruktur IT</p>', '2020-01-29 03:24:05', '2020-01-29 03:24:05', NULL, 6, 5);


-- Dumping data for table db_irms.chemical_releases: ~2 rows (approximately)
DELETE FROM `chemical_releases`;
INSERT INTO `chemical_releases` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`) VALUES
	(1, 'Yes', '2020-05-04 18:01:43', '2020-05-04 18:01:43', NULL),
	(2, 'No', '2020-05-04 18:01:50', '2020-05-04 18:01:50', NULL);


-- Dumping data for table db_irms.root_causes: ~4 rows (approximately)
DELETE FROM `root_causes`;
INSERT INTO `root_causes` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`) VALUES
	(1, 'Tindakan tidak aman', '2020-01-29 03:25:13', '2020-01-29 03:25:13', NULL),
	(2, 'Procedure', '2020-01-29 03:25:19', '2020-01-29 03:25:19', NULL),
	(3, 'Lingkungan', '2020-01-29 03:25:28', '2020-01-29 03:25:28', NULL),
	(4, 'Kegagalan Alat', '2020-01-29 03:25:33', '2020-01-29 03:25:33', NULL);


-- Dumping data for table db_irms.results: ~9 rows (approximately)
DELETE FROM `results`;
INSERT INTO `results` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`) VALUES
	(1, 'Partially Approved', '2020-01-29 09:40:41', '2022-10-03 19:42:54', NULL),
	(2, 'Fully Approved', '2020-01-29 09:40:52', '2022-10-03 19:43:23', NULL),
	(3, 'Rejected', '2020-01-29 09:41:04', '2020-01-29 09:41:04', NULL),
	(4, 'New', '2020-01-29 09:41:14', '2020-02-04 01:09:39', NULL),
	(5, 'In Progress', '2020-02-04 01:09:54', '2020-02-04 01:09:54', NULL),
	(6, 'Done', '2020-02-29 17:07:19', '2020-02-29 17:07:19', NULL),
	(7, 'Canceled', '2023-12-18 05:58:55', '2023-12-18 05:58:55', NULL),
	(8, 'Revised by Administrator', '2023-12-18 05:59:05', '2023-12-18 05:59:05', NULL),
	(9, 'Revised by Manager', '2023-12-18 06:01:50', '2023-12-18 06:01:50', NULL);


-- Dumping data for table db_irms.precortives: ~3 rows (approximately)
DELETE FROM `precortives`;
INSERT INTO `precortives` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`) VALUES
	(1, 'Corrective', '2021-06-01 15:58:02', '2021-06-01 15:58:02', NULL),
	(2, 'Preventive', '2021-06-01 15:58:07', '2021-06-01 15:58:07', NULL),
	(3, 'Correction', '2022-05-06 01:27:21', '2022-05-06 01:27:21', NULL);


-- Dumping data for table db_irms.distribution_incidents: ~0 rows (approximately)
DELETE FROM `distribution_incidents`;


-- Dumping data for table db_irms.root_cause_so: ~6 rows (approximately)
DELETE FROM `root_cause_so`;
INSERT INTO `root_cause_so` (`id`, `name`, `code`, `created_at`, `updated_at`) VALUES
	(1, 'Keselamatan & Kesehatan Kerja', 'K3', '2024-02-13 06:34:52', '2024-02-13 06:34:52'),
	(2, 'Keselamatan Proses', 'PS', '2024-02-13 06:35:01', '2024-02-13 06:35:01'),
	(3, 'Lingkungan Hidup', 'LH', '2024-02-13 06:35:10', '2024-02-13 06:35:10'),
	(4, 'Hygiene Sanitasi', 'HS', '2024-02-13 06:35:24', '2024-02-13 06:35:24'),
	(5, 'Housekeeping', 'HK', '2024-02-13 06:35:36', '2024-02-13 06:35:36'),
	(6, 'Quality', 'QA', '2024-02-13 06:35:43', '2024-02-13 06:35:43'),
	(7, 'Others', 'OT', '2024-02-13 06:35:49', '2024-02-13 06:35:49');


-- Dumping data for table db_irms.entitys: ~24 rows (approximately)
DELETE FROM `entitys`;
INSERT INTO `entitys` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`) VALUES
	(1, 'Block Valve', '2023-04-25 17:57:01', '2023-04-25 17:57:01', NULL),
	(2, 'Blower', '2023-04-25 17:57:19', '2023-04-25 17:57:19', NULL),
	(3, 'Column', '2023-04-25 17:57:24', '2023-09-18 19:01:23', '2023-09-18 19:01:23'),
	(4, 'Column', '2023-04-25 17:57:25', '2023-04-25 17:57:25', NULL),
	(5, 'Compressor', '2023-04-25 17:57:29', '2023-04-25 17:57:29', NULL),
	(6, 'Ejector', '2023-04-25 17:57:37', '2023-04-25 17:57:37', NULL),
	(7, 'Exchanger', '2023-04-25 17:57:43', '2023-04-25 17:57:43', NULL),
	(8, 'Filter', '2023-04-25 17:57:49', '2023-04-25 17:57:49', NULL),
	(9, 'Flange to flange', '2023-04-25 17:57:55', '2023-04-25 17:57:55', NULL),
	(10, 'Furnace', '2023-04-25 17:58:00', '2023-04-25 17:58:00', NULL),
	(11, 'Instrumentation', '2023-04-25 17:58:04', '2023-04-25 17:58:04', NULL),
	(12, 'Motor', '2023-04-25 17:58:11', '2023-04-25 17:58:11', NULL),
	(13, 'Package', '2023-04-25 17:58:17', '2023-04-25 17:58:17', NULL),
	(14, 'Piping', '2023-08-07 16:35:41', '2023-08-07 16:35:41', NULL),
	(15, 'PSV', '2023-08-07 16:35:45', '2023-08-07 16:35:45', NULL),
	(16, 'Pump', '2023-08-07 16:35:50', '2023-08-07 16:35:50', NULL),
	(17, 'Reactor', '2023-08-07 16:35:55', '2023-08-07 16:35:55', NULL),
	(18, 'Stack', '2023-08-07 16:36:01', '2023-08-07 16:36:01', NULL),
	(19, 'Steam Trap', '2023-08-07 16:36:07', '2023-08-07 16:36:07', NULL),
	(20, 'Tank', '2023-08-07 16:36:12', '2023-08-07 16:36:12', NULL),
	(21, 'Turbine', '2023-08-07 16:36:19', '2023-08-07 16:36:19', NULL),
	(22, 'Undefined', '2023-08-07 16:36:25', '2023-08-07 16:36:25', NULL),
	(23, 'Valve', '2023-08-07 16:36:31', '2023-08-07 16:36:31', NULL),
	(24, 'Vessel', '2023-08-07 16:36:38', '2023-08-07 16:36:38', NULL);


-- Dumping data for table db_irms.region_ncrs: ~2 rows (approximately)
DELETE FROM `region_ncrs`;
INSERT INTO `region_ncrs` (`id`, `name`, `deleted_at`, `created_at`, `updated_at`) VALUES
	(1, 'Ammonia', NULL, '2023-05-03 16:27:00', '2023-05-03 16:27:00'),
	(2, 'Utility', NULL, '2023-05-03 16:30:05', '2023-05-03 16:30:05'),
	(3, 'None', NULL, '2025-09-28 14:22:38', '2025-09-28 14:22:38');


-- Dumping data for table db_irms.locations: ~28 rows (approximately)
DELETE FROM `locations`;
INSERT INTO `locations` (`id`, `name`, `region_ncr_id`, `deleted_at`, `created_at`, `updated_at`) VALUES
	(12, 'NG Battery Limit section', 1, NULL, '2023-04-25 17:58:48', '2023-05-03 18:07:59'),
	(13, 'Desulfurization section', 1, NULL, '2023-04-25 17:58:57', '2023-05-03 18:09:09'),
	(14, 'Reforming section', 1, NULL, '2023-04-25 17:59:03', '2023-05-03 18:09:22'),
	(15, 'Shift Converter section', 1, NULL, '2023-04-25 17:59:24', '2023-05-03 18:09:27'),
	(16, 'Compressor Section', 1, NULL, '2023-04-25 17:59:30', '2023-05-03 18:09:38'),
	(17, 'CO2 Removal Section', 1, NULL, '2023-04-25 17:59:35', '2023-05-03 18:09:43'),
	(18, 'Methanation Section', 1, NULL, '2023-04-25 17:59:39', '2023-05-03 18:09:50'),
	(19, 'Ammonia Synloop Section', 1, NULL, '2023-04-25 17:59:49', '2023-05-03 18:09:56'),
	(20, 'Sea Water Intake Section', 2, NULL, '2023-04-25 17:59:54', '2023-05-03 18:26:29'),
	(21, 'Project', NULL, '2023-05-03 18:10:21', '2023-04-25 18:41:39', '2023-05-03 18:10:21'),
	(22, 'Desalination Section', 2, NULL, '2023-05-03 18:25:20', '2023-05-03 18:25:20'),
	(23, 'Deminarilazation Section', 2, NULL, '2023-05-03 18:25:31', '2023-05-03 18:25:31'),
	(24, 'Chlorination Section', 2, NULL, '2023-05-03 18:26:17', '2023-05-03 18:26:17'),
	(25, 'PKB Section', 2, NULL, '2023-05-03 18:26:45', '2023-05-03 18:26:45'),
	(26, 'STG Section', 2, NULL, '2023-05-03 18:26:59', '2023-05-03 18:26:59'),
	(27, 'Utility Battery Limit Section', 2, NULL, '2023-05-03 18:27:08', '2023-05-03 18:27:08'),
	(28, 'MCC Section', 2, NULL, '2023-05-03 18:27:15', '2023-05-03 18:27:15'),
	(29, 'Cooling Water Section', 2, NULL, '2023-05-03 18:27:26', '2023-05-03 18:27:26'),
	(30, 'Cooling Tower Section', 2, NULL, '2023-05-03 18:28:26', '2023-05-03 18:28:26'),
	(31, 'N2 Generation Section', 2, NULL, '2023-05-03 18:28:35', '2023-05-03 18:28:35'),
	(32, 'Steam System Section', 2, NULL, '2023-05-03 18:28:43', '2023-05-03 18:28:43'),
	(33, 'IA Section', 2, NULL, '2023-05-03 18:29:24', '2023-05-03 18:29:24'),
	(34, 'Lube Oil System Section', 2, NULL, '2023-05-03 18:29:36', '2023-05-03 18:29:36'),
	(35, 'Fire Water Section', 2, NULL, '2023-05-03 18:29:50', '2023-05-03 18:29:50'),
	(36, 'Chemical Injection Section', 2, NULL, '2023-05-03 18:29:58', '2023-05-03 18:29:58'),
	(37, 'Waste Water Section', 2, NULL, '2023-05-03 18:30:06', '2023-05-03 18:30:06'),
	(38, 'Ammonia Storage & BOG Section', 2, NULL, '2023-05-03 18:30:14', '2023-05-03 18:30:14'),
	(39, 'Jetty Section', 2, NULL, '2023-05-03 18:30:24', '2023-05-03 18:30:24'),
	(40, 'MCR Building, STG, PKB sampai SWI dan Kendaraan di Parkiran', NULL, '2025-07-15 09:52:31', '2025-07-15 09:49:19', '2025-07-15 09:52:31'),
	(41, 'MCR Building, STG, PKB sampai SWI dan Kendaraan di Parkiran', NULL, '2025-07-15 09:52:31', '2025-07-15 09:51:58', '2025-07-15 09:52:31');


-- Dumping data for table db_irms.jso_locations: ~11 rows (approximately)
DELETE FROM `jso_locations`;
INSERT INTO `jso_locations` (`id`, `name`, `created_at`, `updated_at`) VALUES
	(1, 'MCR Building, STG, PKB sampai SWI dan Kendaraan di Parkiran', '2025-07-15 09:59:07', '2025-07-15 09:59:07'),
	(2, 'Gedung, Parkiran Graha Parna dan Kendaraan di Parkiran', '2025-07-15 09:59:44', '2025-07-15 09:59:44'),
	(3, 'MCC, Cooling Water, Desal sampai Demin', '2025-07-15 09:59:57', '2025-07-15 09:59:57'),
	(4, 'Cooling Tower', '2025-07-15 10:00:20', '2025-07-15 10:00:20'),
	(5, 'Warehouse sampai Maintenance Building, forklift, dan Parkiran', '2025-07-15 10:00:32', '2025-07-15 10:00:32'),
	(6, 'Pos Security, Site Office sampai Shelter Chemical, dan Kendaraan di Parkiran', '2025-07-15 10:01:12', '2025-07-15 10:01:12'),
	(7, 'HARU sampai area Chiller', '2025-07-15 10:01:27', '2025-07-15 10:01:27'),
	(8, 'Ammonia Storage sampai jetty', '2025-07-15 10:01:54', '2025-07-15 10:01:54'),
	(9, 'P-0501, E-0401, sampai V-0401', '2025-07-15 10:02:09', '2025-07-15 10:02:09'),
	(10, 'R-0501, R-0502, R-0203 sampai E-0208', '2025-07-15 10:02:20', '2025-07-15 10:02:20'),
	(11, 'CO2 Removal & Primary Reformer', '2025-07-15 10:02:28', '2025-07-15 10:02:28'),
	(12, 'E-0212A/B, piperack 1 dari E-0312 sampai deaerator', '2025-07-15 10:02:38', '2025-07-15 10:02:38');


-- Dumping data for table db_irms.nonplant_locations: ~13 rows (approximately)
DELETE FROM `nonplant_locations`;
INSERT INTO `nonplant_locations` (`id`, `name`, `deleted_at`, `created_at`, `updated_at`) VALUES
	(1, 'Main Control Room', NULL, '2023-06-11 18:47:14', '2023-06-11 18:47:14'),
	(2, 'Warehouse Building', NULL, '2023-06-11 18:47:23', '2023-06-11 18:47:23'),
	(3, 'Maintenance Building', NULL, '2023-06-11 18:47:30', '2023-06-11 18:47:30'),
	(4, 'Site Office Building', NULL, '2023-06-11 18:47:36', '2023-06-11 18:47:36'),
	(5, 'Chemical Shelter', NULL, '2023-06-11 18:47:41', '2023-06-11 18:47:41'),
	(6, 'Maintenace Shelter', NULL, '2023-06-11 18:47:46', '2023-06-11 18:47:46'),
	(7, 'Gudang Limbah B3', NULL, '2023-06-11 18:47:51', '2023-06-11 18:47:51'),
	(8, 'Field Operator Shelter', NULL, '2023-06-11 18:47:56', '2023-06-11 18:47:56'),
	(9, 'Pos Security', NULL, '2023-06-11 18:48:01', '2023-06-11 18:48:01'),
	(10, 'Graha Parna', NULL, '2023-06-11 18:48:08', '2023-06-11 18:48:08'),
	(11, 'KPI Sport Centre', NULL, '2023-06-11 18:48:12', '2023-06-11 18:48:12'),
	(12, 'Wisma KPI', NULL, '2023-06-11 18:48:18', '2023-06-11 18:48:18'),
	(13, 'Kolam KPI', NULL, '2023-06-11 18:48:23', '2023-06-11 18:48:23');


-- Dumping data for table db_irms.asset_statuses: ~4 rows (approximately)
DELETE FROM `asset_statuses`;
INSERT INTO `asset_statuses` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`) VALUES
	(1, 'Available', '2020-01-30 16:48:12', '2020-01-30 16:48:12', NULL),
	(2, 'Not Available', '2020-01-30 16:48:12', '2020-01-30 16:48:12', NULL),
	(3, 'Broken', '2020-01-30 16:48:12', '2020-01-30 16:48:12', NULL),
	(4, 'Out for Repair', '2020-01-30 16:48:12', '2020-01-30 16:48:12', NULL);


-- Dumping data for table db_irms.asset_categories: ~0 rows (approximately)
DELETE FROM `asset_categories`;


-- Dumping data for table db_irms.asset_locations: ~0 rows (approximately)
DELETE FROM `asset_locations`;



SET FOREIGN_KEY_CHECKS = 1;
