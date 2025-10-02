package com.jikchin.jikchin_app.application.port.in.profile;

public record CompleteProfileResult(
        String accessToken,
        String refreshToken
) {
}
