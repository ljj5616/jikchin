package com.jikchin.jikchin_app.auth.dto;

public record SocialLoginResult(
        boolean needProfile,
        String accessToken,
        String refreshToken
) {
}
