CREATE TABLE `room` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(100) NOT NULL,
    `game_date` DATE NOT NULL,
    `stadium` VARCHAR(100) NOT NULL,
    `home_team_id` INT NOT NULL,
    `away_team_id` INT NOT NULL,
    `chat_thread_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_room_chat_thread` UNIQUE (`chat_thread_id`),
    CONSTRAINT `fk_room_home_team` FOREIGN KEY (`home_team_id`) REFERENCES `team` (`id`),
    CONSTRAINT `fk_room_away_team` FOREIGN KEY (`away_team_id`) REFERENCES `team` (`id`),
    CONSTRAINT `fk_room_chat_thread` FOREIGN KEY (`chat_thread_id`) REFERENCES `chat_thread` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
