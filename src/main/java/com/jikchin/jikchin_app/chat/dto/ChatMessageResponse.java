package com.jikchin.jikchin_app.chat.dto;

import com.jikchin.jikchin_app.chat.domain.MessageType;
import java.time.LocalDateTime;

public record ChatMessageResponse(Long id, Long roomId, Long chatThreadId, Long senderId,
                                  String senderNickname, String senderAvatarUrl, MessageType type,
                                  String body, String clientMessageId, LocalDateTime createdAt) {
    public ChatMessageResponse withSenderNickname(String nickname) {
        return new ChatMessageResponse(id, roomId, chatThreadId, senderId, nickname, senderAvatarUrl,
                type, body, clientMessageId, createdAt);
    }
}
