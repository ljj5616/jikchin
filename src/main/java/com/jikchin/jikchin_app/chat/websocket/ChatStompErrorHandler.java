package com.jikchin.jikchin_app.chat.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jikchin.jikchin_app.chat.dto.ChatErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component
@RequiredArgsConstructor
public class ChatStompErrorHandler extends StompSubProtocolErrorHandler {
    private final ObjectMapper json;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable exception) {
        var error = new ChatErrorResponse(400, "잘못된 채팅 요청입니다.");
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResponseStatusException status) {
                error = new ChatErrorResponse(status.getStatusCode().value(), status.getReason());
                break;
            }
        }
        byte[] body;
        try { body = json.writeValueAsBytes(error); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        var headers = StompHeaderAccessor.create(StompCommand.ERROR);
        headers.setMessage(error.message());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentLength(body.length);
        return MessageBuilder.createMessage(body, headers.getMessageHeaders());
    }
}
