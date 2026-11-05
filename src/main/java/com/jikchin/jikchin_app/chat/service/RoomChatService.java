package com.jikchin.jikchin_app.chat.service;

import com.jikchin.jikchin_app.chat.domain.ChatMessage;
import com.jikchin.jikchin_app.chat.dto.ChatHistoryResponse;
import com.jikchin.jikchin_app.chat.dto.ChatMessageResponse;
import com.jikchin.jikchin_app.chat.dto.SendMessageRequest;
import com.jikchin.jikchin_app.chat.repository.ChatMessageRepository;
import com.jikchin.jikchin_app.room.repository.RoomRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RoomChatService {
    private final RoomRepository rooms;
    private final UserRepository users;
    private final ChatMessageRepository messages;
    private final ChatAccessService access;
    private final ApplicationEventPublisher events;
    private final Validator validator;

    @Value("${app.assets.base-url}") private String assetsBaseUrl;
    @Value("${app.assets.default-avatar-key}") private String defaultAvatarKey;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChatMessageResponse send(Long userId, Long roomId, SendMessageRequest request) {
        if (request == null || !validator.validate(request).isEmpty() || request.body().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 1~2000자, clientMessageId는 UUID로 입력해 주세요.");
        }
        // Share the join/leave lock: leaving, visibility boundaries and retries are serialized per room.
        var room = rooms.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
        var member = access.requireParticipant(userId, roomId);
        String clientId = request.clientMessageId().toLowerCase(Locale.ROOT);
        var existing = messages.findByChatThread_IdAndSender_IdAndClientMessageId(member.threadId(), userId, clientId);
        if (existing.isPresent()) {
            var message = existing.get();
            if (message.getId() <= member.visibleAfterMessageId() || !message.getBody().equals(request.body())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "새 메시지에는 새로운 clientMessageId를 사용해 주세요.");
            }
            return response(roomId, message);
        }
        var message = messages.saveAndFlush(ChatMessage.text(room.getChatThread(), users.getReferenceById(userId), request.body(), clientId));
        var response = response(roomId, message);
        events.publishEvent(new MessageCreated(response));
        return response;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public ChatHistoryResponse history(Long userId, Long roomId, Long beforeId, int size) {
        if (size < 1 || size > 100 || (beforeId != null && beforeId < 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size는 1~100, beforeId는 양수여야 합니다.");
        }
        var member = access.requireParticipant(userId, roomId);
        var found = messages.history(member.threadId(), member.visibleAfterMessageId(), beforeId, PageRequest.of(0, size + 1));
        boolean hasMore = found.size() > size;
        var page = new ArrayList<>(found.subList(0, Math.min(size, found.size())));
        Collections.reverse(page);
        var responses = page.stream().map(message -> response(roomId, message)).toList();
        return new ChatHistoryResponse(responses, hasMore, hasMore ? responses.get(0).id() : null);
    }

    private ChatMessageResponse response(Long roomId, ChatMessage message) {
        var sender = message.getSender();
        var profile = sender.getProfile();
        String avatarKey = profile == null ? defaultAvatarKey : profile.getAvatarKey();
        String avatarUrl = UriComponentsBuilder.fromUriString(assetsBaseUrl)
                .pathSegment(avatarKey.split("/")).build().encode().toUriString();
        return new ChatMessageResponse(message.getId(), roomId, message.getChatThread().getId(), sender.getId(),
                profile == null ? null : profile.getNickname(), avatarUrl, message.getType(), message.getBody(),
                message.getClientMessageId(), message.getCreatedAt());
    }

    public record MessageCreated(ChatMessageResponse message) {}
}
