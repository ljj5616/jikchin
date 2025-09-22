package com.jikchin.jikchin_app.application.dto.profile;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompleteProfileResponse {
    private String accessToken;
    private String refreshToken;
}
