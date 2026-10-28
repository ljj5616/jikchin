package com.jikchin.jikchin_app.user.dto;

public record CompleteProfileResult(
        String accessToken,
        String refreshToken
) {
}
