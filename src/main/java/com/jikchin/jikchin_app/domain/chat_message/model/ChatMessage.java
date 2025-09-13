package com.jikchin.jikchin_app.domain.chat_message.model;

import com.jikchin.jikchin_app.domain.chat_message_asset.model.ChatMessageAsset;
import com.jikchin.jikchin_app.domain.chat_thread.model.ChatThread;
import com.jikchin.jikchin_app.domain.media_asset.model.MediaAsset;
import com.jikchin.jikchin_app.domain.user.model.User;
import com.jikchin.jikchin_app.global.support.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name = "idx_msg_thread", columnList = "chat_thread_id"),
        @Index(name = "idx_msg_sender", columnList = "sender_id")
})
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_thread_id", nullable = false)
    private ChatThread chatThread;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ChatMessageAsset> assets = new ArrayList<>();

    public void addAsset(MediaAsset asset, int pos) {
        this.assets.add(ChatMessageAsset.of(this, asset, pos));
    }

    public void removeAsset(ChatMessageAsset link) {
        this.assets.remove(link);
    }


    public static ChatMessage text(ChatThread thread, User user, String body) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.chatThread = thread;
        chatMessage.sender = user;
        chatMessage.body = body;
        chatMessage.type= MessageType.TEXT;

        return chatMessage;
    }

    public static ChatMessage image(ChatThread thread, User user, String caption) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.chatThread = thread;
        chatMessage.sender = user;
        chatMessage.body = caption == null ? "" : caption;
        chatMessage.type = MessageType.IMAGE;

        return chatMessage;
    }
}
