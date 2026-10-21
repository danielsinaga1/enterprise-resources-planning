-- Migration: Add photos column to incident_reports and safety_observations
-- Stores JSON array of uploaded filenames, e.g. ["uuid1.jpg","uuid2.png"]

ALTER TABLE `incident_reports`
    ADD COLUMN `photos` TEXT NULL AFTER `in_progress_at`;

ALTER TABLE `safety_observations`
    ADD COLUMN `photos` TEXT NULL AFTER `deleted_at`;
