package com.jikchin.jikchin_app.application.port.in.auth;

public record SocialLoginResult(
        boolean needProfile,
        String accessToken,
        String refreshToken
) {
}