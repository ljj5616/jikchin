package com.jikchin.jikchin_app.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jikchin.jikchin_app.auth.infrastructure.jwt.JwtTokenProvider;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.chat.dto.ChatMessageResponse;
import com.jikchin.jikchin_app.chat.dto.ChatErrorResponse;
import com.jikchin.jikchin_app.chat.dto.SendMessageRequest;
import com.jikchin.jikchin_app.chat.service.RoomChatService;
import com.jikchin.jikchin_app.moderation.service.BlockService;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
import com.jikchin.jikchin_app.room.service.JoinRoomService;
import com.jikchin.jikchin_app.room.service.LeaveRoomService;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.Profile;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import com.jikchin.jikchin_app.user.service.WithdrawUserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.*;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:chat-websocket-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Import(ChatWebSocketTest.SubscriptionConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatWebSocketTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired TokenProviderPort tokens;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired CreateRoomService createRooms;
    @Autowired JoinRoomService joinRooms;
    @Autowired LeaveRoomService leaveRooms;
    @Autowired WithdrawUserService withdrawal;
    @Autowired RoomChatService chat;
    @Autowired BlockService blocking;
    @Autowired ProfileRepository profiles;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SubscriptionTracker tracker;
    private WebSocketStompClient client;
    private final List<StompSession> connections = new ArrayList<>();
    private User host;
    private User guest;
    private RoomResponse room;

    @BeforeEach
    void fixture() {
        jdbc.update("delete from block");
        jdbc.update("delete from chat_message");
        jdbc.update("delete from chat_participant");
        jdbc.update("delete from room");
        jdbc.update("delete from chat_thread");
        jdbc.update("delete from profile");
        jdbc.update("delete from user");
        jdbc.update("delete from team");
        jdbc.update("delete from league");
        jdbc.update("insert into league(code) values ('KBO')");
        int league = jdbc.queryForObject("select id from league", Integer.class);
        jdbc.update("insert into team(name,short_name,logo_url,league_id,onboarding_selectable) values ('A','A','',?,true),('B','B','',?,true)", league, league);
        int home = jdbc.queryForObject("select id from team where name='A'", Integer.class);
        int away = jdbc.queryForObject("select id from team where name='B'", Integer.class);
        host = user(true);
        guest = user(true);
        room = createRooms.create(host.getId(), new CreateRoomRequest("실시간 채팅", LocalDate.now(), "잠실", home, away, 5));
        joinRooms.join(guest.getId(), room.id());
        client = new WebSocketStompClient(new StandardWebSocketClient());
        var converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(json);
        client.setMessageConverter(converter);
        client.setDefaultHeartbeat(new long[]{0, 0});
    }

    @AfterEach
    void disconnect() {
        for (var connection : connections) if (connection.isConnected()) connection.disconnect();
        client.stop();
        connections.clear();
    }

    @Test
    void broadcastsCommittedMessagesToTwoMembersAndAcknowledgesRetriesWithoutRebroadcast() throws Exception {
        var sender = connect(host);
        var receiver = connect(guest);
        var sent = subscribe(sender, topic());
        var received = subscribe(receiver, topic());
        var acknowledged = subscribe(sender, "/user/queue/chat-acks");
        var request = request("실시간 메시지 🏟️");
        sender.send(sendDestination(), request);
        var first = received.poll(5, TimeUnit.SECONDS);
        assertThat(first).isNotNull();
        assertThat(first.body()).isEqualTo(request.body());
        assertThat(first.senderId()).isEqualTo(host.getId());
        assertThat(sent.poll(5, TimeUnit.SECONDS).id()).isEqualTo(first.id());
        assertThat(acknowledged.poll(5, TimeUnit.SECONDS).id()).isEqualTo(first.id());
        sender.send(sendDestination(), request);
        assertThat(acknowledged.poll(5, TimeUnit.SECONDS).id()).isEqualTo(first.id());
        assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(chat.history(guest.getId(), room.id(), null, 50).messages()).hasSize(1);
    }

    @Test
    void anExistingSubscriptionStopsReceivingAfterLeaveAndStaysRevokedAfterRejoin() throws Exception {
        var receiver = connect(guest);
        var received = subscribe(receiver, topic());
        var initial = chat.send(host.getId(), room.id(), request("처음"));
        assertThat(received.poll(5, TimeUnit.SECONDS).id()).isEqualTo(initial.id());
        leaveRooms.leave(guest.getId(), room.id());
        chat.send(host.getId(), room.id(), request("퇴장한 사람에게 전송 금지"));
        joinRooms.join(guest.getId(), room.id());
        chat.send(host.getId(), room.id(), request("이전 구독 재사용 금지"));
        assertThat(received.poll(400, TimeUnit.MILLISECONDS)).isNull();
        var rejoined = subscribe(receiver, topic());
        var latest = chat.send(host.getId(), room.id(), request("재구독 후"));
        assertThat(rejoined.poll(5, TimeUnit.SECONDS).id()).isEqualTo(latest.id());
        assertThat(received.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void withdrawalRevokesExistingSubscriptionsSendsAndNewConnections() throws Exception {
        String token = tokens.issueAccessToken(guest.getId(), false);
        var errors = new LinkedBlockingQueue<Object>();
        var receiver = connectToken(token, errorHandler(errors)).get(5, TimeUnit.SECONDS);
        connections.add(receiver);
        var received = subscribe(receiver, topic());
        var initial = chat.send(host.getId(), room.id(), request("탈퇴 전"));
        assertThat(received.poll(5, TimeUnit.SECONDS).id()).isEqualTo(initial.id());

        withdrawal.withdraw(guest.getId());

        chat.send(host.getId(), room.id(), request("탈퇴 후 수신 금지"));
        assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
        receiver.send(sendDestination(), request("탈퇴 후 발신 금지"));
        assertThat(errors.poll(5, TimeUnit.SECONDS)).isInstanceOf(ChatErrorResponse.class)
                .extracting(error -> ((ChatErrorResponse) error).status()).isEqualTo(403);
        assertThatThrownBy(() -> connectToken(token, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        assertThat(chat.history(host.getId(), room.id(), null, 50).messages()).hasSize(2);
    }

    @Test
    void rejectsMissingExpiredOnboardingAndInvalidConnectTokens() {
        var onboarding = user(false);
        var expired = new JwtTokenProvider("jikchin-test-signing-key-for-isolated-tests-only", -1000, -1000)
                .issueAccessToken(host.getId(), false);
        for (String token : List.of("", "invalid", expired, tokens.issueAccessToken(onboarding.getId(), true))) {
            assertThatThrownBy(() -> connectToken(token, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class);
        }
    }

    @Test
    void rejectsNonMemberSubscriptionsWildcardsAndDirectBrokerPublication() throws Exception {
        var outsider = user(true);
        for (String destination : List.of(topic(), "/topic/rooms/*/messages", "/queue/chat-acks", "/user/1/queue/chat-acks")) {
            var errors = new LinkedBlockingQueue<Object>();
            var session = connectToken(tokens.issueAccessToken(outsider.getId(), false), errorHandler(errors)).get(5, TimeUnit.SECONDS);
            connections.add(session);
            session.subscribe(destination, frameHandler(new LinkedBlockingQueue<>()));
            assertThat(errors.poll(5, TimeUnit.SECONDS)).isInstanceOf(ChatErrorResponse.class)
                    .extracting(error -> ((ChatErrorResponse) error).status()).isEqualTo(403);
        }
        var errors = new LinkedBlockingQueue<Object>();
        var session = connectToken(tokens.issueAccessToken(host.getId(), false), errorHandler(errors)).get(5, TimeUnit.SECONDS);
        connections.add(session);
        session.send(topic(), request("브로커 직접 전송 금지"));
        assertThat(errors.poll(5, TimeUnit.SECONDS)).isInstanceOf(ChatErrorResponse.class)
                .extracting(error -> ((ChatErrorResponse) error).status()).isEqualTo(403);
        assertThat(chat.history(host.getId(), room.id(), null, 50).messages()).isEmpty();
    }

    @Test
    void expiresAnAlreadyConnectedSessionForBothReceiveAndSend() throws Exception {
        var shortLived = new JwtTokenProvider("jikchin-test-signing-key-for-isolated-tests-only", 3000, 3000)
                .issueAccessToken(guest.getId(), false);
        var errors = new LinkedBlockingQueue<Object>();
        var receiver = connectToken(shortLived, errorHandler(errors)).get(5, TimeUnit.SECONDS);
        connections.add(receiver);
        var received = subscribe(receiver, topic());
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
            try { tokens.parse(shortLived); return false; }
            catch (io.jsonwebtoken.ExpiredJwtException e) { return true; }
        });
        chat.send(host.getId(), room.id(), request("만료 후 수신 금지"));
        assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
        receiver.send(sendDestination(), request("만료 후 발신 금지"));
        assertThat(errors.poll(5, TimeUnit.SECONDS)).isInstanceOf(ChatErrorResponse.class)
                .extracting(error -> ((ChatErrorResponse) error).status()).isEqualTo(401);
        assertThat(chat.history(host.getId(), room.id(), null, 50).messages()).hasSize(1);
    }

    @Test
    void invalidMessageReportsAnErrorAndTheConnectionCanSendAgain() throws Exception {
        var sender = connect(host);
        var errors = subscribe(sender, "/user/queue/chat-errors", ChatErrorResponse.class);
        var received = subscribe(sender, topic());
        sender.send(sendDestination(), request(" "));
        assertThat(errors.poll(5, TimeUnit.SECONDS).status()).isEqualTo(400);
        assertThat(sender.isConnected()).isTrue();
        sender.send(sendDestination(), request("정상 메시지"));
        assertThat(received.poll(5, TimeUnit.SECONDS).body()).isEqualTo("정상 메시지");
    }

    @Test
    void rolledBackMessagesAreNeitherPersistedNorBroadcast() throws Exception {
        var received = subscribe(connect(guest), topic());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            chat.send(host.getId(), room.id(), request("롤백"));
            status.setRollbackOnly();
        });
        assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(chat.history(host.getId(), room.id(), null, 50).messages()).isEmpty();
        var committed = chat.send(host.getId(), room.id(), request("커밋"));
        assertThat(received.poll(5, TimeUnit.SECONDS).id()).isEqualTo(committed.id());
    }

    @Test
    void concurrentRetriesCreateOneMessageAndOneBroadcast() throws Exception {
        var received = subscribe(connect(guest), topic());
        var request = request("동시 재전송");
        var start = new CyclicBarrier(4);
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<ChatMessageResponse>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) results.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return chat.send(host.getId(), room.id(), request);
            }));
            var ids = new HashSet<Long>();
            for (var result : results) ids.add(result.get(10, TimeUnit.SECONDS).id());
            assertThat(ids).hasSize(1);
            assertThat(received.poll(5, TimeUnit.SECONDS).id()).isIn(ids);
            assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
            assertThat(chat.history(host.getId(), room.id(), null, 50).messages()).hasSize(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void liveBlockAndUnblockPersonalizeOnlyTheBlockersMessagesWithoutResubscribing() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                profiles.saveAndFlush(Profile.create(users.findById(host.getId()).orElseThrow(), "방장",
                        "avatars/defaults/default1.jpg", null)));
        var observer = user(true);
        joinRooms.join(observer.getId(), room.id());
        var sent = subscribe(connect(host), topic());
        var received = subscribe(connect(guest), topic());
        var observed = subscribe(connect(observer), topic());

        var original = chat.send(host.getId(), room.id(), request("차단 전"));
        assertThat(received.poll(5, TimeUnit.SECONDS).senderNickname()).isEqualTo("방장");
        assertThat(sent.poll(5, TimeUnit.SECONDS).id()).isEqualTo(original.id());
        assertThat(observed.poll(5, TimeUnit.SECONDS).senderNickname()).isEqualTo("방장");

        blocking.block(guest.getId(), host.getId());
        var blocked = chat.send(host.getId(), room.id(), request("차단 후 🏟️"));
        var personalized = received.poll(5, TimeUnit.SECONDS);
        assertThat(personalized).isNotNull();
        assertThat(personalized.senderNickname()).isEqualTo("방장(차단)");
        assertThat(personalized.id()).isEqualTo(blocked.id());
        assertThat(personalized.body()).isEqualTo("차단 후 🏟️");
        assertThat(sent.poll(5, TimeUnit.SECONDS).senderNickname()).isEqualTo("방장");
        assertThat(observed.poll(5, TimeUnit.SECONDS).senderNickname()).isEqualTo("방장");

        blocking.unblock(guest.getId(), host.getId());
        var restored = chat.send(host.getId(), room.id(), request("차단 해제 후"));
        assertThat(received.poll(5, TimeUnit.SECONDS).senderNickname()).isEqualTo("방장");
        assertThat(sent.poll(5, TimeUnit.SECONDS).id()).isEqualTo(restored.id());
        assertThat(observed.poll(5, TimeUnit.SECONDS).senderNickname()).isEqualTo("방장");
    }

    private User user(boolean active) {
        var user = User.createSocialUser("google", UUID.randomUUID().toString());
        if (active) user.activate();
        return users.saveAndFlush(user);
    }
    private String topic() { return "/topic/rooms/" + room.id() + "/messages"; }
    private String sendDestination() { return "/app/rooms/" + room.id() + "/messages"; }
    private SendMessageRequest request(String body) { return new SendMessageRequest(body, UUID.randomUUID().toString()); }
    private StompSession connect(User user) throws Exception {
        var session = connectToken(tokens.issueAccessToken(user.getId(), false), new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        connections.add(session);
        return session;
    }
    private CompletableFuture<StompSession> connectToken(String token, StompSessionHandler handler) {
        var headers = new StompHeaders();
        if (!token.isEmpty()) headers.add("Authorization", "Bearer " + token);
        return client.connectAsync("ws://127.0.0.1:" + port + "/ws/chat", new org.springframework.web.socket.WebSocketHttpHeaders(), headers, handler);
    }
    private BlockingQueue<ChatMessageResponse> subscribe(StompSession session, String destination) throws Exception {
        return subscribe(session, destination, ChatMessageResponse.class);
    }
    private <T> BlockingQueue<T> subscribe(StompSession session, String destination, Class<T> type) throws Exception {
        var queue = new LinkedBlockingQueue<T>();
        var id = UUID.randomUUID().toString();
        var ready = tracker.expect(id);
        var headers = new StompHeaders();
        headers.setId(id);
        headers.setDestination(destination);
        session.subscribe(headers, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return type; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { queue.add(type.cast(payload)); }
        });
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        return queue;
    }
    private StompFrameHandler frameHandler(BlockingQueue<ChatMessageResponse> queue) {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return ChatMessageResponse.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { queue.add((ChatMessageResponse) payload); }
        };
    }
    private StompSessionHandler errorHandler(BlockingQueue<Object> errors) {
        return new StompSessionHandlerAdapter() {
            @Override public Type getPayloadType(StompHeaders headers) { return ChatErrorResponse.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { errors.add(payload); }
            @Override public void handleTransportError(StompSession session, Throwable exception) { errors.add(exception); }
        };
    }

    @TestConfiguration
    static class SubscriptionConfig {
        @Bean SubscriptionTracker subscriptionTracker() { return new SubscriptionTracker(); }
        @Bean WebSocketMessageBrokerConfigurer subscriptionTrackingConfigurer(SubscriptionTracker tracker) {
            return new WebSocketMessageBrokerConfigurer() {
                @Override public void configureClientInboundChannel(ChannelRegistration registration) {
                    registration.interceptors(tracker);
                }
                @Override public void configureMessageBroker(org.springframework.messaging.simp.config.MessageBrokerRegistry registry) {
                    registry.configureBrokerChannel().interceptors(tracker);
                }
            };
        }
    }
    static class SubscriptionTracker implements ExecutorChannelInterceptor {
        private final Map<String, CountDownLatch> ready = new ConcurrentHashMap<>();
        CountDownLatch expect(String id) {
            var latch = new CountDownLatch(1);
            ready.put(id, latch);
            return latch;
        }
        @Override public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
            var headers = StompHeaderAccessor.wrap(message);
            if (handler instanceof SimpleBrokerMessageHandler && headers.getCommand() == StompCommand.SUBSCRIBE
                    && headers.getDestination() != null && !headers.getDestination().startsWith("/user/")) {
                var latch = ready.remove(headers.getSubscriptionId());
                if (latch != null) latch.countDown();
            }
        }
    }
}
