package com.jikchin.jikchin_app.adapter.in.web.auth.dto;

public record SocialLoginHttpResponse(
        boolean needProfile,
        String accessToken,
        String refreshToken
) {
}