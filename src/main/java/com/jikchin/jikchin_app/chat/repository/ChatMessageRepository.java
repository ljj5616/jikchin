package com.jikchin.jikchin_app.chat.repository;

import com.jikchin.jikchin_app.chat.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    void deleteByChatThread_Id(Long threadId);

    @Query("select coalesce(max(m.id), 0) from ChatMessage m where m.chatThread.id = :threadId")
    Long latestId(@Param("threadId") Long threadId);

    Optional<ChatMessage> findByChatThread_IdAndSender_IdAndClientMessageId(Long threadId, Long senderId, String clientMessageId);

    @EntityGraph(attributePaths = {"sender", "sender.profile"})
    @Query("""
            select m from ChatMessage m
            where m.chatThread.id = :threadId and m.id > :visibleAfter
                and (:beforeId is null or m.id < :beforeId)
            order by m.id desc
            """)
    List<ChatMessage> history(@Param("threadId") Long threadId, @Param("visibleAfter") Long visibleAfter,
                              @Param("beforeId") Long beforeId, Pageable pageable);
}
