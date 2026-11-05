package com.jikchin.jikchin_app.room.service;

import com.jikchin.jikchin_app.chat.domain.ChatParticipant;
import com.jikchin.jikchin_app.chat.repository.ChatParticipantRepository;
import com.jikchin.jikchin_app.chat.repository.ChatMessageRepository;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.repository.RoomRepository;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class JoinRoomService {
    private final RoomRepository rooms;
    private final UserRepository users;
    private final ChatParticipantRepository participants;
    private final ChatMessageRepository messages;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomResponse join(Long userId, Long roomId) {
        // Serialize joins per room; READ_COMMITTED sees participants committed by the preceding join.
        var room = rooms.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
        var user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "프로필 등록을 완료해 주세요.");
        }

        var thread = room.getChatThread();
        long count = participants.countByChatThread_Id(thread.getId());
        if (participants.existsByChatThread_IdAndUser_Id(thread.getId(), userId)) {
            return RoomResponse.from(room, count);
        }
        if (room.getGameDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "경기 날짜가 지난 방에는 참여할 수 없습니다.");
        }
        if (count >= thread.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "방 정원이 가득 찼습니다.");
        }

        participants.save(ChatParticipant.join(thread, user, messages.latestId(thread.getId())));
        return RoomResponse.from(room, count + 1);
    }
}
