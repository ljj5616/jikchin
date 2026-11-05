ALTER TABLE `room`
    ADD COLUMN `host_id` BIGINT NULL;

UPDATE `room` AS room
    JOIN `chat_thread` AS thread ON thread.`id` = room.`chat_thread_id`
    SET room.`host_id` = thread.`created_by_id`;

ALTER TABLE `room`
    MODIFY COLUMN `host_id` BIGINT NOT NULL,
    ADD CONSTRAINT `fk_room_host` FOREIGN KEY (`host_id`) REFERENCES `user` (`id`);
