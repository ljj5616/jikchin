package com.jikchin.jikchin_app.chat.domain;

import com.jikchin.jikchin_app.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_thread_id", nullable = false)
    private ChatThread chatThread;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static ChatParticipant join(ChatThread thread, User user) {
        ChatParticipant participant = new ChatParticipant();
        participant.chatThread = thread;
        participant.user = user;
        participant.joinedAt = LocalDateTime.now();

        return participant;
    }
}
