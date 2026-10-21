-- ============================================================
-- FCM (Firebase Cloud Messaging) Token Storage
-- Supports multiple devices per user
-- ============================================================

CREATE TABLE IF NOT EXISTS `fcm_tokens` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `token`      VARCHAR(512) NOT NULL,
    `device_info` VARCHAR(255) NULL COMMENT 'Browser/device user agent',
    `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fcm_token` (`token`),
    KEY `idx_fcm_user` (`user_id`),
    CONSTRAINT `fk_fcm_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
