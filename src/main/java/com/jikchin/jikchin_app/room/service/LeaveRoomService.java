package com.jikchin.jikchin_app.room.service;

import com.jikchin.jikchin_app.chat.repository.ChatMessageRepository;
import com.jikchin.jikchin_app.chat.repository.ChatParticipantRepository;
import com.jikchin.jikchin_app.chat.repository.ChatThreadRepository;
import com.jikchin.jikchin_app.room.repository.RoomRepository;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LeaveRoomService {
    private final RoomRepository rooms;
    private final UserRepository users;
    private final ChatParticipantRepository participants;
    private final ChatMessageRepository messages;
    private final ChatThreadRepository threads;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void leave(Long userId, Long roomId) {
        // Use the same room lock as joins so membership changes are serialized.
        var room = rooms.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
        var user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "프로필 등록을 완료해 주세요.");
        }

        var thread = room.getChatThread();
        participants.deleteByChatThread_IdAndUser_Id(thread.getId(), userId);
        if (!room.getHost().getId().equals(userId)) {
            return;
        }

        var successor = participants.findFirstByChatThread_IdAndUser_IdNotOrderByJoinedAtAscIdAsc(thread.getId(), userId);
        if (successor.isPresent()) {
            room.transferHostTo(successor.get().getUser());
            return;
        }

        messages.deleteByChatThread_Id(thread.getId());
        rooms.delete(room);
        threads.delete(thread);
    }
}
