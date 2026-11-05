package com.jikchin.jikchin_app.user.dto;

import com.jikchin.jikchin_app.game.domain.Team;
import com.jikchin.jikchin_app.user.domain.Profile;

public record MyProfileResponse(
        Long id,
        String nickname,
        String avatarKey,
        String avatarUrl,
        String bio,
        FavoriteTeamResponse favoriteKboTeam,
        FavoriteTeamResponse favoriteKleagueTeam
) {
    public static MyProfileResponse from(Profile profile, String avatarUrl) {
        return new MyProfileResponse(profile.getUserId(), profile.getNickname(), profile.getAvatarKey(), avatarUrl,
                profile.getBio(), FavoriteTeamResponse.from(profile.getFavoriteKboTeam()),
                FavoriteTeamResponse.from(profile.getFavoriteKleagueTeam()));
    }

    public record FavoriteTeamResponse(Integer id, String name, String shortName, String logoUrl) {
        private static FavoriteTeamResponse from(Team team) {
            return team == null ? null : new FavoriteTeamResponse(team.getId(), team.getName(),
                    team.getShortName(), team.getLogoUrl());
        }
    }
}
