package com.jikchin.jikchin_app.chat.repository;
import com.jikchin.jikchin_app.chat.domain.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {
    long countByChatThread_Id(Long threadId);
    boolean existsByChatThread_IdAndUser_Id(Long threadId, Long userId);
}
