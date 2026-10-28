package com.jikchin.jikchin_app.auth.dto;

public record SocialLoginCommand(
        String provider,
        String accessToken
) {
}
