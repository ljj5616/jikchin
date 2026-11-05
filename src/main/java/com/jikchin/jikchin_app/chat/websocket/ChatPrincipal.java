package com.jikchin.jikchin_app.chat.websocket;

import java.security.Principal;

public record ChatPrincipal(Long userId, String token) implements Principal {
    @Override public String getName() { return userId.toString(); }
    @Override public String toString() { return "ChatPrincipal[userId=" + userId + "]"; }
}
