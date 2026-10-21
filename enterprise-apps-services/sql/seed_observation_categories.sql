-- ============================================================
-- Seed: Safety Observation Categories for JSO Findings
-- Replaces the legacy incident root_cause rows used in JSO form
-- with the 7 standard safety observation categories (K3-OT).
--
-- IDs 1-7 correspond to ObservationCategoryMatrix.tsx hardcoded list.
-- WARNING: existing jso_reports.category_id values will be invalid
-- if there are already rows referencing old root_cause IDs.
-- Run this ONLY on a fresh DB or after verifying no jso_reports rows exist.
-- ============================================================

-- Clear existing root_causes
TRUNCATE TABLE `root_causes`;

-- Re-insert 7 observation categories with fixed IDs 1-7
INSERT INTO `root_causes` (`id`, `name`, `created_at`, `updated_at`, `deleted_at`) VALUES
(1, 'K3 - Keselamatan dan Kesehatan Kerja', NOW(), NOW(), NULL),
(2, 'PS - Keselamatan Proses',               NOW(), NOW(), NULL),
(3, 'LH - Lingkungan Hidup',                 NOW(), NOW(), NULL),
(4, 'HS - Hygiene Sanitasi',                 NOW(), NOW(), NULL),
(5, 'HK - Housekeeping',                     NOW(), NOW(), NULL),
(6, 'QA - Quality',                         NOW(), NOW(), NULL),
(7, 'OT - Others',                           NOW(), NOW(), NULL);
