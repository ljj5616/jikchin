package com.jikchin.jikchin_app.application.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SocialLoginRequest {
    private String provider;
    private String accessToken;
}
