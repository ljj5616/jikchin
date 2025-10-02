package com.jikchin.jikchin_app.adapter.in.web.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteProfileHttpRequest(
        @NotBlank String nickname,
        String avatarKey,
        String bio,
        Integer favoriteKboTeamId,
        Integer favoriteKleagueTeamId
) {}
