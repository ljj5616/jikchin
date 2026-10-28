package com.jikchin.jikchin_app.user.dto;

public record CompleteProfileHttpResponse(
        String accessToken,
        String refreshToken
) {}
