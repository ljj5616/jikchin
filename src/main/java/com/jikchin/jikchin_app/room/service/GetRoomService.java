package com.jikchin.jikchin_app.room.service;

import com.jikchin.jikchin_app.chat.repository.ChatParticipantRepository;
import com.jikchin.jikchin_app.room.domain.Room;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRoomService {
    private final RoomRepository rooms;
    private final ChatParticipantRepository participants;

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

    private RoomResponse response(Room room) {
        return RoomResponse.from(room, participants.countByChatThread_Id(room.getChatThread().getId()));
    }
}
