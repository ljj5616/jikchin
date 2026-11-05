package com.jikchin.jikchin_app.chat.controller;

import com.jikchin.jikchin_app.chat.dto.ChatErrorResponse;
import com.jikchin.jikchin_app.chat.dto.ChatMessageResponse;
import com.jikchin.jikchin_app.chat.dto.SendMessageRequest;
import com.jikchin.jikchin_app.chat.service.RoomChatService;
import com.jikchin.jikchin_app.chat.websocket.ChatPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatSocketController {
    private final RoomChatService chat;

    @MessageMapping("/rooms/{roomId}/messages")
    @SendToUser(value = "/queue/chat-acks", broadcast = false)
    public ChatMessageResponse send(Principal principal, @DestinationVariable Long roomId, @Valid SendMessageRequest request) {
        return chat.send(((ChatPrincipal) principal).userId(), roomId, request);
    }

    @MessageExceptionHandler(ResponseStatusException.class)
    @SendToUser(value = "/queue/chat-errors", broadcast = false)
    public ChatErrorResponse rejected(ResponseStatusException exception) {
        return new ChatErrorResponse(exception.getStatusCode().value(), exception.getReason());
    }

    @MessageExceptionHandler({MethodArgumentNotValidException.class, MessageConversionException.class})
    @SendToUser(value = "/queue/chat-errors", broadcast = false)
    public ChatErrorResponse invalid(Exception exception) {
        return new ChatErrorResponse(400, "메시지는 1~2000자, clientMessageId는 UUID로 입력해 주세요.");
    }
}
