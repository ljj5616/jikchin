package com.jikchin.jikchin_app.chat.service;

import com.jikchin.jikchin_app.chat.repository.ChatParticipantRepository;
import com.jikchin.jikchin_app.room.repository.RoomRepository;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatAccessService {
    private final UserRepository users;
    private final RoomRepository rooms;
    private final ChatParticipantRepository participants;

    // Outbound delivery can run during afterCommit. A fresh transaction must see current membership.
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void requireActiveUser(Long userId) {
        activeUser(userId);
    }

    @Transactional(readOnly = true)
    public Membership requireParticipant(Long userId, Long roomId) {
        activeUser(userId);
        var room = rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
        var participant = participants.findByChatThread_IdAndUser_Id(room.getChatThread().getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "방 참여자만 채팅을 이용할 수 있습니다."));
        return new Membership(participant.getId(), room.getChatThread().getId(), participant.getVisibleAfterMessageId());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Membership requireCurrentParticipant(Long userId, Long roomId) {
        return requireParticipant(userId, roomId);
    }

    private void activeUser(Long userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "프로필 등록을 완료해 주세요.");
        }
    }

    public record Membership(Long id, Long threadId, Long visibleAfterMessageId) {}
}
