package com.jikchin.jikchin_app.chat.websocket;

import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.chat.service.ChatAccessService;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@Component
public class ChatInboundInterceptor implements ChannelInterceptor {
    static final Pattern ROOM_TOPIC = Pattern.compile("/topic/rooms/([1-9][0-9]*)/messages");
    private static final Pattern ROOM_SEND = Pattern.compile("/app/rooms/([1-9][0-9]*)/messages");
    private final TokenProviderPort tokens;
    private final ChatAccessService access;
    private final ChatSessions sessions;
    private final ChatStompErrorHandler errors;
    private final ObjectProvider<MessageChannel> outbound;

    public ChatInboundInterceptor(TokenProviderPort tokens, ChatAccessService access, ChatSessions sessions,
                                  ChatStompErrorHandler errors,
                                  @Qualifier("clientOutboundChannel") ObjectProvider<MessageChannel> outbound) {
        this.tokens = tokens;
        this.access = access;
        this.sessions = sessions;
        this.errors = errors;
        this.outbound = outbound;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        try {
            return authorize(message);
        } catch (ResponseStatusException exception) {
            // Ordered inbound channels consume interceptor exceptions. Send ERROR explicitly so
            // rejected commands still notify the client and close the WebSocket session.
            var error = errors.handleClientMessageProcessingError(null, exception);
            var headers = StompHeaderAccessor.wrap(error);
            headers.setSessionId(StompHeaderAccessor.wrap(message).getSessionId());
            outbound.getObject().send(MessageBuilder.createMessage(error.getPayload(), headers.getMessageHeaders()));
            return null;
        }
    }

    private Message<?> authorize(Message<?> message) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null || headers.getCommand() == StompCommand.DISCONNECT) return message;
        var command = headers.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            String authorization = headers.getFirstNativeHeader("Authorization");
            headers.removeNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) throw unauthorized();
            String token = authorization.substring(7);
            var parsed = parse(token);
            if (parsed.needProfile()) throw forbidden();
            access.requireActiveUser(parsed.userId());
            var principal = new ChatPrincipal(parsed.userId(), token);
            headers.setUser(principal);
            sessions.connect(headers.getSessionId(), principal);
            return message;
        }
        var session = sessions.get(headers.getSessionId());
        if (session == null) throw unauthorized();
        var principal = session.getPrincipal();
        parse(principal.token()); // Recheck expiry for every client command.
        access.requireActiveUser(principal.userId());
        String destination = headers.getDestination();
        if (command == StompCommand.SEND) {
            Long roomId = roomId(ROOM_SEND, destination);
            if (roomId == null) throw forbidden();
            access.requireParticipant(principal.userId(), roomId);
        } else if (command == StompCommand.SUBSCRIBE) {
            String subscriptionId = headers.getSubscriptionId();
            if (subscriptionId == null || subscriptionId.isBlank()) throw forbidden();
            Long roomId = roomId(ROOM_TOPIC, destination);
            ChatSessions.Subscription subscription;
            if (roomId != null) {
                var member = access.requireParticipant(principal.userId(), roomId);
                subscription = new ChatSessions.Subscription(roomId, member.id());
            } else if ("/user/queue/chat-errors".equals(destination) || "/user/queue/chat-acks".equals(destination)) {
                subscription = new ChatSessions.Subscription(null, null);
            } else {
                throw forbidden();
            }
            if (session.getSubscriptions().putIfAbsent(subscriptionId, subscription) != null) throw forbidden();
        } else if (command == StompCommand.UNSUBSCRIBE) {
            if (headers.getSubscriptionId() != null) session.getSubscriptions().remove(headers.getSubscriptionId());
        } else {
            throw forbidden();
        }
        return message;
    }

    private TokenProviderPort.ParsedToken parse(String token) {
        try {
            var parsed = tokens.parse(token);
            if (!"access".equals(parsed.typ())) throw unauthorized();
            return parsed;
        } catch (JwtException | IllegalArgumentException e) {
            throw unauthorized();
        }
    }

    static Long roomId(Pattern pattern, String destination) {
        if (destination == null) return null;
        var matcher = pattern.matcher(destination);
        if (!matcher.matches()) return null;
        try { return Long.valueOf(matcher.group(1)); } catch (NumberFormatException e) { return null; }
    }

    private ResponseStatusException unauthorized() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효한 액세스 토큰이 필요합니다."); }
    private ResponseStatusException forbidden() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "허용되지 않은 채팅 요청입니다."); }
}
