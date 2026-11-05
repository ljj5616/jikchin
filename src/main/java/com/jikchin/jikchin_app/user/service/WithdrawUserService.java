package com.jikchin.jikchin_app.user.service;

import com.jikchin.jikchin_app.auth.infrastructure.session.RefreshSessionRepository;
import com.jikchin.jikchin_app.chat.repository.ChatParticipantRepository;
import com.jikchin.jikchin_app.moderation.repository.BlockRepository;
import com.jikchin.jikchin_app.record.repository.RecordRepository;
import com.jikchin.jikchin_app.room.repository.RoomRepository;
import com.jikchin.jikchin_app.room.service.LeaveRoomService;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class WithdrawUserService {
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final RefreshSessionRepository sessions;
    private final RoomRepository rooms;
    private final LeaveRoomService leaveRooms;
    private final ChatParticipantRepository participants;
    private final BlockRepository blocks;
    private final RecordRepository records;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(Long userId) {
        // Match join/leave/report lock order: rooms (ascending), then the user.
        List<Long> lockedRooms = new ArrayList<>();
        var userIdsToLock = new TreeSet<Long>();
        userIdsToLock.add(userId);
        for (Long roomId : rooms.findWithdrawalRoomIds(userId)) {
            var room = rooms.findByIdForUpdate(roomId);
            if (room.isEmpty()) continue;
            lockedRooms.add(roomId);
            if (room.get().getHost().getId().equals(userId)) {
                participants.findFirstByChatThread_IdAndUser_IdNotOrderByJoinedAtAscIdAsc(room.get().getChatThread().getId(), userId)
                        .ifPresent(successor -> userIdsToLock.add(successor.getUser().getId()));
            }
        }
        // A host update checks the successor's FK too. Lock successors in the same
        // user-ID order as block/join, before holding any higher-numbered user lock.
        for (Long id : userIdsToLock) {
            users.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }
        // Open-in-view may already have loaded this user before waiting for the lock.
        var status = users.findStatusById(userId).orElseThrow();
        if (status == UserStatus.DELETED) return;
        if (status != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 회원만 탈퇴할 수 있습니다.");
        }
        // Creation and new joins also lock the user. If one finished during the room scan,
        // roll back before changing anything instead of acquiring rooms in the reverse order.
        if (!lockedRooms.containsAll(rooms.findWithdrawalRoomIds(userId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "참여 중인 방이 변경되었습니다. 탈퇴를 다시 요청해 주세요.");
        }
        for (Long roomId : lockedRooms) leaveRooms.leave(userId, roomId);
        // Also remove memberships in legacy/direct threads without a recruiting room.
        participants.deleteByUser_Id(userId);
        sessions.deleteAllForUser(userId);
        blocks.deleteAllForUser(userId);
        records.deleteByUser_Id(userId);
        profiles.findById(userId).ifPresent(profiles::delete);
        // Keep an anonymous user row for shared chat, reports and point history FKs.
        users.getReferenceById(userId).withdraw();
        users.flush();
    }
}
