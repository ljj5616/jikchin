package com.jikchin.jikchin_app.application.dto.profile;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompleteProfileRequest {
    private String nickname;
    private Integer favoriteKboTeamId;
    private Integer favoriteKleagueTeamId;
}
