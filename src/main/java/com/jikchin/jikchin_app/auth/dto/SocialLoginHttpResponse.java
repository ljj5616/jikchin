package com.jikchin.jikchin_app.auth.dto;

public record SocialLoginHttpResponse(
        boolean needProfile,
        String accessToken,
        String refreshToken
) {
}
