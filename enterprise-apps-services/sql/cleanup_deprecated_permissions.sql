-- ============================================================
-- Cleanup deprecated permissions: PENGADAAN, INVENTORY, KEUANGAN, ASET
-- These modules have been replaced by LIMS permissions
-- (LAB_REPORT, DAILY_REPORT, QUALITY)
-- ============================================================

-- 1. Remove role_permissions referencing deprecated permissions
DELETE FROM role_permissions 
WHERE permission_id IN (
    SELECT id FROM permissions 
    WHERE module IN ('PENGADAAN', 'INVENTORY', 'KEUANGAN', 'ASET')
);

-- 2. Remove the deprecated permissions themselves
DELETE FROM permissions 
WHERE module IN ('PENGADAAN', 'INVENTORY', 'KEUANGAN', 'ASET');

-- 3. Verify cleanup
SELECT module, COUNT(*) as count 
FROM permissions 
GROUP BY module 
ORDER BY module;
