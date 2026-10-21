-- ============================================================
-- IRMS Database - Master Seed Script
-- Run this file to set up the complete IRMS database
-- ============================================================
-- 
-- Usage:
--   mysql -u root -p db_irms < 00_irms_seed_all.sql
--
-- Or run individual files in order:
--   01_irms_ddl.sql               - Create all tables (DDL)
--   02_irms_dml_seed.sql          - Complete DML seed (all tables, single file)
--
-- Module-specific DML files (alternative to 02):
--   03_irms_rbac_dml.sql          - Roles, Permissions, Permission-Role
--   04_irms_organization_dml.sql  - Departments, Divisions, Job Titles
--   05_irms_users_dml.sql         - Users, Role-User, JSO Users
--   06_irms_lookup_dml.sql        - Lookup/Reference Data
--   07_irms_incident_dml.sql      - Incident Reports, Investigation Details
--   08_irms_safety_dml.sql        - Safety Observations, Comments, JSO Reports
--   09_irms_media_dml.sql         - Media (Spatie Media Library)
--   10_irms_audit_dml.sql         - Audit Logs
--   11_irms_misc_dml.sql          - OAuth, Sessions, Timesheet, Migrations
--
-- NOTE: Use EITHER 02 (all-in-one) OR 03-11 (module-specific), NOT both.
--
-- REPORT-NUMBER NORMALIZATION (canonical):
--   Report numbers (safety_observations.no_laporan) use the unified format
--   SO-MMYY-NNN. The seed/import files may emit legacy or partial values; the
--   authoritative normalizer is 19_renumber_so_unified_mmyy.sql. Run it LAST,
--   AFTER seeding AND after any manual JSO imports (10_import_jso_*.sql, etc.),
--   to deterministically renumber every row to SO-MMYY-NNN (event-date based,
--   per-month reset). It is idempotent — safe to re-run.
-- ===========================================================

SET FOREIGN_KEY_CHECKS = 0;
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================================
-- Step 1: Create all tables (DDL)
-- ============================================================
SOURCE 01_irms_ddl.sql;

-- ============================================================
-- Step 2: Seed all data (DML) - Module by Module
-- ============================================================

-- RBAC: Roles & Permissions
SOURCE 03_irms_rbac_dml.sql;

-- Organization: Departments, Divisions, Job Titles
SOURCE 04_irms_organization_dml.sql;

-- Users & Role Assignments
SOURCE 05_irms_users_dml.sql;

-- Lookup/Reference Tables
SOURCE 06_irms_lookup_dml.sql;

-- Incident Reports
SOURCE 07_irms_incident_dml.sql;

-- Safety Observations & JSO Reports
SOURCE 08_irms_safety_dml.sql;

-- Media Files
SOURCE 09_irms_media_dml.sql;

-- Audit Logs
SOURCE 10_irms_audit_dml.sql;

-- Miscellaneous (OAuth, Sessions, Migrations, etc.)
SOURCE 11_irms_misc_dml.sql;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- Database seeded successfully!
-- ============================================================
-- Tables: 54 total
--   - 34 tables with data
--   - 20 empty tables (ready for use)
--
-- Key data:
--   - 12 roles (Admin, Super Admin, Approval 1/2, etc.)
--   - 206 permissions
--   - 175 users
--   - 117 incident reports
--   - 33 safety observations
--   - 162 JSO reports
--   - 446 audit log entries
-- ============================================================
