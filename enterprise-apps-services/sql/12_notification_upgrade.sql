-- ============================================================================
-- NOTIFICATION SYSTEM UPGRADE
-- Adds columns for notification categorization and tracking
-- ============================================================================

-- Add notification type columns to user_alerts
-- Use procedure to handle IF NOT EXISTS for MySQL 8.0.30 compatibility
DELIMITER //

DROP PROCEDURE IF EXISTS _notification_upgrade//

CREATE PROCEDURE _notification_upgrade()
BEGIN
    -- alert_type column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'user_alerts' AND column_name = 'alert_type'
    ) THEN
        ALTER TABLE `user_alerts` ADD COLUMN `alert_type` VARCHAR(50) NULL AFTER `alert_link`;
    END IF;

    -- reference_id column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'user_alerts' AND column_name = 'reference_id'
    ) THEN
        ALTER TABLE `user_alerts` ADD COLUMN `reference_id` BIGINT NULL AFTER `alert_type`;
    END IF;

    -- reference_type column
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'user_alerts' AND column_name = 'reference_type'
    ) THEN
        ALTER TABLE `user_alerts` ADD COLUMN `reference_type` VARCHAR(100) NULL AFTER `reference_id`;
    END IF;

    -- id column on user_user_alert
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'user_user_alert' AND column_name = 'id'
    ) THEN
        ALTER TABLE `user_user_alert` ADD COLUMN `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT FIRST, ADD PRIMARY KEY (`id`);
    END IF;
END//

DELIMITER ;

CALL _notification_upgrade();
DROP PROCEDURE IF EXISTS _notification_upgrade;

-- Indexes (safe to call multiple times due to IF NOT EXISTS)
CREATE INDEX `idx_user_alert_user_read` ON `user_user_alert` (`user_id`, `read`);
CREATE INDEX `idx_user_alert_type` ON `user_alerts` (`alert_type`);
