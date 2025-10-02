package com.jikchin.jikchin_app.application.port.in.auth;

public record SocialLoginCommand(
        String provider,
        String accessToken
) {
}