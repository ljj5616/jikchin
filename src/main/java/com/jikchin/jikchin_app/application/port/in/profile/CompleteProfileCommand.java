package com.jikchin.jikchin_app.application.port.in.profile;

public record CompleteProfileCommand(
        Long userId,
        String nickname,
        String avatarKey,
        String bio,
        Integer favoriteKboTeamId,
        Integer favoriteKleagueTeamId
) {
}
