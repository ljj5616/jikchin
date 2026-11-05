package com.jikchin.jikchin_app.chat.websocket;

import lombok.Getter;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSessions {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void connect(String sessionId, ChatPrincipal principal) { sessions.put(sessionId, new Session(principal)); }
    public Session get(String sessionId) { return sessionId == null ? null : sessions.get(sessionId); }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) { sessions.remove(event.getSessionId()); }

    @Getter
    public static class Session {
        private final ChatPrincipal principal;
        private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
        private Session(ChatPrincipal principal) { this.principal = principal; }
    }

    public record Subscription(Long roomId, Long membershipId) {}
}
