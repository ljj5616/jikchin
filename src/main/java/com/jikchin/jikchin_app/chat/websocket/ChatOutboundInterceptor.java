package com.jikchin.jikchin_app.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.chat.dto.ChatMessageResponse;
import com.jikchin.jikchin_app.chat.service.ChatAccessService;
import com.jikchin.jikchin_app.moderation.service.BlockService;
import com.jikchin.jikchin_app.moderation.service.BlockedNickname;
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
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ChatOutboundInterceptor implements ExecutorChannelInterceptor {
    private final ChatSessions sessions;
    private final TokenProviderPort tokens;
    private final ChatAccessService access;
    private final BlockService blocks;
    private final ObjectMapper json;

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
                return personalize(message, principal.userId());
            }
            return message;
        } catch (JwtException | IllegalArgumentException | ResponseStatusException e) {
            return null;
        }
    }

    private Message<?> personalize(Message<?> message, Long viewerId) {
        try {
            var response = json.readValue((byte[]) message.getPayload(), ChatMessageResponse.class);
            if (response.senderNickname() == null || !blocks.hasBlocked(viewerId, response.senderId())) return message;
            var personalized = response.withSenderNickname(BlockedNickname.format(response.senderNickname(), true));
            byte[] payload = json.writeValueAsBytes(personalized);
            var headers = StompHeaderAccessor.wrap(message);
            headers.updateStompCommandAsServerMessage();
            headers.setContentLength(payload.length);
            // Create a separate payload for this receiver; the shared broker message stays unchanged.
            return MessageBuilder.createMessage(payload, headers.getMessageHeaders());
        } catch (IOException e) {
            throw new IllegalArgumentException("채팅 메시지 변환에 실패했습니다.", e);
        }
    }
}
