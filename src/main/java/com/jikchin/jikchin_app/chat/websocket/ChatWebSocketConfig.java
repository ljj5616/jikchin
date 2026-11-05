package com.jikchin.jikchin_app.chat.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final ChatInboundInterceptor inbound;
    private final ChatOutboundInterceptor outbound;
    private final ChatStompErrorHandler errors;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat");
        registry.setErrorHandler(errors);
        registry.setPreserveReceiveOrder(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000}).setTaskScheduler(chatHeartbeatScheduler());
    }

    @Bean
    public ThreadPoolTaskScheduler chatHeartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-heartbeat-");
        return scheduler;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) { registration.interceptors(inbound); }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) { registration.interceptors(outbound); }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16 * 1024).setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(10000).setTimeToFirstMessage(10000);
    }
}
