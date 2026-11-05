package com.jikchin.jikchin_app.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateMyProfileRequest(
        @NotBlank @Size(max = 20) String nickname,
        @Size(max = 200) String avatarKey,
        @Size(max = 160) String bio,
        @Positive Integer favoriteKboTeamId,
        @Positive Integer favoriteKleagueTeamId
) {}
