package com.jikchin.jikchin_app.chat.websocket;

import com.jikchin.jikchin_app.chat.service.RoomChatService.MessageCreated;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMessageBroadcaster {
    private final SimpMessagingTemplate messaging;

    @TransactionalEventListener
    public void committed(MessageCreated event) {
        var message = event.message();
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setNativeHeader("chatMessageId", message.id().toString());
        messaging.convertAndSend("/topic/rooms/" + message.roomId() + "/messages", message, headers.getMessageHeaders());
    }
}
