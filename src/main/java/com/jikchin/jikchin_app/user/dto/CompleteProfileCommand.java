package com.jikchin.jikchin_app.user.dto;

public record CompleteProfileCommand(
        Long userId,
        String nickname,
        String avatarKey,
        String bio,
        Integer favoriteKboTeamId,
        Integer favoriteKleagueTeamId
) {
}
