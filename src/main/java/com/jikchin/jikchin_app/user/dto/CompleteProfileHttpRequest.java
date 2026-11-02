package com.jikchin.jikchin_app.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteProfileHttpRequest(
        @NotBlank @Size(max = 20) String nickname,
        String avatarKey,
        @Size(max = 160) String bio,
        Integer favoriteKboTeamId,
        Integer favoriteKleagueTeamId
) {}
