package com.jikchin.jikchin_app.chat.repository;

import com.jikchin.jikchin_app.chat.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    void deleteByChatThread_Id(Long threadId);
}
