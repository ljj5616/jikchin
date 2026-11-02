CREATE TABLE `auth_refresh_session` (
    `token_hash` VARCHAR(64) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `expires_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`token_hash`),
    INDEX `idx_auth_refresh_user` (`user_id`),
    CONSTRAINT `fk_auth_refresh_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
