package com.jikchin.jikchin_app.room.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record CreateRoomRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull @FutureOrPresent LocalDate gameDate,
        @NotBlank @Size(max = 100) String stadium,
        @NotNull Integer homeTeamId,
        @NotNull Integer awayTeamId,
        @NotNull @Min(2) @Max(100) Integer capacity) {}
