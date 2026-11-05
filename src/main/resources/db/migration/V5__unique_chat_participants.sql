ALTER TABLE `chat_participant`
    ADD CONSTRAINT `uq_chat_participant_thread_user` UNIQUE (`chat_thread_id`, `user_id`);
