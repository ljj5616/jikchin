package com.jikchin.jikchin_app.chat.websocket;

import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.chat.service.ChatAccessService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class ChatOutboundInterceptor implements ExecutorChannelInterceptor {
    private final ChatSessions sessions;
    private final TokenProviderPort tokens;
    private final ChatAccessService access;

    // Check at delivery time, including messages queued before a leave or token expiry.
    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        if (StompHeaderAccessor.wrap(message).getCommand() == StompCommand.ERROR) return message;
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
        var session = sessions.get(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()));
        if (session == null) return null;
        String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());
        if (subscriptionId == null) return null;
        var subscription = session.getSubscriptions().get(subscriptionId);
        if (subscription == null) return null;
        try {
            var principal = session.getPrincipal();
            var parsed = tokens.parse(principal.token());
            if (!"access".equals(parsed.typ()) || parsed.needProfile()) return null;
            if (subscription.roomId() == null) {
                access.requireActiveUser(principal.userId());
            } else {
                Long roomId = ChatInboundInterceptor.roomId(ChatInboundInterceptor.ROOM_TOPIC,
                        SimpMessageHeaderAccessor.getDestination(message.getHeaders()));
                if (!subscription.roomId().equals(roomId)) return null;
                var member = access.requireCurrentParticipant(principal.userId(), roomId);
                if (!member.id().equals(subscription.membershipId())) return null;
                var messageIds = SimpMessageHeaderAccessor.wrap(message).getNativeHeader("chatMessageId");
                if (messageIds == null || Long.parseLong(messageIds.get(0)) <= member.visibleAfterMessageId()) return null;
            }
            return message;
        } catch (JwtException | IllegalArgumentException | ResponseStatusException e) {
            return null;
        }
    }
}
