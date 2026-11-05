package com.jikchin.jikchin_app.chat.repository;

import com.jikchin.jikchin_app.chat.domain.ChatParticipant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {
    long countByChatThread_Id(Long threadId);
    boolean existsByChatThread_IdAndUser_Id(Long threadId, Long userId);
    Optional<ChatParticipant> findByChatThread_IdAndUser_Id(Long threadId, Long userId);
    void deleteByChatThread_IdAndUser_Id(Long threadId, Long userId);
    Optional<ChatParticipant> findFirstByChatThread_IdAndUser_IdNotOrderByJoinedAtAscIdAsc(Long threadId, Long userId);

    @EntityGraph(attributePaths = {"user", "user.profile"})
    List<ChatParticipant> findByChatThread_IdOrderByJoinedAtAscIdAsc(Long threadId);
}
