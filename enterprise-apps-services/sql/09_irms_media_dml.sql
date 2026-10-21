-- ============================================================
-- IRMS Database Seed Data
-- Generated from db_irms legacy database
-- MySQL 8.0 Compatible
-- ============================================================
SET FOREIGN_KEY_CHECKS = 0;
-- Media Data (Spatie Media Library)

-- Dumping data for table db_irms.media: ~32 rows (approximately)
DELETE FROM `media`;
INSERT INTO `media` (`id`, `model_type`, `model_id`, `uuid`, `collection_name`, `name`, `file_name`, `mime_type`, `disk`, `conversions_disk`, `size`, `manipulations`, `custom_properties`, `generated_conversions`, `responsive_images`, `order_column`, `created_at`, `updated_at`) VALUES
	(1, 'App\\NonConformity', 1, 'ef8c08eb-c20e-4e49-8086-3998ed97e5e4', 'photos', '65a7db3f6185c_Screenshot 2024-01-10 105557', '65a7db3f6185c_Screenshot-2024-01-10-105557.png', 'image/png', 'public', 'public', 12175, '[]', '[]', '{"thumb":true}', '[]', 1, '2024-01-17 13:50:58', '2024-01-17 13:50:58'),
	(2, 'App\\NonConformity', 5, 'f39490b9-f3e9-481e-a4d5-632405b1a89e', 'photos', '65a7e541d57e7_Screenshot 2024-01-10 080111', '65a7e541d57e7_Screenshot-2024-01-10-080111.png', 'image/png', 'public', 'public', 64443, '[]', '[]', '{"thumb":true}', '[]', 2, '2024-01-17 14:33:39', '2024-01-17 14:33:40'),
	(3, 'App\\NonConformity', 52, 'c2d1b07b-1ca0-4523-9b41-3e3cd1db604d', 'photos', '686783e295458_careers-img', '686783e295458_careers-img.jpg', 'image/jpeg', 'public', 'public', 36725, '[]', '[]', '{"thumb":true}', '[]', 3, '2025-07-04 07:34:15', '2025-07-04 07:34:16'),
	(4, 'App\\NonConformity', 54, '80c95001-7790-420c-b75d-76e2fea531ef', 'photos', '686797755f112_about-header', '686797755f112_about-header.jpg', 'image/jpeg', 'public', 'public', 270184, '[]', '[]', '{"thumb":true}', '[]', 4, '2025-07-04 08:57:54', '2025-07-04 08:57:57'),
	(5, 'App\\NonConformity', 54, '1235694f-a617-4e24-a770-fa3cd6882cbe', 'file', '68679a3570eb0_Point 5', '68679a3570eb0_Point-5.png', 'image/png', 'public', 'public', 2886428, '[]', '[]', '{"thumb":true}', '[]', 5, '2025-07-04 09:09:11', '2025-07-04 09:09:12'),
	(6, 'App\\NonConformity', 66, '01168ef2-2dd3-4c1c-9303-13b4f58f43bf', 'photos', '68762ab2ea433_careers-img', '68762ab2ea433_careers-img.jpg', 'image/jpeg', 'public', 'public', 36725, '[]', '[]', '{"thumb":true}', '[]', 6, '2025-07-15 10:17:27', '2025-07-15 10:17:27'),
	(7, 'App\\NonConformity', 66, '0a1e1e85-1003-417a-9cc2-126a0cbe56ed', 'photos', '68762ab5a808c_contact-header', '68762ab5a808c_contact-header.jpg', 'image/jpeg', 'public', 'public', 118414, '[]', '[]', '{"thumb":true}', '[]', 7, '2025-07-15 10:17:27', '2025-07-15 10:17:28'),
	(8, 'App\\SafetyObservation', 2, '878fa1d7-edd9-421a-aa14-f6b9b1102faf', 'photos', '68887ba2bc8f8_Screenshot 2024-03-08 112731', '68887ba2bc8f8_Screenshot-2024-03-08-112731.png', 'image/png', 'public', 'public', 37719, '[]', '[]', '{"thumb":true}', '[]', 8, '2025-07-29 07:43:32', '2025-07-29 07:43:32'),
	(9, 'App\\SafetyObservation', 5, '3e1c043d-7ee7-40e1-8b2b-7da0ae896c24', 'photos', '68df25c577163_dkv', '68df25c577163_dkv.jpg', 'image/webp', 'public', 'public', 31638, '[]', '[]', '{"thumb":true}', '[]', 9, '2025-10-03 01:24:29', '2025-10-03 01:24:30'),
	(10, 'App\\SafetyObservation', 1, 'a5672363-ba5b-4211-944a-63a92852234d', 'photos', '68df74845fa31_seni-pertunjukan', '68df74845fa31_seni-pertunjukan.jpg', 'image/webp', 'public', 'public', 37776, '[]', '[]', '{"thumb":true}', '[]', 10, '2025-10-03 07:00:36', '2025-10-03 07:00:37'),
	(11, 'App\\SafetyObservation', 7, 'c30bb029-1ba0-471c-ae4e-78a38890c8c0', 'photos', '68df7f0571c9a_fotografi', '68df7f0571c9a_fotografi.jpg', 'image/webp', 'public', 'public', 87920, '[]', '[]', '{"thumb":true}', '[]', 11, '2025-10-03 07:45:29', '2025-10-03 07:45:30'),
	(17, 'App\\JsoReport', 95, 'dcd6aa26-811e-4e9b-a657-ac36f54957ac', 'jso_evidence', 'kpi_1', 'kpi_1.png', 'image/png', 'public', 'public', 381316, '[]', '[]', '{"thumb":true}', '[]', 12, '2025-10-16 05:40:35', '2025-10-16 05:40:40'),
	(18, 'App\\JsoReport', 97, '67c82e65-0344-4b24-abf9-171cd8881627', 'jso_evidence', 'pantai-beras-basah', 'pantai-beras-basah.jpg', 'image/jpeg', 'public', 'public', 345898, '[]', '[]', '{"thumb":true}', '[]', 13, '2025-10-16 06:49:12', '2025-10-16 06:49:13'),
	(19, 'App\\JsoReport', 98, '067af59e-d22c-48af-97ac-ba39d364be80', 'jso_evidence', 'adipura', 'adipura.jpg', 'image/jpeg', 'public', 'public', 161618, '[]', '[]', '{"thumb":true}', '[]', 14, '2025-10-16 06:49:13', '2025-10-16 06:49:14'),
	(22, 'App\\JsoReport', 109, 'dcbb5be4-1293-4037-87ab-207e381145ed', 'jso_evidence', 'pantai-beras-basah', 'pantai-beras-basah.jpg', 'image/jpeg', 'public', 'public', 345898, '[]', '[]', '{"thumb":true}', '[]', 15, '2025-10-24 06:10:37', '2025-10-24 06:10:39'),
	(23, 'App\\JsoReport', 111, '0f2722ca-0859-482f-8291-04ad48392939', 'jso_evidence', 'adipura', 'adipura.jpg', 'image/jpeg', 'public', 'public', 161618, '[]', '[]', '{"thumb":true}', '[]', 16, '2025-10-24 06:10:39', '2025-10-24 06:10:39'),
	(24, 'App\\SafetyObservation', 9, 'b9220dca-30f5-447b-850b-390fc7c19aa0', 'photos', '68fb1d996222c_musik', '68fb1d996222c_musik.jpg', 'image/webp', 'public', 'public', 10460, '[]', '[]', '{"thumb":true}', '[]', 17, '2025-10-24 06:32:59', '2025-10-24 06:32:59'),
	(25, 'App\\JsoReport', 114, '1d9dfef6-7d58-4896-990f-c82b726fb662', 'jso_evidence', 'Untitled design (3)', 'Untitled-design-(3).png', 'image/png', 'public', 'public', 73567, '[]', '[]', '{"thumb":true}', '[]', 18, '2025-10-24 06:51:16', '2025-10-24 06:51:17'),
	(26, 'App\\SafetyObservation', 14, '2ed0ddcc-70fc-4e88-b535-11eb315aa8ab', 'photos', '68fb246447491_seni-pertunjukan', '68fb246447491_seni-pertunjukan.jpg', 'image/webp', 'public', 'public', 37776, '[]', '[]', '{"thumb":true}', '[]', 19, '2025-10-24 07:01:58', '2025-10-24 07:01:58'),
	(27, 'App\\SafetyObservation', 10, '40b3ca6d-a78a-4671-a388-89a95983e0b1', 'photos', '68fb280b6d8b4_desain-produk', '68fb280b6d8b4_desain-produk.jpg', 'image/webp', 'public', 'public', 30506, '[]', '[]', '{"thumb":true}', '[]', 20, '2025-10-24 07:17:33', '2025-10-24 07:17:33'),
	(29, 'App\\SafetyObservation', 16, 'c101fb67-064e-4378-911e-4251d3bc6cb8', 'photos', '68fb35e0966f7_Petunjuk Iconic 1 - BAC4', '68fb35e0966f7_Petunjuk-Iconic-1---BAC4.png', 'image/png', 'public', 'public', 1736369, '[]', '[]', '{"thumb":true}', '[]', 21, '2025-10-24 08:16:34', '2025-10-24 08:16:35'),
	(30, 'App\\JsoReport', 124, '83e0d68e-eec8-49be-93de-d73c4f689541', 'jso_evidence', 'Petunjuk Iconic 1 - BAC4', 'Petunjuk-Iconic-1---BAC4.png', 'image/png', 'public', 'public', 1736369, '[]', '[]', '{"thumb":true}', '[]', 22, '2025-10-24 08:23:16', '2025-10-24 08:23:17'),
	(31, 'App\\JsoReport', 127, '921d7ca6-74ba-4f7e-a7d0-a23bb2ca80bc', 'jso_evidence', 'anggaran', 'anggaran.jpeg', 'image/jpeg', 'public', 'public', 33901, '[]', '[]', '{"thumb":true}', '[]', 23, '2025-11-06 14:58:22', '2025-11-06 14:58:23'),
	(32, 'App\\SafetyObservation', 19, 'a584b53b-23cf-4273-b5d1-9c10fb822165', 'photos', '690da6174c76a_sampah', '690da6174c76a_sampah.jpg', 'image/webp', 'public', 'public', 58096, '[]', '[]', '{"thumb":true}', '[]', 24, '2025-11-07 07:56:11', '2025-11-07 07:56:12'),
	(33, 'App\\JsoReport', 130, '763696a1-3043-48c2-a60b-c9fbd9f4e1d5', 'jso_evidence', 'adipura', 'adipura.jpg', 'image/jpeg', 'public', 'public', 161618, '[]', '[]', '{"thumb":true}', '[]', 25, '2025-11-14 06:51:13', '2025-11-14 06:51:14'),
	(34, 'App\\JsoReport', 131, 'ad9704a8-3d68-4202-992e-99cfaf06b73b', 'jso_evidence', 'arsip', 'arsip.png', 'image/png', 'public', 'public', 4765, '[]', '[]', '{"thumb":true}', '[]', 26, '2025-11-14 06:51:14', '2025-11-14 06:51:14'),
	(35, 'App\\JsoReport', 132, '934a54df-a5af-4b85-8099-4381355df55a', 'jso_evidence', 'anggaran', 'anggaran.jpeg', 'image/jpeg', 'public', 'public', 33901, '[]', '[]', '{"thumb":true}', '[]', 27, '2025-11-28 02:58:05', '2025-11-28 02:58:06'),
	(36, 'App\\JsoReport', 134, '4a18c4d6-7fa1-49f3-a4a7-38966423071f', 'jso_evidence', 'arsip', 'arsip.png', 'image/png', 'public', 'public', 4765, '[]', '[]', '{"thumb":true}', '[]', 28, '2025-11-28 02:58:06', '2025-11-28 02:58:06'),
	(39, 'App\\JsoReport', 142, 'dcf68835-d74c-48dc-963c-b2486ae4901a', 'jso_evidence', 'adipura', 'adipura.jpg', 'image/jpeg', 'public', 'public', 161618, '[]', '[]', '{"thumb":true}', '[]', 29, '2025-12-03 07:32:40', '2025-12-03 07:32:40'),
	(41, 'App\\JsoReport', 153, '54b02ebd-d699-42f9-bc15-f275f027c70f', 'jso_evidence', '100_83e246a50d2a27fe0d18f991dcb1bccb', '100_83e246a50d2a27fe0d18f991dcb1bccb.png', 'image/png', 'public', 'public', 90718, '[]', '[]', '{"thumb":true}', '[]', 30, '2026-02-11 02:14:15', '2026-02-11 02:14:15'),
	(42, 'App\\JsoReport', 155, '6a930148-7f55-4044-a1b0-d9ecc1ac3ccf', 'jso_evidence', 'logo kpi', 'logo-kpi.png', 'image/png', 'public', 'public', 11523, '[]', '[]', '{"thumb":true}', '[]', 31, '2026-02-11 02:33:54', '2026-02-11 02:33:55'),
	(43, 'App\\SafetyObservation', 31, '967fea9f-1ed8-4c0b-ab7a-06b7d39ef43e', 'photos', '698beee678095_ansi Z535', '698beee678095_ansi-Z535.jpg', 'image/jpeg', 'public', 'public', 176408, '[]', '[]', '{"thumb":true}', '[]', 32, '2026-02-11 02:53:01', '2026-02-11 02:53:02'),
	(44, 'App\\SafetyObservation', 11, 'bc17e629-d391-468f-a88f-048d885493d4', 'photos', '6996a000848a7_smk3_2', '6996a000848a7_smk3_2.png', 'image/png', 'public', 'public', 81168, '[]', '[]', '{"thumb":true}', '[]', 33, '2026-02-19 05:31:20', '2026-02-19 05:31:21');



SET FOREIGN_KEY_CHECKS = 1;
