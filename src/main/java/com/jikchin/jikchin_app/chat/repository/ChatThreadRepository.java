package com.jikchin.jikchin_app.chat.repository;
import com.jikchin.jikchin_app.chat.domain.ChatThread;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChatThreadRepository extends JpaRepository<ChatThread, Long> {}
