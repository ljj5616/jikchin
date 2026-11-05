package com.jikchin.jikchin_app.room.service;

import com.jikchin.jikchin_app.chat.domain.ChatParticipant;
import com.jikchin.jikchin_app.chat.repository.ChatParticipantRepository;
import com.jikchin.jikchin_app.moderation.service.BlockService;
import com.jikchin.jikchin_app.moderation.service.BlockedNickname;
import com.jikchin.jikchin_app.room.domain.Room;
import com.jikchin.jikchin_app.room.dto.RoomParticipantResponse;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRoomService {
    private final RoomRepository rooms;
    private final ChatParticipantRepository participants;
    private final BlockService blocks;

    @Value("${app.assets.base-url}")
    private String assetsBaseUrl;
    @Value("${app.assets.default-avatar-key}")
    private String defaultAvatarKey;

    public List<RoomResponse> list() {
        return rooms.findByGameDateGreaterThanEqualOrderByGameDateAscIdAsc(LocalDate.now()).stream()
                .map(this::response)
                .toList();
    }

    public List<RoomResponse> mine(Long userId) {
        return rooms.findParticipatingRooms(userId).stream()
                .map(this::response)
                .toList();
    }

    public RoomResponse detail(Long roomId) {
        return response(findRoom(roomId));
    }

    public List<RoomParticipantResponse> participants(Long viewerId, Long roomId) {
        var room = findRoom(roomId);
        var thread = room.getChatThread();
        Long hostId = room.getHost().getId();
        var blockedIds = blocks.blockedUserIds(viewerId);
        return participants.findByChatThread_IdOrderByJoinedAtAscIdAsc(thread.getId()).stream()
                .map(participant -> participantResponse(participant, hostId, blockedIds))
                .toList();
    }

    private Room findRoom(Long roomId) {
        return rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."));
    }

    private RoomParticipantResponse participantResponse(ChatParticipant participant, Long hostId, Set<Long> blockedIds) {
        var user = participant.getUser();
        var profile = user.getProfile();
        String avatarKey = profile == null ? defaultAvatarKey : profile.getAvatarKey();
        String avatarUrl = UriComponentsBuilder.fromUriString(assetsBaseUrl)
                .pathSegment(avatarKey.split("/"))
                .build().encode().toUriString();
        String nickname = BlockedNickname.format(profile == null ? null : profile.getNickname(), blockedIds.contains(user.getId()));
        return new RoomParticipantResponse(user.getId(), nickname,
                avatarUrl, user.getId().equals(hostId), participant.getJoinedAt());
    }

    private RoomResponse response(Room room) {
        return RoomResponse.from(room, participants.countByChatThread_Id(room.getChatThread().getId()));
    }
}
