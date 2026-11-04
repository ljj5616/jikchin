package com.jikchin.jikchin_app.room.dto;
import java.time.LocalDate;
public record RoomResponse(Long id, Long chatThreadId, String title, LocalDate gameDate, String stadium,
                           Integer homeTeamId, String homeTeam, Integer awayTeamId, String awayTeam,
                           int capacity, Long hostId, long participantCount) {}
