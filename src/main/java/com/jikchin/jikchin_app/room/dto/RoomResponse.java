package com.jikchin.jikchin_app.room.dto;

import com.jikchin.jikchin_app.room.domain.Room;
import java.time.LocalDate;

public record RoomResponse(Long id, Long chatThreadId, String title, LocalDate gameDate, String stadium,
                           Integer homeTeamId, String homeTeam, Integer awayTeamId, String awayTeam,
                           int capacity, Long hostId, long participantCount) {
    public static RoomResponse from(Room room, long participantCount) {
        var thread = room.getChatThread();
        return new RoomResponse(room.getId(), thread.getId(), room.getTitle(), room.getGameDate(), room.getStadium(),
                room.getHomeTeam().getId(), room.getHomeTeam().getName(), room.getAwayTeam().getId(), room.getAwayTeam().getName(),
                thread.getCapacity(), room.getHost().getId(), participantCount);
    }
}
