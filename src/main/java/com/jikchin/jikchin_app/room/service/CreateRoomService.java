package com.jikchin.jikchin_app.room.service;

import com.jikchin.jikchin_app.chat.domain.ChatParticipant;
import com.jikchin.jikchin_app.chat.domain.ChatThread;
import com.jikchin.jikchin_app.chat.repository.ChatParticipantRepository;
import com.jikchin.jikchin_app.chat.repository.ChatThreadRepository;
import com.jikchin.jikchin_app.game.repository.TeamRepository;
import com.jikchin.jikchin_app.room.domain.Room;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.repository.RoomRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CreateRoomService {
    private final UserRepository users;
    private final TeamRepository teams;
    private final ChatThreadRepository threads;
    private final ChatParticipantRepository participants;
    private final RoomRepository rooms;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomResponse create(Long userId, CreateRoomRequest request) {
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (users.findStatusById(userId).orElseThrow() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "프로필 등록을 완료해 주세요.");
        }
        var home = teams.findById(request.homeTeamId()).orElseThrow(() -> invalidTeam());
        var away = teams.findById(request.awayTeamId()).orElseThrow(() -> invalidTeam());
        if (home.getId().equals(away.getId()) || !home.getLeague().getId().equals(away.getLeague().getId())
                || !home.isOnboardingSelectable() || !away.isOnboardingSelectable()) throw invalidTeam();
        var thread = threads.save(ChatThread.createGroup(user, request.capacity()));
        participants.save(ChatParticipant.join(thread, user));
        var room = rooms.save(Room.create(request.title().trim(), request.gameDate(), request.stadium().trim(), home, away, thread));
        return RoomResponse.from(room, 1);
    }
    private ResponseStatusException invalidTeam() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 리그의 서로 다른 팀을 선택해 주세요.");
    }
}
