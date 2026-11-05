package com.jikchin.jikchin_app.user.dto;

import com.jikchin.jikchin_app.user.dto.MyProfileResponse.FavoriteTeamResponse;

public record UserProfileResponse(Long id, String nickname, String avatarUrl, String bio,
                                  FavoriteTeamResponse favoriteKboTeam, FavoriteTeamResponse favoriteKleagueTeam,
                                  boolean blocked) {}
