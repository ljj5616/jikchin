package com.jikchin.jikchin_app.application.dto.profile;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompleteProfileRequest {
    @NotBlank
    private String nickname;
    private String avatarUrl;
    private String bio;
    private Integer favoriteKboTeamId;
    private Integer favoriteKleagueTeamId;
}
