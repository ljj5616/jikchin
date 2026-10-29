-- Initial schema for the existing JPA entities. Reference data is added separately.

CREATE TABLE `league` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `code` ENUM('KBO', 'KLEAGUE') NOT NULL,
    `logo_url` VARCHAR(200),
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_league_code` UNIQUE (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stadium` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(20) NOT NULL,
    `region` ENUM(
        'JAMSIL', 'SAJIK', 'GOCHEOK', 'MUNHAK', 'DAEJEON',
        'GWANGJU', 'DAEGU', 'SUWON', 'CHANGWON', 'ULSAN',
        'CHUNCHEON', 'JEJU', 'JEONJU', 'SEOUL', 'KIMCHEON',
        'POHANG', 'GANGNEUNG', 'ANYANG', 'CHEONGJU', 'GUNSAN'
    ) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `team` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(20) NOT NULL,
    `short_name` VARCHAR(10) NOT NULL,
    `logo_url` VARCHAR(200) NOT NULL,
    `league_id` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_team_league_name` UNIQUE (`league_id`, `name`),
    CONSTRAINT `fk_team_league` FOREIGN KEY (`league_id`) REFERENCES `league` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `provider` VARCHAR(255) NOT NULL,
    `provider_id` VARCHAR(255) NOT NULL,
    `status` ENUM('REGISTERING', 'ACTIVE', 'SUSPENDED', 'DELETED') NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_user_provider_identity` UNIQUE (`provider`, `provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `game` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `season` INT NOT NULL,
    `start_at` DATETIME(6) NOT NULL,
    `league_id` INT NOT NULL,
    `home_team_id` INT NOT NULL,
    `away_team_id` INT NOT NULL,
    `stadium_id` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_game_start_teams` UNIQUE (`start_at`, `home_team_id`, `away_team_id`),
    CONSTRAINT `fk_game_league` FOREIGN KEY (`league_id`) REFERENCES `league` (`id`),
    CONSTRAINT `fk_game_home_team` FOREIGN KEY (`home_team_id`) REFERENCES `team` (`id`),
    CONSTRAINT `fk_game_away_team` FOREIGN KEY (`away_team_id`) REFERENCES `team` (`id`),
    CONSTRAINT `fk_game_stadium` FOREIGN KEY (`stadium_id`) REFERENCES `stadium` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `profile` (
    `user_id` BIGINT NOT NULL,
    `nickname` VARCHAR(20) NOT NULL,
    `avatar_key` VARCHAR(200) NOT NULL,
    `bio` VARCHAR(160),
    `birth_year` INT,
    `gender` ENUM('MALE', 'FEMALE'),
    `favorite_kbo_team_id` INT,
    `favorite_kleague_team_id` INT,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`user_id`),
    CONSTRAINT `uq_profile_nickname` UNIQUE (`nickname`),
    INDEX `idx_profile_fav_kbo` (`favorite_kbo_team_id`),
    INDEX `idx_profile_fav_kleague` (`favorite_kleague_team_id`),
    CONSTRAINT `fk_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_profile_kbo_team` FOREIGN KEY (`favorite_kbo_team_id`) REFERENCES `team` (`id`),
    CONSTRAINT `fk_profile_kleague_team` FOREIGN KEY (`favorite_kleague_team_id`) REFERENCES `team` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `media_asset` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `url` VARCHAR(500) NOT NULL,
    `mime_type` VARCHAR(100) NOT NULL,
    `width` INT NOT NULL,
    `height` INT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_media_owner` (`user_id`),
    CONSTRAINT `fk_media_asset_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_thread` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `thread_type` ENUM('DIRECT', 'GROUP') NOT NULL,
    `capacity` INT NOT NULL,
    `created_by_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_chat_thread_creator` FOREIGN KEY (`created_by_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_participant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `joined_at` DATETIME(6) NOT NULL,
    `chat_thread_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_chat_participant_thread` FOREIGN KEY (`chat_thread_id`) REFERENCES `chat_thread` (`id`),
    CONSTRAINT `fk_chat_participant_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `body` LONGTEXT NOT NULL,
    `type` ENUM('TEXT', 'IMAGE') NOT NULL,
    `chat_thread_id` BIGINT NOT NULL,
    `sender_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_msg_thread` (`chat_thread_id`),
    INDEX `idx_msg_sender` (`sender_id`),
    CONSTRAINT `fk_chat_message_thread` FOREIGN KEY (`chat_thread_id`) REFERENCES `chat_thread` (`id`),
    CONSTRAINT `fk_chat_message_sender` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `chat_message_asset` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `message_id` BIGINT NOT NULL,
    `asset_id` BIGINT NOT NULL,
    `position` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_msg_asset` UNIQUE (`message_id`, `asset_id`),
    CONSTRAINT `uq_msg_position` UNIQUE (`message_id`, `position`),
    INDEX `idx_msg_asset_message` (`message_id`),
    INDEX `idx_msg_asset_asset` (`asset_id`),
    CONSTRAINT `fk_chat_message_asset_message` FOREIGN KEY (`message_id`) REFERENCES `chat_message` (`id`),
    CONSTRAINT `fk_chat_message_asset_asset` FOREIGN KEY (`asset_id`) REFERENCES `media_asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(30) NOT NULL,
    `content` LONGTEXT NOT NULL,
    `visibility` ENUM('PUBLIC', 'PRIVATE') NOT NULL,
    `is_deleted` BIT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `game_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_record_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_record_game` FOREIGN KEY (`game_id`) REFERENCES `game` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `record_image` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `image_url` VARCHAR(255) NOT NULL,
    `record_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_record_image_record` FOREIGN KEY (`record_id`) REFERENCES `record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `block` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `blocker_id` BIGINT NOT NULL,
    `blocked_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_block_users` UNIQUE (`blocker_id`, `blocked_id`),
    CONSTRAINT `fk_block_blocker` FOREIGN KEY (`blocker_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_block_blocked` FOREIGN KEY (`blocked_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `reason` VARCHAR(20) NOT NULL,
    `reporter_id` BIGINT NOT NULL,
    `reported_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`id`),
    CONSTRAINT `fk_report_reported` FOREIGN KEY (`reported_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `point_account` (
    `user_id` BIGINT NOT NULL,
    `version` BIGINT,
    `balance` INT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_point_account_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `point_ledger` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `delta` INT NOT NULL,
    `reason` ENUM('TOPUP', 'PURCHASE', 'REFUND', 'ADMIN_ADJUST') NOT NULL,
    `ref_type` ENUM('TOPUP', 'ORDER', 'EVENT', 'ADMIN') NOT NULL,
    `ref_id` BIGINT,
    `balance_after` INT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_ledger_user_created` (`user_id`, `created_at`),
    INDEX `idx_ledger_reftype_refid` (`ref_type`, `ref_id`),
    CONSTRAINT `fk_point_ledger_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `point_topup` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `amount` INT NOT NULL,
    `provider` ENUM('PLAY', 'ALT_KAKAOPAY', 'ALT_NAVERPAY', 'ALT_TOSSPAY', 'ADMIN') NOT NULL,
    `status` ENUM('PENDING', 'SUCCESS', 'FAILED') NOT NULL,
    `idempotency_key` VARCHAR(100) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `purchase_token` VARCHAR(200),
    `package_name` VARCHAR(200),
    `product_id` VARCHAR(100),
    `play_acknowledge_at` DATETIME(6),
    `external_transaction_id` VARCHAR(200),
    `receipt_payload` LONGTEXT,
    `requested_at` DATETIME(6) NOT NULL,
    `completed_at` DATETIME(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_topup_idempotency` UNIQUE (`idempotency_key`),
    CONSTRAINT `uq_topup_purchase_token` UNIQUE (`purchase_token`),
    CONSTRAINT `uq_topup_external_txid` UNIQUE (`external_transaction_id`),
    INDEX `idx_topup_user_requested` (`user_id`, `requested_at`),
    INDEX `idx_topup_status_requested` (`status`, `requested_at`),
    INDEX `idx_topup_provider` (`provider`),
    CONSTRAINT `fk_point_topup_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
