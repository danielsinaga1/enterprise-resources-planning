-- ============================================================
-- IRMS Database Seed Data
-- Generated from db_irms legacy database
-- MySQL 8.0 Compatible
-- ============================================================
SET FOREIGN_KEY_CHECKS = 0;
-- Miscellaneous Data: OAuth, Sessions, Timesheet, Migrations, etc.

-- Dumping data for table db_irms.area_user: ~0 rows (approximately)
DELETE FROM `area_user`;


-- Dumping data for table db_irms.assets: ~0 rows (approximately)
DELETE FROM `assets`;


-- Dumping data for table db_irms.assets_histories: ~0 rows (approximately)
DELETE FROM `assets_histories`;


-- Dumping data for table db_irms.oauth_access_tokens: ~0 rows (approximately)
DELETE FROM `oauth_access_tokens`;


-- Dumping data for table db_irms.oauth_auth_codes: ~0 rows (approximately)
DELETE FROM `oauth_auth_codes`;


-- Dumping data for table db_irms.oauth_clients: ~0 rows (approximately)
DELETE FROM `oauth_clients`;


-- Dumping data for table db_irms.oauth_personal_access_clients: ~0 rows (approximately)
DELETE FROM `oauth_personal_access_clients`;


-- Dumping data for table db_irms.oauth_refresh_tokens: ~0 rows (approximately)
DELETE FROM `oauth_refresh_tokens`;


-- Dumping data for table db_irms.password_resets: ~0 rows (approximately)
DELETE FROM `password_resets`;


-- Dumping data for table db_irms.personal_access_tokens: ~0 rows (approximately)
DELETE FROM `personal_access_tokens`;


-- Dumping data for table db_irms.qa_topics: ~0 rows (approximately)
DELETE FROM `qa_topics`;


-- Dumping data for table db_irms.sessions: ~0 rows (approximately)
DELETE FROM `sessions`;


-- Dumping data for table db_irms.time_entries: ~0 rows (approximately)
DELETE FROM `time_entries`;


-- Dumping data for table db_irms.time_projects: ~0 rows (approximately)
DELETE FROM `time_projects`;


-- Dumping data for table db_irms.time_work_types: ~0 rows (approximately)
DELETE FROM `time_work_types`;


-- Dumping data for table db_irms.user_alerts: ~0 rows (approximately)
DELETE FROM `user_alerts`;


-- Dumping data for table db_irms.user_user_alert: ~0 rows (approximately)
DELETE FROM `user_user_alert`;

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=IFNULL(@OLD_CHARACTER_SET_CLIENT, 'utf8mb4') */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;

-- Dumping data for table db_irms.migrations: ~73 rows (approximately)
DELETE FROM `migrations`;
INSERT INTO `migrations` (`id`, `migration`, `batch`) VALUES
	(1, '2014_10_12_100000_create_password_resets_table', 1),
	(2, '2019_12_14_000001_create_personal_access_tokens_table', 1),
	(3, '2020_01_31_000001_create_media_table', 1),
	(4, '2020_01_31_000002_create_audit_logs_table', 1),
	(5, '2020_01_31_000003_create_assets_histories_table', 1),
	(6, '2020_01_31_000004_create_assets_table', 1),
	(7, '2020_01_31_000005_create_asset_statuses_table', 1),
	(8, '2020_01_31_000006_create_asset_locations_table', 1),
	(9, '2020_01_31_000007_create_asset_categories_table', 1),
	(10, '2020_01_31_000008_create_time_entries_table', 1),
	(11, '2020_01_31_000009_create_time_projects_table', 1),
	(12, '2020_01_31_000010_create_time_work_types_table', 1),
	(13, '2020_01_31_000012_create_job_titles_table', 1),
	(14, '2020_01_31_000013_create_origin_departments_table', 1),
	(15, '2020_01_31_000014_create_designation_departments_table', 1),
	(16, '2020_01_31_000015_create_classification_details_table', 1),
	(17, '2020_01_31_000016_create_roles_table', 1),
	(18, '2020_01_31_000017_create_classification_incidents_table', 1),
	(19, '2020_01_31_000018_create_category_incidents_table', 1),
	(20, '2020_01_31_000019_create_root_causes_table', 1),
	(21, '2020_01_31_000020_create_results_table', 1),
	(22, '2020_01_31_000021_create_user_alerts_table', 1),
	(23, '2020_01_31_000022_create_permissions_table', 1),
	(24, '2020_01_31_000023_create_users_table', 1),
	(25, '2020_01_31_000024_create_user_user_alert_pivot_table', 1),
	(26, '2020_01_31_000025_create_role_user_pivot_table', 1),
	(27, '2020_01_31_000026_create_permission_role_pivot_table', 1),
	(28, '2020_01_31_000027_add_relationship_fields_to_assets_histories_table', 1),
	(29, '2020_01_31_000028_add_relationship_fields_to_assets_table', 1),
	(30, '2020_01_31_000030_add_relationship_fields_to_time_entries_table', 1),
	(31, '2020_01_31_000031_add_relationship_fields_to_classification_details_table', 1),
	(32, '2020_01_31_000033_create_qa_topics_table', 1),
	(33, '2020_02_17_130622_create_area_incidents_table', 1),
	(34, '2020_03_11_081410_add_code_to_origin_departments_table', 1),
	(35, '2020_03_11_081947_add_code_to_designation_departments_table', 1),
	(36, '2021_02_17_115517_create_chemical_releases_table', 1),
	(37, '2021_02_17_115547_create_incident_reports_table', 1),
	(38, '2021_02_17_115605_create_distribution_incidents_table', 1),
	(39, '2021_02_17_124311_create_region_incidents_table', 1),
	(40, '2021_02_17_141056_create_area_user_table', 1),
	(41, '2021_02_22_135631_create_precortives_table', 1),
	(42, '2021_06_09_082633_create_investigation_details_table', 1),
	(43, '2021_06_09_084502_add_relationship_fields_to_incident_reports_table', 1),
	(44, '2021_06_09_084929_add_relationship_fields_to_distribution_incidents_table', 1),
	(45, '2022_05_06_103847_add_correction_to_investigation_details_table', 1),
	(46, '2016_06_01_000001_create_oauth_auth_codes_table', 2),
	(47, '2016_06_01_000002_create_oauth_access_tokens_table', 2),
	(48, '2016_06_01_000003_create_oauth_refresh_tokens_table', 2),
	(49, '2016_06_01_000004_create_oauth_clients_table', 2),
	(50, '2016_06_01_000005_create_oauth_personal_access_clients_table', 2),
	(51, '2023_10_20_105923_create_media_table', 3),
	(60, '2023_12_14_141448_create_entitys_table', 4),
	(61, '2023_12_14_141512_create_nonplant_locations_table', 4),
	(62, '2023_12_14_141536_create_region_ncrs_table', 4),
	(63, '2023_12_14_210644_create_locations_table', 5),
	(64, '2023_12_14_212603_create_nonconformity_reports_table', 6),
	(76, '2023_12_15_103135_create_nonconformity_reports_table', 7),
	(77, '2023_12_15_105734_add_relationship_fields_to_nonconformity_reports_table', 7),
	(83, '2023_12_22_143647_add_is_jso_to_nonconformity_reports_table', 8),
	(84, '2024_01_11_101424_create_comment_nonconformitys', 8),
	(85, '2024_01_11_141736_add_relationship_fields_to_comment_nonconformitys_table', 8),
	(88, '2024_02_09_163723_create_root_cause_so_table', 9),
	(89, '2024_02_15_160622_add_relationship_to_nonconformity_reports_table', 10),
	(90, '2024_02_23_102000_create_divisions_table', 11),
	(91, '2025_07_09_225057_create_jso_locations_table', 12),
	(92, '2025_07_09_223112_create_jso_details_table', 13),
	(93, '2025_07_10_102015_create_jso_users_table', 14),
	(94, '2025_07_09_215016_create_jso_reports_table', 15),
	(95, '2025_07_09_230827_add_fields_to_nonconfirmity_reports_table', 16),
	(96, '2025_07_10_205601_fix_nonconformity_jso_foreign_keys', 17),
	(102, '2025_07_10_213311_fix_nonconformity_jso_foreign_keys', 18),
	(160, '2025_07_17_175506_create_safety_observations_table', 19),
	(161, '2025_07_17_201617_create_comment_safety_observations_table', 19),
	(162, '2025_07_24_224047_create_jso_reports_table', 19),
	(163, '2025_07_24_224212_add_relationship_fields_to_table', 19),
	(164, '2025_09_28_000001_fix_nonplant_fk_on_jso_reports', 20),
	(165, '2025_10_02_000001_create_divisions_table', 21),
	(166, '2025_10_02_000002_add_division_id_to_users_table', 21),
	(167, '2025_10_02_000003_add_division_permissions', 22),
	(168, '2025_10_02_000004_add_division_id_to_jso_reports_table', 23),
	(169, '2025_10_03_000001_add_corrective_preventive_to_jso_reports_table', 24),
	(170, '2025_10_03_000001_add_request_to_jso_reports_table', 25),
	(171, '2025_10_07_161603_create_sessions_table', 26),
	(172, '2025_12_02_000001_add_wr_number_to_jso_reports_table', 27),
	(173, '2025_10_10_001010_add_action_by_id_to_jso_reports_table', 28),
	(174, '2025_12_03_000001_add_wo_number_to_safety_observations_table', 29),
	(175, '2025_12_05_000000_rename_preventive_to_entity_name_in_safety_observations_table', 30),
	(176, '2026_02_11_000000_add_delegation_note_to_safety_observations_table', 31),
	(177, '2026_02_26_000001_move_delegation_note_to_jso_reports_table', 32);



SET FOREIGN_KEY_CHECKS = 1;
